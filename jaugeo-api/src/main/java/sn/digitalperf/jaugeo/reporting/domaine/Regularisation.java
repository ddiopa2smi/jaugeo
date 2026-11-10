package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Correction d'un mois clos, comptee dans le resultat du mois comptable. En insertion seule : une regularisation
 * erronee se corrige par une regularisation contraire. Le montant est l'effet sur le resultat (positif = l'ameliore).
 */
@Entity
@Table(schema = "reporting", name = "regularisation")
public class Regularisation extends EntiteAuditee {

    public enum Poste { CHIFFRE_AFFAIRES, COUT_ACHAT, STOCK, DEPENSES, AUTRE }

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "mois_concerne", nullable = false, updatable = false)
    private LocalDate moisConcerne;

    @Column(name = "mois_comptable", nullable = false, updatable = false)
    private LocalDate moisComptable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Poste poste;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Column(nullable = false, updatable = false)
    private String motif;

    @Column(updatable = false)
    private String reference;

    protected Regularisation() {
    }

    public Regularisation(long stationId, YearMonth concerne, YearMonth comptable, Poste poste, BigDecimal montant,
                          String motif, String reference) {
        if (poste == null) {
            throw ErreurMetier.regle("POSTE_OBLIGATOIRE", "Indiquez le poste du compte d'exploitation à corriger.");
        }
        if (montant == null || montant.signum() == 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant d'une régularisation ne peut pas être nul.");
        }
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Expliquez la régularisation : elle reste dans l'historique.");
        }
        this.stationId = stationId;
        this.moisConcerne = concerne.atDay(1);
        this.moisComptable = comptable.atDay(1);
        this.poste = poste;
        this.montant = montant.setScale(0, java.math.RoundingMode.HALF_UP);
        this.motif = motif.trim();
        this.reference = reference == null || reference.isBlank() ? null : reference.trim();
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getMoisConcerne() {
        return moisConcerne;
    }

    public LocalDate getMoisComptable() {
        return moisComptable;
    }

    public Poste getPoste() {
        return poste;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public String getMotif() {
        return motif;
    }

    public String getReference() {
        return reference;
    }
}
