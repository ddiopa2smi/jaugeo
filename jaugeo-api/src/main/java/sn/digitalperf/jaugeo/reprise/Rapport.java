package sn.digitalperf.jaugeo.reprise;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import sn.digitalperf.jaugeo.reprise.Classeur.Constat;

/**
 * Rapport de reprise : ce qui a ete importe, ce que Jaugeo recalcule par rapport au classeur (jour par jour et mois
 * par mois) et chaque particularite du classeur, pour que tout ecart soit documente.
 */
public final class Rapport {

    /** Grandeur comparee : valeur du classeur, valeur recalculee par Jaugeo. */
    public record Comparaison(String station, LocalDate date, YearMonth mois, String grandeur, BigDecimal classeur,
                              BigDecimal jaugeo, String explication) {

        public BigDecimal ecart() {
            return jaugeo == null || classeur == null ? null : jaugeo.subtract(classeur);
        }

        public boolean concorde(BigDecimal tolerance) {
            if (classeur == null && jaugeo == null) {
                return true;
            }
            BigDecimal e = ecart();
            return e != null && e.abs().compareTo(tolerance) <= 0;
        }
    }

    /** Bilan d'une station. */
    public static final class BilanStation {
        final String station;
        int journees;
        int journeesConcordantes;
        LocalDate premiere;
        LocalDate derniere;
        String arret;

        BilanStation(String station) {
            this.station = station;
        }

        public int journees() {
            return journees;
        }

        public int journeesConcordantes() {
            return journeesConcordantes;
        }

        public String arret() {
            return arret;
        }
    }

    /** Tolerance des comparaisons journalieres : 1 F (le classeur ne retient pas l'arrondi au franc). */
    static final BigDecimal TOLERANCE = BigDecimal.ONE;

    private final LocalDate du;
    private final LocalDate au;
    private final boolean simulation;
    private final List<Constat> constats = new ArrayList<>();
    private final List<Comparaison> journalieres = new ArrayList<>();
    private final List<Comparaison> mensuelles = new ArrayList<>();
    private final Map<String, BilanStation> bilans = new LinkedHashMap<>();

    public Rapport(LocalDate du, LocalDate au, boolean simulation, List<Constat> constatsLecture) {
        this.du = du;
        this.au = au;
        this.simulation = simulation;
        constatsLecture.stream()
                .filter(c -> c.date() == null || (!c.date().isBefore(du.withDayOfMonth(1)) && !c.date().isAfter(au)))
                .forEach(constats::add);
    }

    BilanStation bilan(String station) {
        return bilans.computeIfAbsent(station, BilanStation::new);
    }

    void constater(String station, LocalDate date, String ligne, String code, String message) {
        constats.add(new Constat(station, date, ligne, code, message));
    }

    /** Enregistre les comparaisons d'une journee ; renvoie vrai si toutes concordent. */
    boolean journee(String station, LocalDate date, List<Comparaison> comparaisons) {
        BilanStation b = bilan(station);
        b.journees++;
        b.premiere = b.premiere == null ? date : b.premiere;
        b.derniere = date;
        boolean ok = true;
        for (Comparaison c : comparaisons) {
            if (!c.concorde(TOLERANCE)) {
                journalieres.add(c);
                ok = false;
            }
        }
        if (ok) {
            b.journeesConcordantes++;
        }
        return ok;
    }

    void mensuelle(Comparaison c) {
        mensuelles.add(c);
    }

    public List<Constat> constats() {
        return constats;
    }

    public List<Comparaison> ecartsJournaliers() {
        return journalieres;
    }

    public List<Comparaison> mensuelles() {
        return mensuelles;
    }

    public Map<String, BilanStation> bilans() {
        return bilans;
    }

    /** Reprise sans ecart journalier ni arret. */
    public boolean conforme() {
        return journalieres.isEmpty() && bilans.values().stream().allMatch(b -> b.arret == null);
    }

    // ---------------------------------------------------------------- Markdown

    public String markdown(String classeur) {
        StringBuilder m = new StringBuilder();
        m.append("# Rapport de reprise du classeur\n\n");
        m.append("- Classeur : ").append(classeur).append('\n');
        m.append("- Période : du ").append(date(du)).append(" au ").append(date(au)).append('\n');
        m.append("- Produit le ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")))
                .append(simulation ? " (simulation : lecture du classeur seulement, rien n'est envoyé à Jaugeo)" : "").append("\n\n");

        if (!simulation) {
            m.append("## Synthèse\n\n");
            m.append("| Station | Journées importées | Concordantes avec le classeur | Arrêt |\n|---|---:|---:|---|\n");
            for (BilanStation b : bilans.values()) {
                m.append("| ").append(b.station).append(" | ").append(b.journees).append(" | ")
                        .append(b.journeesConcordantes).append(" | ").append(b.arret == null ? "—" : cellule(b.arret)).append(" |\n");
            }
            m.append('\n');
            m.append(conforme()
                    ? "**Toutes les journées importées concordent avec les feuilles station** (litres, ventes, montant dû, écarts de stock du mois).\n\n"
                    : "**Des journées diffèrent des feuilles station** : voir le détail ci-dessous.\n\n");

            m.append("## Écarts jour par jour\n\n");
            if (journalieres.isEmpty()) {
                m.append("Aucun.\n\n");
            } else {
                m.append("| Station | Date | Grandeur | Classeur | Jaugeo | Écart | Explication |\n|---|---|---|---:|---:|---:|---|\n");
                journalieres.stream().sorted(Comparator.comparing(Comparaison::station).thenComparing(Comparaison::date))
                        .forEach(c -> m.append("| ").append(c.station()).append(" | ").append(date(c.date())).append(" | ")
                                .append(c.grandeur()).append(" | ").append(nombre(c.classeur())).append(" | ")
                                .append(nombre(c.jaugeo())).append(" | ").append(nombre(c.ecart())).append(" | ")
                                .append(cellule(c.explication())).append(" |\n"));
                m.append('\n');
            }

            m.append("## Rapprochement mensuel avec les feuilles d'analyse\n\n");
            m.append("Les feuilles d'analyse sont saisies à la main : leurs montants ne sont pas toujours la somme des "
                    + "feuilles station. Chaque différence est expliquée quand la cause est connue.\n\n");
            if (mensuelles.isEmpty()) {
                m.append("Aucune feuille d'analyse pour la période.\n\n");
            } else {
                m.append("| Station | Mois | Grandeur | Analyse | Jaugeo | Écart | Explication |\n|---|---|---|---:|---:|---:|---|\n");
                for (Comparaison c : mensuelles) {
                    BigDecimal e = c.ecart();
                    m.append("| ").append(c.station()).append(" | ").append(mois(c.mois())).append(" | ").append(c.grandeur())
                            .append(" | ").append(nombre(c.classeur())).append(" | ").append(nombre(c.jaugeo())).append(" | ")
                            .append(e == null || e.signum() == 0 ? "=" : nombre(e)).append(" | ")
                            .append(e == null || e.signum() == 0 ? "" : cellule(c.explication())).append(" |\n");
                }
                m.append('\n');
            }
        }

        m.append("## Particularités du classeur\n\n");
        if (constats.isEmpty()) {
            m.append("Aucune.\n");
        } else {
            m.append("| Station | Date | Ligne | Code | Constat |\n|---|---|---|---|---|\n");
            constats.stream()
                    .sorted(Comparator.comparing((Constat c) -> Objects.requireNonNullElse(c.station(), ""))
                            .thenComparing(c -> Objects.requireNonNullElse(c.date(), LocalDate.MIN)))
                    .forEach(c -> m.append("| ").append(Objects.requireNonNullElse(c.station(), "—")).append(" | ")
                            .append(c.date() == null ? "—" : date(c.date())).append(" | ")
                            .append(Objects.requireNonNullElse(c.ligne(), "—")).append(" | ").append(c.code()).append(" | ")
                            .append(cellule(c.message())).append(" |\n"));
        }
        return m.toString();
    }

    private static String cellule(String t) {
        return t == null ? "" : t.replace("|", "/").replace("\n", " ");
    }

    private static String date(LocalDate d) {
        return d == null ? "—" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String mois(YearMonth m) {
        return m == null ? "—" : m.format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    static String nombre(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.##", s).format(v);
    }
}
