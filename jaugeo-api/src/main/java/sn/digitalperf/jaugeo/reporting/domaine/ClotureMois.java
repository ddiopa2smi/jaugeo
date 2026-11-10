package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Cloture d'un mois pour une station : le compte d'exploitation est fige, le mois ne recoit plus d'ecriture. */
@Entity
@Table(schema = "reporting", name = "cloture_mois")
public class ClotureMois extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false, updatable = false)
    private LocalDate mois;

    @Column(name = "cloture_par", nullable = false, updatable = false)
    private String cloturePar;

    @Column(name = "cloture_le", nullable = false, updatable = false)
    private Instant clotureLe;

    protected ClotureMois() {
    }

    public ClotureMois(long stationId, YearMonth mois, String par, Instant le) {
        this.stationId = stationId;
        this.mois = mois.atDay(1);
        this.cloturePar = par;
        this.clotureLe = le;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getMois() {
        return mois;
    }

    public String getCloturePar() {
        return cloturePar;
    }

    public Instant getClotureLe() {
        return clotureLe;
    }
}
