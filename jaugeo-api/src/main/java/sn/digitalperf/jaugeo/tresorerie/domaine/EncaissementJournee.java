package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Total encaisse dans la journee par un moyen electronique (Wave, Orange Money, carte bancaire) : ces fonds
 * arrivent directement sur les comptes de la societe et sortent des especes que le gerant doit verser.
 */
@Entity
@Table(schema = "tresorerie", name = "encaissement_journee")
public class EncaissementJournee extends EntiteAuditee {

    public enum Mode { WAVE, ORANGE_MONEY, CARTE }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Mode mode;

    @Column(nullable = false)
    private BigDecimal montant;

    protected EncaissementJournee() {
    }

    public EncaissementJournee(long journeeId, long stationId, LocalDate dateJour, Mode mode) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
        this.mode = mode;
    }

    public void encaisser(BigDecimal montant) {
        this.montant = montant;
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

    public Mode getMode() {
        return mode;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
