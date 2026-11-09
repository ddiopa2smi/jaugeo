package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Echeance du remboursement d'un manquant : montant attendu a une date. */
@Entity
@Table(schema = "tresorerie", name = "echeance_manquant")
public class EcheanceManquant extends EntiteAuditee {

    public static final int NOMBRE_MAX = 24;

    @Column(name = "manquant_id", nullable = false, updatable = false)
    private Long manquantId;

    @Column(nullable = false, updatable = false)
    private int rang;

    @Column(name = "date_echeance", nullable = false, updatable = false)
    private LocalDate dateEcheance;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    protected EcheanceManquant() {
    }

    public EcheanceManquant(long manquantId, int rang, LocalDate dateEcheance, BigDecimal montant) {
        this.manquantId = manquantId;
        this.rang = rang;
        this.dateEcheance = dateEcheance;
        this.montant = montant;
    }

    /**
     * Echeances mensuelles egales (au franc pres, arrondies a la centaine sauf la derniere) du reste du, la premiere
     * a la date indiquee.
     */
    public static List<EcheanceManquant> plan(long manquantId, BigDecimal reste, int nombre, LocalDate premiere) {
        if (nombre < 1 || nombre > NOMBRE_MAX) {
            throw ErreurMetier.regle("NOMBRE_ECHEANCES_INVALIDE", "L'échéancier compte de 1 à " + NOMBRE_MAX + " échéances.");
        }
        if (premiere == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date de la première échéance.");
        }
        BigDecimal part = reste.divide(BigDecimal.valueOf(nombre), 0, RoundingMode.DOWN);
        if (nombre > 1 && part.compareTo(BigDecimal.valueOf(100)) >= 0) {
            part = part.divide(BigDecimal.valueOf(100), 0, RoundingMode.UP).multiply(BigDecimal.valueOf(100));
        }
        List<EcheanceManquant> plan = new ArrayList<>();
        BigDecimal cumul = BigDecimal.ZERO;
        for (int i = 1; i <= nombre; i++) {
            BigDecimal m = i == nombre ? reste.subtract(cumul) : part.min(reste.subtract(cumul));
            if (m.signum() <= 0) {
                break;
            }
            plan.add(new EcheanceManquant(manquantId, i, premiere.plusMonths(i - 1L), m));
            cumul = cumul.add(m);
        }
        return plan;
    }

    public Long getManquantId() {
        return manquantId;
    }

    public int getRang() {
        return rang;
    }

    public LocalDate getDateEcheance() {
        return dateEcheance;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
