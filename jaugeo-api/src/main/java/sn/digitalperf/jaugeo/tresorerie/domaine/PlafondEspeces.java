package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Plafond d'especes non versees d'une station : au-dela, une alerte signale le risque (vol, versement oublie). */
@Entity
@Table(schema = "tresorerie", name = "plafond_especes")
public class PlafondEspeces extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false)
    private BigDecimal plafond;

    protected PlafondEspeces() {
    }

    public PlafondEspeces(long stationId) {
        this.stationId = stationId;
    }

    public void fixer(BigDecimal plafond) {
        this.plafond = plafond;
    }

    public Long getStationId() {
        return stationId;
    }

    public BigDecimal getPlafond() {
        return plafond;
    }
}
