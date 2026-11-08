package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Observation du controle de gestion sur la synthese mensuelle d'une station (colonne OBSERVATIONS). */
@Entity
@Table(schema = "reporting", name = "commentaire_mensuel")
public class CommentaireMensuel extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(nullable = false, updatable = false)
    private LocalDate mois;

    @Column(nullable = false)
    private String texte;

    protected CommentaireMensuel() {
    }

    public CommentaireMensuel(long stationId, YearMonth mois) {
        this.stationId = stationId;
        this.mois = mois.atDay(1);
    }

    public void rediger(String texte) {
        this.texte = texte.trim();
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getMois() {
        return mois;
    }

    public String getTexte() {
        return texte;
    }
}
