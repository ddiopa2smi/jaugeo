package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Fichier de releve importe ; son empreinte (SHA-256) interdit de l'importer deux fois. */
@Entity
@Table(schema = "tresorerie", name = "releve")
public class Releve extends EntiteAuditee {

    @Column(name = "format_id", nullable = false, updatable = false)
    private Long formatId;

    @Column(name = "station_id", updatable = false)
    private Long stationId;

    @Column(name = "nom_fichier", nullable = false, updatable = false)
    private String nomFichier;

    @Column(nullable = false, updatable = false)
    private String empreinte;

    @Column(name = "date_min")
    private LocalDate dateMin;

    @Column(name = "date_max")
    private LocalDate dateMax;

    @Column(nullable = false)
    private int operations;

    @Column(nullable = false)
    private int doublons;

    protected Releve() {
    }

    public Releve(long formatId, Long stationId, String nomFichier, String empreinte) {
        this.formatId = formatId;
        this.stationId = stationId;
        this.nomFichier = nomFichier;
        this.empreinte = empreinte;
    }

    public void bilan(LocalDate dateMin, LocalDate dateMax, int operations, int doublons) {
        this.dateMin = dateMin;
        this.dateMax = dateMax;
        this.operations = operations;
        this.doublons = doublons;
    }

    public Long getFormatId() {
        return formatId;
    }

    public Long getStationId() {
        return stationId;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getEmpreinte() {
        return empreinte;
    }

    public LocalDate getDateMin() {
        return dateMin;
    }

    public LocalDate getDateMax() {
        return dateMax;
    }

    public int getOperations() {
        return operations;
    }

    public int getDoublons() {
        return doublons;
    }
}
