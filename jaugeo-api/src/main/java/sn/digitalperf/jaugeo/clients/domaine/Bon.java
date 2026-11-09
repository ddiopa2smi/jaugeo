package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Bon a credit saisi dans la journee : litres d'un produit servis a un client en compte, valorises au prix du jour
 * diminue de la remise contractuelle du client (montant du par le client, arrondi au franc). La valeur a la pompe
 * (sans remise) est celle qui sort de la recette de la station. Le mode de recouvrement est celui du client au moment
 * de la saisie.
 * <p>
 * Un bon hors regle (plafond, echeance, engin non autorise, contrat) est enregistre en attente : la journee ne peut
 * pas etre cloturee tant que la direction ne l'a pas accorde ou refuse (bon alors a recouvrer par le gerant).
 */
@Entity
@Table(schema = "clients", name = "bon")
public class Bon extends EntiteAuditee {

    public enum Statut { VALIDE, EN_ATTENTE, ACCORDE, REFUSE }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String numero;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(nullable = false)
    private BigDecimal litres;

    @Column(name = "prix_unitaire", nullable = false)
    private BigDecimal prixUnitaire;

    @Column(name = "remise_unitaire", nullable = false)
    private BigDecimal remiseUnitaire = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(name = "montant_pompe", nullable = false)
    private BigDecimal montantPompe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Client.Recouvrement recouvrement;

    private String immatriculation;

    private String porteur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.VALIDE;

    @Column(name = "motif_blocage")
    private String motifBlocage;

    @Column(name = "derogation_par")
    private String derogationPar;

    @Column(name = "derogation_le")
    private Instant derogationLe;

    @Column(name = "derogation_motif")
    private String derogationMotif;

    @Column(name = "facture_id")
    private Long factureId;

    protected Bon() {
    }

    public Bon(long journeeId, long stationId, LocalDate dateJour) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
    }

    /**
     * Renseigne le bon ; prix a la pompe a l'ouverture de la journee et remise au litre du client a cette date.
     * La decision eventuelle de la direction est conservee par {@link #controler}.
     */
    public void renseigner(Client client, String numero, long produitId, BigDecimal litres, BigDecimal prixPompe,
                           BigDecimal remise, String immatriculation, String porteur) {
        if (!client.isActif() && !client.getId().equals(clientId)) {
            throw ErreurMetier.regle("CLIENT_INACTIF", "Le client " + client.getNom() + " n'est plus actif : aucun bon ne peut lui être accordé.");
        }
        if (factureId != null) {
            throw ErreurMetier.regle("BON_FACTURE", "Le bon n° " + this.numero + " est facturé : annulez d'abord la facture.");
        }
        if (numero == null || numero.isBlank()) {
            throw ErreurMetier.regle("NUMERO_OBLIGATOIRE", "Indiquez le numéro du bon.");
        }
        if (litres == null || litres.signum() <= 0 || litres.stripTrailingZeros().scale() > 2) {
            throw ErreurMetier.regle("LITRES_INVALIDES", "Les litres servis sont supérieurs à zéro, avec deux décimales au plus.");
        }
        BigDecimal r = remise == null ? BigDecimal.ZERO : remise;
        if (r.compareTo(prixPompe) >= 0) {
            throw ErreurMetier.regle("REMISE_INVALIDE", "La remise du client (" + Libelles.francs(r) + " par litre) atteint le prix à la pompe.");
        }
        this.clientId = client.getId();
        this.numero = numero.trim().toUpperCase();
        this.produitId = produitId;
        this.litres = litres;
        this.remiseUnitaire = r;
        this.prixUnitaire = prixPompe.subtract(r);
        this.montant = litres.multiply(prixUnitaire).setScale(0, RoundingMode.HALF_UP);
        this.montantPompe = litres.multiply(prixPompe).setScale(0, RoundingMode.HALF_UP);
        this.recouvrement = client.getRecouvrement();
        this.immatriculation = EnginClient.normaliser(immatriculation);
        this.porteur = porteur == null || porteur.isBlank() ? null : porteur.trim();
    }

    /**
     * Statut apres saisie : sans motif de blocage, le bon est valide ; sinon il attend la direction. Une decision
     * deja prise est conservee si la correction ne change pas le client et n'augmente pas le montant.
     */
    public void controler(List<String> motifs, boolean conserverDecision) {
        if (conserverDecision && (statut == Statut.ACCORDE || statut == Statut.REFUSE)) {
            if (statut == Statut.REFUSE) {
                recouvrement = Client.Recouvrement.GERANT;
            }
            return;
        }
        derogationPar = null;
        derogationLe = null;
        derogationMotif = null;
        if (motifs.isEmpty()) {
            statut = Statut.VALIDE;
            motifBlocage = null;
        } else {
            statut = Statut.EN_ATTENTE;
            String m = String.join(" ", motifs);
            motifBlocage = m.length() > 500 ? m.substring(0, 497) + "..." : m;
        }
    }

    /** Decision de la direction sur un bon en attente : accord (derogation tracee) ou refus (a recouvrer par le gerant). */
    public void decider(boolean accord, String motif, String par, Instant le) {
        if (statut != Statut.EN_ATTENTE) {
            throw ErreurMetier.conflit("BON_NON_EN_ATTENTE", resume() + " n'attend pas de dérogation.");
        }
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif de la décision.");
        }
        statut = accord ? Statut.ACCORDE : Statut.REFUSE;
        if (!accord) {
            recouvrement = Client.Recouvrement.GERANT;
        }
        derogationPar = par;
        derogationLe = le;
        derogationMotif = motif.trim();
    }

    public void facturer(long factureId) {
        this.factureId = factureId;
    }

    public void liberer() {
        this.factureId = null;
    }

    public String resume() {
        return "Bon n° " + numero + " (" + Libelles.francs(montant) + ")";
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getNumero() {
        return numero;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getRemiseUnitaire() {
        return remiseUnitaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public BigDecimal getMontantPompe() {
        return montantPompe;
    }

    public Client.Recouvrement getRecouvrement() {
        return recouvrement;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public String getPorteur() {
        return porteur;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getMotifBlocage() {
        return motifBlocage;
    }

    public String getDerogationPar() {
        return derogationPar;
    }

    public Instant getDerogationLe() {
        return derogationLe;
    }

    public String getDerogationMotif() {
        return derogationMotif;
    }

    public Long getFactureId() {
        return factureId;
    }
}
