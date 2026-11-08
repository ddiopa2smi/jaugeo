package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.application.VuesClients.BonVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CreditJourneeVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ProduitCredit;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RecouvrementVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieBon;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
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
 * Ventes a credit d'une journee (feuille station : BONS A RECOUVRER PAR LA DIRECTION / PAR LE GERANT) et recouvrements
 * de creances encaisses en especes par le gerant. Un bon exige un client actif sous plafond, un produit vendu a la
 * station et un numero jamais utilise ; il est valorise au prix en vigueur a l'ouverture de la journee.
 */
@Service
@Transactional(readOnly = true)
public class CreditJourneeService {

    private final BonDepot bons;
    private final OperationCompteDepot operations;
    private final ClientDepot clients;
    private final ClientService comptes;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final Tarification tarification;
    private final PerimetreCourant perimetre;

    public CreditJourneeService(BonDepot bons, OperationCompteDepot operations, ClientDepot clients, ClientService comptes,
                                JourneesExploitation exploitation, ReferentielConsultation referentiel,
                                Tarification tarification, PerimetreCourant perimetre) {
        this.bons = bons;
        this.operations = operations;
        this.clients = clients;
        this.comptes = comptes;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.tarification = tarification;
        this.perimetre = perimetre;
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
        b.renseigner(c, numero, p.id(), s.litres(), prix, s.immatriculation(), s.porteur());
        comptes.exigerPlafond(c, b.getMontant(), b.getId());
        bons.saveAndFlush(b);
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

    /** Les litres servis sur bons d'un produit ne depassent pas les sorties nettes de ce produit dans la journee. */
    List<String> anomalies(JourneeRef j, Map<Long, BigDecimal> sortiesParCuve) {
        Map<Long, BigDecimal> litres = litresParProduit(j.id());
        if (litres.isEmpty()) {
            return List.of();
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
        List<String> r = new ArrayList<>();
        litres.forEach((produitId, l) -> {
            if (inconnues.containsKey(produitId)) {
                return;
            }
            BigDecimal sortie = sorties.getOrDefault(produitId, BigDecimal.ZERO);
            if (l.compareTo(sortie) > 0) {
                ProduitRef p = produits.get(produitId);
                r.add("Bons " + (p == null ? "" : p.libelle()) + " : " + l.stripTrailingZeros().toPlainString()
                        + " L servis à crédit pour " + sortie.stripTrailingZeros().toPlainString()
                        + " L sortis des pompes. Vérifiez les bons ou les index.");
            }
        });
        return r;
    }

    private Map<Long, BigDecimal> litresParProduit(long journeeId) {
        return bons.litresParProduit(journeeId).stream()
                .collect(Collectors.toMap(o -> (Long) o[0], o -> (BigDecimal) o[1]));
    }

    // ---------------------------------------------------------------- Vue

    private CreditJourneeVue vue(JourneeRef j) {
        StationRef s = referentiel.station(j.stationId()).orElseThrow();
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        List<Bon> liste = bons.findByJourneeIdOrderByIdAsc(j.id());
        List<OperationCompte> recouvrements = operations.findByJourneeIdOrderByIdAsc(j.id());
        List<Long> ids = new ArrayList<>(liste.stream().map(Bon::getClientId).toList());
        ids.addAll(recouvrements.stream().map(OperationCompte::getClientId).toList());
        Map<Long, Client> parId = clients.findAllById(ids).stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        List<BonVue> vuesBons = liste.stream().map(b -> {
            Client c = parId.get(b.getClientId());
            ProduitRef p = produits.get(b.getProduitId());
            return new BonVue(b.getUuid(), b.getNumero(), c.getUuid(), c.getCode(), c.getNom(), p.uuid(), p.code(), p.libelle(),
                    b.getLitres(), b.getPrixUnitaire(), b.getMontant(), b.getRecouvrement().name(), b.getImmatriculation(),
                    b.getPorteur(), b.getCreePar(), b.getModifieLe());
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
        BigDecimal recouvre = recouvrements.stream().map(OperationCompte::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> anomalies = j.brouillon() && !liste.isEmpty() ? anomalies(j, exploitation.sortiesParCuve(j.id())) : List.of();
        return new CreditJourneeVue(j.uuid(), s.uuid(), s.code(), j.date(), j.statut(), j.version(),
                j.brouillon() && ClientService.aPermission("JOURNEE_SAISIR"), vendus, vuesBons, vuesRecouvrements, direction, gerant,
                recouvre, anomalies);
    }

    private static BigDecimal total(List<Bon> liste, Client.Recouvrement r) {
        return liste.stream().filter(b -> b.getRecouvrement() == r).map(Bon::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
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
