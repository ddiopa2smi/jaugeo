package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.EtatMois;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.EtatStation;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.RegularisationVue;
import sn.digitalperf.jaugeo.reporting.domaine.ClotureMois;
import sn.digitalperf.jaugeo.reporting.domaine.ClotureMoisDepot;
import sn.digitalperf.jaugeo.reporting.domaine.CompteFigeDepot;
import sn.digitalperf.jaugeo.reporting.domaine.Regularisation;
import sn.digitalperf.jaugeo.reporting.domaine.RegularisationDepot;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;

/**
 * Cloture mensuelle, station par station. Un mois se cloture quand il est termine, que toutes ses journees sont
 * validees, que ses versements sont verifies et que le mois precedent est clos. La cloture fige le compte
 * d'exploitation et verrouille le mois (voir {@link VerrouMois}) ; elle ne se defait pas. Une erreur decouverte
 * ensuite se corrige par une regularisation, comptee dans le resultat du mois en cours.
 */
@Service
@Transactional(readOnly = true)
public class ClotureMensuelleService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final ClotureMoisDepot clotures;
    private final CompteFigeDepot figes;
    private final RegularisationDepot regularisations;
    private final CompteExploitationService comptes;
    private final JourneesExploitation exploitation;
    private final SyntheseCaisse caisse;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final Clock horloge;
    private final BudgetService budgets;
    private final ExportCloture exports;

    public ClotureMensuelleService(ClotureMoisDepot clotures, CompteFigeDepot figes, RegularisationDepot regularisations,
                                   CompteExploitationService comptes, JourneesExploitation exploitation,
                                   SyntheseCaisse caisse, ReferentielConsultation referentiel,
                                   PerimetreCourant perimetre, Clock horloge, BudgetService budgets,
                                   ExportCloture exports) {
        this.budgets = budgets;
        this.exports = exports;
        this.clotures = clotures;
        this.figes = figes;
        this.regularisations = regularisations;
        this.comptes = comptes;
        this.exploitation = exploitation;
        this.caisse = caisse;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Etat

    /** Etat de la cloture du mois pour chaque station du perimetre. */
    public EtatMois etat(YearMonth mois) {
        Map<Long, ClotureMois> closes = clotures.findByMois(mois.atDay(1)).stream()
                .collect(Collectors.toMap(ClotureMois::getStationId, Function.identity()));
        List<EtatStation> etats = comptes.stations(mois.atEndOfMonth()).stream()
                .map(s -> etat(s, mois, closes.get(s.id()))).toList();
        return new EtatMois(mois, etats, (int) etats.stream().filter(e -> "CLOS".equals(e.statut())).count(),
                (int) etats.stream().filter(e -> "PRET".equals(e.statut())).count());
    }

    private EtatStation etat(StationRef s, YearMonth mois, ClotureMois cloture) {
        List<JourneeRef> journees = exploitation.journees(s.id(), mois.atDay(1), mois.atEndOfMonth());
        int validees = (int) journees.stream().filter(j -> "VALIDEE".equals(j.statut())).count();
        if (cloture != null) {
            return new EtatStation(s.uuid(), s.code(), s.nom(), "CLOS", List.of(), journees.size(), validees,
                    cloture.getCloturePar(), cloture.getClotureLe());
        }
        List<String> blocages = blocages(s, mois, journees);
        return new EtatStation(s.uuid(), s.code(), s.nom(), blocages.isEmpty() ? "PRET" : "BLOQUE", blocages,
                journees.size(), validees, null, null);
    }

    /** Points qui s'opposent a la cloture du mois, en phrases lisibles ; liste vide si rien ne s'y oppose. */
    private List<String> blocages(StationRef s, YearMonth mois, List<JourneeRef> journees) {
        List<String> r = new ArrayList<>();
        LocalDate fin = mois.atEndOfMonth();
        if (!aujourdhui().isAfter(fin)) {
            r.add("Le mois n'est pas terminé.");
            return r;
        }
        if (journees.isEmpty()) {
            r.add("Aucune journée dans ce mois.");
            return r;
        }
        List<JourneeRef> aTraiter = journees.stream().filter(j -> !"VALIDEE".equals(j.statut())).toList();
        if (!aTraiter.isEmpty()) {
            r.add(aTraiter.size() + (aTraiter.size() > 1 ? " journées à clôturer ou à valider" : " journée à clôturer ou à valider")
                    + " (à partir du " + Libelles.date(aTraiter.get(0).date()) + ").");
        }
        LocalDate derniere = journees.get(journees.size() - 1).date();
        if (derniere.isBefore(fin)) {
            r.add("Journées manquantes du " + Libelles.date(derniere.plusDays(1)) + " au " + Libelles.date(fin)
                    + " : un jour sans activité se clôture à zéro.");
        }
        long versements = caisse.versementsAVerifier(s.id(), mois);
        if (versements > 0) {
            r.add(versements + (versements > 1 ? " versements du mois à vérifier" : " versement du mois à vérifier")
                    + " par la comptabilité.");
        }
        YearMonth precedent = mois.minusMonths(1);
        if (!exploitation.journees(s.id(), precedent.atDay(1), precedent.atEndOfMonth()).isEmpty()
                && !clotures.existsByStationIdAndMois(s.id(), precedent.atDay(1))) {
            r.add("Clôturez d'abord " + VerrouMois.libelle(precedent) + ".");
        }
        return r;
    }

    // ---------------------------------------------------------------- Cloture

    /** Cloture le mois d'une station : controle, fige le compte d'exploitation, verrouille le mois. */
    @Transactional
    public EtatStation cloturer(UUID stationUuid, YearMonth mois) {
        StationRef s = station(stationUuid);
        if (clotures.existsByStationIdAndMois(s.id(), mois.atDay(1))) {
            throw ErreurMetier.conflit("MOIS_DEJA_CLOS", "Le mois de " + VerrouMois.libelle(mois) + " est déjà clôturé pour "
                    + s.nom() + ".");
        }
        List<JourneeRef> journees = exploitation.journees(s.id(), mois.atDay(1), mois.atEndOfMonth());
        List<String> blocages = blocages(s, mois, journees);
        if (!blocages.isEmpty()) {
            throw ErreurMetier.regle("CLOTURE_MOIS_IMPOSSIBLE", String.join(" ", blocages));
        }
        ClotureMois c = figer(s, mois);
        return new EtatStation(s.uuid(), s.code(), s.nom(), "CLOS", List.of(), journees.size(), journees.size(),
                c.getCloturePar(), c.getClotureLe());
    }

    /** Cloture d'un coup toutes les stations du perimetre pretes pour ce mois. */
    @Transactional
    public EtatMois cloturerPretes(YearMonth mois) {
        List<StationRef> stations = comptes.stations(mois.atEndOfMonth());
        int closes = 0;
        for (StationRef s : stations) {
            if (clotures.existsByStationIdAndMois(s.id(), mois.atDay(1))) {
                continue;
            }
            if (blocages(s, mois, exploitation.journees(s.id(), mois.atDay(1), mois.atEndOfMonth())).isEmpty()) {
                figer(s, mois);
                closes++;
            }
        }
        if (closes == 0) {
            throw ErreurMetier.regle("AUCUNE_STATION_PRETE", "Aucune station n'est prête à être clôturée pour "
                    + VerrouMois.libelle(mois) + ".");
        }
        return etat(mois);
    }

    private ClotureMois figer(StationRef s, YearMonth mois) {
        ClotureMois c = clotures.saveAndFlush(new ClotureMois(s.id(), mois, perimetre.identifiant(), horloge.instant()));
        figes.saveAll(comptes.aFiger(c.getId(), s, mois));
        figes.flush();
        return c;
    }

    // ---------------------------------------------------------------- Regularisations

    /** Regularisations des stations du perimetre comptees dans l'annee du mois donne, les plus recentes d'abord. */
    public List<RegularisationVue> regularisations(int annee) {
        Map<Long, StationRef> stations = referentiel.stations().stream()
                .filter(s -> perimetre.peutVoirStation(s.id()))
                .collect(Collectors.toMap(StationRef::id, Function.identity()));
        if (stations.isEmpty()) {
            return List.of();
        }
        return regularisations.findByStationIdInAndMoisComptableBetweenOrderByIdDesc(stations.keySet(),
                        LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 1)).stream()
                .map(r -> vue(r, stations.get(r.getStationId()))).toList();
    }

    /**
     * Corrige un mois clos : la regularisation vise ce mois et s'ajoute au resultat du mois en cours.
     * Un mois encore ouvert se corrige directement (renvoi de la journee, versement...).
     */
    @Transactional
    public RegularisationVue regulariser(UUID stationUuid, YearMonth concerne, String poste, BigDecimal montant,
                                         String motif, String reference) {
        StationRef s = station(stationUuid);
        if (concerne == null || !clotures.existsByStationIdAndMois(s.id(), concerne.atDay(1))) {
            throw ErreurMetier.regle("MOIS_OUVERT", "Une régularisation corrige un mois clôturé. Ce mois est encore ouvert "
                    + "pour " + s.nom() + " : corrigez directement la journée ou l'écriture concernée.");
        }
        Regularisation r = regularisations.saveAndFlush(new Regularisation(s.id(), concerne, YearMonth.from(aujourdhui()),
                poste(poste), montant, motif, reference));
        return vue(r, s);
    }

    private static Regularisation.Poste poste(String poste) {
        try {
            return Regularisation.Poste.valueOf(poste == null ? "" : poste.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("POSTE_INVALIDE", "Poste inconnu : chiffre d'affaires, coût d'achat, stock, dépenses ou autre.");
        }
    }

    private static RegularisationVue vue(Regularisation r, StationRef s) {
        return new RegularisationVue(r.getUuid(), s.uuid(), s.code(), s.nom(), YearMonth.from(r.getMoisConcerne()),
                YearMonth.from(r.getMoisComptable()), r.getPoste().name(), r.getMontant(), r.getMotif(), r.getReference(),
                r.getCreePar(), r.getCreeLe());
    }

    // ---------------------------------------------------------------- Exports

    /** Classeur Excel : compte d'exploitation du mois par station, et reel contre budget de l'annee pour le reseau. */
    public ResponseEntity<byte[]> excel(YearMonth mois) {
        return fichier(exports.excel(comptes.compte(mois), budgets.suivi(null, mois.getYear())),
                "compte-exploitation-" + mois + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    public ResponseEntity<byte[]> pdf(YearMonth mois) {
        return fichier(exports.pdf(comptes.compte(mois)), "compte-exploitation-" + mois + ".pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .contentType(MediaType.parseMediaType(type))
                .body(contenu);
    }

    // ---------------------------------------------------------------- Outils

    private StationRef station(UUID uuid) {
        Optional<StationRef> s = referentiel.station(uuid);
        StationRef station = s.orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(station.id());
        return station;
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }
}
