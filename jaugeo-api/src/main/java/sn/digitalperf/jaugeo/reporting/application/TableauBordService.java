package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.StocksMensuels;
import sn.digitalperf.jaugeo.stock.StocksMensuels.BilanCuve;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse.MoisCaisse;

/**
 * Tableau de bord du reseau : pour chaque station du perimetre, niveau d'alerte, volumes vendus, ecarts de stock
 * et situation de caisse du mois en cours, et retard de cloture des journees.
 *
 * <p>Niveau d'une station = le plus grave de ses motifs :
 * <ul>
 *   <li>ENQUETE : une cuve au-dela du seuil d'enquete, ou 4 jours ou plus sans journee cloturee ;</li>
 *   <li>ALERTE : une cuve au-dela de sa tolerance, 2 ou 3 jours sans journee cloturee, plus de 2 journees
 *       cloturees en attente de validation, ou un manquant de caisse dans le mois ;</li>
 *   <li>NORMAL sinon.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TableauBordService {

    /** Profondeur de recherche de la derniere journee cloturee. */
    static final int HISTORIQUE_JOURS = 60;

    public enum Niveau { NORMAL, ALERTE, ENQUETE }

    public record StationTableau(UUID stationId, String code, String nom, BigDecimal latitude, BigDecimal longitude,
                                 Niveau niveau, List<String> motifs, LocalDate derniereCloture, Integer joursSansCloture,
                                 int journeesAValider, Map<String, BigDecimal> litresMois, BigDecimal litresMoisTotal,
                                 BigDecimal litresVeille, BigDecimal ventesMois, BigDecimal versementsMois,
                                 BigDecimal soldeCaisse, BigDecimal ecartsCaisseMois, BigDecimal ecartStockValeurMois,
                                 List<EcartProduit> ecartsStock) {
    }

    public record EcartProduit(String produitCode, String produitLibelle, BigDecimal ecartLitres, BigDecimal ecartPct,
                               String niveau) {
    }

    public record TableauReseau(LocalDate date, YearMonth mois, Map<Niveau, Long> parNiveau, BigDecimal litresMois,
                                BigDecimal litresVeille, BigDecimal ventesMois, BigDecimal versementsMois,
                                BigDecimal ecartStockValeurMois, List<StationTableau> stations) {
    }

    private final ReferentielConsultation referentiel;
    private final JourneesExploitation exploitation;
    private final StocksMensuels stocks;
    private final SyntheseCaisse caisse;
    private final PerimetreCourant perimetre;

    public TableauBordService(ReferentielConsultation referentiel, JourneesExploitation exploitation,
                              StocksMensuels stocks, SyntheseCaisse caisse, PerimetreCourant perimetre) {
        this.referentiel = referentiel;
        this.exploitation = exploitation;
        this.stocks = stocks;
        this.caisse = caisse;
        this.perimetre = perimetre;
    }

    /** Situation au jour {@code date} (journees jusqu'a la veille attendues cloturees). */
    public TableauReseau reseau(LocalDate date) {
        YearMonth mois = YearMonth.from(date);
        Map<Long, ProduitRef> produits = referentiel.produits().stream()
                .collect(Collectors.toMap(ProduitRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        List<StationTableau> lignes = referentiel.stations().stream()
                .filter(StationRef::actif)
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .filter(s -> s.dateOuverture() == null || !s.dateOuverture().isAfter(date))
                .sorted(Comparator.comparing(StationRef::code))
                .map(s -> station(s, date, mois, produits))
                .toList();
        Map<Niveau, Long> parNiveau = new LinkedHashMap<>();
        for (Niveau n : Niveau.values()) {
            parNiveau.put(n, lignes.stream().filter(l -> l.niveau() == n).count());
        }
        return new TableauReseau(date, mois, parNiveau, somme(lignes, StationTableau::litresMoisTotal),
                somme(lignes, StationTableau::litresVeille), somme(lignes, StationTableau::ventesMois),
                somme(lignes, StationTableau::versementsMois), somme(lignes, StationTableau::ecartStockValeurMois), lignes);
    }

    private StationTableau station(StationRef s, LocalDate date, YearMonth mois, Map<Long, ProduitRef> produits) {
        List<String> motifs = new ArrayList<>();
        Niveau niveau = Niveau.NORMAL;

        // Journees : derniere cloture, retard, validations en attente
        LocalDate veille = date.minusDays(1);
        List<JourneeRef> journees = exploitation.journees(s.id(), date.minusDays(HISTORIQUE_JOURS), date);
        LocalDate derniere = journees.stream().filter(j -> !j.brouillon()).map(JourneeRef::date)
                .max(Comparator.naturalOrder()).orElse(null);
        int aValider = (int) journees.stream().filter(j -> "CLOTUREE".equals(j.statut())).count();
        Integer retard = null;
        if (derniere != null) {
            retard = (int) Math.max(0, ChronoUnit.DAYS.between(derniere, veille));
        } else if (s.dateOuverture() == null || s.dateOuverture().isBefore(veille)) {
            motifs.add("Aucune journée clôturée depuis " + HISTORIQUE_JOURS + " jours");
            niveau = Niveau.ENQUETE;
        }
        if (retard != null && retard >= 4) {
            motifs.add(retard + " jours sans journée clôturée");
            niveau = pire(niveau, Niveau.ENQUETE);
        } else if (retard != null && retard >= 2) {
            motifs.add(retard + " jours sans journée clôturée");
            niveau = pire(niveau, Niveau.ALERTE);
        }
        if (aValider > 2) {
            motifs.add(aValider + " journées clôturées à valider");
            niveau = pire(niveau, Niveau.ALERTE);
        }

        // Volumes
        Map<String, BigDecimal> litres = new LinkedHashMap<>();
        exploitation.litresVendus(s.id(), mois.atDay(1), date).forEach((produitId, l) ->
                litres.put(produits.containsKey(produitId) ? produits.get(produitId).code() : String.valueOf(produitId), l));
        BigDecimal litresMois = litres.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal litresVeille = exploitation.litresVendus(s.id(), veille, veille).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Stocks : ecart du mois par produit et niveau le plus grave
        List<EcartProduit> ecarts = new ArrayList<>();
        BigDecimal ecartValeur = BigDecimal.ZERO;
        for (BilanCuve b : stocks.bilans(s.id(), mois)) {
            ecarts.add(new EcartProduit(b.produitCode(), b.produitLibelle(), b.ecartLitres(), b.ecartPct(), b.niveau()));
            if (b.ecartValeur() != null) {
                ecartValeur = ecartValeur.add(b.ecartValeur());
            }
            if ("ENQUETE".equals(b.niveau())) {
                motifs.add("Écart de stock " + b.produitLibelle() + " au-delà du seuil d'enquête");
                niveau = pire(niveau, Niveau.ENQUETE);
            } else if ("ALERTE".equals(b.niveau())) {
                motifs.add("Écart de stock " + b.produitLibelle() + " au-delà de la tolérance");
                niveau = pire(niveau, Niveau.ALERTE);
            }
        }

        // Caisse du mois
        MoisCaisse m = caisse.mois(s.id(), mois);
        if (m.ecarts() != null && m.ecarts().signum() < 0) {
            motifs.add("Manquant de caisse dans le mois");
            niveau = pire(niveau, Niveau.ALERTE);
        }
        return new StationTableau(s.uuid(), s.code(), s.nom(), s.latitude(), s.longitude(), niveau, motifs, derniere,
                retard, aValider, litres, litresMois, litresVeille, m.ventes(), m.versements(), m.soldeFin(), m.ecarts(),
                ecartValeur, ecarts);
    }

    private static Niveau pire(Niveau a, Niveau b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static BigDecimal somme(List<StationTableau> l, Function<StationTableau, BigDecimal> f) {
        return l.stream().map(f).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
