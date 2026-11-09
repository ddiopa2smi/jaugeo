package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.CuveRef;
import sn.digitalperf.jaugeo.stock.application.VuesStock.AnomalieVue;
import sn.digitalperf.jaugeo.stock.domaine.CalculStock;
import sn.digitalperf.jaugeo.stock.domaine.EcartStockDepot;
import sn.digitalperf.jaugeo.stock.domaine.Jaugeage;
import sn.digitalperf.jaugeo.stock.domaine.JaugeageDepot;
import sn.digitalperf.jaugeo.stock.domaine.Reception;
import sn.digitalperf.jaugeo.stock.domaine.ReceptionDepot;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Evaluation du stock de chaque cuve d'une journee en saisie : partagee par l'ecran (apercu immediat)
 * et par la cloture (controles bloquants, puis valeurs figees).
 */
@Component
class EvaluationStock {

    /** Etat calcule d'une cuve ; anomalies = points bloquants pour la cloture. */
    record CuveEvaluee(CuveRef cuve, boolean departReporte, BigDecimal depart, Jaugeage ouverture,
                       BigDecimal receptions, BigDecimal sorties, Jaugeage cloture, BigDecimal prixUnitaire,
                       BigDecimal cumulSortiesAvant, CalculStock.Resultat resultat, List<AnomalieVue> anomalies,
                       List<AnomalieVue> alertes) {

        boolean ouvertureRequise() {
            return !departReporte;
        }

        boolean observationRequise() {
            return resultat.horsTolerance();
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReferentielConsultation referentiel;
    private final Tarification tarification;
    private final JaugeageDepot jaugeages;
    private final ReceptionDepot receptions;
    private final EcartStockDepot ecarts;
    private final BigDecimal seuilEnquetePct;
    private final int seuilEauMm;

    EvaluationStock(ReferentielConsultation referentiel, Tarification tarification, JaugeageDepot jaugeages,
                    ReceptionDepot receptions, EcartStockDepot ecarts,
                    @Value("${jaugeo.stock.seuil-enquete-pct:2}") BigDecimal seuilEnquetePct,
                    @Value("${jaugeo.stock.seuil-eau-mm:20}") int seuilEauMm) {
        this.seuilEauMm = seuilEauMm;
        this.referentiel = referentiel;
        this.tarification = tarification;
        this.jaugeages = jaugeages;
        this.receptions = receptions;
        this.ecarts = ecarts;
        this.seuilEnquetePct = seuilEnquetePct;
    }

    /** Cuves suivies de la journee : actives, ou alimentant un pistolet du jour, ou deja jaugees ou livrees. */
    List<CuveEvaluee> evaluer(JourneeRef j, Map<Long, BigDecimal> sortiesParCuve) {
        List<Jaugeage> mesures = jaugeages.findByJourneeId(j.id());
        List<Reception> livraisons = receptions.findByJourneeIdOrderByIdAsc(j.id());
        Set<Long> concernees = new java.util.HashSet<>(sortiesParCuve.keySet());
        mesures.forEach(m -> concernees.add(m.getCuveId()));
        livraisons.forEach(r -> concernees.add(r.getCuveId()));
        Map<Long, BigDecimal> recuParCuve = livraisons.stream().collect(Collectors.groupingBy(Reception::getCuveId,
                HashMap::new, Collectors.reducing(BigDecimal.ZERO, Reception::getQuantiteRecue, BigDecimal::add)));

        List<CuveEvaluee> r = new ArrayList<>();
        for (CuveRef c : referentiel.cuves(j.stationId())) {
            if (!c.actif() && !concernees.contains(c.id())) {
                continue;
            }
            Jaugeage ouverture = trouver(mesures, c.id(), Jaugeage.Moment.OUVERTURE);
            Jaugeage cloture = trouver(mesures, c.id(), Jaugeage.Moment.CLOTURE);
            Optional<Jaugeage> veille = jaugeages.findByCuveIdAndDateJourAndMoment(c.id(), j.date().minusDays(1), Jaugeage.Moment.CLOTURE);
            BigDecimal depart = veille.map(Jaugeage::getVolumeLitres)
                    .orElse(ouverture == null ? null : ouverture.getVolumeLitres());
            BigDecimal recu = recuParCuve.getOrDefault(c.id(), BigDecimal.ZERO);
            // Une cuve sans pistolet ce jour-la n'a rien vendu ; une cuve au releve incomplet a une sortie inconnue
            BigDecimal sorties = sortiesParCuve.containsKey(c.id()) ? sortiesParCuve.get(c.id()) : BigDecimal.ZERO;
            BigDecimal prix = tarification.prixEnVigueur(j.stationId(), c.produitId(), j.fin().minusSeconds(1)).orElse(null);
            LocalDate premierDuMois = j.date().withDayOfMonth(1);
            EcartStockDepot.Cumul avant = ecarts.cumul(c.id(), premierDuMois, j.date());
            CalculStock.Resultat res = CalculStock.calculer(new CalculStock.Entree(depart, recu, sorties,
                    cloture == null ? null : cloture.getVolumeLitres(), avant.getSorties(), avant.getEcart(), prix,
                    c.toleranceEcartPct(), seuilEnquetePct));

            List<AnomalieVue> anomalies = new ArrayList<>();
            if (veille.isEmpty() && ouverture == null) {
                anomalies.add(new AnomalieVue("STOCK_DEPART_MANQUANT",
                        "première journée suivie : jaugez le stock de départ (aucun jaugeage de clôture le "
                                + DATE.format(j.date().minusDays(1)) + ")."));
            }
            if (cloture == null) {
                anomalies.add(new AnomalieVue("JAUGEAGE_MANQUANT", "jaugeage de fin de journée à saisir."));
            }
            if (res.theorique() != null && res.theorique().signum() < 0) {
                anomalies.add(new AnomalieVue("STOCK_THEORIQUE_NEGATIF", "le stock théorique est négatif ("
                        + litres(res.theorique()) + ") : saisissez la réception manquante."));
            }
            if (res.horsTolerance() && (cloture == null || cloture.getObservation() == null)) {
                anomalies.add(new AnomalieVue("OBSERVATION_OBLIGATOIRE", "écart de " + litres(res.cumulEcart())
                        + " sur le mois" + (res.ecartPctMois() == null ? "" : " (" + pct(res.ecartPctMois()) + ")")
                        + ", au-delà de la tolérance de " + pct(c.toleranceEcartPct()) + " : une observation est obligatoire."));
            }
            r.add(new CuveEvaluee(c, veille.isPresent(), depart, ouverture, recu, sorties, cloture, prix,
                    avant.getSorties(), res, anomalies, alertesEau(c, ouverture, cloture)));
        }
        return r;
    }

    BigDecimal seuilEnquetePct() {
        return seuilEnquetePct;
    }

    /** Seuil d'eau de la cuve, ou celui du reseau. */
    int seuilEauMm(CuveRef c) {
        return c.seuilEauMm() != null ? c.seuilEauMm() : seuilEauMm;
    }

    boolean eauAuDela(CuveRef c, Jaugeage m) {
        return m != null && m.getHauteurEauMm() != null && m.getHauteurEauMm() > seuilEauMm(c);
    }

    /** Alertes non bloquantes : eau en fond de cuve au-dela du seuil. */
    List<AnomalieVue> alertesEau(CuveRef c, Jaugeage... mesures) {
        List<AnomalieVue> a = new ArrayList<>();
        for (Jaugeage m : mesures) {
            if (eauAuDela(c, m)) {
                a.add(new AnomalieVue("EAU_FOND_CUVE", m.getHauteurEauMm() + " mm d'eau en fond de cuve"
                        + (m.getVolumeEauLitres() == null ? "" : " (" + litres(m.getVolumeEauLitres()) + ")")
                        + ", au-delà du seuil de " + seuilEauMm(c) + " mm : faites purger la cuve et prévenez la logistique."));
                break;
            }
        }
        return a;
    }

    private static Jaugeage trouver(List<Jaugeage> mesures, long cuveId, Jaugeage.Moment moment) {
        return mesures.stream().filter(m -> m.getCuveId() == cuveId && m.getMoment() == moment).findFirst().orElse(null);
    }

    static String litres(BigDecimal v) {
        return nombre(v) + " L";
    }

    static String pct(BigDecimal v) {
        return nombre(v) + " %";
    }

    static String nombre(BigDecimal v) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.##", s).format(v);
    }

    static String date(LocalDate d) {
        return DATE.format(d);
    }
}
