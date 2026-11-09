package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.security.SecureRandom;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Carte carburant prepayee, nominative (client, porteur, immatriculation eventuelle), utilisable dans toute station.
 * Numero unique « JC » + 10 chiffres dont une cle de Luhn, porte par un QR code. Le solde (recharges - pleins) est
 * tenu sur la carte ; le verrouillage optimiste empeche deux debits simultanes de le rendre negatif.
 */
@Entity
@Table(schema = "clients", name = "carte")
public class Carte extends EntiteAuditee {

    public enum Statut { ACTIVE, BLOQUEE, ANNULEE }

    private static final SecureRandom HASARD = new SecureRandom();

    @Column(nullable = false, updatable = false)
    private String numero;

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(nullable = false)
    private String porteur;

    private String immatriculation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIVE;

    @Column(nullable = false)
    private BigDecimal solde = BigDecimal.ZERO;

    protected Carte() {
    }

    public Carte(String numero, long clientId) {
        this.numero = numero;
        this.clientId = clientId;
    }

    /** Nouveau numero : JC + 9 chiffres aleatoires + cle de Luhn. */
    public static String nouveauNumero() {
        StringBuilder chiffres = new StringBuilder();
        chiffres.append(1 + HASARD.nextInt(9));
        for (int i = 0; i < 8; i++) {
            chiffres.append(HASARD.nextInt(10));
        }
        return "JC" + chiffres + cleLuhn(chiffres.toString());
    }

    /** Numero saisi ou lu dans un QR code : forme canonique, cle de Luhn verifiee ; vide si invalide. */
    public static String normaliser(String saisi) {
        if (saisi == null) {
            return null;
        }
        String s = saisi.trim().toUpperCase().replace(" ", "").replace("-", "");
        if (s.startsWith("JAUGEO:CARTE:")) {
            s = s.substring("JAUGEO:CARTE:".length());
        }
        if (!s.matches("JC\\d{10}")) {
            return null;
        }
        String corps = s.substring(2, 11);
        return cleLuhn(corps) == s.charAt(11) - '0' ? s : null;
    }

    static int cleLuhn(String chiffres) {
        int somme = 0;
        boolean doubler = true;
        for (int i = chiffres.length() - 1; i >= 0; i--) {
            int d = chiffres.charAt(i) - '0';
            if (doubler) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            somme += d;
            doubler = !doubler;
        }
        return (10 - somme % 10) % 10;
    }

    public void modifier(String porteur, String immatriculation) {
        if (porteur == null || porteur.isBlank()) {
            throw ErreurMetier.regle("PORTEUR_OBLIGATOIRE", "Indiquez le porteur de la carte.");
        }
        this.porteur = porteur.trim();
        this.immatriculation = EnginClient.normaliser(immatriculation);
    }

    public void changerStatut(Statut nouveau) {
        if (statut == Statut.ANNULEE) {
            throw ErreurMetier.conflit("CARTE_ANNULEE", "La carte " + numero + " est annulée.");
        }
        if (nouveau == Statut.ANNULEE && solde.signum() > 0) {
            throw ErreurMetier.regle("CARTE_SOLDE_RESTANT", "La carte " + numero + " porte encore " + Libelles.francs(solde)
                    + " : bloquez-la, ou videz son solde avant de l'annuler.");
        }
        statut = nouveau;
    }

    public void crediter(BigDecimal montant) {
        if (statut == Statut.ANNULEE) {
            throw ErreurMetier.regle("CARTE_ANNULEE", "La carte " + numero + " est annulée.");
        }
        solde = solde.add(montant);
    }

    /** Plein paye par la carte : carte active et solde suffisant. */
    public void debiter(BigDecimal montant) {
        if (statut != Statut.ACTIVE) {
            throw ErreurMetier.regle("CARTE_" + statut.name(), "La carte " + numero + " est "
                    + (statut == Statut.BLOQUEE ? "bloquée" : "annulée") + " : elle ne peut pas payer de carburant.");
        }
        if (solde.compareTo(montant) < 0) {
            throw ErreurMetier.regle("CARTE_SOLDE_INSUFFISANT", "Solde de la carte " + numero + " insuffisant : "
                    + Libelles.francs(solde) + " pour un plein de " + Libelles.francs(montant) + ".");
        }
        solde = solde.subtract(montant);
    }

    /** Retrait d'un plein saisi par erreur : le montant revient sur la carte, quel que soit son statut. */
    public void restituer(BigDecimal montant) {
        solde = solde.add(montant);
    }

    public String getNumero() {
        return numero;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getPorteur() {
        return porteur;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public Statut getStatut() {
        return statut;
    }

    public BigDecimal getSolde() {
        return solde;
    }
}
