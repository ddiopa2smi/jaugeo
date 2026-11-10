package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.LivraisonVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.PropositionLigne;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.Propositions;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieBl;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieLivraison;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieTournee;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TourneeVue;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CalculAppro;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Citerne;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CiterneDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Fournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Livraison;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LivraisonDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TourneeDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TransporteurDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.EtatCuve;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.ReceptionRef;

/** Propositions de commande et tournees citernes (commandee -> chargee -> en route -> depotee). */
@Service
@Transactional(readOnly = true)
public class TourneeService {

    private static final Set<Tournee.Statut> EN_COURS = Set.of(Tournee.Statut.COMMANDEE, Tournee.Statut.CHARGEE,
            Tournee.Statut.EN_ROUTE);

    private final TourneeDepot tournees;
    private final LivraisonDepot livraisons;
    private final FournisseurDepot fournisseurs;
    private final TransporteurDepot transporteurs;
    private final CiterneDepot citernes;
    private final SuiviLivraisons suivi;
    private final ReferentielConsultation referentiel;
    private final Clock horloge;
    private final int margeJours;
    private final BigDecimal tauxRemplissagePct;

    TourneeService(TourneeDepot tournees, LivraisonDepot livraisons, FournisseurDepot fournisseurs,
                   TransporteurDepot transporteurs, CiterneDepot citernes, SuiviLivraisons suivi,
                   ReferentielConsultation referentiel, Clock horloge,
                   @Value("${jaugeo.appro.marge-securite-jours:2}") int margeJours,
                   @Value("${jaugeo.appro.taux-remplissage-pct:95}") BigDecimal tauxRemplissagePct) {
        this.tournees = tournees;
        this.livraisons = livraisons;
        this.fournisseurs = fournisseurs;
        this.transporteurs = transporteurs;
        this.citernes = citernes;
        this.suivi = suivi;
        this.referentiel = referentiel;
        this.horloge = horloge;
        this.margeJours = margeJours;
        this.tauxRemplissagePct = tauxRemplissagePct;
    }

    // ---------------------------------------------------------------- Proposition de commande

    public Propositions propositions() {
        LocalDate jour = LocalDate.now(horloge);
        Fournisseur principal = fournisseurs.findByPrincipalTrue().orElse(null);
        int delai = principal == null ? 0 : principal.getDelaiLivraison();
        Map<Long, StationRef> stations = suivi.stations();
        Map<Long, ProduitRef> produits = suivi.produits();

        // Livraisons des tournees en cours non encore receptionnees, par station et produit
        Map<List<Long>, BigDecimal> enCours = new HashMap<>();
        List<Livraison> ouvertes = livraisonsEnCours();
        Set<Long> recues = ouvertes.isEmpty() ? Set.of() : suivi.receptions(ouvertes.stream().map(Livraison::getId).toList()).keySet();
        for (Livraison l : ouvertes) {
            if (!recues.contains(l.getId())) {
                enCours.merge(List.of(l.getStationId(), l.getProduitId()), l.quantitePrevue(), BigDecimal::add);
            }
        }

        Map<List<Long>, List<EtatCuve>> parBesoin = suivi.stocks().etatCuves().stream()
                .collect(Collectors.groupingBy(e -> List.of(e.stationId(), e.produitId()), LinkedHashMap::new, Collectors.toList()));
        BigDecimal taux = tauxRemplissagePct.divide(BigDecimal.valueOf(100));
        List<PropositionLigne> lignes = new ArrayList<>();
        parBesoin.forEach((cle, cuves) -> {
            StationRef s = stations.get(cle.get(0));
            ProduitRef p = produits.get(cle.get(1));
            BigDecimal route = enCours.getOrDefault(cle, BigDecimal.ZERO);
            CalculAppro.Besoin b = CalculAppro.besoin(cuves.stream().map(c -> new CalculAppro.Cuve(c.capaciteLitres(),
                    c.creuxSecuriteLitres(), c.seuilReapproLitres(), c.stockPhysique(), c.date(), c.ventesMoyennes())).toList(),
                    route, jour, delai, margeJours, taux);
            LocalDate dateStock = cuves.stream().map(EtatCuve::date).min(Comparator.naturalOrder()).orElse(null);
            lignes.add(new PropositionLigne(s.uuid(), s.code(), s.nom(), p.uuid(), p.code(), p.libelle(), dateStock,
                    b.stockAujourdhui(), b.capaciteUtile(), b.ventesMoyennes(), b.autonomieJours(), route, b.stockArrivee(),
                    b.quantite(), b.ruptureLe(), b.aCommander(), b.motif()));
        });
        lignes.sort(Comparator.comparing((PropositionLigne l) -> !l.aCommander())
                .thenComparing(PropositionLigne::ruptureLe, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PropositionLigne::stationCode).thenComparing(PropositionLigne::produitCode));
        return new Propositions(principal == null ? null : principal.getUuid(), principal == null ? null : principal.getNom(),
                delai, margeJours, tauxRemplissagePct, jour, lignes);
    }

    private List<Livraison> livraisonsEnCours() {
        List<Long> ids = tournees.findByStatutIn(EN_COURS).stream().map(Tournee::getId).toList();
        return ids.isEmpty() ? List.of() : livraisons.findByTourneeIdIn(ids);
    }

    // ---------------------------------------------------------------- Tournees

    public List<TourneeVue> liste(boolean enCoursSeulement) {
        List<Tournee> liste = tournees.findAllByOrderByDateLivraisonPrevueDescIdDesc();
        List<TourneeVue> vues = vues(liste);
        return enCoursSeulement ? vues.stream().filter(v -> !"ANNULEE".equals(v.statut()) && !"DEPOTEE".equals(v.statut())).toList()
                : vues;
    }

    public TourneeVue detail(UUID id) {
        return vues(List.of(tournee(id))).get(0);
    }

    @Transactional
    public TourneeVue creer(SaisieTournee s) {
        LocalDate debut = s.dateChargementPrevue() == null ? LocalDate.now(horloge) : s.dateChargementPrevue();
        Tournee t = new Tournee("TO" + debut.getYear() + "-" + String.format(Locale.ROOT, "%05d", tournees.prochainNumero()));
        planifier(t, s);
        if (s.livraisons() == null || s.livraisons().isEmpty()) {
            throw ErreurMetier.regle("LIVRAISONS_OBLIGATOIRES", "Ajoutez au moins une livraison (station, produit, quantité).");
        }
        Tournee enregistree = tournees.saveAndFlush(t);
        commander(enregistree, s.livraisons());
        return detail(enregistree.getUuid());
    }

    @Transactional
    public TourneeVue modifier(UUID id, SaisieTournee s) {
        Tournee t = tournee(id);
        t.verifierVersion(s.version());
        planifier(t, s);
        if (s.livraisons() != null) {
            if (t.getStatut() != Tournee.Statut.COMMANDEE) {
                throw ErreurMetier.regle("TOURNEE_CHARGEE", "La citerne est chargée : les livraisons ne changent plus.");
            }
            List<Livraison> anciennes = livraisons.findByTourneeIdOrderByOrdreAsc(t.getId());
            exigerNonReceptionnees(anciennes, "modifier les livraisons");
            livraisons.deleteAll(anciennes);
            livraisons.flush();
            commander(t, s.livraisons());
        }
        tournees.saveAndFlush(t);
        return detail(id);
    }

    @Transactional
    public TourneeVue charger(UUID id, Integer version, LocalDate le, String scelles, List<SaisieBl> bls) {
        Tournee t = tournee(id);
        t.verifierVersion(version);
        if (le != null && le.isAfter(LocalDate.now(horloge))) {
            throw ErreurMetier.regle("DATE_FUTURE", "Le chargement ne peut pas être daté dans le futur.");
        }
        t.charger(le, scelles);
        List<Livraison> siennes = livraisons.findByTourneeIdOrderByOrdreAsc(t.getId());
        Map<UUID, SaisieBl> parLivraison = (bls == null ? List.<SaisieBl>of() : bls).stream()
                .collect(Collectors.toMap(SaisieBl::livraisonId, Function.identity(), (a, b) -> b));
        Map<Long, ReceptionRef> recues = suivi.receptions(siennes.stream().map(Livraison::getId).toList());
        Set<String> numeros = new HashSet<>();
        for (Livraison l : siennes) {
            SaisieBl b = parLivraison.get(l.getUuid());
            if (b == null) {
                throw ErreurMetier.regle("BL_OBLIGATOIRE", "Indiquez le BL de chaque livraison de la tournée.");
            }
            if (recues.containsKey(l.getId())) {
                // Livraison deja receptionnee : son BL fait foi
                continue;
            }
            String numero = b.numeroBl() == null ? "" : b.numeroBl().trim().toUpperCase();
            if (!numero.isEmpty() && !numeros.add(numero)) {
                throw ErreurMetier.regle("BL_EN_DOUBLE", "Le BL n° " + numero + " figure deux fois dans la tournée.");
            }
            livraisons.parBl(t.getFournisseurId(), numero, Tournee.Statut.ANNULEE).stream()
                    .filter(x -> !x.getId().equals(l.getId())).findFirst().ifPresent(x -> {
                        String autre = tournees.findById(x.getTourneeId()).map(Tournee::getNumero).orElse("?");
                        throw ErreurMetier.conflit("BL_DEJA_CHARGE", "Le BL n° " + numero + " est déjà celui de la tournée " + autre + ".");
                    });
            l.renseignerBl(b.numeroBl(), b.quantiteBl(), b.quantiteBl15());
        }
        livraisons.saveAllAndFlush(siennes);
        tournees.saveAndFlush(t);
        return detail(id);
    }

    @Transactional
    public TourneeVue partir(UUID id, Integer version) {
        Tournee t = tournee(id);
        t.verifierVersion(version);
        t.partir(horloge.instant());
        tournees.saveAndFlush(t);
        return detail(id);
    }

    @Transactional
    public TourneeVue annuler(UUID id, Integer version, String motif) {
        Tournee t = tournee(id);
        t.verifierVersion(version);
        exigerNonReceptionnees(livraisons.findByTourneeIdOrderByOrdreAsc(t.getId()), "annuler la tournée");
        t.annuler(motif);
        tournees.saveAndFlush(t);
        return detail(id);
    }

    private void exigerNonReceptionnees(List<Livraison> liste, String action) {
        if (!liste.isEmpty() && !suivi.receptions(liste.stream().map(Livraison::getId).toList()).isEmpty()) {
            throw ErreurMetier.regle("LIVRAISON_RECEPTIONNEE", "Une livraison de la tournée est déjà réceptionnée : impossible de "
                    + action + ".");
        }
    }

    private void planifier(Tournee t, SaisieTournee s) {
        if (s.fournisseurId() == null || s.transporteurId() == null) {
            throw ErreurMetier.regle("PARTENAIRES_OBLIGATOIRES", "Choisissez le dépôt de chargement et le transporteur.");
        }
        Fournisseur f = fournisseurs.findByUuid(s.fournisseurId()).orElseThrow(() -> ErreurMetier.introuvable("Fournisseur"));
        Transporteur tr = transporteurs.findByUuid(s.transporteurId()).orElseThrow(() -> ErreurMetier.introuvable("Transporteur"));
        if (!f.isActif() && !f.getId().equals(t.getFournisseurId())) {
            throw ErreurMetier.regle("FOURNISSEUR_INACTIF", "Le fournisseur " + f.getNom() + " n'est plus actif.");
        }
        if (!tr.isActif() && !tr.getId().equals(t.getTransporteurId())) {
            throw ErreurMetier.regle("TRANSPORTEUR_INACTIF", "Le transporteur " + tr.getNom() + " n'est plus actif.");
        }
        Citerne c = null;
        if (s.citerneId() != null) {
            c = citernes.findByUuid(s.citerneId()).orElseThrow(() -> ErreurMetier.introuvable("Citerne"));
            if (!c.getTransporteurId().equals(tr.getId())) {
                throw ErreurMetier.regle("CITERNE_AUTRE_TRANSPORTEUR", "La citerne " + c.getImmatriculation()
                        + " n'appartient pas à " + tr.getNom() + ".");
            }
            if (!c.isActif() && !c.getId().equals(t.getCiterneId())) {
                throw ErreurMetier.regle("CITERNE_INACTIVE", "La citerne " + c.getImmatriculation() + " n'est plus en service.");
            }
        }
        t.planifier(new Tournee.Plan(f.getId(), tr.getId(), c == null ? null : c.getId(),
                c != null ? c.getImmatriculation() : s.immatriculation(), s.chauffeur(), s.dateChargementPrevue(),
                s.dateLivraisonPrevue(), s.observation()));
        if (c != null && t.getStatut() == Tournee.Statut.COMMANDEE && s.livraisons() != null) {
            BigDecimal total = s.livraisons().stream().map(SaisieLivraison::quantite).filter(q -> q != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(c.getCapaciteLitres()) > 0) {
                throw ErreurMetier.regle("CAPACITE_DEPASSEE", "La tournée totalise " + litres(total) + " pour une citerne de "
                        + litres(c.getCapaciteLitres()) + ".");
            }
        }
    }

    private void commander(Tournee t, List<SaisieLivraison> saisies) {
        Set<List<Long>> vus = new HashSet<>();
        int ordre = 0;
        List<Livraison> nouvelles = new ArrayList<>();
        for (SaisieLivraison s : saisies) {
            if (s.stationId() == null || s.produitId() == null) {
                throw ErreurMetier.regle("LIVRAISON_INCOMPLETE", "Chaque livraison précise la station et le produit.");
            }
            StationRef st = suivi.station(s.stationId());
            ProduitRef p = suivi.produit(s.produitId());
            if (!st.actif()) {
                throw ErreurMetier.regle("STATION_INACTIVE", "La station " + st.code() + " n'est plus active.");
            }
            if (referentiel.cuves(st.id()).stream().noneMatch(c -> c.actif() && c.produitId() == p.id())) {
                throw ErreurMetier.regle("CUVE_ABSENTE", "La station " + st.code() + " n'a pas de cuve de " + p.libelle() + " en service.");
            }
            if (!vus.add(List.of(st.id(), p.id()))) {
                throw ErreurMetier.regle("LIVRAISON_EN_DOUBLE", "La tournée livre deux fois le " + p.libelle() + " à " + st.code()
                        + " : regroupez les quantités.");
            }
            Livraison l = new Livraison(t.getId());
            l.commander(++ordre, st.id(), p.id(), s.quantite());
            nouvelles.add(l);
        }
        livraisons.saveAllAndFlush(nouvelles);
    }

    private Tournee tournee(UUID id) {
        return tournees.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Tournée"));
    }

    private List<TourneeVue> vues(List<Tournee> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Livraison>> parTournee = livraisons.findByTourneeIdIn(liste.stream().map(Tournee::getId).toList()).stream()
                .sorted(Comparator.comparingInt(Livraison::getOrdre))
                .collect(Collectors.groupingBy(Livraison::getTourneeId));
        List<Long> ids = parTournee.values().stream().flatMap(List::stream).map(Livraison::getId).toList();
        Map<Long, ReceptionRef> recues = ids.isEmpty() ? Map.of() : suivi.receptions(ids);
        Map<Long, Fournisseur> fs = fournisseurs.findAll().stream().collect(Collectors.toMap(Fournisseur::getId, Function.identity()));
        Map<Long, Transporteur> ts = transporteurs.findAll().stream().collect(Collectors.toMap(Transporteur::getId, Function.identity()));
        Map<Long, StationRef> stations = suivi.stations();
        Map<Long, ProduitRef> produits = suivi.produits();
        return liste.stream().map(t -> {
            Fournisseur f = fs.get(t.getFournisseurId());
            Transporteur tr = ts.get(t.getTransporteurId());
            Citerne c = t.getCiterneId() == null ? null : citernes.findById(t.getCiterneId()).orElse(null);
            BigDecimal tolerance = suivi.tolerance(tr);
            List<LivraisonVue> lignes = parTournee.getOrDefault(t.getId(), List.of()).stream().map(l -> {
                StationRef s = stations.get(l.getStationId());
                ProduitRef p = produits.get(l.getProduitId());
                return new LivraisonVue(l.getUuid(), l.getOrdre(), s.uuid(), s.code(), s.nom(), p.uuid(), p.code(),
                        l.getQuantiteCommandee(), l.getNumeroBl(), l.getQuantiteBl(), l.getQuantiteBl15(),
                        suivi.vue(recues.get(l.getId()), t, tolerance), l.getVersion());
            }).toList();
            int receptionnees = (int) lignes.stream().filter(l -> l.reception() != null).count();
            String statut = t.getStatut() != Tournee.Statut.ANNULEE && !lignes.isEmpty() && receptionnees == lignes.size()
                    ? "DEPOTEE" : t.getStatut().name();
            BigDecimal total = lignes.stream().map(LivraisonVue::quantiteCommandee).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TourneeVue(t.getUuid(), t.getNumero(), statut, f.getUuid(), f.getNom(), tr.getUuid(), tr.getNom(),
                    c == null ? null : c.getUuid(), t.getImmatriculation(), c == null ? null : c.getCapaciteLitres(),
                    t.getChauffeur(), t.getDateChargementPrevue(), t.getDateLivraisonPrevue(), t.getChargeLe(), t.getScelles(),
                    t.getPartiLe(), t.getMotifAnnulation(), t.getObservation(), total, lignes.size(), receptionnees,
                    t.getVersion(), lignes);
        }).toList();
    }

    private static String litres(BigDecimal v) {
        return Libelles.litres(v);
    }
}
