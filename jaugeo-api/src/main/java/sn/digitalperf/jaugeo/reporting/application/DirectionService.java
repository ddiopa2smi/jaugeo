package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.PrixAchats;
import sn.digitalperf.jaugeo.approvisionnement.PrixAchats;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.VenteJour;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.reporting.domaine.CalculMarge;
import sn.digitalperf.jaugeo.reporting.domaine.CalculMarge.VenteDuJour;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse.MoisCaisse;

/**
 * Vue de la Direction : activite du mois arretee a la veille (journees cloturees), comparee a la meme periode du
 * mois precedent, evolution jour par jour par produit, activite et caisse par station.
 *
 * <p>La periode va du 1er du mois de la veille a la veille : le jour en cours n'est jamais clos, et le 1er du mois
 * la vue montre le mois qui vient de s'achever. La periode de comparaison couvre les memes jours du mois precedent
 * (bornes au dernier jour de ce mois).
 */
@Service
@Transactional(readOnly = true)
public class DirectionService {

    public record Periode(LocalDate du, LocalDate au, BigDecimal litres, BigDecimal ventes) {
    }

    public record ProduitDirection(String code, String libelle, BigDecimal litres, BigDecimal ventes,
                                   BigDecimal litresPrecedent, BigDecimal ventesPrecedent) {
    }

    /** Un jour de la periode : litres et ventes par code produit (jours sans vente compris, a zero). */
    public record JourDirection(LocalDate date, Map<String, BigDecimal> litres, Map<String, BigDecimal> ventes,
                                BigDecimal ventesTotal) {
    }

    /** Une station : activite de la periode et caisse du mois (montant du, verse, solde non verse en fin de mois). */
    public record StationDirection(UUID stationId, String code, String nom, BigDecimal litres, BigDecimal ventes,
                                   BigDecimal ventesPrecedent, BigDecimal montantDu, BigDecimal versements,
                                   BigDecimal soldeCaisse, int joursNonClotures) {
    }

    public record VueDirection(LocalDate date, YearMonth mois, Periode periode, Periode precedente,
                               BigDecimal ventesMoisPrecedent, BigDecimal montantDu, BigDecimal versements,
                               BigDecimal soldeCaisse, List<ProduitDirection> produits, List<JourDirection> jours,
                               List<StationDirection> stations, BigDecimal marge, BigDecimal tauxMarge) {
    }

    private final ReferentielConsultation referentiel;
    private final JourneesExploitation exploitation;
    private final SyntheseCaisse caisse;
    private final PerimetreCourant perimetre;
    private final PrixAchats prixAchats;

    public DirectionService(ReferentielConsultation referentiel, JourneesExploitation exploitation,
                            SyntheseCaisse caisse, PerimetreCourant perimetre, PrixAchats prixAchats) {
        this.prixAchats = prixAchats;
        this.referentiel = referentiel;
        this.exploitation = exploitation;
        this.caisse = caisse;
        this.perimetre = perimetre;
    }

    /** Situation au jour {@code date} : periode du 1er du mois de la veille a la veille. */
    public VueDirection vue(LocalDate date) {
        LocalDate au = date.minusDays(1);
        YearMonth mois = YearMonth.from(au);
        LocalDate du = mois.atDay(1);
        YearMonth moisPrecedent = mois.minusMonths(1);
        LocalDate duPrec = moisPrecedent.atDay(1);
        LocalDate auPrec = moisPrecedent.atDay(Math.min(au.getDayOfMonth(), moisPrecedent.lengthOfMonth()));

        Map<Long, ProduitRef> produits = referentiel.produits().stream()
                .collect(Collectors.toMap(ProduitRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        List<StationRef> stations = referentiel.stations().stream()
                .filter(StationRef::actif)
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .filter(s -> s.dateOuverture() == null || !s.dateOuverture().isAfter(au))
                .sorted(Comparator.comparing(StationRef::code))
                .toList();

        // Cumuls par produit et par jour sur le reseau du perimetre
        Map<Long, BigDecimal[]> parProduit = new LinkedHashMap<>();
        Map<LocalDate, JourCumul> parJour = new TreeMap<>();
        for (LocalDate d = du; !d.isAfter(au); d = d.plusDays(1)) {
            parJour.put(d, new JourCumul());
        }
        List<StationDirection> lignes = new ArrayList<>();
        // Litres vendus par produit et par jour, pour le cout d'achat au prix convenu du jour
        Map<Long, List<VenteDuJour>> parProduitJour = new LinkedHashMap<>();
        BigDecimal ventesMoisPrec = BigDecimal.ZERO;
        for (StationRef s : stations) {
            BigDecimal litres = BigDecimal.ZERO;
            BigDecimal ventes = BigDecimal.ZERO;
            for (VenteJour v : exploitation.ventesParJour(s.id(), du, au)) {
                BigDecimal[] c = cumul(parProduit, v.produitId());
                c[0] = c[0].add(v.litres());
                c[1] = c[1].add(v.montant());
                parJour.get(v.date()).ajouter(code(produits, v.produitId()), v.litres(), v.montant());
                parProduitJour.computeIfAbsent(v.produitId(), k -> new ArrayList<>()).add(new VenteDuJour(v.date(), v.litres()));
                litres = litres.add(v.litres());
                ventes = ventes.add(v.montant());
            }
            BigDecimal ventesPrec = BigDecimal.ZERO;
            for (VenteJour v : exploitation.ventesParJour(s.id(), duPrec, moisPrecedent.atEndOfMonth())) {
                if (!v.date().isAfter(auPrec)) {
                    BigDecimal[] c = cumul(parProduit, v.produitId());
                    c[2] = c[2].add(v.litres());
                    c[3] = c[3].add(v.montant());
                    ventesPrec = ventesPrec.add(v.montant());
                }
                ventesMoisPrec = ventesMoisPrec.add(v.montant());
            }
            MoisCaisse m = caisse.mois(s.id(), mois);
            lignes.add(new StationDirection(s.uuid(), s.code(), s.nom(), litres, ventes, ventesPrec, m.montants(),
                    m.versements(), m.soldeFin(), m.joursNonClotures()));
        }

        List<ProduitDirection> lignesProduits = parProduit.entrySet().stream()
                .map(e -> {
                    ProduitRef p = produits.get(e.getKey());
                    BigDecimal[] c = e.getValue();
                    return new ProduitDirection(p == null ? String.valueOf(e.getKey()) : p.code(),
                            p == null ? String.valueOf(e.getKey()) : p.libelle(), c[0], c[1], c[2], c[3]);
                })
                .sorted(Comparator.comparing(ProduitDirection::code))
                .toList();
        List<JourDirection> jours = parJour.entrySet().stream()
                .map(e -> e.getValue().vue(e.getKey(), lignesProduits))
                .toList();

        Periode periode = new Periode(du, au, somme(lignesProduits, ProduitDirection::litres),
                somme(lignesProduits, ProduitDirection::ventes));
        Periode precedente = new Periode(duPrec, auPrec, somme(lignesProduits, ProduitDirection::litresPrecedent),
                somme(lignesProduits, ProduitDirection::ventesPrecedent));
        // Marge sur carburants : inconnue des qu'un produit vendu n'a pas de prix d'achat convenu en vigueur
        BigDecimal cout = BigDecimal.ZERO;
        for (Map.Entry<Long, List<VenteDuJour>> e : parProduitJour.entrySet()) {
            BigDecimal c = CalculMarge.coutDesVentes(e.getValue(), prixAchats.prixConvenus(e.getKey()));
            cout = c == null || cout == null ? null : cout.add(c);
        }
        BigDecimal marge = cout == null || periode.ventes().signum() == 0 ? null : periode.ventes().subtract(cout);
        return new VueDirection(date, mois, periode, precedente, ventesMoisPrec,
                somme(lignes, StationDirection::montantDu), somme(lignes, StationDirection::versements),
                somme(lignes, StationDirection::soldeCaisse), lignesProduits, jours, lignes, marge,
                CalculMarge.taux(marge, periode.ventes()));
    }

    /** [litres, ventes, litres precedents, ventes precedentes] d'un produit. */
    private static BigDecimal[] cumul(Map<Long, BigDecimal[]> m, long produitId) {
        return m.computeIfAbsent(produitId,
                k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
    }

    private static String code(Map<Long, ProduitRef> produits, long id) {
        ProduitRef p = produits.get(id);
        return p == null ? String.valueOf(id) : p.code();
    }

    private static <T> BigDecimal somme(List<T> l, Function<T, BigDecimal> f) {
        return l.stream().map(f).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class JourCumul {
        private final Map<String, BigDecimal> litres = new LinkedHashMap<>();
        private final Map<String, BigDecimal> ventes = new LinkedHashMap<>();

        void ajouter(String produit, BigDecimal l, BigDecimal v) {
            litres.merge(produit, l, BigDecimal::add);
            ventes.merge(produit, v, BigDecimal::add);
        }

        JourDirection vue(LocalDate date, List<ProduitDirection> produits) {
            Map<String, BigDecimal> l = new LinkedHashMap<>();
            Map<String, BigDecimal> v = new LinkedHashMap<>();
            for (ProduitDirection p : produits) {
                l.put(p.code(), litres.getOrDefault(p.code(), BigDecimal.ZERO));
                v.put(p.code(), ventes.getOrDefault(p.code(), BigDecimal.ZERO));
            }
            BigDecimal total = v.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return new JourDirection(date, l, v, total);
        }
    }
}
