package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Budget de depenses d'exploitation d'une station pour un mois. */
@Entity
@Table(schema = "reporting", name = "budget_depense")
public class BudgetDepense extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false, updatable = false)
    private LocalDate mois;

    @Column(nullable = false)
    private BigDecimal montant;

    protected BudgetDepense() {
    }

    public BudgetDepense(long stationId, YearMonth mois) {
        this.stationId = stationId;
        this.mois = mois.atDay(1);
    }

    public void fixer(BigDecimal montant) {
        this.montant = montant;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getMois() {
        return mois;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
