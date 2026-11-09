package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Vente d'un article dans une journee, au prix de vente du jour ; CMUP de sortie fige a la cloture. */
@Entity
@Table(schema = "stock", name = "vente_article")
public class VenteArticle extends EntiteAuditee {

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(nullable = false)
    private BigDecimal quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private BigDecimal prixUnitaire;

    @Column(nullable = false)
    private BigDecimal montant;

    private BigDecimal cmup;

    protected VenteArticle() {
    }

    public VenteArticle(long journeeId, long stationId, long produitId, LocalDate dateJour) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.produitId = produitId;
        this.dateJour = dateJour;
    }

    public void vendre(BigDecimal quantite, BigDecimal prixUnitaire) {
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
        this.montant = quantite.multiply(prixUnitaire).setScale(0, RoundingMode.HALF_UP);
    }

    public void figer(BigDecimal cmup) {
        this.cmup = cmup;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public BigDecimal getCmup() {
        return cmup;
    }
}
