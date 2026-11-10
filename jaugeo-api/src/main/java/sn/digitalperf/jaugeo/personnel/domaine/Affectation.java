package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Passage d'un employe dans une station ; fin nulle = affectation en cours. */
@Entity
@Table(schema = "personnel", name = "affectation")
public class Affectation extends EntiteAuditee {

    @Column(name = "employe_id", nullable = false, updatable = false)
    private Long employeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false, updatable = false)
    private LocalDate debut;

    private LocalDate fin;

    protected Affectation() {
    }

    public Affectation(long employeId, long stationId, LocalDate debut) {
        this.employeId = employeId;
        this.stationId = stationId;
        this.debut = debut;
    }

    public void terminer(LocalDate le) {
        this.fin = le.isBefore(debut) ? debut : le;
    }

    public Long getEmployeId() {
        return employeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDebut() {
        return debut;
    }

    public LocalDate getFin() {
        return fin;
    }
}
