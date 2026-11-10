package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Reglage du personnel d'une station : feuilles de caisse par pompiste exigees ou non a la cloture. */
@Entity
@Table(schema = "personnel", name = "parametre_station")
public class ParametreStation extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "feuilles_caisse", nullable = false)
    private boolean feuillesCaisse;

    protected ParametreStation() {
    }

    public ParametreStation(long stationId) {
        this.stationId = stationId;
    }

    public void activerFeuilles(boolean actif) {
        this.feuillesCaisse = actif;
    }

    public Long getStationId() {
        return stationId;
    }

    public boolean isFeuillesCaisse() {
        return feuillesCaisse;
    }
}
