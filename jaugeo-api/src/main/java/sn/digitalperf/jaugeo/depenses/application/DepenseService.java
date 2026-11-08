package sn.digitalperf.jaugeo.depenses.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.PieceJointe;
import sn.digitalperf.jaugeo.depenses.DepensesJournee;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.DepenseAValider;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.DepenseVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.DepensesJourneeVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.JustificatifVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.NatureVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.SaisieDepense;
import sn.digitalperf.jaugeo.depenses.domaine.Depense;
import sn.digitalperf.jaugeo.depenses.domaine.DepenseDepot;
import sn.digitalperf.jaugeo.depenses.domaine.Justificatif;
import sn.digitalperf.jaugeo.depenses.domaine.JustificatifDepot;
import sn.digitalperf.jaugeo.depenses.domaine.NatureDepense;
import sn.digitalperf.jaugeo.depenses.domaine.NatureDepenseDepot;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;

/** Depenses de station (M7) : natures et plafonds, depenses d'une journee, justificatifs, validation. */
@Service
@Transactional(readOnly = true)
public class DepenseService implements DepensesJournee {

    private final NatureDepenseDepot natures;
    private final DepenseDepot depenses;
    private final JustificatifDepot justificatifs;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final Clock horloge;
    private final boolean justificatifObligatoire;

    public DepenseService(NatureDepenseDepot natures, DepenseDepot depenses, JustificatifDepot justificatifs,
                          JourneesExploitation exploitation, ReferentielConsultation referentiel, PerimetreCourant perimetre,
                          Clock horloge,
                          @Value("${jaugeo.depenses.justificatif-obligatoire:true}") boolean justificatifObligatoire) {
        this.natures = natures;
        this.depenses = depenses;
        this.justificatifs = justificatifs;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
        this.justificatifObligatoire = justificatifObligatoire;
    }

    // ---------------------------------------------------------------- Natures de depense

    public List<NatureVue> natures() {
        return natures.findAllByOrderByLibelleAsc().stream().map(DepenseService::vue).toList();
    }

    @Transactional
    public NatureVue creerNature(String code, String libelle, BigDecimal plafond) {
        if (code != null && natures.existsByCodeIgnoreCase(code.trim())) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Une nature de dépense porte déjà le code " + code.trim().toUpperCase() + ".");
        }
        NatureDepense n = new NatureDepense(code);
        n.modifier(libelle, plafond, true);
        return vue(natures.saveAndFlush(n));
    }

    @Transactional
    public NatureVue modifierNature(UUID id, Integer version, String libelle, BigDecimal plafond, boolean actif) {
        NatureDepense n = natures.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Nature de dépense"));
        n.verifierVersion(version);
        n.modifier(libelle, plafond, actif);
        return vue(natures.saveAndFlush(n));
    }

    private static NatureVue vue(NatureDepense n) {
        return new NatureVue(n.getUuid(), n.getCode(), n.getLibelle(), n.getPlafond(), n.isActif(), n.getVersion());
    }

    // ---------------------------------------------------------------- Depenses d'une journee

    public DepensesJourneeVue journee(UUID journeeUuid) {
        return vue(journeeAccessible(journeeUuid));
    }

    @Transactional
    public DepensesJourneeVue ajouter(UUID journeeUuid, Integer version, SaisieDepense s) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Depense d = new Depense(j.id(), j.stationId(), j.date());
        d.renseigner(nature(s.natureId()), s.libelle(), s.beneficiaire(), s.montant());
        depenses.saveAndFlush(d);
        return vue(j);
    }

    @Transactional
    public DepensesJourneeVue modifier(UUID journeeUuid, UUID depenseUuid, Integer version, SaisieDepense s) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Depense d = depense(j, depenseUuid);
        d.renseigner(nature(s.natureId()), s.libelle(), s.beneficiaire(), s.montant());
        depenses.saveAndFlush(d);
        return vue(j);
    }

    @Transactional
    public DepensesJourneeVue supprimer(UUID journeeUuid, UUID depenseUuid, Integer version) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Depense d = depense(j, depenseUuid);
        justificatifs.supprimerPourDepense(d.getId());
        depenses.delete(d);
        depenses.flush();
        return vue(j);
    }

    @Transactional
    public DepensesJourneeVue joindre(UUID journeeUuid, UUID depenseUuid, Integer version, PieceJointe piece) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Depense d = depense(j, depenseUuid);
        justificatifs.supprimerPourDepense(d.getId());
        justificatifs.saveAndFlush(new Justificatif(d.getId(), piece, perimetre.identifiant(), horloge.instant()));
        return vue(j);
    }

    public ResponseEntity<byte[]> justificatif(UUID depenseUuid) {
        Depense d = depenses.findByUuid(depenseUuid).orElseThrow(() -> ErreurMetier.introuvable("Dépense"));
        perimetre.exigerStation(d.getStationId());
        Justificatif p = justificatifs.findByDepenseId(d.getId()).orElseThrow(() -> ErreurMetier.introuvable("Justificatif"));
        return PieceJointe.reponse(p.getNomFichier(), p.getTypeMime(), p.getContenu());
    }

    /** Decision du superviseur sur une depense hors plafond, tant que la journee est en saisie. */
    @Transactional
    public DepensesJourneeVue decider(UUID journeeUuid, UUID depenseUuid, Integer version, boolean autoriser, String motif) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Depense d = depense(j, depenseUuid);
        d.decider(autoriser, motif, perimetre.identifiant(), horloge.instant());
        depenses.saveAndFlush(d);
        return vue(j);
    }

    /** Depenses en attente de decision dans le perimetre, de la plus ancienne a la plus recente. */
    public List<DepenseAValider> aValider() {
        Map<Long, StationRef> stations = stationsVisibles();
        if (stations.isEmpty()) {
            return List.of();
        }
        List<Depense> liste = depenses.findByStationIdInAndStatutOrderByDateJourAscIdAsc(stations.keySet(), Depense.Statut.A_VALIDER);
        Map<Long, NatureDepense> parNature = naturesParId();
        Set<Long> avecPiece = descriptions(liste.stream().map(Depense::getId).toList()).keySet();
        Map<Long, JourneeRef> journees = new HashMap<>();
        boolean peutDecider = aPermission("DEPENSE_VALIDER");
        return liste.stream().map(d -> {
            JourneeRef j = journees.computeIfAbsent(d.getJourneeId(), id -> exploitation.journee(id).orElseThrow());
            StationRef s = stations.get(d.getStationId());
            return new DepenseAValider(d.getUuid(), j.uuid(), j.version(), s.uuid(), s.code(), d.getDateJour(),
                    parNature.get(d.getNatureId()).getLibelle(), d.getLibelle(), d.getBeneficiaire(), d.getMontant(),
                    d.getPlafondApplique(), d.getCreePar(), avecPiece.contains(d.getId()),
                    peutDecider && j.brouillon() && !perimetre.identifiant().equals(d.getCreePar()));
        }).toList();
    }

    // ---------------------------------------------------------------- Interface publique et cloture

    @Override
    public BigDecimal totalAutorise(long journeeId) {
        return depenses.totalAutorise(journeeId);
    }

    /** Points bloquants de la cloture : depense en attente de decision, justificatif manquant. */
    List<String> anomalies(long journeeId) {
        List<Depense> liste = depenses.findByJourneeIdOrderByIdAsc(journeeId);
        Set<Long> avecPiece = descriptions(liste.stream().map(Depense::getId).toList()).keySet();
        List<String> r = new ArrayList<>();
        for (Depense d : liste) {
            if (d.getStatut() == Depense.Statut.A_VALIDER) {
                r.add(d.resume() + " : au-delà du plafond de " + sn.digitalperf.jaugeo.commun.Libelles.francs(d.getPlafondApplique())
                        + ", elle attend la validation du superviseur.");
            }
            if (justificatifObligatoire && d.getStatut() != Depense.Statut.REFUSEE && !avecPiece.contains(d.getId())) {
                r.add(d.resume() + " : joignez le justificatif (photo de la facture ou du reçu).");
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- Vue

    private DepensesJourneeVue vue(JourneeRef j) {
        StationRef s = referentiel.station(j.stationId()).orElseThrow();
        List<Depense> liste = depenses.findByJourneeIdOrderByIdAsc(j.id());
        Map<Long, NatureDepense> parNature = naturesParId();
        Map<Long, Justificatif.Description> pieces = descriptions(liste.stream().map(Depense::getId).toList());
        boolean peutDecider = aPermission("DEPENSE_VALIDER") && j.brouillon();
        List<DepenseVue> vues = liste.stream().map(d -> {
            NatureDepense n = parNature.get(d.getNatureId());
            Justificatif.Description p = pieces.get(d.getId());
            return new DepenseVue(d.getUuid(), n.getUuid(), n.getCode(), n.getLibelle(), d.getLibelle(), d.getBeneficiaire(),
                    d.getMontant(), d.getPlafondApplique(), d.getStatut().name(),
                    p == null ? null : new JustificatifVue(p.getNomFichier(), p.getTypeMime(), p.getTaille()),
                    d.getDecisionPar(), d.getDecisionLe(), d.getMotifDecision(), d.getCreePar(), d.getModifieLe(),
                    peutDecider && d.getStatut() == Depense.Statut.A_VALIDER && !perimetre.identifiant().equals(d.getCreePar()));
        }).toList();
        return new DepensesJourneeVue(j.uuid(), s.uuid(), s.code(), j.date(), j.statut(), j.version(),
                j.brouillon() && aPermission("JOURNEE_SAISIR"), justificatifObligatoire, vues,
                total(liste, Depense.Statut.AUTORISEE), total(liste, Depense.Statut.A_VALIDER),
                total(liste, Depense.Statut.REFUSEE), j.brouillon() ? anomalies(j.id()) : List.of());
    }

    private static BigDecimal total(List<Depense> liste, Depense.Statut statut) {
        return liste.stream().filter(d -> d.getStatut() == statut).map(Depense::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- Outils

    private Map<Long, Justificatif.Description> descriptions(Collection<Long> depenseIds) {
        if (depenseIds.isEmpty()) {
            return Map.of();
        }
        return justificatifs.descriptions(depenseIds).stream()
                .collect(Collectors.toMap(Justificatif.Description::getDepenseId, Function.identity()));
    }

    private Map<Long, NatureDepense> naturesParId() {
        return natures.findAll().stream().collect(Collectors.toMap(NatureDepense::getId, Function.identity()));
    }

    private NatureDepense nature(UUID id) {
        if (id == null) {
            throw ErreurMetier.regle("NATURE_OBLIGATOIRE", "Choisissez la nature de la dépense.");
        }
        return natures.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Nature de dépense"));
    }

    private Depense depense(JourneeRef j, UUID depenseUuid) {
        Depense d = depenses.findByUuid(depenseUuid).orElseThrow(() -> ErreurMetier.introuvable("Dépense"));
        if (!d.getJourneeId().equals(j.id())) {
            throw ErreurMetier.introuvable("Dépense");
        }
        return d;
    }

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

    private Map<Long, StationRef> stationsVisibles() {
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        return referentiel.stations().stream()
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .collect(Collectors.toMap(StationRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
