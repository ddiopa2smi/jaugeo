package sn.digitalperf.jaugeo.personnel.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.AffectationVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.CreneauVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EcartEmploye;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EcartsStation;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EmployeDetail;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EmployeVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.FeuilleResume;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PersonnelStation;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PlanningSemaine;
import sn.digitalperf.jaugeo.personnel.domaine.Affectation;
import sn.digitalperf.jaugeo.personnel.domaine.AffectationDepot;
import sn.digitalperf.jaugeo.personnel.domaine.Creneau;
import sn.digitalperf.jaugeo.personnel.domaine.CreneauDepot;
import sn.digitalperf.jaugeo.personnel.domaine.Employe;
import sn.digitalperf.jaugeo.personnel.domaine.EmployeDepot;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisse;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisseDepot;
import sn.digitalperf.jaugeo.personnel.domaine.ParametreStation;
import sn.digitalperf.jaugeo.personnel.domaine.ParametreStationDepot;
import sn.digitalperf.jaugeo.personnel.domaine.TypePoste;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;

/** Employes d'une station, affectations, planning par poste et suivi des ecarts de caisse individuels. */
@Service
@Transactional(readOnly = true)
public class PersonnelService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    /** Reconduction du planning : nombre de semaines au plus. */
    private static final int SEMAINES_MAX = 8;

    private final EmployeDepot employes;
    private final AffectationDepot affectations;
    private final CreneauDepot creneaux;
    private final ParametreStationDepot parametres;
    private final FeuilleCaisseDepot feuilles;
    private final ReferentielConsultation referentiel;
    private final JourneesExploitation exploitation;
    private final PerimetreCourant perimetre;
    private final Clock horloge;

    PersonnelService(EmployeDepot employes, AffectationDepot affectations, CreneauDepot creneaux,
                     ParametreStationDepot parametres, FeuilleCaisseDepot feuilles, ReferentielConsultation referentiel,
                     JourneesExploitation exploitation, PerimetreCourant perimetre, Clock horloge) {
        this.employes = employes;
        this.affectations = affectations;
        this.creneaux = creneaux;
        this.parametres = parametres;
        this.feuilles = feuilles;
        this.referentiel = referentiel;
        this.exploitation = exploitation;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Employes

    public PersonnelStation station(UUID stationUuid) {
        StationRef s = stationAccessible(stationUuid);
        boolean feuillesCaisse = parametres.findByStationId(s.id()).map(ParametreStation::isFeuillesCaisse).orElse(false);
        return new PersonnelStation(s.uuid(), s.code(), s.nom(), feuillesCaisse, FeuilleService.aPermission("PERSONNEL_GERER"),
                peutReglerFeuilles(), employes.findByStationIdOrderByNomAscPrenomAsc(s.id()).stream().map(e -> vue(e, s)).toList());
    }

    @Transactional
    public EmployeVue creer(UUID stationUuid, Employe.Donnees d) {
        StationRef s = stationAccessible(stationUuid);
        Employe e = new Employe(s.id());
        e.renseigner(d);
        exigerUnique(e);
        Employe enregistre = employes.saveAndFlush(e);
        affectations.saveAndFlush(new Affectation(enregistre.getId(), s.id(), d.dateEntree() == null ? aujourdhui() : d.dateEntree()));
        return vue(enregistre, s);
    }

    /** Modifie la fiche ; un changement de station clot l'affectation en cours et en ouvre une nouvelle. */
    @Transactional
    public EmployeVue modifier(UUID id, Integer version, UUID stationUuid, Employe.Donnees d) {
        Employe e = employe(id);
        e.verifierVersion(version);
        e.renseigner(d);
        exigerUnique(e);
        StationRef s = referentiel.station(e.getStationId()).orElseThrow();
        if (stationUuid != null && !stationUuid.equals(s.uuid())) {
            StationRef nouvelle = stationAccessible(stationUuid);
            LocalDate jour = aujourdhui();
            affectations.findFirstByEmployeIdAndFinIsNullOrderByDebutDescIdDesc(e.getId()).ifPresent(a -> {
                a.terminer(jour);
                affectations.saveAndFlush(a);
            });
            affectations.saveAndFlush(new Affectation(e.getId(), nouvelle.id(), jour));
            e.affecter(nouvelle.id());
            s = nouvelle;
        }
        return vue(employes.saveAndFlush(e), s);
    }

    /** Fiche de l'employe : affectations et feuilles de caisse figees du mois (ecarts individuels). */
    public EmployeDetail detail(UUID id, YearMonth mois) {
        Employe e = employe(id);
        StationRef s = referentiel.station(e.getStationId()).orElseThrow();
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        YearMonth m = mois == null ? YearMonth.from(aujourdhui()) : mois;
        List<FeuilleResume> resumes = new ArrayList<>();
        BigDecimal manquants = BigDecimal.ZERO;
        BigDecimal excedents = BigDecimal.ZERO;
        for (FeuilleCaisse f : feuilles.findByEmployeIdAndDateJourBetweenAndEcartIsNotNullOrderByDateJourDescIdDesc(e.getId(),
                m.atDay(1), m.atEndOfMonth())) {
            UUID journee = exploitation.journee(f.getJourneeId()).map(JourneeRef::uuid).orElse(null);
            resumes.add(new FeuilleResume(f.getDateJour(), journee, stations.get(f.getStationId()).code(), f.getType(),
                    f.getMontantDu(), f.remis(), f.getEcart(), f.getObservation()));
            if (f.getEcart().signum() < 0) {
                manquants = manquants.add(f.getEcart().negate());
            } else {
                excedents = excedents.add(f.getEcart());
            }
        }
        List<AffectationVue> passages = affectations.findByEmployeIdOrderByDebutDescIdDesc(e.getId()).stream().map(a -> {
            StationRef st = stations.get(a.getStationId());
            return new AffectationVue(st.code(), st.nom(), a.getDebut(), a.getFin());
        }).toList();
        return new EmployeDetail(vue(e, s), passages, m.atDay(1), m.atEndOfMonth(), resumes, manquants, excedents);
    }

    /** Ecarts de caisse du mois par employe (feuilles des journees cloturees). */
    public EcartsStation ecarts(UUID stationUuid, YearMonth mois) {
        StationRef s = stationAccessible(stationUuid);
        YearMonth m = mois == null ? YearMonth.from(aujourdhui()) : mois;
        Map<Long, List<FeuilleCaisse>> parEmploye = feuilles
                .findByStationIdAndDateJourBetweenAndEcartIsNotNullOrderByDateJourAscIdAsc(s.id(), m.atDay(1), m.atEndOfMonth())
                .stream().collect(Collectors.groupingBy(FeuilleCaisse::getEmployeId));
        Map<Long, Employe> parId = employes.findAllById(parEmploye.keySet()).stream()
                .collect(Collectors.toMap(Employe::getId, Function.identity()));
        List<EcartEmploye> lignes = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int nombre = 0;
        for (Map.Entry<Long, List<FeuilleCaisse>> x : parEmploye.entrySet()) {
            Employe e = parId.get(x.getKey());
            BigDecimal du = BigDecimal.ZERO;
            BigDecimal remis = BigDecimal.ZERO;
            BigDecimal manquants = BigDecimal.ZERO;
            BigDecimal excedents = BigDecimal.ZERO;
            for (FeuilleCaisse f : x.getValue()) {
                du = du.add(f.getMontantDu());
                remis = remis.add(f.remis());
                if (f.getEcart().signum() < 0) {
                    manquants = manquants.add(f.getEcart().negate());
                } else {
                    excedents = excedents.add(f.getEcart());
                }
            }
            lignes.add(new EcartEmploye(e.getUuid(), e.nomComplet(), e.getFonction().name(), e.isActif(), x.getValue().size(), du,
                    remis, manquants, excedents, excedents.subtract(manquants)));
            nombre += x.getValue().size();
            t[0] = t[0].add(du);
            t[1] = t[1].add(remis);
            t[2] = t[2].add(manquants);
            t[3] = t[3].add(excedents);
        }
        // Les plus gros manquants d'abord
        lignes.sort(Comparator.comparing(EcartEmploye::net).thenComparing(EcartEmploye::employe));
        return new EcartsStation(s.uuid(), m.toString(), lignes, nombre, t[0], t[1], t[2], t[3], t[3].subtract(t[2]));
    }

    /** Active ou desactive les feuilles de caisse par pompiste d'une station. */
    @Transactional
    public PersonnelStation reglerFeuilles(UUID stationUuid, boolean actif) {
        StationRef s = stationAccessible(stationUuid);
        ParametreStation p = parametres.findByStationId(s.id()).orElseGet(() -> new ParametreStation(s.id()));
        p.activerFeuilles(actif);
        parametres.saveAndFlush(p);
        return station(stationUuid);
    }

    // ---------------------------------------------------------------- Planning

    public PlanningSemaine planning(UUID stationUuid, LocalDate du) {
        StationRef s = stationAccessible(stationUuid);
        LocalDate debut = du == null ? aujourdhui() : du;
        LocalDate fin = debut.plusDays(6);
        List<Employe> tous = employes.findByStationIdOrderByNomAscPrenomAsc(s.id());
        Map<Long, Employe> parId = tous.stream().collect(Collectors.toMap(Employe::getId, Function.identity()));
        List<CreneauVue> vues = creneaux.findByStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(s.id(), debut, fin).stream()
                .filter(c -> parId.containsKey(c.getEmployeId()))
                .map(c -> new CreneauVue(c.getDateJour(), c.getPoste(), parId.get(c.getEmployeId()).getUuid())).toList();
        return new PlanningSemaine(s.uuid(), debut, fin, FeuilleService.aPermission("PERSONNEL_GERER"),
                tous.stream().filter(Employe::isActif).map(e -> vue(e, s)).toList(), vues);
    }

    /** Remplace le planning des 7 jours qui commencent a la date donnee. */
    @Transactional
    public PlanningSemaine planifier(UUID stationUuid, LocalDate du, List<CreneauVue> saisis) {
        StationRef s = stationAccessible(stationUuid);
        if (du == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez le premier jour de la semaine à planifier.");
        }
        LocalDate fin = du.plusDays(6);
        Map<UUID, Employe> parUuid = employes.findByStationIdOrderByNomAscPrenomAsc(s.id()).stream()
                .collect(Collectors.toMap(Employe::getUuid, Function.identity()));
        Set<List<Object>> vus = new HashSet<>();
        List<Creneau> nouveaux = new ArrayList<>();
        for (CreneauVue c : saisis == null ? List.<CreneauVue>of() : saisis) {
            if (c.date() == null || c.poste() == null || c.employeId() == null) {
                throw ErreurMetier.regle("CRENEAU_INCOMPLET", "Chaque créneau précise le jour, le poste et l'employé.");
            }
            if (c.date().isBefore(du) || c.date().isAfter(fin)) {
                throw ErreurMetier.regle("CRENEAU_HORS_SEMAINE", "Un créneau est en dehors de la semaine planifiée.");
            }
            Employe e = parUuid.get(c.employeId());
            if (e == null || !e.isActif()) {
                throw ErreurMetier.regle("EMPLOYE_HORS_STATION", "Un employé du planning n'est pas un employé actif de cette station.");
            }
            if (vus.add(List.of(c.date(), c.poste(), e.getId()))) {
                nouveaux.add(new Creneau(s.id(), c.date(), c.poste(), e.getId()));
            }
        }
        creneaux.supprimer(s.id(), du, fin);
        creneaux.saveAllAndFlush(nouveaux);
        return planning(stationUuid, du);
    }

    /**
     * Reconduit la semaine qui commence a la date donnee sur les semaines suivantes. Avec rotation, chaque equipe
     * passe au poste suivant a chaque semaine (matin, puis soir, puis nuit, parmi les postes tenus).
     */
    @Transactional
    public PlanningSemaine reconduire(UUID stationUuid, LocalDate du, int semaines, boolean rotation) {
        StationRef s = stationAccessible(stationUuid);
        if (du == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez le premier jour de la semaine à reconduire.");
        }
        if (semaines < 1 || semaines > SEMAINES_MAX) {
            throw ErreurMetier.regle("SEMAINES_INVALIDES", "Le planning se reconduit sur 1 à " + SEMAINES_MAX + " semaines.");
        }
        List<Creneau> source = creneaux.findByStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(s.id(), du, du.plusDays(6));
        if (source.isEmpty()) {
            throw ErreurMetier.regle("PLANNING_VIDE", "La semaine à reconduire n'a aucun créneau.");
        }
        Set<Long> actifs = employes.findByStationIdOrderByNomAscPrenomAsc(s.id()).stream().filter(Employe::isActif)
                .map(Employe::getId).collect(Collectors.toSet());
        List<TypePoste> tenus = source.stream().map(Creneau::getPoste).distinct().sorted().toList();
        record Modele(LocalDate date, TypePoste poste, long employeId) {
        }
        List<Modele> modeles = source.stream().filter(c -> actifs.contains(c.getEmployeId()))
                .map(c -> new Modele(c.getDateJour(), c.getPoste(), c.getEmployeId())).toList();
        creneaux.supprimer(s.id(), du.plusDays(7), du.plusDays(7L * semaines + 6));
        List<Creneau> copies = new ArrayList<>();
        for (int k = 1; k <= semaines; k++) {
            for (Modele m : modeles) {
                TypePoste poste = rotation ? tenus.get((tenus.indexOf(m.poste()) + k) % tenus.size()) : m.poste();
                copies.add(new Creneau(s.id(), m.date().plusDays(7L * k), poste, m.employeId()));
            }
        }
        creneaux.saveAllAndFlush(copies);
        return planning(stationUuid, du.plusDays(7));
    }

    // ---------------------------------------------------------------- Outils

    static EmployeVue vue(Employe e, StationRef s) {
        return new EmployeVue(e.getUuid(), s.uuid(), s.code(), e.getMatricule(), e.getNom(), e.getPrenom(), e.nomComplet(),
                e.getTelephone(), e.getFonction().name(), e.getDateEntree(), e.getDateSortie(), e.getIdentifiant(), e.isActif(),
                e.getVersion());
    }

    static boolean peutReglerFeuilles() {
        return FeuilleService.aPermission("REFERENTIEL_GERER") || FeuilleService.aPermission("JOURNEE_VALIDER");
    }

    private void exigerUnique(Employe e) {
        if (e.getMatricule() != null) {
            employes.findByMatriculeIgnoreCase(e.getMatricule()).filter(x -> !x.getId().equals(e.getId())).ifPresent(x -> {
                throw ErreurMetier.conflit("MATRICULE_EXISTANT", "Le matricule " + x.getMatricule() + " est déjà celui de " + x.nomComplet() + ".");
            });
        }
        if (e.getIdentifiant() != null) {
            employes.findByIdentifiantIgnoreCase(e.getIdentifiant()).filter(x -> !x.getId().equals(e.getId())).ifPresent(x -> {
                throw ErreurMetier.conflit("COMPTE_DEJA_LIE", "Le compte " + x.getIdentifiant() + " est déjà lié à " + x.nomComplet() + ".");
            });
        }
    }

    private Employe employe(UUID id) {
        Employe e = employes.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Employé"));
        perimetre.exigerStation(e.getStationId());
        return e;
    }

    private StationRef stationAccessible(UUID uuid) {
        StationRef s = referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }
}
