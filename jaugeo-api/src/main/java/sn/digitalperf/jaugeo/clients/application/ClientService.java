package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.ComptesClients;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ClientVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CompteStation;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FicheClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.LigneReleve;
import sn.digitalperf.jaugeo.clients.application.VuesClients.Releve;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieOperation;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompte;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompteDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;

/**
 * Clients en compte (M6) : fiche, encours sous plafond, creances par station (reprises + bons - reglements),
 * releve de compte, reprises et reglements encaisses par le siege.
 */
@Service
@Transactional(readOnly = true)
public class ClientService implements ComptesClients {

    static final LocalDate ORIGINE = LocalDate.of(1900, 1, 1);
    static final LocalDate FIN = LocalDate.of(9999, 12, 31);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final ClientDepot clients;
    private final BonDepot bons;
    private final OperationCompteDepot operations;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final Clock horloge;

    public ClientService(ClientDepot clients, BonDepot bons, OperationCompteDepot operations,
                         ReferentielConsultation referentiel, PerimetreCourant perimetre, Clock horloge) {
        this.clients = clients;
        this.bons = bons;
        this.operations = operations;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Clients

    public List<ClientVue> liste() {
        Map<Long, BigDecimal> encours = new HashMap<>();
        bons.totauxReseau().forEach(r -> encours.merge((Long) r[0], (BigDecimal) r[1], BigDecimal::add));
        operations.totauxReseau().forEach(r -> encours.merge((Long) r[0], effet((OperationCompte.Type) r[1], (BigDecimal) r[2]),
                BigDecimal::add));
        return clients.findAllByOrderByCodeAsc().stream()
                .map(c -> vue(c, encours.getOrDefault(c.getId(), BigDecimal.ZERO))).toList();
    }

    public FicheClient fiche(UUID id) {
        Client c = client(id);
        Map<Long, BigDecimal> parStation = creancesParStation(c.getId());
        Map<Long, StationRef> visibles = stationsVisibles();
        List<CompteStation> comptes = parStation.entrySet().stream()
                .filter(e -> visibles.containsKey(e.getKey()))
                .map(e -> {
                    StationRef s = visibles.get(e.getKey());
                    return new CompteStation(s.uuid(), s.code(), s.nom(), e.getValue(), bons.dernierBon(c.getId(), s.id()),
                            operations.dernierReglement(c.getId(), s.id()));
                })
                .sorted(Comparator.comparing(CompteStation::stationCode)).toList();
        BigDecimal encours = parStation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FicheClient(vue(c, encours), comptes, aPermission("CLIENT_GERER"));
    }

    @Transactional
    public ClientVue creer(SaisieClient s) {
        if (s.code() != null && clients.existsByCodeIgnoreCase(s.code().trim())) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Un client porte déjà le code " + s.code().trim().toUpperCase() + ".");
        }
        Client c = new Client(s.code());
        c.modifier(s.nom(), s.telephone(), recouvrement(s.recouvrement()), s.plafond(), s.actif() == null || s.actif(),
                s.observation());
        return vue(clients.saveAndFlush(c), BigDecimal.ZERO);
    }

    @Transactional
    public ClientVue modifier(UUID id, Integer version, SaisieClient s) {
        Client c = client(id);
        c.verifierVersion(version);
        c.modifier(s.nom(), s.telephone(), recouvrement(s.recouvrement()), s.plafond(), s.actif() == null || s.actif(),
                s.observation());
        clients.saveAndFlush(c);
        return vue(c, encours(c.getId(), null));
    }

    // ---------------------------------------------------------------- Releve et operations du siege

    public Releve releve(UUID clientId, UUID stationUuid, LocalDate du, LocalDate au) {
        Client c = client(clientId);
        StationRef s = stationAccessible(stationUuid);
        LocalDate debut = du == null ? ORIGINE : du;
        LocalDate fin = au == null ? FIN : au;
        if (fin.isBefore(debut)) {
            throw ErreurMetier.regle("PERIODE_INVALIDE", "La fin de la période précède son début.");
        }
        BigDecimal soldeDebut = debut.equals(ORIGINE) ? BigDecimal.ZERO : creance(c.getId(), s.id(), ORIGINE, debut.minusDays(1));
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        boolean peutGerer = aPermission("CLIENT_GERER");

        record Mouvement(LocalDate date, long ordre, LigneReleve ligne) {
        }
        List<Mouvement> mouvements = new ArrayList<>();
        for (Bon b : bons.findByClientIdAndStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(c.getId(), s.id(), debut, fin)) {
            ProduitRef p = produits.get(b.getProduitId());
            mouvements.add(new Mouvement(b.getDateJour(), b.getId(), new LigneReleve(b.getDateJour(), "BON", b.getRecouvrement().name(),
                    b.getNumero(), p == null ? null : p.code(), b.getLitres(), b.getPrixUnitaire(), b.getImmatriculation(),
                    b.getPorteur(), b.getMontant(), null, null, null, null, false, b.getCreePar())));
        }
        for (OperationCompte o : operations.findByClientIdAndStationIdAndDateOperationBetweenOrderByDateOperationAscIdAsc(
                c.getId(), s.id(), debut, fin)) {
            boolean reprise = o.getType() == OperationCompte.Type.REPRISE;
            mouvements.add(new Mouvement(o.getDateOperation(), o.getId(), new LigneReleve(o.getDateOperation(), o.getType().name(),
                    o.getMode() == null ? null : o.getMode().name(), null, null, null, null, o.getReference(), o.getObservation(),
                    reprise ? o.getMontant() : null, reprise ? null : o.getMontant(), null, o.getUuid(), o.getVersion(),
                    peutGerer && o.getMode() != OperationCompte.Mode.ESPECES_STATION, o.getCreePar())));
        }
        // Dans la journee : reprises et bons avant les reglements
        mouvements.sort(Comparator.comparing(Mouvement::date).thenComparing(m -> m.ligne().credit() != null)
                .thenComparing(Mouvement::ordre));
        List<LigneReleve> lignes = new ArrayList<>();
        BigDecimal solde = soldeDebut;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Mouvement m : mouvements) {
            LigneReleve l = m.ligne();
            if (l.debit() != null) {
                solde = solde.add(l.debit());
                debit = debit.add(l.debit());
            } else {
                solde = solde.subtract(l.credit());
                credit = credit.add(l.credit());
            }
            lignes.add(new LigneReleve(l.date(), l.type(), l.mode(), l.numero(), l.produitCode(), l.litres(), l.prixUnitaire(),
                    l.reference(), l.observation(), l.debit(), l.credit(), solde, l.operationId(), l.version(), l.supprimable(),
                    l.creePar()));
        }
        return new Releve(c.getUuid(), c.getCode(), c.getNom(), s.uuid(), s.code(), du, au, soldeDebut, debit, credit, solde, lignes);
    }

    /** Reprise d'une creance existante, ou reglement encaisse par le siege (virement, cheque, especes au siege...). */
    @Transactional
    public FicheClient enregistrerOperation(UUID clientId, SaisieOperation s) {
        Client c = client(clientId);
        StationRef st = stationAccessible(s.stationId());
        if (s.date() != null && s.date().isAfter(aujourdhui())) {
            throw ErreurMetier.regle("DATE_FUTURE", "L'opération ne peut pas être datée dans le futur.");
        }
        OperationCompte o;
        if ("REPRISE".equalsIgnoreCase(s.type())) {
            o = OperationCompte.reprise(c.getId(), st.id(), s.date(), s.montant(), s.reference(), s.observation());
        } else if ("REGLEMENT".equalsIgnoreCase(s.type())) {
            o = OperationCompte.reglementSiege(c.getId(), st.id(), s.date(), mode(s.mode()), s.montant(), s.reference(),
                    s.observation());
            exigerCreanceSuffisante(c, st, s.montant());
        } else {
            throw ErreurMetier.regle("TYPE_INVALIDE", "Une opération de compte est une reprise de créance ou un règlement.");
        }
        operations.saveAndFlush(o);
        return fiche(clientId);
    }

    /** Retrait d'une reprise ou d'un reglement du siege saisi par erreur (les especes de la station se retirent de la journee). */
    @Transactional
    public FicheClient supprimerOperation(UUID operationId, Integer version) {
        OperationCompte o = operations.findByUuid(operationId).orElseThrow(() -> ErreurMetier.introuvable("Opération"));
        perimetre.exigerStation(o.getStationId());
        o.verifierVersion(version);
        if (o.getMode() == OperationCompte.Mode.ESPECES_STATION) {
            throw ErreurMetier.regle("OPERATION_DE_JOURNEE", "Ce recouvrement en espèces se retire de la journée de la station.");
        }
        operations.delete(o);
        operations.flush();
        exigerCreancePositive(o.getClientId(), o.getStationId());
        return fiche(clients.findById(o.getClientId()).orElseThrow().getUuid());
    }

    // ---------------------------------------------------------------- Interface publique

    @Override
    public TotauxJournee totauxJournee(long journeeId) {
        BigDecimal direction = BigDecimal.ZERO;
        BigDecimal gerant = BigDecimal.ZERO;
        for (Object[] r : bons.totauxJournee(journeeId)) {
            if (r[0] == Client.Recouvrement.DIRECTION) {
                direction = (BigDecimal) r[1];
            } else {
                gerant = (BigDecimal) r[1];
            }
        }
        return new TotauxJournee(direction, gerant, operations.totalJournee(journeeId));
    }

    @Override
    public List<CreanceClient> creances(long stationId, LocalDate du, LocalDate au) {
        Map<Long, BigDecimal[]> parClient = new HashMap<>();
        // [0] debut, [1] bons, [2] reglements station, [3] reglements siege
        Function<Long, BigDecimal[]> nouveau = id -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Object[] r : bons.totauxParClient(stationId, ORIGINE, du.minusDays(1))) {
            ajouter(parClient.computeIfAbsent((Long) r[0], nouveau), 0, (BigDecimal) r[1]);
        }
        for (Object[] r : operations.totauxParClient(stationId, ORIGINE, du.minusDays(1))) {
            ajouter(parClient.computeIfAbsent((Long) r[0], nouveau), 0, effet((OperationCompte.Type) r[1], (BigDecimal) r[3]));
        }
        for (Object[] r : bons.totauxParClient(stationId, du, au)) {
            ajouter(parClient.computeIfAbsent((Long) r[0], nouveau), 1, (BigDecimal) r[1]);
        }
        for (Object[] r : operations.totauxParClient(stationId, du, au)) {
            BigDecimal[] t = parClient.computeIfAbsent((Long) r[0], nouveau);
            BigDecimal montant = (BigDecimal) r[3];
            if (r[1] == OperationCompte.Type.REPRISE) {
                ajouter(t, 0, montant);
            } else if (r[2] == OperationCompte.Mode.ESPECES_STATION) {
                ajouter(t, 2, montant);
            } else {
                ajouter(t, 3, montant);
            }
        }
        Map<Long, Client> fiches = clients.findAllById(parClient.keySet()).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        return parClient.entrySet().stream()
                .filter(e -> e.getValue()[0].signum() != 0 || e.getValue()[1].signum() != 0 || e.getValue()[2].signum() != 0
                        || e.getValue()[3].signum() != 0)
                .map(e -> {
                    Client c = fiches.get(e.getKey());
                    BigDecimal[] t = e.getValue();
                    return new CreanceClient(c.getUuid(), c.getCode(), c.getNom(), c.getRecouvrement().name(), t[0], t[1], t[2], t[3],
                            t[0].add(t[1]).subtract(t[2]).subtract(t[3]));
                })
                .sorted(Comparator.comparing(CreanceClient::code)).toList();
    }

    private static void ajouter(BigDecimal[] t, int i, BigDecimal montant) {
        t[i] = t[i].add(montant);
    }

    // ---------------------------------------------------------------- Calculs partages avec la journee

    /** Encours du client sur tout le reseau, hors un bon en cours de correction. */
    BigDecimal encours(long clientId, Long bonExclu) {
        BigDecimal r = bons.totalClient(clientId, bonExclu == null ? -1L : bonExclu);
        for (Object[] o : operations.totauxParStation(clientId)) {
            r = r.add(effet((OperationCompte.Type) o[1], (BigDecimal) o[2]));
        }
        return r;
    }

    /** Mouvements nets du client a la station entre deux dates incluses (reprises + bons - reglements). */
    BigDecimal creance(long clientId, long stationId, LocalDate du, LocalDate au) {
        BigDecimal r = bons.findByClientIdAndStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(clientId, stationId, du, au)
                .stream().map(Bon::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        return operations.findByClientIdAndStationIdAndDateOperationBetweenOrderByDateOperationAscIdAsc(clientId, stationId, du, au)
                .stream().map(OperationCompte::effet).reduce(r, BigDecimal::add);
    }

    /** Creance a ce jour du client a la station. */
    BigDecimal creanceStation(long clientId, long stationId) {
        return creancesParStation(clientId).getOrDefault(stationId, BigDecimal.ZERO);
    }

    /** Plafond (regle 6) : l'encours du client, ce bon compris, ne depasse pas son plafond. */
    void exigerPlafond(Client c, BigDecimal montant, Long bonExclu) {
        if (c.getPlafond() == null) {
            return;
        }
        BigDecimal encours = encours(c.getId(), bonExclu);
        if (encours.add(montant).compareTo(c.getPlafond()) > 0) {
            BigDecimal disponible = c.getPlafond().subtract(encours).max(BigDecimal.ZERO);
            throw ErreurMetier.regle("BON_PLAFOND_DEPASSE", "Plafond de " + c.getNom() + " dépassé : encours "
                    + Libelles.francs(encours) + " pour un plafond de " + Libelles.francs(c.getPlafond()) + ", disponible "
                    + Libelles.francs(disponible) + ". Le bon de " + Libelles.francs(montant) + " ne peut pas être accordé.");
        }
    }

    /** Un reglement ne depasse pas ce que le client doit a la station. */
    void exigerCreanceSuffisante(Client c, StationRef s, BigDecimal montant) {
        BigDecimal due = creanceStation(c.getId(), s.id());
        if (montant != null && montant.compareTo(due) > 0) {
            throw ErreurMetier.regle("CREANCE_INSUFFISANTE", c.getNom() + " doit " + Libelles.francs(due.max(BigDecimal.ZERO))
                    + " à la station " + s.code() + " : un règlement de " + Libelles.francs(montant) + " la dépasse.");
        }
    }

    /** Apres un retrait ou une correction : la creance de la station ne devient pas negative. */
    void exigerCreancePositive(long clientId, long stationId) {
        BigDecimal due = creanceStation(clientId, stationId);
        if (due.signum() < 0) {
            throw ErreurMetier.regle("CREANCE_NEGATIVE", "Le compte du client deviendrait créditeur de " + Libelles.francs(due.negate())
                    + " à cette station : retirez d'abord le règlement correspondant.");
        }
    }

    Client client(UUID id) {
        if (id == null) {
            throw ErreurMetier.regle("CLIENT_OBLIGATOIRE", "Choisissez le client.");
        }
        return clients.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Client"));
    }

    // ---------------------------------------------------------------- Outils

    private Map<Long, BigDecimal> creancesParStation(long clientId) {
        Map<Long, BigDecimal> r = new HashMap<>();
        bons.totauxParStation(clientId).forEach(o -> r.merge((Long) o[0], (BigDecimal) o[1], BigDecimal::add));
        operations.totauxParStation(clientId).forEach(o -> r.merge((Long) o[0], effet((OperationCompte.Type) o[1], (BigDecimal) o[2]),
                BigDecimal::add));
        return r;
    }

    private static BigDecimal effet(OperationCompte.Type type, BigDecimal montant) {
        return type == OperationCompte.Type.REPRISE ? montant : montant.negate();
    }

    private static ClientVue vue(Client c, BigDecimal encours) {
        BigDecimal disponible = c.getPlafond() == null ? null : c.getPlafond().subtract(encours).max(BigDecimal.ZERO);
        return new ClientVue(c.getUuid(), c.getCode(), c.getNom(), c.getTelephone(), c.getRecouvrement().name(), c.getPlafond(),
                c.isActif(), c.getObservation(), encours, disponible, c.getVersion());
    }

    private static Client.Recouvrement recouvrement(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return Client.Recouvrement.valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("RECOUVREMENT_INVALIDE", "Le recouvrement est assuré par la direction ou par le gérant.");
        }
    }

    private static OperationCompte.Mode mode(String valeur) {
        try {
            return valeur == null ? null : OperationCompte.Mode.valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("MODE_INVALIDE", "Mode de règlement inconnu.");
        }
    }

    StationRef stationAccessible(UUID uuid) {
        if (uuid == null) {
            throw ErreurMetier.regle("STATION_OBLIGATOIRE", "Choisissez la station.");
        }
        StationRef s = referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
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

    static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
