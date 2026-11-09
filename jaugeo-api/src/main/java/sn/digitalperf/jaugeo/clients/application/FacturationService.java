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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.application.VuesClients.BalanceAgee;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureDetail;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.LigneBalance;
import sn.digitalperf.jaugeo.clients.application.VuesClients.LigneFacture;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RelanceVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ResultatFacturation;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.ClientDepot;
import sn.digitalperf.jaugeo.clients.domaine.Facture;
import sn.digitalperf.jaugeo.clients.domaine.FactureDepot;
import sn.digitalperf.jaugeo.clients.domaine.Imputation;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Tranches;
import sn.digitalperf.jaugeo.clients.domaine.Relance;
import sn.digitalperf.jaugeo.clients.domaine.RelanceDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;

/**
 * Facturation periodique des bons (toutes stations, une facture par client), balance agee des creances et relances.
 * Les reglements s'imputent sur les dettes les plus anciennes ; une facture est echue quand sa date d'echeance est
 * passee et qu'il en reste une part non reglee.
 */
@Service
@Transactional(readOnly = true)
public class FacturationService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final FactureDepot factures;
    private final BonDepot bons;
    private final ClientDepot clients;
    private final RelanceDepot relances;
    private final ClientService comptes;
    private final ImputationClients imputation;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final DocumentsClients documents;
    private final Clock horloge;

    public FacturationService(FactureDepot factures, BonDepot bons, ClientDepot clients, RelanceDepot relances,
                              ClientService comptes, ImputationClients imputation, JourneesExploitation exploitation,
                              ReferentielConsultation referentiel, DocumentsClients documents, Clock horloge) {
        this.factures = factures;
        this.bons = bons;
        this.clients = clients;
        this.relances = relances;
        this.comptes = comptes;
        this.imputation = imputation;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.documents = documents;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Factures

    /** Factures du reseau, les plus recentes d'abord (filtre facultatif par date de facture). */
    public List<FactureVue> liste(LocalDate du, LocalDate au) {
        Map<Long, Client> parId = clients.findAll().stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<Long, List<Reste>> restes = imputation.restes(parId.values());
        return factures.findAll().stream()
                .filter(f -> (du == null || !f.getDateFacture().isBefore(du)) && (au == null || !f.getDateFacture().isAfter(au)))
                .sorted(Comparator.comparing(Facture::getDateFacture).thenComparing(Facture::getId).reversed())
                .map(f -> Mappeurs.facture(f, parId.get(f.getClientId()), restes.getOrDefault(f.getClientId(), List.of()),
                        (int) bons.countByFactureId(f.getId())))
                .toList();
    }

    public FactureDetail detail(UUID id) {
        Facture f = facture(id);
        Client c = clients.findById(f.getClientId()).orElseThrow();
        Map<Long, ProduitRef> produits = referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        List<Bon> liste = f.getStatut() == Facture.Statut.EMISE ? bons.findByFactureIdOrderByDateJourAscIdAsc(f.getId()) : List.of();
        List<LigneFacture> lignes = liste.stream().map(b -> new LigneFacture(b.getDateJour(), stations.get(b.getStationId()).code(),
                b.getNumero(), produits.get(b.getProduitId()).code(), b.getLitres(), b.getPrixUnitaire().add(b.getRemiseUnitaire()),
                b.getRemiseUnitaire(), b.getPrixUnitaire(), b.getMontant(), b.getImmatriculation())).toList();
        return new FactureDetail(Mappeurs.facture(f, c, imputation.restes(c), liste.size()), lignes);
    }

    /**
     * Facture les bons non factures de la periode, un document par client (ou pour un seul client). Les bons en
     * attente de derogation et ceux d'une journee encore en saisie attendent la facturation suivante.
     */
    @Transactional
    public ResultatFacturation facturer(LocalDate du, LocalDate au, LocalDate dateFacture, UUID clientId) {
        if (du == null || au == null) {
            throw ErreurMetier.regle("PERIODE_OBLIGATOIRE", "Indiquez la période à facturer.");
        }
        if (au.isBefore(du)) {
            throw ErreurMetier.regle("PERIODE_INVALIDE", "La fin de la période précède son début.");
        }
        LocalDate jour = aujourdhui();
        LocalDate date = dateFacture == null ? (au.isBefore(jour) ? jour : au) : dateFacture;
        if (date.isBefore(au)) {
            throw ErreurMetier.regle("DATE_FACTURE_INVALIDE", "La date de facture ne précède pas la fin de la période facturée.");
        }
        if (date.isAfter(jour)) {
            throw ErreurMetier.regle("DATE_FUTURE", "La facture ne peut pas être datée dans le futur.");
        }
        Long seul = clientId == null ? null : comptes.client(clientId).getId();
        Map<Long, JourneeRef> journees = new HashMap<>();
        Map<Long, List<Bon>> parClient = new LinkedHashMap<>();
        int enSaisie = 0;
        for (Bon b : bons.aFacturer(du, au)) {
            if (seul != null && !seul.equals(b.getClientId())) {
                continue;
            }
            JourneeRef j = journees.computeIfAbsent(b.getJourneeId(), id -> exploitation.journee(id).orElseThrow());
            if (j.brouillon()) {
                enSaisie++;
                continue;
            }
            parClient.computeIfAbsent(b.getClientId(), k -> new ArrayList<>()).add(b);
        }
        Map<Long, Client> fiches = clients.findAllById(parClient.keySet()).stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        List<Facture> emises = new ArrayList<>();
        int nb = 0;
        for (var e : parClient.entrySet()) {
            Client c = fiches.get(e.getKey());
            BigDecimal montant = e.getValue().stream().map(Bon::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
            String numero = "FA" + date.getYear() + "-" + String.format(Locale.ROOT, "%05d", factures.prochainNumero());
            Facture f = factures.saveAndFlush(new Facture(numero, c.getId(), du, au, date, c.getDelaiPaiement(), montant));
            e.getValue().forEach(b -> b.facturer(f.getId()));
            bons.saveAllAndFlush(e.getValue());
            emises.add(f);
            nb += e.getValue().size();
        }
        List<String> remarques = new ArrayList<>();
        if (enSaisie > 0) {
            remarques.add(enSaisie + " bon(s) d'une journée encore en saisie attendront la prochaine facturation.");
        }
        if (emises.isEmpty()) {
            remarques.add("Aucun bon à facturer sur la période.");
        }
        Map<Long, List<Reste>> restes = imputation.restes(fiches.values());
        List<FactureVue> vues = emises.stream().map(f -> Mappeurs.facture(f, fiches.get(f.getClientId()),
                restes.getOrDefault(f.getClientId(), List.of()), (int) bons.countByFactureId(f.getId())))
                .sorted(Comparator.comparing(FactureVue::clientCode)).toList();
        return new ResultatFacturation(vues, nb, remarques);
    }

    /** Annule une facture (erreur, contestation) : ses bons redeviennent a facturer. */
    @Transactional
    public FactureDetail annuler(UUID id, Integer version, String motif) {
        Facture f = facture(id);
        f.verifierVersion(version);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        f.annuler(motif, auth == null ? "?" : auth.getName(), horloge.instant());
        List<Bon> liste = bons.findByFactureIdOrderByDateJourAscIdAsc(f.getId());
        liste.forEach(Bon::liberer);
        bons.saveAllAndFlush(liste);
        factures.saveAndFlush(f);
        return detail(id);
    }

    public ResponseEntity<byte[]> pdf(UUID id) {
        FactureDetail d = detail(id);
        Client c = clients.findByUuid(d.facture().clientId()).orElseThrow();
        return fichier(documents.facture(d, c), "facture-" + d.facture().numero() + ".pdf");
    }

    // ---------------------------------------------------------------- Balance agee et relances

    public BalanceAgee balance(LocalDate au) {
        LocalDate date = au == null ? aujourdhui() : au;
        List<Client> tous = clients.findAllByOrderByCodeAsc();
        Map<Long, List<Reste>> restes = imputation.restes(tous);
        Map<Long, List<Relance>> parClient = relances.findAll().stream().collect(Collectors.groupingBy(Relance::getClientId));
        List<LigneBalance> lignes = new ArrayList<>();
        BigDecimal[] t = new BigDecimal[7];
        java.util.Arrays.fill(t, BigDecimal.ZERO);
        for (Client c : tous) {
            List<Reste> r = restes.getOrDefault(c.getId(), List.of());
            Tranches x = Imputation.tranches(r, date);
            if (x.total().signum() == 0) {
                continue;
            }
            Reste ancienne = Imputation.premiereEchue(r, date);
            List<Relance> rel = parClient.getOrDefault(c.getId(), List.of());
            Relance derniere = rel.stream().max(Comparator.comparing(Relance::getDateRelance).thenComparing(Relance::getId)).orElse(null);
            lignes.add(new LigneBalance(c.getUuid(), c.getCode(), c.getNom(), c.getRecouvrement().name(), c.getDelaiPaiement(),
                    c.getPlafond(), x.nonFacture(), x.jours0a30(), x.jours31a60(), x.jours61a90(), x.plusDe90(), x.total(), x.echu(),
                    ancienne == null ? null : ancienne.dette().echeance(), derniere == null ? null : derniere.getDateRelance(),
                    derniere == null ? null : derniere.getNiveau(), niveauSuggere(r, rel, date)));
            BigDecimal[] v = {x.nonFacture(), x.jours0a30(), x.jours31a60(), x.jours61a90(), x.plusDe90(), x.total(), x.echu()};
            for (int i = 0; i < 7; i++) {
                t[i] = t[i].add(v[i]);
            }
        }
        return new BalanceAgee(date, lignes, t[0], t[1], t[2], t[3], t[4], t[5], t[6]);
    }

    /**
     * Niveau de la prochaine relance : 1 + nombre de relances faites depuis l'echeance de la plus ancienne dette
     * echue non reglee (au plus 3) ; 0 sans montant echu.
     */
    static int niveauSuggere(List<Reste> restes, List<Relance> relancesClient, LocalDate date) {
        Reste ancienne = Imputation.premiereEchue(restes, date);
        if (ancienne == null) {
            return 0;
        }
        long faites = relancesClient.stream().filter(x -> x.getDateRelance().isAfter(ancienne.dette().echeance())).count();
        return (int) Math.min(3, faites + 1);
    }

    /** Enregistre une relance du client au niveau suggere, pour le montant echu a ce jour. */
    @Transactional
    public RelanceVue relancer(UUID clientId, String mode, String commentaire) {
        Client c = comptes.client(clientId);
        LocalDate jour = aujourdhui();
        List<Reste> r = imputation.restes(c);
        BigDecimal echu = Imputation.echu(r, jour);
        if (echu.signum() <= 0) {
            throw ErreurMetier.regle("RIEN_A_RELANCER", c.getNom() + " n'a aucune somme échue impayée.");
        }
        Relance.Mode m;
        try {
            m = Relance.Mode.valueOf((mode == null ? "COURRIER" : mode).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("MODE_INVALIDE", "Une relance se fait par courrier, e-mail, téléphone ou visite.");
        }
        int niveau = niveauSuggere(r, relances.findByClientIdOrderByDateRelanceDescIdDesc(c.getId()), jour);
        return Mappeurs.relance(relances.saveAndFlush(new Relance(c.getId(), jour, niveau, m, echu, commentaire)));
    }

    /** Lettre de relance d'une relance enregistree (sommes echues a la date de la relance, imputation actuelle). */
    public ResponseEntity<byte[]> lettre(UUID relanceId) {
        Relance x = relances.findByUuid(relanceId).orElseThrow(() -> ErreurMetier.introuvable("Relance"));
        Client c = clients.findById(x.getClientId()).orElseThrow();
        byte[] pdf = documents.relance(c, x.getDateRelance(), x.getNiveau(), x.getMontantEchu(), imputation.restes(c));
        return fichier(pdf, "relance-" + c.getCode() + "-" + x.getDateRelance() + ".pdf");
    }

    // ---------------------------------------------------------------- Outils

    private Facture facture(UUID id) {
        return factures.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Facture"));
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenu);
    }
}
