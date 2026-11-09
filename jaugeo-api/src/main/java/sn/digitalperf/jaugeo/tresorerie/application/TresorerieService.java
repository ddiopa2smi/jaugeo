package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.commun.PieceJointe;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.ArreteVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.LigneCaisse;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.LivreCaisse;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.ManquantVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.PieceVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.RecetteVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.Situation;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.SituationStation;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.VersementVue;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisse;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisseDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.CalculCaisse;
import sn.digitalperf.jaugeo.tresorerie.domaine.ManquantGerant;
import sn.digitalperf.jaugeo.tresorerie.domaine.ManquantGerantDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.EcheanceManquant;
import sn.digitalperf.jaugeo.tresorerie.domaine.EcheanceManquantDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.PaiementManquant;
import sn.digitalperf.jaugeo.tresorerie.domaine.PaiementManquantDepot;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.EcheanceVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.PaiementVue;
import sn.digitalperf.jaugeo.tresorerie.domaine.OperationReleve;
import sn.digitalperf.jaugeo.tresorerie.domaine.OperationReleveDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.PieceVersement;
import sn.digitalperf.jaugeo.tresorerie.domaine.PlafondEspeces;
import sn.digitalperf.jaugeo.tresorerie.domaine.PlafondEspecesDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.PieceVersementDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJourneeDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.Versement;
import sn.digitalperf.jaugeo.tresorerie.domaine.VersementDepot;

/**
 * Tresorerie (M5) : montant a verser de chaque journee, versements bancaires (BV), solde de caisse du gerant
 * reporte jour apres jour, arretes de caisse et manquants.
 */
@Service
@Transactional(readOnly = true)
public class TresorerieService implements SyntheseCaisse {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final RecetteJourneeDepot recettes;
    private final VersementDepot versements;
    private final PieceVersementDepot pieces;
    private final ArreteCaisseDepot arretes;
    private final ManquantGerantDepot manquants;
    private final PlafondEspecesDepot plafonds;
    private final OperationReleveDepot operationsReleve;
    private final EcheanceManquantDepot echeances;
    private final PaiementManquantDepot paiements;
    private final CaisseGerant caisse;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final Clock horloge;
    private final int delaiVersementJours;
    /** Plafond d'especes des stations sans plafond propre (null : aucun). */
    private final BigDecimal plafondReseau;

    public TresorerieService(RecetteJourneeDepot recettes, VersementDepot versements, PieceVersementDepot pieces,
                             ArreteCaisseDepot arretes, ManquantGerantDepot manquants, PlafondEspecesDepot plafonds,
                             OperationReleveDepot operationsReleve, EcheanceManquantDepot echeances,
                             PaiementManquantDepot paiements,
                             CaisseGerant caisse,
                             JourneesExploitation exploitation, ReferentielConsultation referentiel,
                             PerimetreCourant perimetre, Clock horloge,
                             @Value("${jaugeo.tresorerie.delai-versement-jours:3}") int delaiVersementJours,
                             @Value("${jaugeo.tresorerie.plafond-especes:#{null}}") BigDecimal plafondReseau) {
        this.plafondReseau = plafondReseau;
        this.recettes = recettes;
        this.versements = versements;
        this.pieces = pieces;
        this.arretes = arretes;
        this.manquants = manquants;
        this.plafonds = plafonds;
        this.operationsReleve = operationsReleve;
        this.echeances = echeances;
        this.paiements = paiements;
        this.caisse = caisse;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
        this.delaiVersementJours = delaiVersementJours;
    }

    // ---------------------------------------------------------------- Recette d'une journee

    public RecetteVue recette(UUID journeeUuid) {
        JourneeRef j = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        StationRef s = stationAccessible(j.stationId());
        Optional<RecetteJournee.Composantes> c = caisse.composantes(j);
        boolean fige = !j.brouillon();
        BigDecimal ouverture = caisse.soldeFin(s.id(), j.date().minusDays(1));
        List<Versement> duJour = versements.findByStationIdAndDateDepotBetweenOrderByDateDepotAscIdAsc(s.id(), j.date(), j.date());
        List<VersementVue> vues = vuesVersements(duJour, Map.of(s.id(), s));
        BigDecimal verse = totalNonRejete(duJour);
        BigDecimal montant = c.map(RecetteJournee.Composantes::montantAVerser).orElse(null);
        Optional<ArreteCaisse> arrete = arretes.findFirstByStationIdAndDateJourLessThanEqualOrderByDateJourDesc(s.id(), j.date())
                .filter(a -> a.getDateJour().equals(j.date()));
        BigDecimal soldeFin = arrete.map(ArreteCaisse::getEspecesComptees)
                .orElse(montant == null ? null : ouverture.add(montant).subtract(verse));
        return new RecetteVue(j.uuid(), s.uuid(), s.code(), j.date(), j.statut(), fige,
                c.map(RecetteJournee.Composantes::ventes).orElse(null),
                c.map(RecetteJournee.Composantes::ventesArticles).orElse(BigDecimal.ZERO),
                c.map(RecetteJournee.Composantes::bonsDirection).orElse(BigDecimal.ZERO),
                c.map(RecetteJournee.Composantes::bonsGerant).orElse(BigDecimal.ZERO),
                c.map(RecetteJournee.Composantes::cartes).orElse(BigDecimal.ZERO),
                c.map(RecetteJournee.Composantes::depenses).orElse(null),
                c.map(RecetteJournee.Composantes::complements).orElse(null),
                c.map(RecetteJournee.Composantes::encaissements).orElse(BigDecimal.ZERO),
                c.map(RecetteJournee.Composantes::recouvrements).orElse(BigDecimal.ZERO),
                montant, ouverture, vues, verse, soldeFin, arrete.map(a -> vue(a, s)).orElse(null),
                aPermission("VERSEMENT_SAISIR"), plafond(s.id()), CalculCaisse.depassePlafond(soldeFin, plafond(s.id())));
    }

    // ---------------------------------------------------------------- Livre de caisse (ecart de versement jour par jour)

    public LivreCaisse livre(UUID stationUuid, YearMonth mois) {
        return livre(stationAccessible(stationUuid), mois);
    }

    @Override
    public MoisCaisse mois(long stationId, YearMonth mois) {
        LivreCaisse l = livre(referentiel.station(stationId).orElseThrow(() -> ErreurMetier.introuvable("Station")), mois);
        return new MoisCaisse(l.soldeDebut(), l.totalVentes(), l.totalBonsDirection(), l.totalBonsGerant(), l.totalCartes(), l.totalDepenses(),
                l.totalComplements(), l.totalEncaissements(), l.totalRecouvrements(), l.totalMontants(), l.totalVersements(), l.totalEcarts(),
                l.soldeFin(), l.joursNonClotures());
    }

    private LivreCaisse livre(StationRef s, YearMonth mois) {
        LocalDate du = mois.atDay(1);
        LocalDate au = mois.atEndOfMonth();
        BigDecimal soldeDebut = caisse.soldeFin(s.id(), du.minusDays(1));

        Map<LocalDate, JourneeRef> journees = exploitation.journees(s.id(), du, au).stream()
                .collect(Collectors.toMap(JourneeRef::date, Function.identity()));
        Map<LocalDate, RecetteJournee> parDate = recettes.findByStationIdAndDateJourBetweenOrderByDateJourAsc(s.id(), du, au)
                .stream().collect(Collectors.toMap(RecetteJournee::getDateJour, Function.identity()));
        Map<LocalDate, BigDecimal> verses = versements.findByStationIdAndDateDepotBetweenOrderByDateDepotAscIdAsc(s.id(), du, au)
                .stream().filter(v -> v.getStatut() != Versement.Statut.REJETE)
                .collect(Collectors.groupingBy(Versement::getDateDepot, Collectors.reducing(BigDecimal.ZERO, Versement::getMontant, BigDecimal::add)));
        Map<LocalDate, ArreteCaisse> arretesDuMois = arretes.findByStationIdAndDateJourBetweenOrderByDateJourAsc(s.id(), du, au)
                .stream().collect(Collectors.toMap(ArreteCaisse::getDateJour, Function.identity()));

        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.addAll(journees.keySet());
        dates.addAll(parDate.keySet());
        dates.addAll(verses.keySet());
        dates.addAll(arretesDuMois.keySet());
        List<CalculCaisse.Jour> jours = dates.stream().map(d -> {
            ArreteCaisse a = arretesDuMois.get(d);
            RecetteJournee r = parDate.get(d);
            return new CalculCaisse.Jour(d, r == null ? null : r.getMontantAVerser(), verses.getOrDefault(d, BigDecimal.ZERO),
                    a == null ? null : new CalculCaisse.Arrete(a.getType() == ArreteCaisse.Type.REPRISE, a.getEspecesComptees()));
        }).toList();

        List<LigneCaisse> lignes = new ArrayList<>();
        BigDecimal ventes = BigDecimal.ZERO;
        BigDecimal bonsDirection = BigDecimal.ZERO;
        BigDecimal bonsGerant = BigDecimal.ZERO;
        BigDecimal cartes = BigDecimal.ZERO;
        BigDecimal depenses = BigDecimal.ZERO;
        BigDecimal complements = BigDecimal.ZERO;
        BigDecimal encaissements = BigDecimal.ZERO;
        BigDecimal recouvrements = BigDecimal.ZERO;
        BigDecimal ecarts = BigDecimal.ZERO;
        int nonCloturees = 0;
        for (CalculCaisse.Ligne l : CalculCaisse.derouler(soldeDebut, jours)) {
            JourneeRef j = journees.get(l.date());
            RecetteJournee r = parDate.get(l.date());
            ArreteCaisse a = arretesDuMois.get(l.date());
            RecetteJournee.Composantes c = r == null ? null : r.composantes();
            if (j != null && j.brouillon()) {
                nonCloturees++;
            }
            if (c != null) {
                // Ventes du livre de caisse : carburant, lubrifiants et boutique
                ventes = ventes.add(c.ventesTotales());
                bonsDirection = bonsDirection.add(c.bonsDirection());
                bonsGerant = bonsGerant.add(c.bonsGerant());
                cartes = cartes.add(c.cartes());
                depenses = depenses.add(c.depenses());
                complements = complements.add(c.complements());
                encaissements = encaissements.add(c.encaissements());
                recouvrements = recouvrements.add(c.recouvrements());
            }
            // Un arrete garde l'ecart constate le jour ou il a ete dresse
            BigDecimal ecart = a == null ? null : a.getEcart();
            BigDecimal theorique = a != null && a.getSoldeTheorique() != null ? a.getSoldeTheorique() : l.theorique();
            if (ecart != null) {
                ecarts = ecarts.add(ecart);
            }
            lignes.add(new LigneCaisse(l.date(), j == null ? null : j.uuid(), j == null ? null : j.statut(),
                    c == null ? null : c.ventesTotales(), c == null ? null : c.bonsDirection().add(c.bonsGerant()), c == null ? null : c.cartes(),
                    c == null ? null : c.depenses(), c == null ? null : c.complements(), c == null ? null : c.encaissements(),
                    c == null ? null : c.recouvrements(),
                    l.montantAVerser(),
                    l.ouverture(), l.versements(), theorique, a == null ? null : vue(a, s), ecart, l.solde()));
        }
        BigDecimal montants = parDate.values().stream().map(RecetteJournee::getMontantAVerser).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal verse = verses.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldeFin = lignes.isEmpty() ? soldeDebut : lignes.get(lignes.size() - 1).solde();
        return new LivreCaisse(s.uuid(), s.code(), s.nom(), mois, soldeDebut, ventes, bonsDirection, bonsGerant, cartes, depenses,
                complements, encaissements, recouvrements, montants, verse,
                ecarts, soldeFin, nonCloturees, lignes, aPermission("CAISSE_CONTROLER"), plafond(s.id()));
    }

    // ---------------------------------------------------------------- Versements

    /** Versements d'une station sur un mois, ou (sans station) versements a verifier du perimetre. */
    public List<VersementVue> versements(UUID stationUuid, YearMonth mois) {
        if (stationUuid != null) {
            StationRef s = stationAccessible(stationUuid);
            YearMonth m = mois == null ? YearMonth.from(aujourdhui()) : mois;
            return vuesVersements(versements.findByStationIdAndDateDepotBetweenOrderByDateDepotAscIdAsc(s.id(),
                    m.atDay(1), m.atEndOfMonth()), Map.of(s.id(), s));
        }
        Map<Long, StationRef> stations = stationsVisibles();
        if (stations.isEmpty()) {
            return List.of();
        }
        return vuesVersements(versements.findByStationIdInAndStatutOrderByDateDepotAscIdAsc(stations.keySet(),
                Versement.Statut.SAISI), stations);
    }

    @Transactional
    public VersementVue creerVersement(UUID stationUuid, Versement.Donnees d) {
        StationRef s = stationAccessible(stationUuid);
        Versement v = new Versement(s.id());
        renseigner(v, d);
        return vue(v, s);
    }

    @Transactional
    public VersementVue modifierVersement(UUID id, Integer version, Versement.Donnees d) {
        Versement v = versementModifiable(id, version);
        renseigner(v, d);
        return vue(v, referentiel.station(v.getStationId()).orElseThrow());
    }

    @Transactional
    public void supprimerVersement(UUID id, Integer version) {
        Versement v = versementModifiable(id, version);
        pieces.supprimerPourVersement(v.getId());
        versements.delete(v);
        versements.flush();
    }

    @Transactional
    public VersementVue joindrePiece(UUID id, Integer version, PieceJointe piece) {
        Versement v = versementModifiable(id, version);
        pieces.supprimerPourVersement(v.getId());
        pieces.saveAndFlush(new PieceVersement(v.getId(), piece, perimetre.identifiant(), horloge.instant()));
        return vue(v, referentiel.station(v.getStationId()).orElseThrow());
    }

    public ResponseEntity<byte[]> piece(UUID id) {
        Versement v = versements.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Versement"));
        perimetre.exigerStation(v.getStationId());
        PieceVersement p = pieces.findByVersementId(v.getId()).orElseThrow(() -> ErreurMetier.introuvable("Bordereau"));
        return PieceJointe.reponse(p.getNomFichier(), p.getTypeMime(), p.getContenu());
    }

    /** Verification (ou rejet) par la comptabilite, qui ne peut pas etre l'auteur de la saisie. */
    @Transactional
    public VersementVue verifier(UUID id, Integer version, boolean valide, String motif) {
        Versement v = versements.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Versement"));
        perimetre.exigerStation(v.getStationId());
        v.verifierVersion(version);
        if (valide) {
            v.verifier(perimetre.identifiant(), horloge.instant(), pieces.existe(v.getId()));
        } else {
            v.rejeter(motif, perimetre.identifiant(), horloge.instant());
        }
        versements.saveAndFlush(v);
        return vue(v, referentiel.station(v.getStationId()).orElseThrow());
    }

    private void renseigner(Versement v, Versement.Donnees d) {
        v.renseigner(d, aujourdhui());
        exigerApresDernierArrete(v.getStationId(), v.getDateDepot());
        versements.parBordereau(v.getBanque(), v.getReferenceBv()).filter(x -> !x.getId().equals(v.getId())).ifPresent(x -> {
            String ou = referentiel.station(x.getStationId()).map(StationRef::code).orElse("?");
            throw ErreurMetier.conflit("BV_DEJA_SAISI", "Le bordereau " + x.getReferenceBv() + " de la banque " + x.getBanque()
                    + " a déjà été saisi (" + ou + ", dépôt du " + Libelles.date(x.getDateDepot()) + ").");
        });
        versements.saveAndFlush(v);
    }

    private Versement versementModifiable(UUID id, Integer version) {
        Versement v = versements.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Versement"));
        perimetre.exigerStation(v.getStationId());
        v.verifierVersion(version);
        v.exigerSaisi();
        exigerApresDernierArrete(v.getStationId(), v.getDateDepot());
        return v;
    }

    /** Un arrete de caisse fige le passe : aucun versement ne peut etre ajoute, modifie ou retire a sa date ou avant. */
    private void exigerApresDernierArrete(long stationId, LocalDate date) {
        arretes.findFirstByStationIdOrderByDateJourDesc(stationId).filter(a -> !date.isAfter(a.getDateJour())).ifPresent(a -> {
            throw ErreurMetier.regle("CAISSE_ARRETEE", "La caisse a été arrêtée le " + Libelles.date(a.getDateJour())
                    + " : un versement déposé à cette date ou avant ne peut plus être saisi ni modifié.");
        });
    }

    private List<VersementVue> vuesVersements(List<Versement> liste, Map<Long, StationRef> stations) {
        List<Long> ids = liste.stream().map(Versement::getId).toList();
        Map<Long, PieceVersement.Description> descriptions = descriptions(ids);
        Map<Long, LocalDate> releves = datesReleve(ids);
        return liste.stream().map(v -> vue(v, stations.get(v.getStationId()), descriptions.get(v.getId()),
                releves.get(v.getId()))).toList();
    }

    private VersementVue vue(Versement v, StationRef s) {
        return vue(v, s, descriptions(List.of(v.getId())).get(v.getId()), datesReleve(List.of(v.getId())).get(v.getId()));
    }

    /** Date de l'operation du releve bancaire lettree avec chaque versement rapproche. */
    private Map<Long, LocalDate> datesReleve(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : operationsReleve.findByVersementIdIn(ids).stream()
                .collect(Collectors.toMap(OperationReleve::getVersementId, OperationReleve::getDateOperation, (a, b) -> a));
    }

    private VersementVue vue(Versement v, StationRef s, PieceVersement.Description p, LocalDate dateReleve) {
        boolean saisi = v.getStatut() == Versement.Statut.SAISI;
        return new VersementVue(v.getUuid(), s.uuid(), s.code(), v.getDateDepot(), v.getBanque(), v.getReferenceBv(),
                v.getMontant(), v.getDeposant(), v.getObservation(), v.getStatut().name(),
                p == null ? null : new PieceVue(p.getNomFichier(), p.getTypeMime(), p.getTaille()),
                v.getVerifiePar(), v.getVerifieLe(), v.getMotifRejet(), v.getCreePar(), v.getCreeLe(), v.getVersion(),
                saisi && aPermission("VERSEMENT_SAISIR"),
                saisi && aPermission("VERSEMENT_VALIDER") && !perimetre.identifiant().equals(v.getCreePar()), dateReleve);
    }

    private Map<Long, PieceVersement.Description> descriptions(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return pieces.descriptions(ids).stream().collect(Collectors.toMap(PieceVersement.Description::getVersementId, Function.identity()));
    }

    private static BigDecimal totalNonRejete(List<Versement> liste) {
        return liste.stream().filter(v -> v.getStatut() != Versement.Statut.REJETE).map(Versement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- Arretes de caisse et manquants

    public List<ArreteVue> arretes(UUID stationUuid) {
        StationRef s = stationAccessible(stationUuid);
        return arretes.findByStationIdOrderByDateJourDesc(s.id()).stream().map(a -> vue(a, s)).toList();
    }

    /**
     * Arrete de caisse en fin de journee : apres le dernier arrete, jamais dans le futur, journee du jour cloturee
     * (pour une reprise : aucune journee de ce jour en saisie). Un ecart negatif ouvre un manquant du gerant.
     */
    @Transactional
    public ArreteVue arreter(UUID stationUuid, LocalDate date, ArreteCaisse.Type type, BigDecimal comptees,
                             String responsable, String observation) {
        StationRef s = stationAccessible(stationUuid);
        if (date == null || date.isAfter(aujourdhui())) {
            throw ErreurMetier.regle("DATE_INVALIDE", "La caisse s'arrête à la fin d'une journée passée ou du jour.");
        }
        arretes.findFirstByStationIdOrderByDateJourDesc(s.id()).filter(a -> !date.isAfter(a.getDateJour())).ifPresent(a -> {
            throw ErreurMetier.regle("ARRETE_ANTERIEUR", "La caisse a déjà été arrêtée le " + Libelles.date(a.getDateJour())
                    + " : un nouvel arrêté doit être postérieur.");
        });
        Optional<JourneeRef> j = exploitation.journee(s.id(), date);
        if (type == ArreteCaisse.Type.CONTROLE && (j.isEmpty() || j.get().brouillon())) {
            throw ErreurMetier.regle("JOURNEE_NON_CLOTUREE", "Clôturez la journée du " + Libelles.date(date)
                    + " avant d'arrêter la caisse : le solde théorique repose sur son montant à verser.");
        }
        if (j.isPresent() && j.get().brouillon()) {
            throw ErreurMetier.regle("JOURNEE_NON_CLOTUREE", "La journée du " + Libelles.date(date) + " est en saisie.");
        }
        BigDecimal theorique = type == ArreteCaisse.Type.CONTROLE ? caisse.soldeTheorique(s.id(), date) : null;
        ArreteCaisse a = arretes.saveAndFlush(new ArreteCaisse(s.id(), date, type, theorique, comptees, responsable, observation));
        if (a.manquant()) {
            manquants.saveAndFlush(new ManquantGerant(a));
        }
        return vue(a, s);
    }

    public List<ManquantVue> manquants(String statut) {
        Map<Long, StationRef> stations = stationsVisibles();
        if (stations.isEmpty()) {
            return List.of();
        }
        List<ManquantGerant> liste = manquants.findByStationIdInOrderByDateJourDesc(stations.keySet()).stream()
                .filter(m -> statut == null || statut.isBlank() || m.getStatut().name().equalsIgnoreCase(statut)).toList();
        Map<Long, ArreteCaisse> parArrete = arretes.findAllById(liste.stream().map(ManquantGerant::getArreteId).toList())
                .stream().collect(Collectors.toMap(ArreteCaisse::getId, Function.identity()));
        List<Long> ids = liste.stream().map(ManquantGerant::getId).toList();
        Map<Long, List<EcheanceManquant>> plans = ids.isEmpty() ? Map.of()
                : echeances.findByManquantIdInOrderByManquantIdAscRangAsc(ids).stream().collect(Collectors.groupingBy(EcheanceManquant::getManquantId));
        Map<Long, List<PaiementManquant>> regles = ids.isEmpty() ? Map.of()
                : paiements.findByManquantIdInOrderByDatePaiementAscIdAsc(ids).stream().collect(Collectors.groupingBy(PaiementManquant::getManquantId));
        return liste.stream().map(m -> vue(m, stations.get(m.getStationId()), parArrete.get(m.getArreteId()),
                plans.getOrDefault(m.getId(), List.of()), regles.getOrDefault(m.getId(), List.of()))).toList();
    }

    /** Reconnaissance de dette du gerant et echeancier mensuel du reste du (remplace l'echeancier precedent). */
    @Transactional
    public ManquantVue echeancier(UUID id, Integer version, String reference, LocalDate reconnuLe, int nombre, LocalDate premiere) {
        ManquantGerant m = manquantAccessible(id, version);
        m.reconnaitre(reference, reconnuLe);
        if (premiere != null && premiere.isBefore(m.getDateJour())) {
            throw ErreurMetier.regle("DATE_INVALIDE", "La première échéance ne peut pas précéder le constat du manquant.");
        }
        List<EcheanceManquant> anciennes = echeances.findByManquantIdOrderByRangAsc(m.getId());
        echeances.deleteAll(anciennes);
        echeances.flush();
        // Les reglements deja recus restent imputes : l'echeancier couvre le montant total
        BigDecimal deja = m.getMontantRegle();
        List<EcheanceManquant> plan = new ArrayList<>();
        if (deja.signum() > 0) {
            plan.add(new EcheanceManquant(m.getId(), 1, m.getDateJour(), deja));
        }
        int decalage = plan.size();
        for (EcheanceManquant e : EcheanceManquant.plan(m.getId(), m.reste(), nombre, premiere)) {
            plan.add(new EcheanceManquant(m.getId(), e.getRang() + decalage, e.getDateEcheance(), e.getMontant()));
        }
        echeances.saveAll(plan);
        manquants.saveAndFlush(m);
        return vue(m);
    }

    /** Reglement partiel ou total d'un manquant (remboursement, retenue sur salaire ou perte). */
    @Transactional
    public ManquantVue payerManquant(UUID id, Integer version, BigDecimal montant, ManquantGerant.Mode mode, String reference,
                                     LocalDate le) {
        ManquantGerant m = manquantAccessible(id, version);
        m.payer(montant, mode, reference, le, perimetre.identifiant());
        paiements.save(new PaiementManquant(m.getId(), le, montant, mode, reference));
        manquants.saveAndFlush(m);
        return vue(m);
    }

    private ManquantGerant manquantAccessible(UUID id, Integer version) {
        ManquantGerant m = manquants.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Manquant"));
        perimetre.exigerStation(m.getStationId());
        m.verifierVersion(version);
        return m;
    }

    private ManquantVue vue(ManquantGerant m) {
        return vue(m, referentiel.station(m.getStationId()).orElseThrow(), arretes.findById(m.getArreteId()).orElse(null),
                echeances.findByManquantIdOrderByRangAsc(m.getId()),
                paiements.findByManquantIdInOrderByDatePaiementAscIdAsc(List.of(m.getId())));
    }

    @Transactional
    public ManquantVue reglerManquant(UUID id, Integer version, ManquantGerant.Mode mode, String reference, LocalDate le) {
        ManquantGerant m = manquantAccessible(id, version);
        BigDecimal reste = m.regler(mode, reference, le, perimetre.identifiant());
        paiements.save(new PaiementManquant(m.getId(), le, reste, mode, reference));
        manquants.saveAndFlush(m);
        return vue(m);
    }

    private ArreteVue vue(ArreteCaisse a, StationRef s) {
        Optional<ManquantGerant> m = a.manquant() ? manquants.findByArreteId(a.getId()) : Optional.empty();
        return new ArreteVue(a.getUuid(), s.uuid(), s.code(), a.getDateJour(), a.getType().name(), a.getSoldeTheorique(),
                a.getEspecesComptees(), a.getEcart(), a.getResponsable(), a.getObservation(), a.getCreePar(), a.getCreeLe(),
                m.map(ManquantGerant::getUuid).orElse(null), m.map(x -> x.getStatut().name()).orElse(null));
    }

    private ManquantVue vue(ManquantGerant m, StationRef s, ArreteCaisse a, List<EcheanceManquant> plan,
                            List<PaiementManquant> regles) {
        LocalDate aujourdhui = aujourdhui();
        BigDecimal aImputer = m.getMontantRegle();
        BigDecimal retard = BigDecimal.ZERO;
        LocalDate prochaine = null;
        List<EcheanceVue> vues = new ArrayList<>();
        for (EcheanceManquant e : plan) {
            BigDecimal regle = aImputer.min(e.getMontant());
            aImputer = aImputer.subtract(regle);
            boolean payee = regle.compareTo(e.getMontant()) == 0;
            boolean echue = !e.getDateEcheance().isAfter(aujourdhui);
            if (!payee && echue) {
                retard = retard.add(e.getMontant().subtract(regle));
            }
            if (!payee && prochaine == null) {
                prochaine = e.getDateEcheance();
            }
            vues.add(new EcheanceVue(e.getRang(), e.getDateEcheance(), e.getMontant(), regle,
                    payee ? "PAYEE" : echue ? "EN_RETARD" : "A_VENIR"));
        }
        boolean ouvert = m.getStatut() == ManquantGerant.Statut.OUVERT;
        return new ManquantVue(m.getUuid(), s.uuid(), s.code(), m.getDateJour(), m.getMontant(), m.getResponsable(),
                a == null ? null : a.getObservation(), m.getStatut().name(),
                m.getModeReglement() == null ? null : m.getModeReglement().name(), m.getReferenceReglement(),
                m.getRegleLe(), m.getReglePar(), m.getVersion(), ouvert && aPermission("VERSEMENT_VALIDER"),
                m.getMontantRegle(), m.reste(), m.getReconnaissanceReference(), m.getReconnuLe(), vues,
                regles.stream().map(p -> new PaiementVue(p.getDatePaiement(), p.getMontant(), p.getMode().name(), p.getReference(),
                        p.getCreePar())).toList(),
                ouvert ? prochaine : null, ouvert ? retard : BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- Situation du reseau

    public Situation situation() {
        Map<Long, StationRef> stations = stationsVisibles();
        LocalDate aujourdhui = aujourdhui();
        Map<Long, Integer> aVerifier = new HashMap<>();
        if (!stations.isEmpty()) {
            versements.findByStationIdInAndStatutOrderByDateDepotAscIdAsc(stations.keySet(), Versement.Statut.SAISI)
                    .forEach(v -> aVerifier.merge(v.getStationId(), 1, Integer::sum));
        }
        Map<Long, List<ManquantGerant>> ouverts = stations.isEmpty() ? Map.of()
                : manquants.findByStationIdInOrderByDateJourDesc(stations.keySet()).stream()
                .filter(m -> m.getStatut() == ManquantGerant.Statut.OUVERT)
                .collect(Collectors.groupingBy(ManquantGerant::getStationId));
        List<SituationStation> lignes = new ArrayList<>();
        for (StationRef s : stations.values()) {
            if (!s.actif()) {
                continue;
            }
            List<RecetteJournee> dernieres = recettes.findByStationIdAndDateJourLessThanEqualOrderByDateJourDesc(s.id(),
                    aujourdhui, Limit.of(delaiVersementJours));
            BigDecimal solde = caisse.soldeFin(s.id(), aujourdhui);
            LocalDate dernierDepot = versements.dernierDepot(s.id());
            LocalDate derniereRecette = dernieres.isEmpty() ? null : dernieres.get(0).getDateJour();
            Long sansVersement = dernierDepot == null ? null : ChronoUnit.DAYS.between(dernierDepot, aujourdhui);
            boolean retard = CalculCaisse.retard(solde, dernieres.stream().map(RecetteJournee::getMontantAVerser).toList(),
                    delaiVersementJours);
            Optional<ArreteCaisse> arrete = arretes.findFirstByStationIdOrderByDateJourDesc(s.id());
            List<ManquantGerant> m = ouverts.getOrDefault(s.id(), List.of());
            BigDecimal plafond = plafond(s.id());
            lignes.add(new SituationStation(s.uuid(), s.code(), s.nom(), solde, derniereRecette, dernierDepot, sansVersement,
                    retard, aVerifier.getOrDefault(s.id(), 0), m.size(),
                    m.stream().map(ManquantGerant::reste).reduce(BigDecimal.ZERO, BigDecimal::add),
                    arrete.map(ArreteCaisse::getDateJour).orElse(null), arrete.map(ArreteCaisse::getEcart).orElse(null),
                    plafond, CalculCaisse.depassePlafond(solde, plafond)));
        }
        lignes.sort(Comparator.comparing(SituationStation::stationCode));
        return new Situation(lignes, (int) lignes.stream().filter(SituationStation::retard).count(),
                lignes.stream().mapToInt(SituationStation::versementsAVerifier).sum(),
                lignes.stream().mapToInt(SituationStation::manquantsOuverts).sum(),
                lignes.stream().map(SituationStation::montantManquants).reduce(BigDecimal.ZERO, BigDecimal::add),
                delaiVersementJours, (int) lignes.stream().filter(SituationStation::depassePlafond).count());
    }

    // ---------------------------------------------------------------- Plafond d'especes

    /** Plafond d'especes non versees de la station, ou celui du reseau ; null = pas de plafond. */
    BigDecimal plafond(long stationId) {
        return plafonds.findByStationId(stationId).map(PlafondEspeces::getPlafond).orElse(plafondReseau);
    }

    @Transactional
    public BigDecimal fixerPlafond(UUID stationUuid, BigDecimal plafond) {
        StationRef s = stationAccessible(stationUuid);
        if (plafond == null) {
            plafonds.findByStationId(s.id()).ifPresent(plafonds::delete);
            return plafondReseau;
        }
        if (plafond.signum() <= 0 || plafond.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le plafond est un nombre entier de francs, supérieur à zéro.");
        }
        PlafondEspeces p = plafonds.findByStationId(s.id()).orElseGet(() -> new PlafondEspeces(s.id()));
        p.fixer(plafond);
        plafonds.save(p);
        return plafond;
    }

    // ---------------------------------------------------------------- Outils

    private StationRef stationAccessible(UUID uuid) {
        StationRef s = referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }

    private StationRef stationAccessible(long id) {
        StationRef s = referentiel.station(id).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }

    private Map<Long, StationRef> stationsVisibles() {
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        return referentiel.stations().stream()
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .collect(Collectors.toMap(StationRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
