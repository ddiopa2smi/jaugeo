package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Budget de ventes d'un produit dans une station pour un mois : litres et chiffre d'affaires. */
@Entity
@Table(schema = "reporting", name = "budget_vente")
public class BudgetVente extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false, updatable = false)
    private LocalDate mois;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false)
    private BigDecimal litres;

    @Column(nullable = false)
    private BigDecimal montant;

    protected BudgetVente() {
    }

    public BudgetVente(long stationId, YearMonth mois, long produitId) {
        this.stationId = stationId;
        this.mois = mois.atDay(1);
        this.produitId = produitId;
    }

    public void fixer(BigDecimal litres, BigDecimal montant) {
        this.litres = litres;
        this.montant = montant;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getMois() {
        return mois;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
