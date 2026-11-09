package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.application.VuesClients.BonEnAttenteVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.BonVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CreditJourneeVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.PleinCarteVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ProduitCredit;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RecouvrementVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieBon;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Carte;
import sn.digitalperf.jaugeo.clients.domaine.CarteDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.MouvementCarte;
import sn.digitalperf.jaugeo.clients.domaine.MouvementCarteDepot;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompte;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompteDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.CuveRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.PistoletRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Ventes a credit d'une journee (feuille station : BONS A RECOUVRER PAR LA DIRECTION / PAR LE GERANT), pleins payes
 * par carte prepayee et recouvrements de creances encaisses en especes par le gerant.
 * <p>
 * Un bon exige un client actif, un produit vendu a la station et un numero jamais utilise ; il est valorise au prix en
 * vigueur a l'ouverture de la journee, diminue de la remise du client. Hors plafond, hors echeance, pour un engin non
 * autorise ou hors contrat, il est enregistre en attente : la journee ne peut pas etre cloturee tant que la direction
 * ne l'a pas accorde ou refuse.
 */
@Service
@Transactional(readOnly = true)
public class CreditJourneeService {

    private final BonDepot bons;
    private final OperationCompteDepot operations;
    private final ClientDepot clients;
    private final ClientService comptes;
    private final CarteDepot cartes;
    private final MouvementCarteDepot pleins;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final Tarification tarification;
    private final PerimetreCourant perimetre;
    private final Clock horloge;

    public CreditJourneeService(BonDepot bons, OperationCompteDepot operations, ClientDepot clients, ClientService comptes,
                                CarteDepot cartes, MouvementCarteDepot pleins, JourneesExploitation exploitation,
                                ReferentielConsultation referentiel, Tarification tarification, PerimetreCourant perimetre,
                                Clock horloge) {
        this.bons = bons;
        this.operations = operations;
        this.clients = clients;
        this.comptes = comptes;
        this.cartes = cartes;
        this.pleins = pleins;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.tarification = tarification;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    public CreditJourneeVue journee(UUID journeeUuid) {
        return vue(journeeAccessible(journeeUuid));
    }

    // ---------------------------------------------------------------- Bons

    @Transactional
    public CreditJourneeVue ajouterBon(UUID journeeUuid, Integer version, SaisieBon s) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Bon b = new Bon(j.id(), j.stationId(), j.date());
        renseigner(j, b, s);
        return vue(j);
    }

    @Transactional
    public CreditJourneeVue modifierBon(UUID journeeUuid, UUID bonUuid, Integer version, SaisieBon s) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Bon b = bon(j, bonUuid);
        long ancienClient = b.getClientId();
        renseigner(j, b, s);
        if (ancienClient != b.getClientId()) {
            comptes.exigerCreancePositive(ancienClient, j.stationId());
        }
        return vue(j);
    }

    @Transactional
    public CreditJourneeVue supprimerBon(UUID journeeUuid, UUID bonUuid, Integer version) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Bon b = bon(j, bonUuid);
        if (b.getFactureId() != null) {
            throw ErreurMetier.regle("BON_FACTURE", b.resume() + " est facturé : annulez d'abord la facture.");
        }
        bons.delete(b);
        bons.flush();
        comptes.exigerCreancePositive(b.getClientId(), j.stationId());
        return vue(j);
    }

    private void renseigner(JourneeRef j, Bon b, SaisieBon s) {
        Client c = comptes.client(s.clientId());
        ProduitRef p = produitVendu(j, s.produitId());
        BigDecimal prix = prix(j, p).orElseThrow(() -> ErreurMetier.regle("PRIX_INCONNU", "Aucun prix du "
                + p.libelle() + " n'est en vigueur à l'ouverture de la journée : le bon ne peut pas être valorisé."));
        String numero = s.numero() == null ? null : s.numero().trim();
        if (numero != null && !numero.isEmpty()) {
            bons.findByStationIdAndNumeroIgnoreCase(j.stationId(), numero).filter(x -> !x.getId().equals(b.getId())).ifPresent(x -> {
                throw ErreurMetier.conflit("BON_DEJA_UTILISE", "Le bon n° " + x.getNumero() + " a déjà été utilisé le "
                        + Libelles.date(x.getDateJour()) + " : un bon ne sert qu'une fois.");
            });
        }
        Long ancienClient = b.getClientId();
        BigDecimal ancienMontant = b.getMontant();
        b.renseigner(c, numero, p.id(), s.litres(), prix, comptes.remise(c.getId(), p.id(), j.date()), s.immatriculation(),
                s.porteur());
        boolean conserver = ancienClient != null && ancienClient.equals(b.getClientId()) && ancienMontant != null
                && b.getMontant().compareTo(ancienMontant) <= 0;
        b.controler(comptes.motifsBlocage(c, j.date(), b.getMontant(), b.getId(), b.getImmatriculation()), conserver);
        bons.saveAndFlush(b);
    }

    // ---------------------------------------------------------------- Derogation de la direction

    /** Bons en attente de derogation dans le perimetre, par date. */
    public List<BonEnAttenteVue> enAttente() {
        List<Bon> liste = bons.findByStatutOrderByDateJourAscIdAsc(Bon.Statut.EN_ATTENTE).stream()
                .filter(b -> perimetre.estReseau() || perimetre.stationIds().contains(b.getStationId())).toList();
        Map<Long, Client> parId = clients.findAllById(liste.stream().map(Bon::getClientId).toList()).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        return liste.stream().map(b -> {
            Client c = parId.get(b.getClientId());
            JourneeRef j = exploitation.journee(b.getJourneeId()).orElseThrow();
            StationRef s = stations.get(b.getStationId());
            return new BonEnAttenteVue(b.getUuid(), b.getVersion(), j.uuid(), s.uuid(), s.code(), b.getDateJour(), j.statut(),
                    c.getUuid(), c.getCode(), c.getNom(), b.getNumero(), produits.get(b.getProduitId()).code(), b.getLitres(),
                    b.getMontant(), b.getImmatriculation(), b.getMotifBlocage(), b.getCreePar());
        }).toList();
    }

    /** Accord (derogation tracee) ou refus (bon a recouvrer par le gerant) d'un bon en attente. */
    @Transactional
    public List<BonEnAttenteVue> decider(UUID bonUuid, Integer version, boolean accord, String motif) {
        Bon b = bons.findByUuid(bonUuid).orElseThrow(() -> ErreurMetier.introuvable("Bon"));
        perimetre.exigerStation(b.getStationId());
        b.verifierVersion(version);
        JourneeRef j = exploitation.journee(b.getJourneeId()).orElseThrow();
        if (!j.brouillon()) {
            throw ErreurMetier.regle("JOURNEE_CLOTUREE", "La journée du " + Libelles.date(j.date()) + " n'est plus en saisie.");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        b.decider(accord, motif, auth == null ? "?" : auth.getName(), horloge.instant());
        bons.saveAndFlush(b);
        return enAttente();
    }

    // ---------------------------------------------------------------- Pleins payes par carte prepayee

    @Transactional
    public CreditJourneeVue ajouterPleinCarte(UUID journeeUuid, Integer version, String numero, UUID produitId, BigDecimal litres,
                                              String reference) {
        JourneeRef j = verrouiller(journeeUuid, version);
        String n = Carte.normaliser(numero);
        if (n == null) {
            throw ErreurMetier.regle("CARTE_NUMERO_INVALIDE", "Numéro de carte invalide : vérifiez la saisie ou scannez le QR code.");
        }
        Carte k = cartes.findByNumero(n).orElseThrow(() -> ErreurMetier.introuvable("Carte " + n));
        Client c = clients.findById(k.getClientId()).orElseThrow();
        if (!c.isActif()) {
            throw ErreurMetier.regle("CLIENT_INACTIF", "Le client " + c.getNom() + " n'est plus actif : sa carte ne peut pas être utilisée.");
        }
        ProduitRef p = produitVendu(j, produitId);
        BigDecimal prix = prix(j, p).orElseThrow(() -> ErreurMetier.regle("PRIX_INCONNU", "Aucun prix du "
                + p.libelle() + " n'est en vigueur à l'ouverture de la journée."));
        MouvementCarte m = MouvementCarte.debit(k.getId(), j.id(), j.stationId(), j.date(), p.id(), litres, prix,
                comptes.remise(c.getId(), p.id(), j.date()), reference);
        k.debiter(m.getMontant());
        cartes.saveAndFlush(k);
        pleins.saveAndFlush(m);
        return vue(j);
    }

    @Transactional
    public CreditJourneeVue supprimerPleinCarte(UUID journeeUuid, UUID pleinUuid, Integer version) {
        JourneeRef j = verrouiller(journeeUuid, version);
        MouvementCarte m = pleins.findByUuid(pleinUuid).filter(x -> x.getJourneeId() != null && x.getJourneeId() == j.id())
                .orElseThrow(() -> ErreurMetier.introuvable("Plein"));
        Carte k = cartes.findById(m.getCarteId()).orElseThrow();
        k.restituer(m.getMontant());
        cartes.saveAndFlush(k);
        pleins.delete(m);
        pleins.flush();
        return vue(j);
    }

    // ---------------------------------------------------------------- Recouvrements en especes

    @Transactional
    public CreditJourneeVue ajouterRecouvrement(UUID journeeUuid, Integer version, UUID clientId, BigDecimal montant,
                                                String reference, String observation) {
        JourneeRef j = verrouiller(journeeUuid, version);
        Client c = comptes.client(clientId);
        StationRef s = referentiel.station(j.stationId()).orElseThrow();
        comptes.exigerCreanceSuffisante(c, s, montant);
        operations.saveAndFlush(OperationCompte.recouvrementStation(c.getId(), j.stationId(), j.date(), j.id(), montant,
                reference, observation));
        return vue(j);
    }

    @Transactional
    public CreditJourneeVue supprimerRecouvrement(UUID journeeUuid, UUID operationUuid, Integer version) {
        JourneeRef j = verrouiller(journeeUuid, version);
        OperationCompte o = operations.findByUuid(operationUuid).filter(x -> j.id() == (x.getJourneeId() == null ? -1 : x.getJourneeId()))
                .orElseThrow(() -> ErreurMetier.introuvable("Recouvrement"));
        operations.delete(o);
        operations.flush();
        return vue(j);
    }

    // ---------------------------------------------------------------- Cloture

    /**
     * Points bloquants pour la cloture : bons en attente de derogation ; litres servis sur bons et par carte d'un
     * produit au-dela des sorties nettes de ce produit dans la journee.
     */
    List<String> anomalies(JourneeRef j, Map<Long, BigDecimal> sortiesParCuve) {
        List<String> r = new ArrayList<>();
        for (Bon b : bons.findByJourneeIdOrderByIdAsc(j.id())) {
            if (b.getStatut() == Bon.Statut.EN_ATTENTE) {
                r.add(b.resume() + " en attente de la dérogation de la direction : " + b.getMotifBlocage());
            }
        }
        Map<Long, BigDecimal> litres = litresParProduit(j.id());
        if (litres.isEmpty()) {
            return r;
        }
        Map<Long, BigDecimal> sorties = new HashMap<>();
        Map<Long, Boolean> inconnues = new HashMap<>();
        for (CuveRef c : referentiel.cuves(j.stationId())) {
            if (!sortiesParCuve.containsKey(c.id())) {
                continue;
            }
            BigDecimal v = sortiesParCuve.get(c.id());
            if (v == null) {
                inconnues.put(c.produitId(), true);
            } else {
                sorties.merge(c.produitId(), v, BigDecimal::add);
            }
        }
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        litres.forEach((produitId, l) -> {
            if (inconnues.containsKey(produitId)) {
                return;
            }
            BigDecimal sortie = sorties.getOrDefault(produitId, BigDecimal.ZERO);
            if (l.compareTo(sortie) > 0) {
                ProduitRef p = produits.get(produitId);
                r.add("Bons et cartes " + (p == null ? "" : p.libelle()) + " : " + l.stripTrailingZeros().toPlainString()
                        + " L servis à crédit ou par carte pour " + sortie.stripTrailingZeros().toPlainString()
                        + " L sortis des pompes. Vérifiez les bons ou les index.");
            }
        });
        return r;
    }

    private Map<Long, BigDecimal> litresParProduit(long journeeId) {
        Map<Long, BigDecimal> r = new HashMap<>();
        bons.litresParProduit(journeeId).forEach(o -> r.merge((Long) o[0], (BigDecimal) o[1], BigDecimal::add));
        pleins.litresParProduit(journeeId).forEach(o -> r.merge((Long) o[0], (BigDecimal) o[1], BigDecimal::add));
        return r;
    }

    // ---------------------------------------------------------------- Vue

    private CreditJourneeVue vue(JourneeRef j) {
        StationRef s = referentiel.station(j.stationId()).orElseThrow();
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        List<Bon> liste = bons.findByJourneeIdOrderByIdAsc(j.id());
        List<OperationCompte> recouvrements = operations.findByJourneeIdOrderByIdAsc(j.id());
        List<MouvementCarte> mouvements = pleins.findByJourneeIdOrderByIdAsc(j.id());
        Map<Long, Carte> parCarte = cartes.findAllById(mouvements.stream().map(MouvementCarte::getCarteId).toList()).stream()
                .collect(Collectors.toMap(Carte::getId, Function.identity()));
        List<Long> ids = new ArrayList<>(liste.stream().map(Bon::getClientId).toList());
        ids.addAll(recouvrements.stream().map(OperationCompte::getClientId).toList());
        ids.addAll(parCarte.values().stream().map(Carte::getClientId).toList());
        Map<Long, Client> parId = clients.findAllById(Set.copyOf(ids)).stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        List<BonVue> vuesBons = liste.stream().map(b -> {
            Client c = parId.get(b.getClientId());
            ProduitRef p = produits.get(b.getProduitId());
            return new BonVue(b.getUuid(), b.getNumero(), c.getUuid(), c.getCode(), c.getNom(), p.uuid(), p.code(), p.libelle(),
                    b.getLitres(), b.getPrixUnitaire(), b.getRemiseUnitaire(), b.getMontant(), b.getMontantPompe(),
                    b.getRecouvrement().name(), b.getImmatriculation(), b.getPorteur(), b.getStatut().name(), b.getMotifBlocage(),
                    b.getDerogationPar(), b.getDerogationMotif(), b.getFactureId() != null, b.getCreePar(), b.getModifieLe(),
                    b.getVersion());
        }).toList();
        List<PleinCarteVue> vuesCartes = mouvements.stream().map(m -> {
            Carte k = parCarte.get(m.getCarteId());
            Client c = parId.get(k.getClientId());
            ProduitRef p = produits.get(m.getProduitId());
            return new PleinCarteVue(m.getUuid(), k.getNumero(), k.getPorteur(), c.getCode(), c.getNom(), k.getImmatriculation(),
                    p.uuid(), p.code(), m.getLitres(), m.getPrixUnitaire(), m.getMontant(), m.getMontantPompe(), m.getReference(),
                    m.getCreePar());
        }).toList();
        List<RecouvrementVue> vuesRecouvrements = recouvrements.stream().map(o -> {
            Client c = parId.get(o.getClientId());
            return new RecouvrementVue(o.getUuid(), c.getUuid(), c.getCode(), c.getNom(), o.getMontant(), o.getReference(),
                    o.getObservation(), o.getCreePar());
        }).toList();
        List<ProduitCredit> vendus = produitsVendus(j.stationId()).values().stream()
                .map(p -> new ProduitCredit(p.uuid(), p.code(), p.libelle(), prix(j, p).orElse(null))).toList();
        BigDecimal direction = total(liste, Client.Recouvrement.DIRECTION);
        BigDecimal gerant = total(liste, Client.Recouvrement.GERANT);
        BigDecimal parCartes = mouvements.stream().map(MouvementCarte::getMontantPompe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recouvre = recouvrements.stream().map(OperationCompte::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        int enAttente = (int) liste.stream().filter(b -> b.getStatut() == Bon.Statut.EN_ATTENTE).count();
        List<String> anomalies = j.brouillon() && (!liste.isEmpty() || !mouvements.isEmpty())
                ? anomalies(j, exploitation.sortiesParCuve(j.id())) : List.of();
        return new CreditJourneeVue(j.uuid(), s.uuid(), s.code(), j.date(), j.statut(), j.version(),
                j.brouillon() && ClientService.aPermission("JOURNEE_SAISIR"), vendus, vuesBons, vuesCartes, vuesRecouvrements,
                direction, gerant, parCartes, recouvre, enAttente, anomalies);
    }

    /** Valeur a la pompe des bons d'un mode de recouvrement (ce qui sort de la recette). */
    private static BigDecimal total(List<Bon> liste, Client.Recouvrement r) {
        return liste.stream().filter(b -> b.getRecouvrement() == r).map(Bon::getMontantPompe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- Outils

    private Optional<BigDecimal> prix(JourneeRef j, ProduitRef p) {
        return tarification.prixEnVigueur(j.stationId(), p.id(), j.debut());
    }

    /** Produits distribues par un pistolet actif de la station, dans l'ordre des pistolets. */
    private Map<Long, ProduitRef> produitsVendus(long stationId) {
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        Map<Long, ProduitRef> r = new LinkedHashMap<>();
        for (PistoletRef p : referentiel.pistolets(stationId)) {
            if (p.actif() && produits.containsKey(p.produitId())) {
                r.putIfAbsent(p.produitId(), produits.get(p.produitId()));
            }
        }
        return r;
    }

    private ProduitRef produitVendu(JourneeRef j, UUID produitId) {
        if (produitId == null) {
            throw ErreurMetier.regle("PRODUIT_OBLIGATOIRE", "Choisissez le produit servi.");
        }
        return produitsVendus(j.stationId()).values().stream().filter(p -> p.uuid().equals(produitId)).findFirst()
                .orElseThrow(() -> ErreurMetier.regle("PRODUIT_NON_VENDU", "Ce produit n'est pas distribué par la station."));
    }

    private Bon bon(JourneeRef j, UUID bonUuid) {
        Bon b = bons.findByUuid(bonUuid).orElseThrow(() -> ErreurMetier.introuvable("Bon"));
        if (!b.getJourneeId().equals(j.id())) {
            throw ErreurMetier.introuvable("Bon");
        }
        return b;
    }

    private JourneeRef journeeAccessible(UUID uuid) {
        JourneeRef j = exploitation.journee(uuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j.stationId());
        return j;
    }

    private JourneeRef verrouiller(UUID uuid, Integer version) {
        journeeAccessible(uuid);
        return exploitation.verrouillerPourSaisie(uuid, version);
    }
}
