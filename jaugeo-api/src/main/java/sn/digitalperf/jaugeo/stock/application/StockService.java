package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.CuveRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.application.EvaluationStock.CuveEvaluee;
import sn.digitalperf.jaugeo.stock.application.VuesStock.AnalyseMensuelle;
import sn.digitalperf.jaugeo.stock.application.VuesStock.AnomalieVue;
import sn.digitalperf.jaugeo.stock.application.VuesStock.ComplementVue;
import sn.digitalperf.jaugeo.stock.application.VuesStock.CuveStock;
import sn.digitalperf.jaugeo.stock.application.VuesStock.JaugeageVue;
import sn.digitalperf.jaugeo.stock.application.VuesStock.LigneStock;
import sn.digitalperf.jaugeo.stock.application.VuesStock.ReceptionVue;
import sn.digitalperf.jaugeo.stock.application.VuesStock.SaisieJaugeage;
import sn.digitalperf.jaugeo.stock.application.VuesStock.Situation;
import sn.digitalperf.jaugeo.stock.application.VuesStock.SituationCuve;
import sn.digitalperf.jaugeo.stock.application.VuesStock.StockJournee;
import sn.digitalperf.jaugeo.stock.application.VuesStock.TotalCuve;
import sn.digitalperf.jaugeo.stock.domaine.CalculStock;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterne;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterneDepot;
import sn.digitalperf.jaugeo.stock.domaine.EcartStock;
import sn.digitalperf.jaugeo.stock.domaine.EcartStockDepot;
import sn.digitalperf.jaugeo.stock.domaine.Jaugeage;
import sn.digitalperf.jaugeo.stock.domaine.JaugeageDepot;
import sn.digitalperf.jaugeo.stock.domaine.Reception;
import sn.digitalperf.jaugeo.stock.domaine.ReceptionDepot;

/** Stocks (M4) : jaugeages et receptions d'une journee, analyse mensuelle, situation du reseau, complements citerne. */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final JaugeageDepot jaugeages;
    private final ReceptionDepot receptions;
    private final EcartStockDepot ecarts;
    private final ComplementCiterneDepot complements;
    private final EvaluationStock evaluation;
    private final ClotureStock cloture;

    public StockService(JourneesExploitation exploitation, ReferentielConsultation referentiel, PerimetreCourant perimetre,
                        JaugeageDepot jaugeages, ReceptionDepot receptions, EcartStockDepot ecarts,
                        ComplementCiterneDepot complements, EvaluationStock evaluation, ClotureStock cloture) {
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.jaugeages = jaugeages;
        this.receptions = receptions;
        this.ecarts = ecarts;
        this.complements = complements;
        this.evaluation = evaluation;
        this.cloture = cloture;
    }

    // ---------------------------------------------------------------- Journee

    public StockJournee journee(UUID journeeUuid) {
        JourneeRef j = journeeAccessible(journeeUuid);
        return vue(j);
    }

    @Transactional
    public StockJournee enregistrerJaugeages(UUID journeeUuid, Integer version, List<SaisieJaugeage> saisies) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Map<UUID, CuveRef> cuves = cuvesParUuid(j.stationId());
        for (SaisieJaugeage s : saisies) {
            CuveRef c = cuves.get(s.cuveId());
            if (c == null) {
                throw ErreurMetier.regle("CUVE_INCONNUE", "Cette cuve n'appartient pas à la station.");
            }
            Jaugeage.Moment moment = moment(s.moment());
            if (moment == Jaugeage.Moment.OUVERTURE) {
                jaugeages.findByCuveIdAndDateJourAndMoment(c.id(), j.date().minusDays(1), Jaugeage.Moment.CLOTURE).ifPresent(v -> {
                    throw ErreurMetier.regle("DEPART_REPORTE", "Le stock de départ de la cuve " + c.code()
                            + " est reporté du jaugeage de clôture du " + EvaluationStock.date(v.getDateJour()) + ".");
                });
            }
            Optional<Jaugeage> existant = jaugeages.findByJourneeIdAndCuveIdAndMoment(j.id(), c.id(), moment);
            if (s.hauteurMm() == null && s.volumeLitres() == null) {
                existant.ifPresent(jaugeages::delete);
                continue;
            }
            BigDecimal volume = s.hauteurMm() != null ? referentiel.volumeJauge(c.id(), s.hauteurMm()) : s.volumeLitres();
            if (c.capaciteLitres() != null && volume.compareTo(c.capaciteLitres()) > 0) {
                throw ErreurMetier.regle("JAUGE_SUPERIEURE_CAPACITE", "La cuve " + c.code() + " contient au plus "
                        + EvaluationStock.litres(c.capaciteLitres()) + " : " + EvaluationStock.litres(volume) + " est impossible.");
            }
            Jaugeage m = existant.orElseGet(() -> new Jaugeage(j.id(), j.stationId(), c.id(), j.date(), moment));
            m.mesurer(s.hauteurMm(), volume, s.observation());
            jaugeages.save(m);
        }
        jaugeages.flush();
        return vue(j);
    }

    @Transactional
    public StockJournee ajouterReception(UUID journeeUuid, Integer version, UUID cuveId, Reception.Donnees d) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Reception r = new Reception(j.id(), j.stationId(), j.date());
        renseigner(j, r, cuveId, d);
        return vue(j);
    }

    @Transactional
    public StockJournee modifierReception(UUID journeeUuid, UUID receptionId, Integer version, UUID cuveId, Reception.Donnees d) {
        JourneeRef j = verrouiller(journeeUuid, version);
        renseigner(j, reception(j, receptionId), cuveId, d);
        return vue(j);
    }

    @Transactional
    public StockJournee supprimerReception(UUID journeeUuid, UUID receptionId, Integer version) {
        JourneeRef j = verrouiller(journeeUuid, version);
        receptions.delete(reception(j, receptionId));
        receptions.flush();
        return vue(j);
    }

    private void renseigner(JourneeRef j, Reception r, UUID cuveId, Reception.Donnees d) {
        CuveRef c = cuvesParUuid(j.stationId()).get(cuveId);
        if (c == null || !c.actif()) {
            throw ErreurMetier.regle("CUVE_INCONNUE", "Choisissez une cuve en service de la station.");
        }
        receptions.parBl(d.fournisseur().trim(), d.numeroBl().trim()).filter(x -> !x.getId().equals(r.getId())).ifPresent(x -> {
            String ou = referentiel.station(x.getStationId()).map(StationRef::code).orElse("?");
            throw ErreurMetier.conflit("BL_DEJA_RECEPTIONNE", "Le BL n° " + x.getNumeroBl() + " de " + x.getFournisseur()
                    + " a déjà été réceptionné le " + EvaluationStock.date(x.getDateJour()) + " à " + ou + ".");
        });
        if (c.capaciteLitres() != null && d.jaugeApres() != null && d.jaugeApres().compareTo(c.capaciteLitres()) > 0) {
            throw ErreurMetier.regle("JAUGE_SUPERIEURE_CAPACITE", "La cuve " + c.code() + " contient au plus "
                    + EvaluationStock.litres(c.capaciteLitres()) + ".");
        }
        r.renseigner(c.id(), c.produitId(), d);
        receptions.saveAndFlush(r);
    }

    private Reception reception(JourneeRef j, UUID receptionId) {
        Reception r = receptions.findByUuid(receptionId).orElseThrow(() -> ErreurMetier.introuvable("Réception"));
        if (!r.getJourneeId().equals(j.id())) {
            throw ErreurMetier.introuvable("Réception");
        }
        return r;
    }

    private StockJournee vue(JourneeRef j) {
        StationRef s = referentiel.station(j.stationId()).orElseThrow();
        Map<Long, CuveRef> cuvesParId = referentiel.cuves(j.stationId()).stream()
                .collect(Collectors.toMap(CuveRef::id, Function.identity()));
        List<Jaugeage> mesures = jaugeages.findByJourneeId(j.id());
        List<CuveStock> cuves = new ArrayList<>();
        if (j.brouillon()) {
            for (CuveEvaluee c : evaluation.evaluer(j, exploitation.sortiesParCuve(j.id()))) {
                CalculStock.Resultat r = c.resultat();
                cuves.add(new CuveStock(c.cuve().uuid(), c.cuve().code(), c.cuve().produitCode(), c.cuve().produitLibelle(),
                        c.cuve().capaciteLitres(), c.cuve().bareme(), c.departReporte(), c.ouvertureRequise(), c.depart(),
                        vue(c.ouverture()), c.receptions(), c.sorties(), r.theorique(), vue(c.cloture()), r.ecart(),
                        r.ecartPctJour(), c.prixUnitaire(), r.ecartValeur(), r.cumulSorties(), r.cumulEcart(),
                        r.ecartPctMois(), c.cuve().toleranceEcartPct(), r.niveau() == null ? null : r.niveau().name(),
                        c.observationRequise(), c.anomalies()));
            }
        } else {
            // Journee cloturee : valeurs figees (une journee anterieure au suivi des stocks n'en a pas)
            for (EcartStock e : ecarts.findByJourneeId(j.id())) {
                CuveRef c = cuvesParId.get(e.getCuveId());
                Jaugeage ouverture = trouver(mesures, c.id(), Jaugeage.Moment.OUVERTURE);
                cuves.add(new CuveStock(c.uuid(), c.code(), c.produitCode(), c.produitLibelle(), c.capaciteLitres(),
                        c.bareme(), ouverture == null, false, e.getStockDepart(), vue(ouverture), e.getReceptions(),
                        e.getSorties(), e.getStockTheorique(), vue(trouver(mesures, c.id(), Jaugeage.Moment.CLOTURE)),
                        e.getEcartLitres(), CalculStock.calculer(new CalculStock.Entree(e.getStockDepart(), e.getReceptions(),
                                e.getSorties(), e.getStockPhysique(), BigDecimal.ZERO, BigDecimal.ZERO, null,
                                e.getTolerancePct(), evaluation.seuilEnquetePct())).ecartPctJour(),
                        e.getPrixUnitaire(), e.getEcartValeur(), e.getCumulSortiesMois(), e.getCumulEcartMois(),
                        e.getEcartPctMois(), e.getTolerancePct(), e.getNiveau().name(), false, List.of()));
            }
            cuves.sort(Comparator.comparing(CuveStock::cuveCode));
        }
        List<Reception> livraisons = receptions.findByJourneeIdOrderByIdAsc(j.id());
        Map<Long, ComplementCiterne> complementsParReception = livraisons.isEmpty() ? Map.of()
                : complements.findByReceptionIdIn(livraisons.stream().map(Reception::getId).toList()).stream()
                .collect(Collectors.toMap(ComplementCiterne::getReceptionId, Function.identity()));
        List<ReceptionVue> recues = livraisons.stream().map(r -> {
            CuveRef c = cuvesParId.get(r.getCuveId());
            ComplementCiterne cc = complementsParReception.get(r.getId());
            BigDecimal complement = cc != null ? cc.getLitres()
                    : CalculStock.complement(r.getQuantiteBl(), r.getQuantiteRecue(), cloture.toleranceTransportPct());
            return new ReceptionVue(r.getUuid(), c.uuid(), c.code(), c.produitCode(), r.getNumeroBl(), r.getFournisseur(),
                    r.getTransporteur(), r.getChauffeur(), r.getImmatriculation(), r.getScelles(), r.getQuantiteBl(),
                    r.getJaugeAvant(), r.getJaugeApres(), r.getQuantiteRecue(), r.getTemperatureC(), r.getDensite(),
                    r.getObservation(), r.getQuantiteRecue().subtract(r.getQuantiteBl()),
                    CalculStock.pourcentageLivraison(r.getQuantiteBl(), r.getQuantiteRecue()),
                    complement.signum() > 0 ? complement : null, cc == null ? null : cc.getStatut().name(),
                    r.getModifiePar(), r.getModifieLe());
        }).toList();
        int anomalies = cuves.stream().mapToInt(c -> c.anomalies().size()).sum();
        return new StockJournee(j.uuid(), s.uuid(), s.code(), j.date(), j.statut(), j.version(),
                j.brouillon() && aPermission("JOURNEE_SAISIR"), cuves, recues, anomalies, cloture.toleranceTransportPct());
    }

    private static JaugeageVue vue(Jaugeage m) {
        return m == null ? null : new JaugeageVue(m.getHauteurMm(), m.getVolumeLitres(), m.getObservation(),
                m.getModifiePar(), m.getModifieLe());
    }

    private static Jaugeage trouver(List<Jaugeage> mesures, long cuveId, Jaugeage.Moment moment) {
        return mesures.stream().filter(m -> m.getCuveId() == cuveId && m.getMoment() == moment).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- Analyse mensuelle (feuille ANALYSE DES STOCKS)

    public AnalyseMensuelle mensuel(UUID stationUuid, YearMonth mois) {
        StationRef s = referentiel.station(stationUuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        Map<Long, CuveRef> cuves = referentiel.cuves(s.id()).stream().collect(Collectors.toMap(CuveRef::id, Function.identity()));
        List<EcartStock> lignes = ecarts.findByStationIdAndDateJourBetweenOrderByDateJourAscCuveIdAsc(s.id(),
                mois.atDay(1), mois.atEndOfMonth());
        Map<Long, UUID> journees = new HashMap<>();
        List<LigneStock> vues = lignes.stream().map(e -> {
            CuveRef c = cuves.get(e.getCuveId());
            UUID jid = journees.computeIfAbsent(e.getJourneeId(), id -> exploitation.journee(id).map(JourneeRef::uuid).orElse(null));
            return new LigneStock(e.getDateJour(), jid, c.uuid(), c.code(), c.produitCode(), e.getStockDepart(),
                    e.getReceptions(), e.getSorties(), e.getStockTheorique(), e.getStockPhysique(), e.getEcartLitres(),
                    e.getEcartValeur(), e.getCumulEcartMois(), e.getEcartPctMois(), e.getNiveau().name(), e.getObservation());
        }).toList();

        Map<Long, List<EcartStock>> parCuve = lignes.stream().collect(Collectors.groupingBy(EcartStock::getCuveId,
                LinkedHashMap::new, Collectors.toList()));
        List<TotalCuve> totaux = new ArrayList<>();
        parCuve.forEach((cuveId, l) -> {
            CuveRef c = cuves.get(cuveId);
            EcartStock premiere = l.get(0);
            EcartStock derniere = l.get(l.size() - 1);
            BigDecimal recu = l.stream().map(EcartStock::getReceptions).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sorties = l.stream().map(EcartStock::getSorties).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ecart = l.stream().map(EcartStock::getEcartLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal valeur = l.stream().map(EcartStock::getEcartValeur).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            CalculStock.Resultat r = CalculStock.calculer(new CalculStock.Entree(premiere.getStockDepart(), recu, sorties,
                    premiere.getStockDepart().add(recu).subtract(sorties).add(ecart), BigDecimal.ZERO, BigDecimal.ZERO,
                    null, c.toleranceEcartPct(), evaluation.seuilEnquetePct()));
            totaux.add(new TotalCuve(c.uuid(), c.code(), c.produitCode(), c.produitLibelle(), premiere.getStockDepart(),
                    recu, sorties, r.theorique(), derniere.getStockPhysique(), ecart, r.ecartPctMois(), valeur,
                    c.toleranceEcartPct(), r.niveau().name(), l.size()));
        });
        totaux.sort(Comparator.comparing(TotalCuve::cuveCode));
        return new AnalyseMensuelle(s.uuid(), s.code(), s.nom(), mois, totaux, vues);
    }

    // ---------------------------------------------------------------- Situation du reseau et alertes

    public Situation situation() {
        Map<Long, StationRef> stations = stationsVisibles();
        if (stations.isEmpty()) {
            return new Situation(List.of(), 0, 0, BigDecimal.ZERO);
        }
        Map<Long, CuveRef> cuves = new HashMap<>();
        stations.keySet().forEach(id -> referentiel.cuves(id).forEach(c -> cuves.put(c.id(), c)));
        List<SituationCuve> lignes = ecarts.dernieres(stations.keySet()).stream()
                .filter(e -> cuves.containsKey(e.getCuveId()) && cuves.get(e.getCuveId()).actif())
                .map(e -> {
                    StationRef s = stations.get(e.getStationId());
                    CuveRef c = cuves.get(e.getCuveId());
                    return new SituationCuve(s.uuid(), s.code(), s.nom(), c.uuid(), c.code(), c.produitCode(),
                            c.produitLibelle(), e.getDateJour(), e.getStockPhysique(), c.capaciteLitres(),
                            e.getCumulEcartMois(), e.getEcartPctMois(), e.getTolerancePct(), e.getNiveau().name(),
                            e.getObservation());
                })
                .sorted(Comparator.comparing(SituationCuve::stationCode).thenComparing(SituationCuve::cuveCode))
                .toList();
        List<ComplementCiterne> aRegler = complements.findByStationIdInOrderByDateJourDesc(stations.keySet()).stream()
                .filter(c -> c.getStatut() == ComplementCiterne.Statut.A_REGLER).toList();
        int alertes = (int) lignes.stream().filter(l -> !"NORMAL".equals(l.niveau())).count();
        BigDecimal montant = aRegler.stream().map(ComplementCiterne::getMontant).filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Situation(lignes, alertes, aRegler.size(), montant);
    }

    // ---------------------------------------------------------------- Complements citerne

    public List<ComplementVue> complements(String statut) {
        Map<Long, StationRef> stations = stationsVisibles();
        if (stations.isEmpty()) {
            return List.of();
        }
        List<ComplementCiterne> liste = complements.findByStationIdInOrderByDateJourDesc(stations.keySet()).stream()
                .filter(c -> statut == null || statut.isBlank() || c.getStatut().name().equalsIgnoreCase(statut))
                .toList();
        Map<Long, Reception> recues = receptions.findAllById(liste.stream().map(ComplementCiterne::getReceptionId).toList())
                .stream().collect(Collectors.toMap(Reception::getId, Function.identity()));
        return liste.stream().map(c -> vue(c, stations.get(c.getStationId()), recues.get(c.getReceptionId()))).toList();
    }

    @Transactional
    public ComplementVue reglerComplement(UUID id, Integer version, String reference, LocalDate le) {
        ComplementCiterne c = complements.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Complément citerne"));
        perimetre.exigerStation(c.getStationId());
        c.verifierVersion(version);
        c.regler(reference, le, perimetre.identifiant());
        complements.saveAndFlush(c);
        return vue(c, referentiel.station(c.getStationId()).orElseThrow(), receptions.findById(c.getReceptionId()).orElseThrow());
    }

    private ComplementVue vue(ComplementCiterne c, StationRef s, Reception r) {
        String produit = referentiel.cuves(s.id()).stream().filter(x -> x.id() == r.getCuveId()).map(CuveRef::produitCode)
                .findFirst().orElse(null);
        UUID journee = exploitation.journee(c.getJourneeId()).map(JourneeRef::uuid).orElse(null);
        return new ComplementVue(c.getUuid(), s.uuid(), s.code(), c.getDateJour(), journee, r.getNumeroBl(),
                r.getFournisseur(), r.getTransporteur(), r.getImmatriculation(), produit, r.getQuantiteBl(),
                r.getQuantiteRecue(), c.getLitres(), c.getPrixUnitaire(), c.getMontant(), c.getStatut().name(),
                c.getReferenceReglement(), c.getRegleLe(), c.getReglePar(), c.getVersion(),
                c.getStatut() == ComplementCiterne.Statut.A_REGLER && aPermission("RECEPTION_VALIDER"));
    }

    // ---------------------------------------------------------------- Outils

    private JourneeRef journeeAccessible(UUID uuid) {
        JourneeRef j = exploitation.journee(uuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j.stationId());
        return j;
    }

    /** Perimetre controle avant le verrou ; le verrou exige une journee en saisie a jour et en change la version. */
    private JourneeRef verrouiller(UUID uuid, Integer version) {
        journeeAccessible(uuid);
        return exploitation.verrouillerPourSaisie(uuid, version);
    }

    private Map<UUID, CuveRef> cuvesParUuid(long stationId) {
        return referentiel.cuves(stationId).stream().collect(Collectors.toMap(CuveRef::uuid, Function.identity()));
    }

    private Map<Long, StationRef> stationsVisibles() {
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        return referentiel.stations().stream()
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .collect(Collectors.toMap(StationRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static Jaugeage.Moment moment(String m) {
        if (m == null) {
            return Jaugeage.Moment.CLOTURE;
        }
        try {
            return Jaugeage.Moment.valueOf(m.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("MOMENT_INVALIDE", "Moment de jaugeage inconnu : OUVERTURE ou CLOTURE.");
        }
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
