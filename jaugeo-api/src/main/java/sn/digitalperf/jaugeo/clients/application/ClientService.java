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
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FicheClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.LigneReleve;
import sn.digitalperf.jaugeo.clients.application.VuesClients.Releve;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RemiseVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieOperation;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.CarteDepot;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.EnginClient;
import sn.digitalperf.jaugeo.clients.domaine.EnginClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.FactureDepot;
import sn.digitalperf.jaugeo.clients.domaine.Imputation;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.clients.domaine.MouvementCarteDepot;
import sn.digitalperf.jaugeo.clients.domaine.RelanceDepot;
import sn.digitalperf.jaugeo.clients.domaine.RemiseClient;
import sn.digitalperf.jaugeo.clients.domaine.RemiseClientDepot;
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

    private final EnginClientDepot engins;
    private final RemiseClientDepot remises;
    private final FactureDepot factures;
    private final CarteDepot cartes;
    private final RelanceDepot relances;
    private final ImputationClients imputation;
    private final MouvementCarteDepot pleins;

    public ClientService(ClientDepot clients, BonDepot bons, OperationCompteDepot operations,
                         ReferentielConsultation referentiel, PerimetreCourant perimetre, Clock horloge,
                         EnginClientDepot engins, RemiseClientDepot remises, FactureDepot factures, CarteDepot cartes,
                         RelanceDepot relances, ImputationClients imputation, MouvementCarteDepot pleins) {
        this.pleins = pleins;
        this.clients = clients;
        this.bons = bons;
        this.operations = operations;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
        this.engins = engins;
        this.remises = remises;
        this.factures = factures;
        this.cartes = cartes;
        this.relances = relances;
        this.imputation = imputation;
    }

    // ---------------------------------------------------------------- Clients

    public List<ClientVue> liste() {
        Map<Long, BigDecimal> encours = new HashMap<>();
        bons.totauxReseau().forEach(r -> encours.merge((Long) r[0], (BigDecimal) r[1], BigDecimal::add));
        operations.totauxReseau().forEach(r -> encours.merge((Long) r[0], effet((OperationCompte.Type) r[1], (BigDecimal) r[2]),
                BigDecimal::add));
        List<Client> tous = clients.findAllByOrderByCodeAsc();
        Map<Long, List<Reste>> restes = imputation.restes(tous);
        LocalDate jour = aujourdhui();
        return tous.stream().map(c -> Mappeurs.client(c, encours.getOrDefault(c.getId(), BigDecimal.ZERO),
                Imputation.echu(restes.getOrDefault(c.getId(), List.of()), jour))).toList();
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
        List<Reste> restes = imputation.restes(c);
        LocalDate jour = aujourdhui();
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        Set<Long> enVigueur = new java.util.HashSet<>();
        List<RemiseClient> liste = remises.findByClientIdOrderByProduitIdAscDebutDesc(c.getId());
        Set<Long> vus = new java.util.HashSet<>();
        for (RemiseClient r : liste) {
            if (!r.getDebut().isAfter(jour) && vus.add(r.getProduitId())) {
                enVigueur.add(r.getId());
            }
        }
        List<RemiseVue> vuesRemises = liste.stream().map(r -> {
            ProduitRef p = produits.get(r.getProduitId());
            return new RemiseVue(r.getUuid(), p.uuid(), p.code(), p.libelle(), r.getDebut(), r.getRemise(), enVigueur.contains(r.getId()));
        }).sorted(Comparator.comparing(RemiseVue::produitCode).thenComparing(RemiseVue::debut, Comparator.reverseOrder())).toList();
        List<FactureVue> vuesFactures = factures.findByClientIdOrderByDateFactureDescIdDesc(c.getId()).stream()
                .map(f -> Mappeurs.facture(f, c, restes, (int) bons.countByFactureId(f.getId()))).toList();
        Map<Long, Client> soi = Map.of(c.getId(), c);
        return new FicheClient(Mappeurs.client(c, encours, Imputation.echu(restes, jour)), comptes,
                engins.findByClientIdOrderByImmatriculationAsc(c.getId()).stream().map(Mappeurs::engin).toList(), vuesRemises,
                vuesFactures, cartes.findByClientIdOrderByNumeroAsc(c.getId()).stream().map(k -> Mappeurs.carte(k, soi)).toList(),
                relances.findByClientIdOrderByDateRelanceDescIdDesc(c.getId()).stream().map(Mappeurs::relance).toList(),
                aPermission("CLIENT_GERER"));
    }

    @Transactional
    public ClientVue creer(SaisieClient s) {
        if (s.code() != null && clients.existsByCodeIgnoreCase(s.code().trim())) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Un client porte déjà le code " + s.code().trim().toUpperCase() + ".");
        }
        Client c = new Client(s.code());
        c.modifier(s.nom(), s.telephone(), recouvrement(s.recouvrement()), s.plafond(), s.actif() == null || s.actif(),
                s.observation());
        c.renseignerContrat(s.contrat() == null ? Client.Contrat.AUCUN : s.contrat());
        return Mappeurs.client(clients.saveAndFlush(c), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public ClientVue modifier(UUID id, Integer version, SaisieClient s) {
        Client c = client(id);
        c.verifierVersion(version);
        c.modifier(s.nom(), s.telephone(), recouvrement(s.recouvrement()), s.plafond(), s.actif() == null || s.actif(),
                s.observation());
        if (s.contrat() != null) {
            c.renseignerContrat(s.contrat());
        }
        clients.saveAndFlush(c);
        return Mappeurs.client(c, encours(c.getId(), null), Imputation.echu(imputation.restes(c), aujourdhui()));
    }

    // ---------------------------------------------------------------- Engins autorises et remises

    @Transactional
    public FicheClient enregistrerEngin(UUID clientId, UUID enginId, Integer version, String type, String immatriculation,
                                        String libelle, Boolean actif) {
        Client c = client(clientId);
        EnginClient e;
        if (enginId == null) {
            e = new EnginClient(c.getId());
        } else {
            e = engins.findByUuid(enginId).filter(x -> x.getClientId().equals(c.getId()))
                    .orElseThrow(() -> ErreurMetier.introuvable("Engin"));
            e.verifierVersion(version);
        }
        EnginClient.Type t;
        try {
            t = type == null ? null : EnginClient.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ErreurMetier.regle("TYPE_ENGIN_INVALIDE", "Un engin est un véhicule ou une pirogue.");
        }
        e.modifier(t, immatriculation, libelle, actif == null || actif);
        engins.findByClientIdAndImmatriculationIgnoreCase(c.getId(), e.getImmatriculation())
                .filter(x -> !x.getId().equals(e.getId()))
                .ifPresent(x -> {
                    throw ErreurMetier.conflit("ENGIN_EXISTANT", "L'immatriculation " + x.getImmatriculation() + " est déjà enregistrée pour ce client.");
                });
        engins.saveAndFlush(e);
        return fiche(clientId);
    }

    @Transactional
    public FicheClient ajouterRemise(UUID clientId, UUID produitId, LocalDate debut, BigDecimal remise) {
        Client c = client(clientId);
        ProduitRef p = referentiel.produits().stream().filter(x -> x.uuid().equals(produitId)).findFirst()
                .orElseThrow(() -> ErreurMetier.introuvable("Produit"));
        if (debut != null && remises.existsByClientIdAndProduitIdAndDebut(c.getId(), p.id(), debut)) {
            throw ErreurMetier.conflit("REMISE_EXISTANTE", "Une remise du " + p.libelle() + " commence déjà le "
                    + Libelles.date(debut) + " : retirez-la avant d'en saisir une autre.");
        }
        remises.saveAndFlush(new RemiseClient(c.getId(), p.id(), debut, remise));
        return fiche(clientId);
    }

    /** Retrait d'une remise saisie par erreur ; les bons deja saisis gardent la remise appliquee. */
    @Transactional
    public FicheClient supprimerRemise(UUID remiseId) {
        RemiseClient r = remises.findByUuid(remiseId).orElseThrow(() -> ErreurMetier.introuvable("Remise"));
        Client c = clients.findById(r.getClientId()).orElseThrow();
        remises.delete(r);
        remises.flush();
        return fiche(c.getUuid());
    }

    /** Remise au litre du client sur un produit a une date (zero sans remise). */
    BigDecimal remise(long clientId, long produitId, LocalDate date) {
        return remises.findFirstByClientIdAndProduitIdAndDebutLessThanEqualOrderByDebutDesc(clientId, produitId, date)
                .map(RemiseClient::getRemise).orElse(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- Regles d'octroi d'un bon (blocage)

    /**
     * Motifs pour lesquels un bon doit attendre la derogation de la direction : plafond depasse, echeance depassee
     * (facture ou reprise echue non reglee), engin non autorise, contrat hors validite.
     */
    List<String> motifsBlocage(Client c, LocalDate date, BigDecimal montant, Long bonExclu, String immatriculation) {
        List<String> motifs = new ArrayList<>();
        if (c.getPlafond() != null) {
            BigDecimal encours = encours(c.getId(), bonExclu);
            if (encours.add(montant).compareTo(c.getPlafond()) > 0) {
                BigDecimal disponible = c.getPlafond().subtract(encours).max(BigDecimal.ZERO);
                motifs.add("Plafond dépassé : encours " + Libelles.francs(encours) + " pour un plafond de "
                        + Libelles.francs(c.getPlafond()) + ", disponible " + Libelles.francs(disponible) + ".");
            }
        }
        if (c.getDelaiPaiement() != null) {
            Reste r = Imputation.premiereEchue(imputation.restes(c), date);
            if (r != null) {
                motifs.add("Échéance dépassée : " + r.dette().reference() + " échue le " + Libelles.date(r.dette().echeance())
                        + ", " + Libelles.francs(r.reste()) + " non réglés.");
            }
        }
        List<EnginClient> autorises = engins.findByClientIdOrderByImmatriculationAsc(c.getId()).stream()
                .filter(EnginClient::isActif).toList();
        if (!autorises.isEmpty()) {
            String immat = EnginClient.normaliser(immatriculation);
            if (immat == null) {
                motifs.add("Immatriculation absente : le client n'autorise que ses véhicules et pirogues enregistrés.");
            } else if (autorises.stream().noneMatch(e -> e.getImmatriculation().equalsIgnoreCase(immat))) {
                motifs.add("Immatriculation " + immat + " non autorisée pour ce client.");
            }
        }
        if (!c.sousContrat(date)) {
            motifs.add("Contrat hors validité au " + Libelles.date(date) + ".");
        }
        return motifs;
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
        return new TotauxJournee(direction, gerant, pleins.totalPompeJournee(journeeId), operations.totalJournee(journeeId));
    }

    @Override
    public BigDecimal remises(long stationId, LocalDate du, LocalDate au) {
        return bons.remises(stationId, du, au);
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

    LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }

    static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
