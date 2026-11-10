package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Poste (matin, soir, nuit) d'une journee de station : porte les feuilles de caisse de ses pompistes. */
@Entity
@Table(schema = "personnel", name = "poste")
public class Poste extends EntiteAuditee {

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(name = "poste", nullable = false, updatable = false)
    private TypePoste type;

    private String observation;

    protected Poste() {
    }

    public Poste(long journeeId, long stationId, LocalDate dateJour, TypePoste type) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
        this.type = type;
    }

    public void observer(String observation) {
        this.observation = Textes.texte(observation);
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

    public TypePoste getType() {
        return type;
    }

    public String getObservation() {
        return observation;
    }
}
