package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Jaugeage d'une cuve pour une journee : stock physique de fin de journee (CLOTURE), ou stock de depart
 * de la premiere journee suivie (OUVERTURE). Hauteur nulle = volume lu directement.
 * L'eau en fond de cuve (hauteur relevee a la pate a eau) est deduite : le volume est celui du produit.
 */
@Entity
@Table(schema = "stock", name = "jaugeage")
public class Jaugeage extends EntiteAuditee {

    public enum Moment { OUVERTURE, CLOTURE }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "cuve_id", nullable = false, updatable = false)
    private Long cuveId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Moment moment;

    @Column(name = "hauteur_mm")
    private Integer hauteurMm;

    @Column(name = "volume_litres", nullable = false)
    private BigDecimal volumeLitres;

    private String observation;

    @Column(name = "hauteur_eau_mm")
    private Integer hauteurEauMm;

    @Column(name = "volume_eau_litres")
    private BigDecimal volumeEauLitres;

    protected Jaugeage() {
    }

    public Jaugeage(long journeeId, long stationId, long cuveId, LocalDate dateJour, Moment moment) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.cuveId = cuveId;
        this.dateJour = dateJour;
        this.moment = moment;
    }

    public void mesurer(Integer hauteurMm, BigDecimal volumeLitres, String observation, Integer hauteurEauMm,
                        BigDecimal volumeEauLitres) {
        this.hauteurMm = hauteurMm;
        this.volumeLitres = volumeLitres;
        this.observation = observation == null || observation.isBlank() ? null : observation.trim();
        this.hauteurEauMm = hauteurEauMm;
        this.volumeEauLitres = volumeEauLitres;
    }

    public Integer getHauteurEauMm() {
        return hauteurEauMm;
    }

    public BigDecimal getVolumeEauLitres() {
        return volumeEauLitres;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getCuveId() {
        return cuveId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public Moment getMoment() {
        return moment;
    }

    public Integer getHauteurMm() {
        return hauteurMm;
    }

    public BigDecimal getVolumeLitres() {
        return volumeLitres;
    }

    public String getObservation() {
        return observation;
    }
}
