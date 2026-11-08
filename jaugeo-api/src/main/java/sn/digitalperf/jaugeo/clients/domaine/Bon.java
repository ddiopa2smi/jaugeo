package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Bon a credit saisi dans la journee : litres d'un produit servis a un client en compte, valorises au prix du jour
 * (montant arrondi au franc). Le mode de recouvrement est celui du client au moment de la saisie.
 */
@Entity
@Table(schema = "clients", name = "bon")
public class Bon extends EntiteAuditee {

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

    @Column(nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Client.Recouvrement recouvrement;

    private String immatriculation;

    private String porteur;

    protected Bon() {
    }

    public Bon(long journeeId, long stationId, LocalDate dateJour) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
    }

    public void renseigner(Client client, String numero, long produitId, BigDecimal litres, BigDecimal prixUnitaire,
                           String immatriculation, String porteur) {
        if (!client.isActif() && !client.getId().equals(clientId)) {
            throw ErreurMetier.regle("CLIENT_INACTIF", "Le client " + client.getNom() + " n'est plus actif : aucun bon ne peut lui être accordé.");
        }
        if (numero == null || numero.isBlank()) {
            throw ErreurMetier.regle("NUMERO_OBLIGATOIRE", "Indiquez le numéro du bon.");
        }
        if (litres == null || litres.signum() <= 0 || litres.stripTrailingZeros().scale() > 2) {
            throw ErreurMetier.regle("LITRES_INVALIDES", "Les litres servis sont supérieurs à zéro, avec deux décimales au plus.");
        }
        this.clientId = client.getId();
        this.numero = numero.trim().toUpperCase();
        this.produitId = produitId;
        this.litres = litres;
        this.prixUnitaire = prixUnitaire;
        this.montant = litres.multiply(prixUnitaire).setScale(0, RoundingMode.HALF_UP);
        this.recouvrement = client.getRecouvrement();
        this.immatriculation = immatriculation == null || immatriculation.isBlank() ? null : immatriculation.trim().toUpperCase();
        this.porteur = porteur == null || porteur.isBlank() ? null : porteur.trim();
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

    public BigDecimal getMontant() {
        return montant;
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
}
