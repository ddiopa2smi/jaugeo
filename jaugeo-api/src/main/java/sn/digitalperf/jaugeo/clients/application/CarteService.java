package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CarteDetail;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CarteVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.MouvementCarteVue;
import sn.digitalperf.jaugeo.clients.domaine.Carte;
import sn.digitalperf.jaugeo.clients.domaine.CarteDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.MouvementCarte;
import sn.digitalperf.jaugeo.clients.domaine.MouvementCarteDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;

/**
 * Cartes carburant prepayees : emission (numero unique et QR code), recharges encaissees par le siege, blocage ou
 * annulation, consultation du solde par numero (scan du QR code a la station).
 */
@Service
@Transactional(readOnly = true)
public class CarteService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final CarteDepot cartes;
    private final MouvementCarteDepot mouvements;
    private final ClientDepot clients;
    private final ClientService comptes;
    private final ReferentielConsultation referentiel;
    private final Clock horloge;

    public CarteService(CarteDepot cartes, MouvementCarteDepot mouvements, ClientDepot clients, ClientService comptes,
                        ReferentielConsultation referentiel, Clock horloge) {
        this.cartes = cartes;
        this.mouvements = mouvements;
        this.clients = clients;
        this.comptes = comptes;
        this.referentiel = referentiel;
        this.horloge = horloge;
    }

    public List<CarteVue> liste() {
        Map<Long, Client> parId = clients.findAll().stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        return cartes.findAll().stream().map(k -> Mappeurs.carte(k, parId))
                .sorted(java.util.Comparator.comparing(CarteVue::clientCode).thenComparing(CarteVue::numero)).toList();
    }

    public CarteDetail detail(UUID id) {
        return detail(cartes.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Carte")));
    }

    /** Carte lue par son numero ou par le contenu de son QR code. */
    public CarteDetail parNumero(String numero) {
        String n = Carte.normaliser(numero);
        if (n == null) {
            throw ErreurMetier.regle("CARTE_NUMERO_INVALIDE", "Numéro de carte invalide : vérifiez la saisie ou scannez le QR code.");
        }
        return detail(cartes.findByNumero(n).orElseThrow(() -> ErreurMetier.introuvable("Carte " + n)));
    }

    @Transactional
    public CarteDetail emettre(UUID clientId, String porteur, String immatriculation) {
        Client c = comptes.client(clientId);
        if (!c.isActif()) {
            throw ErreurMetier.regle("CLIENT_INACTIF", "Le client " + c.getNom() + " n'est plus actif.");
        }
        String numero;
        do {
            numero = Carte.nouveauNumero();
        } while (cartes.existsByNumero(numero));
        Carte k = new Carte(numero, c.getId());
        k.modifier(porteur, immatriculation);
        return detail(cartes.saveAndFlush(k));
    }

    @Transactional
    public CarteDetail modifier(UUID id, Integer version, String porteur, String immatriculation, String statut) {
        Carte k = cartes.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Carte"));
        k.verifierVersion(version);
        k.modifier(porteur, immatriculation);
        if (statut != null && !statut.equalsIgnoreCase(k.getStatut().name())) {
            try {
                k.changerStatut(Carte.Statut.valueOf(statut.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw ErreurMetier.regle("STATUT_INVALIDE", "Une carte est active, bloquée ou annulée.");
            }
        }
        return detail(cartes.saveAndFlush(k));
    }

    @Transactional
    public CarteDetail recharger(UUID id, Integer version, LocalDate date, String mode, BigDecimal montant, String reference) {
        Carte k = cartes.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Carte"));
        k.verifierVersion(version);
        LocalDate jour = date == null ? LocalDate.ofInstant(horloge.instant(), DAKAR) : date;
        if (jour.isAfter(LocalDate.ofInstant(horloge.instant(), DAKAR))) {
            throw ErreurMetier.regle("DATE_FUTURE", "La recharge ne peut pas être datée dans le futur.");
        }
        MouvementCarte.Mode m;
        try {
            m = mode == null ? null : MouvementCarte.Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("MODE_INVALIDE", "Mode de paiement de la recharge inconnu.");
        }
        MouvementCarte r = MouvementCarte.recharge(k.getId(), jour, m, montant, reference);
        k.crediter(r.getMontant());
        cartes.saveAndFlush(k);
        mouvements.saveAndFlush(r);
        return detail(k);
    }

    private CarteDetail detail(Carte k) {
        Client c = clients.findById(k.getClientId()).orElseThrow();
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        List<MouvementCarteVue> liste = mouvements.findByCarteIdOrderByDateMouvementDescIdDesc(k.getId()).stream().map(m -> {
            StationRef s = m.getStationId() == null ? null : stations.get(m.getStationId());
            ProduitRef p = m.getProduitId() == null ? null : produits.get(m.getProduitId());
            return new MouvementCarteVue(m.getUuid(), m.getType().name(), m.getDateMouvement(), m.getMontant(),
                    m.getMode() == null ? null : m.getMode().name(), m.getReference(), s == null ? null : s.code(),
                    p == null ? null : p.code(), m.getLitres(), m.getPrixUnitaire(), m.getCreePar());
        }).toList();
        return new CarteDetail(Mappeurs.carte(k, Map.of(c.getId(), c)), liste, ClientService.aPermission("CLIENT_GERER"));
    }
}
