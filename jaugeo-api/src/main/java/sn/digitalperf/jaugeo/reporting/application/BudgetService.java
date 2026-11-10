package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.VenteJour;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.BudgetStation;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.MoisBudget;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.ProduitBudget;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviBudget;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviMois;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviProduit;
import sn.digitalperf.jaugeo.reporting.domaine.BudgetDepense;
import sn.digitalperf.jaugeo.reporting.domaine.BudgetDepenseDepot;
import sn.digitalperf.jaugeo.reporting.domaine.BudgetVente;
import sn.digitalperf.jaugeo.reporting.domaine.BudgetVenteDepot;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;

/**
 * Budget annuel par station : litres et chiffre d'affaires par produit et par mois, depenses par mois.
 * Le suivi compare le reel (journees cloturees) au budget, mois par mois et en cumul sur les mois ecoules.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    /** Saisie du budget d'un produit : douze mois. */
    public record SaisieProduit(UUID produitId, List<MoisBudget> mois) {
    }

    private final BudgetVenteDepot ventes;
    private final BudgetDepenseDepot depenses;
    private final JourneesExploitation exploitation;
    private final SyntheseCaisse caisse;
    private final ReferentielConsultation referentiel;
    private final CompteExploitationService comptes;
    private final PerimetreCourant perimetre;
    private final Clock horloge;

    public BudgetService(BudgetVenteDepot ventes, BudgetDepenseDepot depenses, JourneesExploitation exploitation,
                         SyntheseCaisse caisse, ReferentielConsultation referentiel, CompteExploitationService comptes,
                         PerimetreCourant perimetre, Clock horloge) {
        this.ventes = ventes;
        this.depenses = depenses;
        this.exploitation = exploitation;
        this.caisse = caisse;
        this.referentiel = referentiel;
        this.comptes = comptes;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Saisie

    /** Budget d'une station pour une annee : ses carburants en cuve, et tout produit deja budgete. */
    public BudgetStation budget(UUID stationUuid, int annee) {
        StationRef s = station(stationUuid);
        Map<Long, ProduitRef> produits = comptes.produits();
        List<BudgetVente> lignes = ventes.findByStationIdInAndMoisBetween(List.of(s.id()), debut(annee), fin(annee));
        Set<Long> ids = new LinkedHashSet<>(referentiel.produitsEnCuve().getOrDefault(s.id(), Set.of()));
        lignes.forEach(l -> ids.add(l.getProduitId()));
        Map<Long, Map<Integer, BudgetVente>> parProduit = lignes.stream().collect(Collectors.groupingBy(BudgetVente::getProduitId,
                Collectors.toMap(l -> l.getMois().getMonthValue(), Function.identity())));
        List<ProduitBudget> vues = ids.stream().map(produits::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ProduitRef::code))
                .map(p -> {
                    Map<Integer, BudgetVente> m = parProduit.getOrDefault(p.id(), Map.of());
                    List<MoisBudget> mois = new ArrayList<>();
                    for (int i = 1; i <= 12; i++) {
                        BudgetVente b = m.get(i);
                        mois.add(new MoisBudget(b == null ? BigDecimal.ZERO : b.getLitres(), b == null ? BigDecimal.ZERO : b.getMontant()));
                    }
                    return new ProduitBudget(p.uuid(), p.code(), p.libelle(), mois);
                }).toList();
        Map<Integer, BigDecimal> dep = depenses.findByStationIdInAndMoisBetween(List.of(s.id()), debut(annee), fin(annee)).stream()
                .collect(Collectors.toMap(d -> d.getMois().getMonthValue(), BudgetDepense::getMontant));
        List<BigDecimal> douze = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            douze.add(dep.getOrDefault(i, BigDecimal.ZERO));
        }
        return new BudgetStation(s.uuid(), s.code(), s.nom(), annee, vues, douze);
    }

    /** Enregistre le budget d'une station pour une annee ; un mois a zero n'est pas conserve. */
    @Transactional
    public BudgetStation enregistrer(UUID stationUuid, int annee, List<SaisieProduit> produits, List<BigDecimal> depensesMois) {
        StationRef s = station(stationUuid);
        if (annee < 2020 || annee > 2100) {
            throw ErreurMetier.regle("ANNEE_INVALIDE", "Année de budget invalide.");
        }
        Map<UUID, ProduitRef> parUuid = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::uuid, Function.identity()));
        Map<String, BudgetVente> existants = ventes.findByStationIdInAndMoisBetween(List.of(s.id()), debut(annee), fin(annee)).stream()
                .collect(Collectors.toMap(b -> b.getProduitId() + "|" + b.getMois().getMonthValue(), Function.identity()));
        for (SaisieProduit p : produits == null ? List.<SaisieProduit>of() : produits) {
            ProduitRef ref = parUuid.get(p.produitId());
            if (ref == null) {
                throw ErreurMetier.introuvable("Produit");
            }
            exigerDouze(p.mois());
            for (int i = 1; i <= 12; i++) {
                MoisBudget m = p.mois().get(i - 1);
                BigDecimal litres = positif(m == null ? null : m.litres(), 2);
                BigDecimal montant = positif(m == null ? null : m.montant(), 0);
                BudgetVente b = existants.get(ref.id() + "|" + i);
                if (litres.signum() == 0 && montant.signum() == 0) {
                    if (b != null) {
                        ventes.delete(b);
                    }
                    continue;
                }
                if (b == null) {
                    b = new BudgetVente(s.id(), YearMonth.of(annee, i), ref.id());
                }
                b.fixer(litres, montant);
                ventes.save(b);
            }
        }
        if (depensesMois != null) {
            exigerDouze(depensesMois);
            Map<Integer, BudgetDepense> dep = depenses.findByStationIdInAndMoisBetween(List.of(s.id()), debut(annee), fin(annee)).stream()
                    .collect(Collectors.toMap(d -> d.getMois().getMonthValue(), Function.identity()));
            for (int i = 1; i <= 12; i++) {
                BigDecimal montant = positif(depensesMois.get(i - 1), 0);
                BudgetDepense d = dep.get(i);
                if (montant.signum() == 0) {
                    if (d != null) {
                        depenses.delete(d);
                    }
                    continue;
                }
                if (d == null) {
                    d = new BudgetDepense(s.id(), YearMonth.of(annee, i));
                }
                d.fixer(montant);
                depenses.save(d);
            }
        }
        ventes.flush();
        depenses.flush();
        return budget(stationUuid, annee);
    }

    private static void exigerDouze(List<?> mois) {
        if (mois == null || mois.size() != 12) {
            throw ErreurMetier.regle("BUDGET_INCOMPLET", "Un budget se saisit sur douze mois, de janvier à décembre.");
        }
    }

    private static BigDecimal positif(BigDecimal v, int decimales) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(decimales);
        }
        if (v.signum() < 0) {
            throw ErreurMetier.regle("BUDGET_NEGATIF", "Un budget ne peut pas être négatif.");
        }
        return v.setScale(decimales, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- Suivi

    /** Reel contre budget pour une station, ou pour toutes les stations du perimetre (station nulle). */
    public SuiviBudget suivi(UUID stationUuid, int annee) {
        List<StationRef> stations = stationUuid == null ? comptes.stations(fin(annee).plusMonths(1).minusDays(1))
                : List.of(station(stationUuid));
        Map<Long, ProduitRef> produits = comptes.produits();
        YearMonth courant = YearMonth.from(LocalDate.ofInstant(horloge.instant(), DAKAR));
        List<Long> ids = stations.stream().map(StationRef::id).toList();

        // [litres budget, litres reel, CA budget, CA reel, depenses budget, depenses reel] par mois
        BigDecimal[][] mois = new BigDecimal[12][6];
        for (BigDecimal[] m : mois) {
            java.util.Arrays.fill(m, BigDecimal.ZERO);
        }
        // [litres budget, litres reel, CA budget, CA reel] par produit, sur les mois ecoules
        Map<Long, BigDecimal[]> parProduit = new TreeMap<>();
        if (!ids.isEmpty()) {
            for (BudgetVente b : ventes.findByStationIdInAndMoisBetween(ids, debut(annee), fin(annee))) {
                int i = b.getMois().getMonthValue() - 1;
                mois[i][0] = mois[i][0].add(b.getLitres());
                mois[i][2] = mois[i][2].add(b.getMontant());
                if (YearMonth.from(b.getMois()).isBefore(courant)) {
                    BigDecimal[] c = produit(parProduit, b.getProduitId());
                    c[0] = c[0].add(b.getLitres());
                    c[2] = c[2].add(b.getMontant());
                }
            }
            for (BudgetDepense d : depenses.findByStationIdInAndMoisBetween(ids, debut(annee), fin(annee))) {
                int i = d.getMois().getMonthValue() - 1;
                mois[i][4] = mois[i][4].add(d.getMontant());
            }
        }
        for (StationRef s : stations) {
            for (VenteJour v : exploitation.ventesParJour(s.id(), debut(annee), fin(annee).plusMonths(1).minusDays(1))) {
                int i = v.date().getMonthValue() - 1;
                mois[i][1] = mois[i][1].add(v.litres());
                mois[i][3] = mois[i][3].add(v.montant());
                if (YearMonth.from(v.date()).isBefore(courant)) {
                    BigDecimal[] c = produit(parProduit, v.produitId());
                    c[1] = c[1].add(v.litres());
                    c[3] = c[3].add(v.montant());
                }
            }
            for (int i = 0; i < 12; i++) {
                YearMonth m = YearMonth.of(annee, i + 1);
                // Le livre de caisse d'un mois a venir est vide : inutile de le calculer
                if (!m.isAfter(courant)) {
                    mois[i][5] = mois[i][5].add(caisse.mois(s.id(), m).depenses());
                }
            }
        }

        List<SuiviMois> lignes = new ArrayList<>();
        BigDecimal[] cumul = new BigDecimal[6];
        java.util.Arrays.fill(cumul, BigDecimal.ZERO);
        for (int i = 0; i < 12; i++) {
            YearMonth m = YearMonth.of(annee, i + 1);
            boolean ecoule = m.isBefore(courant);
            lignes.add(new SuiviMois(m, ecoule, mois[i][0], mois[i][1], mois[i][2], mois[i][3], mois[i][4], mois[i][5]));
            if (ecoule) {
                for (int k = 0; k < 6; k++) {
                    cumul[k] = cumul[k].add(mois[i][k]);
                }
            }
        }
        List<SuiviProduit> vues = parProduit.entrySet().stream()
                .map(e -> {
                    ProduitRef p = produits.get(e.getKey());
                    BigDecimal[] c = e.getValue();
                    return new SuiviProduit(p == null ? String.valueOf(e.getKey()) : p.code(),
                            p == null ? String.valueOf(e.getKey()) : p.libelle(), c[0], c[1], c[2], c[3]);
                })
                .sorted(Comparator.comparing(SuiviProduit::code)).toList();
        return new SuiviBudget(annee, stationUuid, lignes, vues,
                new SuiviMois(null, true, cumul[0], cumul[1], cumul[2], cumul[3], cumul[4], cumul[5]));
    }

    private static BigDecimal[] produit(Map<Long, BigDecimal[]> m, long id) {
        return m.computeIfAbsent(id, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
    }

    // ---------------------------------------------------------------- Outils

    private static LocalDate debut(int annee) {
        return LocalDate.of(annee, 1, 1);
    }

    /** Premier jour de decembre : les budgets sont dates du premier du mois. */
    private static LocalDate fin(int annee) {
        return LocalDate.of(annee, 12, 1);
    }

    private StationRef station(UUID uuid) {
        StationRef s = referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }
}
