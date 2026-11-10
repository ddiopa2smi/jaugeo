package sn.digitalperf.jaugeo.personnel.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.RelevePistolet;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.CreneauVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EmployeVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.FeuilleVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.LigneVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PistoletVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PosteVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PostesJournee;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisieFeuille;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisieLigne;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisiePoste;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.Totaux;
import sn.digitalperf.jaugeo.personnel.domaine.CalculFeuille;
import sn.digitalperf.jaugeo.personnel.domaine.CreneauDepot;
import sn.digitalperf.jaugeo.personnel.domaine.Employe;
import sn.digitalperf.jaugeo.personnel.domaine.EmployeDepot;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisse;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisseDepot;
import sn.digitalperf.jaugeo.personnel.domaine.LigneFeuille;
import sn.digitalperf.jaugeo.personnel.domaine.LigneFeuilleDepot;
import sn.digitalperf.jaugeo.personnel.domaine.ParametreStation;
import sn.digitalperf.jaugeo.personnel.domaine.ParametreStationDepot;
import sn.digitalperf.jaugeo.personnel.domaine.Poste;
import sn.digitalperf.jaugeo.personnel.domaine.PosteDepot;
import sn.digitalperf.jaugeo.personnel.domaine.TypePoste;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.PistoletRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Feuilles de caisse par pompiste dans la journee : a chaque changement de poste, l'index de chaque pistolet est
 * releve et son pompiste remet sa caisse. Les ventes du poste sont valorisees au prix en vigueur a l'ouverture de
 * la journee ; l'ecart (remis - du) est un suivi individuel, fige a la cloture.
 */
@Service
@Transactional(readOnly = true)
public class FeuilleService {

    /** Etat calcule des feuilles d'une journee. */
    record Etat(List<Poste> postes, List<FeuilleCaisse> feuilles, List<LigneFeuille> lignes,
                Map<Long, CalculFeuille.Pistolet> pistolets, List<CalculFeuille.Ligne> calculees,
                Map<Long, BigDecimal> duParFeuille, List<String> problemes) {
    }

    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final Tarification tarification;
    private final PerimetreCourant perimetre;
    private final PosteDepot postes;
    private final FeuilleCaisseDepot feuilles;
    private final LigneFeuilleDepot lignes;
    private final EmployeDepot employes;
    private final CreneauDepot creneaux;
    private final ParametreStationDepot parametres;

    FeuilleService(JourneesExploitation exploitation, ReferentielConsultation referentiel, Tarification tarification,
                   PerimetreCourant perimetre, PosteDepot postes, FeuilleCaisseDepot feuilles, LigneFeuilleDepot lignes,
                   EmployeDepot employes, CreneauDepot creneaux, ParametreStationDepot parametres) {
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.tarification = tarification;
        this.perimetre = perimetre;
        this.postes = postes;
        this.feuilles = feuilles;
        this.lignes = lignes;
        this.employes = employes;
        this.creneaux = creneaux;
        this.parametres = parametres;
    }

    boolean actives(long stationId) {
        return parametres.findByStationId(stationId).map(ParametreStation::isFeuillesCaisse).orElse(false);
    }

    public PostesJournee journee(UUID journeeUuid) {
        JourneeRef j = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j.stationId());
        return vue(j);
    }

    /** Remplace les feuilles de caisse d'un poste de la journee. */
    @Transactional
    public PostesJournee enregistrer(UUID journeeUuid, TypePoste type, SaisiePoste s) {
        JourneeRef j0 = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j0.stationId());
        if (!actives(j0.stationId())) {
            throw ErreurMetier.regle("FEUILLES_INACTIVES", "Cette station ne tient pas de feuilles de caisse par pompiste.");
        }
        if (s.feuilles() == null || s.feuilles().isEmpty()) {
            throw ErreurMetier.regle("FEUILLES_OBLIGATOIRES", "Saisissez au moins une feuille de caisse pour ce poste.");
        }
        JourneeRef j = exploitation.verrouillerPourSaisie(journeeUuid, s.version());
        Map<UUID, PistoletRef> pistolets = referentiel.pistolets(j.stationId()).stream()
                .collect(Collectors.toMap(PistoletRef::uuid, Function.identity()));
        Set<Long> releves = exploitation.releves(j.id()).stream().map(RelevePistolet::pistoletId).collect(Collectors.toSet());

        Long ancien = postes.findByJourneeIdAndType(j.id(), type).map(Poste::getId).orElse(null);
        if (ancien != null) {
            lignes.supprimerPourPoste(ancien);
            feuilles.supprimerPourPoste(ancien);
        }
        Poste poste = ancien == null ? new Poste(j.id(), j.stationId(), j.date(), type) : postes.findById(ancien).orElseThrow();
        poste.observer(s.observation());
        poste = postes.saveAndFlush(poste);

        Set<Long> pompistes = new HashSet<>();
        Set<Long> tenus = new HashSet<>();
        for (SaisieFeuille f : s.feuilles()) {
            if (f.employeId() == null) {
                throw ErreurMetier.regle("POMPISTE_OBLIGATOIRE", "Indiquez le pompiste de chaque feuille de caisse.");
            }
            Employe e = employes.findByUuid(f.employeId()).orElseThrow(() -> ErreurMetier.introuvable("Employé"));
            if (!e.getStationId().equals(j.stationId()) || !e.isActif()) {
                throw ErreurMetier.regle("EMPLOYE_HORS_STATION", e.nomComplet() + " n'est pas un employé actif de cette station.");
            }
            if (!pompistes.add(e.getId())) {
                throw ErreurMetier.regle("POMPISTE_EN_DOUBLE", e.nomComplet() + " a deux feuilles de caisse pour ce poste : regroupez ses pistolets.");
            }
            if (f.lignes() == null || f.lignes().isEmpty()) {
                throw ErreurMetier.regle("PISTOLETS_OBLIGATOIRES", "Indiquez les pistolets tenus par " + e.nomComplet() + ".");
            }
            FeuilleCaisse feuille = new FeuilleCaisse(poste, e.getId());
            feuille.remettre(f.especes(), f.electronique(), f.bons(), f.cartes(), f.observation());
            feuille = feuilles.saveAndFlush(feuille);
            for (SaisieLigne l : f.lignes()) {
                PistoletRef p = l.pistoletId() == null ? null : pistolets.get(l.pistoletId());
                if (p == null || !releves.contains(p.id())) {
                    throw ErreurMetier.regle("PISTOLET_INCONNU", "Ce pistolet ne fait pas partie de la journée.");
                }
                if (!tenus.add(p.id())) {
                    throw ErreurMetier.regle("PISTOLET_DEJA_TENU", "Le pistolet " + p.code() + " est tenu par deux pompistes pendant ce poste : "
                            + "un seul pompiste par pistolet.");
                }
                lignes.save(new LigneFeuille(feuille, p.id(), l.indexFin(), l.remiseCuve()));
            }
        }
        lignes.flush();
        return vue(j);
    }

    @Transactional
    public PostesJournee supprimer(UUID journeeUuid, TypePoste type, Integer version) {
        JourneeRef j0 = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j0.stationId());
        JourneeRef j = exploitation.verrouillerPourSaisie(journeeUuid, version);
        postes.findByJourneeIdAndType(j.id(), type).map(Poste::getId).ifPresent(id -> {
            lignes.supprimerPourPoste(id);
            feuilles.supprimerPourPoste(id);
            postes.deleteById(id);
            postes.flush();
        });
        return vue(j);
    }

    // ---------------------------------------------------------------- Calcul

    Etat etat(JourneeRef j) {
        List<Poste> ps = postes.findByJourneeId(j.id()).stream().sorted(Comparator.comparing(Poste::getType)).toList();
        List<FeuilleCaisse> fs = feuilles.findByJourneeIdOrderByIdAsc(j.id());
        List<LigneFeuille> ls = ps.isEmpty() ? List.of() : lignes.findByPosteIdInOrderByIdAsc(ps.stream().map(Poste::getId).toList());
        Map<Long, PistoletRef> refs = referentiel.pistolets(j.stationId()).stream()
                .collect(Collectors.toMap(PistoletRef::id, Function.identity()));
        Map<Long, BigDecimal> prix = new HashMap<>();
        Map<Long, CalculFeuille.Pistolet> pistolets = new LinkedHashMap<>();
        for (RelevePistolet r : exploitation.releves(j.id())) {
            PistoletRef p = refs.get(r.pistoletId());
            BigDecimal pu = prix.computeIfAbsent(r.produitId(),
                    id -> tarification.prixEnVigueur(j.stationId(), id, j.debut()).orElse(null));
            List<CalculFeuille.Rupture> ruptures = referentiel.changementsCompteur(p.id(), j.debut(), j.fin()).stream()
                    .map(c -> new CalculFeuille.Rupture(c.indexAvant(), c.indexApres())).toList();
            pistolets.put(p.id(), new CalculFeuille.Pistolet(p.id(), p.code(), r.indexDepart(), r.sortieNette(), pu, ruptures));
        }
        Map<Long, TypePoste> typeParPoste = ps.stream().collect(Collectors.toMap(Poste::getId, Poste::getType));
        List<CalculFeuille.Ligne> calculees = CalculFeuille.lignes(pistolets, ls.stream()
                .filter(l -> pistolets.containsKey(l.getPistoletId()))
                .map(l -> new CalculFeuille.Saisie(typeParPoste.get(l.getPosteId()), l.getFeuilleId(), l.getPistoletId(),
                        l.getIndexFin(), l.getRemiseCuve())).toList());
        List<String> problemes = new ArrayList<>();
        if (ps.isEmpty()) {
            problemes.add("Aucune feuille de caisse n'est saisie : relevez les index à chaque changement de poste.");
        } else {
            problemes.addAll(CalculFeuille.problemes(pistolets, calculees));
        }
        return new Etat(ps, fs, ls, pistolets, calculees, CalculFeuille.duParFeuille(calculees), problemes);
    }

    private PostesJournee vue(JourneeRef j) {
        boolean actif = actives(j.stationId());
        Etat e = etat(j);
        StationRef station = referentiel.station(j.stationId()).orElseThrow();
        Map<Long, PistoletRef> refs = referentiel.pistolets(j.stationId()).stream()
                .collect(Collectors.toMap(PistoletRef::id, Function.identity()));
        List<Employe> actifs = employes.findByStationIdOrderByNomAscPrenomAsc(j.stationId());
        Map<Long, Employe> parId = new HashMap<>();
        actifs.forEach(x -> parId.put(x.getId(), x));
        // Un pompiste mute depuis reste nomme sur ses anciennes feuilles
        e.feuilles().stream().map(FeuilleCaisse::getEmployeId).filter(id -> !parId.containsKey(id))
                .forEach(id -> employes.findById(id).ifPresent(x -> parId.put(id, x)));

        List<PistoletVue> pistolets = exploitation.releves(j.id()).stream().map(r -> {
            PistoletRef p = refs.get(r.pistoletId());
            return new PistoletVue(p.uuid(), p.code(), p.pompeCode(), p.produitCode(), r.indexDepart(), r.indexArrivee(),
                    r.sortieNette(), e.pistolets().get(p.id()).prix());
        }).sorted(Comparator.comparing(PistoletVue::pompeCode).thenComparing(PistoletVue::code)).toList();

        Map<Long, List<CalculFeuille.Ligne>> parFeuille = e.calculees().stream()
                .collect(Collectors.groupingBy(l -> l.saisie().feuilleId()));
        BigDecimal[] t = new BigDecimal[8];
        java.util.Arrays.fill(t, BigDecimal.ZERO);
        List<PosteVue> vues = new ArrayList<>();
        for (Poste p : e.postes()) {
            List<FeuilleVue> fv = new ArrayList<>();
            BigDecimal du = BigDecimal.ZERO;
            BigDecimal remis = BigDecimal.ZERO;
            for (FeuilleCaisse f : e.feuilles()) {
                if (!f.getPosteId().equals(p.getId())) {
                    continue;
                }
                List<LigneVue> lv = parFeuille.getOrDefault(f.getId(), List.of()).stream().map(l -> {
                    PistoletRef r = refs.get(l.saisie().pistoletId());
                    return new LigneVue(r.uuid(), r.code(), r.pompeCode(), r.produitCode(), l.indexDebut(), l.saisie().indexFin(),
                            l.saisie().remiseCuve(), l.litres(), l.prix(), l.montant(), l.anomalie());
                }).toList();
                BigDecimal litres = lv.stream().map(LigneVue::litres).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal duFeuille = f.getMontantDu() != null ? f.getMontantDu() : e.duParFeuille().getOrDefault(f.getId(), BigDecimal.ZERO);
                BigDecimal ecart = f.remis().subtract(duFeuille);
                Employe emp = parId.get(f.getEmployeId());
                fv.add(new FeuilleVue(f.getUuid(), emp.getUuid(), emp.nomComplet(), lv, litres, duFeuille, f.getEspeces(),
                        f.getElectronique(), f.getBons(), f.getCartes(), f.remis(), ecart, f.getObservation()));
                du = du.add(duFeuille);
                remis = remis.add(f.remis());
                t[0] = t[0].add(litres);
                t[1] = t[1].add(duFeuille);
                t[2] = t[2].add(f.getEspeces());
                t[3] = t[3].add(f.getElectronique());
                t[4] = t[4].add(f.getBons());
                t[5] = t[5].add(f.getCartes());
                t[6] = t[6].add(f.remis());
                t[7] = t[7].add(ecart);
            }
            vues.add(new PosteVue(p.getType(), p.getType().libelle(), p.getObservation(), fv, du, remis, remis.subtract(du)));
        }
        Map<Long, Employe> tous = parId;
        List<CreneauVue> planning = creneaux.findByStationIdAndDateJour(j.stationId(), j.date()).stream()
                .filter(c -> tous.containsKey(c.getEmployeId()))
                .map(c -> new CreneauVue(c.getDateJour(), c.getPoste(), tous.get(c.getEmployeId()).getUuid())).toList();
        List<EmployeVue> choix = actifs.stream().filter(Employe::isActif).map(x -> PersonnelService.vue(x, station)).toList();
        return new PostesJournee(j.uuid(), j.version(), j.statut(), actif, actif && j.brouillon() && aPermission("JOURNEE_SAISIR"),
                pistolets, choix, planning, vues, new Totaux(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]),
                actif && j.brouillon() ? e.problemes() : List.of());
    }

    static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
