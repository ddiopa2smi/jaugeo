package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Recette d'une journee figee a la cloture : ce que le gerant doit verser pour ce jour. */
@Entity
@Table(schema = "tresorerie", name = "recette_journee")
public class RecetteJournee extends EntiteAuditee {

    /** Composantes du montant a verser. */
    public record Composantes(BigDecimal ventes, BigDecimal bonsDirection, BigDecimal bonsGerant, BigDecimal depenses,
                              BigDecimal complements) {

        public BigDecimal montantAVerser() {
            return CalculCaisse.montantAVerser(ventes, bonsDirection, bonsGerant, depenses, complements);
        }
    }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(nullable = false, updatable = false)
    private BigDecimal ventes;

    @Column(name = "bons_direction", nullable = false, updatable = false)
    private BigDecimal bonsDirection;

    @Column(name = "bons_gerant", nullable = false, updatable = false)
    private BigDecimal bonsGerant;

    @Column(nullable = false, updatable = false)
    private BigDecimal depenses;

    @Column(nullable = false, updatable = false)
    private BigDecimal complements;

    @Column(name = "montant_a_verser", nullable = false, updatable = false)
    private BigDecimal montantAVerser;

    protected RecetteJournee() {
    }

    public RecetteJournee(long journeeId, long stationId, LocalDate dateJour, Composantes c) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
        this.ventes = c.ventes();
        this.bonsDirection = c.bonsDirection();
        this.bonsGerant = c.bonsGerant();
        this.depenses = c.depenses();
        this.complements = c.complements();
        this.montantAVerser = c.montantAVerser();
    }

    public Composantes composantes() {
        return new Composantes(ventes, bonsDirection, bonsGerant, depenses, complements);
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

    public BigDecimal getMontantAVerser() {
        return montantAVerser;
    }
}
