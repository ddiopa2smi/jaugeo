package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Creneau du planning : un employe tient un poste un jour donne. */
@Entity
@Table(schema = "personnel", name = "planning")
public class Creneau extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TypePoste poste;

    @Column(name = "employe_id", nullable = false, updatable = false)
    private Long employeId;

    protected Creneau() {
    }

    public Creneau(long stationId, LocalDate dateJour, TypePoste poste, long employeId) {
        this.stationId = stationId;
        this.dateJour = dateJour;
        this.poste = poste;
        this.employeId = employeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public TypePoste getPoste() {
        return poste;
    }

    public Long getEmployeId() {
        return employeId;
    }
}
