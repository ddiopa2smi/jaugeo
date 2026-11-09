package sn.digitalperf.jaugeo.clients.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Imputation des reglements d'un client sur ses dettes, de la plus ancienne a la plus recente (sans lettrage saisi) :
 * reprises de creances et factures par date, puis bons non encore factures. Sert a l'echeance (blocage des bons),
 * a la balance agee et au reste du de chaque facture.
 */
public final class Imputation {

    private Imputation() {
    }

    public enum Nature { REPRISE, FACTURE, NON_FACTURE }

    /** Dette du client : reprise ou facture (echeance connue), ou bon non facture (pas encore exigible). */
    public record Dette(Nature nature, String reference, LocalDate date, LocalDate echeance, BigDecimal montant, Long factureId) {
    }

    public record Reste(Dette dette, BigDecimal reste) {

        /** Exigible et non reglee a cette date (echeance depassee). */
        public boolean echue(LocalDate date) {
            return dette.echeance() != null && dette.echeance().isBefore(date) && reste.signum() > 0;
        }
    }

    /** Tranches de la balance agee selon l'anciennete du document (jours depuis sa date). */
    public record Tranches(BigDecimal nonFacture, BigDecimal jours0a30, BigDecimal jours31a60, BigDecimal jours61a90,
                           BigDecimal plusDe90, BigDecimal echu) {

        public BigDecimal total() {
            return nonFacture.add(jours0a30).add(jours31a60).add(jours61a90).add(plusDe90);
        }
    }

    /** Impute le total des reglements sur les dettes ; un excedent de reglement reste non impute. */
    public static List<Reste> imputer(List<Dette> dettes, BigDecimal reglements) {
        List<Dette> triees = new ArrayList<>(dettes);
        triees.sort(Comparator.comparing((Dette d) -> d.nature() == Nature.NON_FACTURE)
                .thenComparing(Dette::date).thenComparing(d -> d.nature() == Nature.FACTURE));
        BigDecimal disponible = reglements == null ? BigDecimal.ZERO : reglements;
        List<Reste> r = new ArrayList<>();
        for (Dette d : triees) {
            BigDecimal impute = disponible.min(d.montant()).max(BigDecimal.ZERO);
            disponible = disponible.subtract(impute);
            r.add(new Reste(d, d.montant().subtract(impute)));
        }
        return r;
    }

    /** Montant echu (echeance anterieure a la date) et non regle. */
    public static BigDecimal echu(List<Reste> restes, LocalDate date) {
        return restes.stream().filter(x -> x.echue(date)).map(Reste::reste).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Premiere dette echue non reglee a la date, s'il y en a une. */
    public static Reste premiereEchue(List<Reste> restes, LocalDate date) {
        return restes.stream().filter(x -> x.echue(date)).findFirst().orElse(null);
    }

    public static Tranches tranches(List<Reste> restes, LocalDate au) {
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Reste x : restes) {
            if (x.reste().signum() <= 0) {
                continue;
            }
            int i;
            if (x.dette().nature() == Nature.NON_FACTURE) {
                i = 0;
            } else {
                long jours = ChronoUnit.DAYS.between(x.dette().date(), au);
                i = jours <= 30 ? 1 : jours <= 60 ? 2 : jours <= 90 ? 3 : 4;
            }
            t[i] = t[i].add(x.reste());
        }
        return new Tranches(t[0], t[1], t[2], t[3], t[4], echu(restes, au));
    }
}
