package sn.digitalperf.jaugeo.referentiel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

@Entity
@Table(schema = "referentiel", name = "pompe")
public class Pompe extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false, updatable = false)
    private Station station;

    @Column(nullable = false, updatable = false)
    private String code;

    private String marque;

    @Column(name = "numero_serie")
    private String numeroSerie;

    @Column(nullable = false)
    private boolean actif = true;

    protected Pompe() {
    }

    public Pompe(Station station, String code) {
        this.station = station;
        this.code = code;
    }

    public void modifier(String marque, String numeroSerie, boolean actif) {
        this.marque = marque;
        this.numeroSerie = numeroSerie;
        this.actif = actif;
    }

    public Station getStation() {
        return station;
    }

    public String getCode() {
        return code;
    }

    public String getMarque() {
        return marque;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public boolean isActif() {
        return actif;
    }
}
