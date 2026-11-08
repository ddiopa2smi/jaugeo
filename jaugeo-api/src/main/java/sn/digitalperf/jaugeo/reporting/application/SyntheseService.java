package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.clients.ComptesClients;
import sn.digitalperf.jaugeo.clients.ComptesClients.CreanceClient;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.CommentaireVue;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.LigneStocks;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.LigneVentes;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.ProduitColonne;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseStocks;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseVentes;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.TotalProduit;
import sn.digitalperf.jaugeo.reporting.domaine.CommentaireMensuel;
import sn.digitalperf.jaugeo.reporting.domaine.CommentaireMensuelDepot;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.StocksMensuels;
import sn.digitalperf.jaugeo.stock.StocksMensuels.BilanCuve;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse;
import sn.digitalperf.jaugeo.tresorerie.SyntheseCaisse.MoisCaisse;

/**
 * Syntheses mensuelles des stations du perimetre, calculees a partir des journees cloturees : ventes et recettes
 * (feuille ANALYSE VENTES &amp; RECETTES) et stocks (feuille ANALYSE DES STOCKS).
 */
@Service
@Transactional(readOnly = true)
public class SyntheseService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final SyntheseCaisse caisse;
    private final ComptesClients clients;
    private final StocksMensuels stocks;
    private final JourneesExploitation exploitation;
    private final ReferentielConsultation referentiel;
    private final CommentaireMensuelDepot commentaires;
    private final PerimetreCourant perimetre;
    private final ExportSyntheses exports;

    public SyntheseService(SyntheseCaisse caisse, ComptesClients clients, StocksMensuels stocks,
                           JourneesExploitation exploitation, ReferentielConsultation referentiel,
                           CommentaireMensuelDepot commentaires, PerimetreCourant perimetre,
                           ExportSyntheses exports) {
        this.caisse = caisse;
        this.clients = clients;
        this.stocks = stocks;
        this.exploitation = exploitation;
        this.referentiel = referentiel;
        this.commentaires = commentaires;
        this.perimetre = perimetre;
        this.exports = exports;
    }

    // ---------------------------------------------------------------- Ventes et recettes

    public SyntheseVentes ventes(YearMonth mois) {
        LocalDate du = mois.atDay(1);
        LocalDate au = mois.atEndOfMonth();
        Map<Long, ProduitRef> produits = referentiel.produits().stream()
                .collect(Collectors.toMap(ProduitRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, CommentaireMensuel> notes = commentaires.findByMois(du).stream()
                .collect(Collectors.toMap(CommentaireMensuel::getStationId, Function.identity()));
        List<LigneVentes> lignes = new ArrayList<>();
        Set<Long> vendus = new java.util.HashSet<>();
        for (StationRef s : stations(au)) {
            Map<Long, BigDecimal> litres = exploitation.litresVendus(s.id(), du, au);
            vendus.addAll(litres.keySet());
            lignes.add(ligne(s, mois, litres, produits, notes.get(s.id())));
        }
        List<ProduitColonne> colonnes = produits.values().stream().filter(p -> vendus.contains(p.id()))
                .map(p -> new ProduitColonne(p.code(), p.libelle())).toList();
        return new SyntheseVentes(mois, colonnes, lignes, cumul(lignes), aPermission("CLOTURE_MENSUELLE"));
    }

    private LigneVentes ligne(StationRef s, YearMonth mois, Map<Long, BigDecimal> litres, Map<Long, ProduitRef> produits,
                              CommentaireMensuel note) {
        MoisCaisse m = caisse.mois(s.id(), mois);
        List<CreanceClient> creances = clients.creances(s.id(), mois.atDay(1), mois.atEndOfMonth());
        BigDecimal g = somme(creances, CreanceClient::debut);
        BigDecimal k = somme(creances, CreanceClient::fin);
        BigDecimal o = somme(creances, CreanceClient::reglementsSiege);
        BigDecimal gerantFin = somme(creances.stream().filter(c -> "GERANT".equals(c.recouvrement())).toList(), CreanceClient::fin);
        BigDecimal h = m.ventes().add(m.soldeDebut()).add(g);
        BigDecimal q = m.versements().add(m.soldeFin()).add(k).add(m.depenses()).add(m.complements()).add(o);
        BigDecimal r = q.subtract(h);
        Map<String, BigDecimal> parProduit = new LinkedHashMap<>();
        produits.values().forEach(p -> {
            if (litres.containsKey(p.id())) {
                parProduit.put(p.code(), litres.get(p.id()));
            }
        });
        return new LigneVentes(s.uuid(), s.code(), s.nom(), parProduit, m.ventes(), m.soldeDebut(), g, h, m.versements(),
                m.soldeFin(), k, m.depenses(), m.complements(), o, q, r, m.bonsDirection(), m.bonsGerant(), m.recouvrements(),
                gerantFin, m.ecarts(), m.joursNonClotures(), observation(r, m, gerantFin),
                note == null ? null : note.getTexte(), note == null ? null : note.getVersion());
    }

    /** Observation calculee : journees ouvertes, ecart sur versement, creances laissees au gerant. */
    static String observation(BigDecimal ecart, MoisCaisse m, BigDecimal creancesGerant) {
        List<String> r = new ArrayList<>();
        if (m.joursNonClotures() > 0) {
            r.add(m.joursNonClotures() + (m.joursNonClotures() > 1 ? " journées non clôturées" : " journée non clôturée")
                    + " : synthèse provisoire.");
        }
        if (ecart.signum() != 0) {
            r.add("Écart sur versement de " + (ecart.signum() > 0 ? "+" : "−") + Libelles.francs(ecart.abs())
                    + (ecart.signum() > 0 ? " (surplus)." : " (manquant)."));
            if (m.joursNonClotures() == 0 && ecart.compareTo(m.ecarts()) != 0) {
                r.add("Écart non expliqué par les arrêtés de caisse (" + Libelles.francs(m.ecarts()) + ") : reprise de caisse à vérifier.");
            }
        }
        if (creancesGerant.signum() > 0) {
            r.add("Créances à recouvrer par le gérant : " + Libelles.francs(creancesGerant) + ".");
        }
        return r.isEmpty() ? null : String.join(" ", r);
    }

    private static LigneVentes cumul(List<LigneVentes> lignes) {
        Map<String, BigDecimal> litres = new LinkedHashMap<>();
        lignes.forEach(l -> l.litres().forEach((p, v) -> litres.merge(p, v, BigDecimal::add)));
        return new LigneVentes(null, "CUMUL", "Cumul du réseau", litres, s(lignes, LigneVentes::recettes),
                s(lignes, LigneVentes::soldeOuverture), s(lignes, LigneVentes::creancesDebut), s(lignes, LigneVentes::aVerser),
                s(lignes, LigneVentes::versements), s(lignes, LigneVentes::soldeCaisse), s(lignes, LigneVentes::creancesFin),
                s(lignes, LigneVentes::depenses), s(lignes, LigneVentes::complements), s(lignes, LigneVentes::reglementsSiege),
                s(lignes, LigneVentes::totalVersements), s(lignes, LigneVentes::ecart), s(lignes, LigneVentes::bonsDirection),
                s(lignes, LigneVentes::bonsGerant), s(lignes, LigneVentes::recouvrements), s(lignes, LigneVentes::creancesGerantFin),
                s(lignes, LigneVentes::ecartsArretes), lignes.stream().mapToInt(LigneVentes::joursNonClotures).sum(),
                null, null, null);
    }

    private static <T> BigDecimal somme(List<T> l, Function<T, BigDecimal> f) {
        return l.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal s(List<LigneVentes> l, Function<LigneVentes, BigDecimal> f) {
        return somme(l, f);
    }

    // ---------------------------------------------------------------- Stocks

    public SyntheseStocks stocks(YearMonth mois) {
        List<LigneStocks> lignes = new ArrayList<>();
        for (StationRef s : stations(mois.atEndOfMonth())) {
            for (BilanCuve b : stocks.bilans(s.id(), mois)) {
                lignes.add(new LigneStocks(s.uuid(), s.code(), s.nom(), b.cuveCode(), b.produitCode(), b.produitLibelle(),
                        b.stockDepart(), b.receptions(), b.sorties(), b.stockTheorique(), b.stockPhysique(), b.ecartLitres(),
                        b.ecartPct(), b.ecartValeur(), b.tolerancePct(), b.niveau(), b.jours()));
            }
        }
        Map<String, List<LigneStocks>> parProduit = lignes.stream()
                .collect(Collectors.groupingBy(LigneStocks::produitCode, LinkedHashMap::new, Collectors.toList()));
        List<TotalProduit> totaux = new ArrayList<>();
        parProduit.forEach((code, l) -> {
            BigDecimal sorties = somme(l, LigneStocks::sorties);
            BigDecimal ecart = somme(l, LigneStocks::ecartLitres);
            BigDecimal valeur = l.stream().map(LigneStocks::ecartValeur).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pct = sorties.signum() == 0 ? null : ecart.multiply(CENT).divide(sorties, 2, RoundingMode.HALF_UP);
            totaux.add(new TotalProduit(code, l.get(0).produitLibelle(), somme(l, LigneStocks::receptions), sorties, ecart, pct, valeur));
        });
        return new SyntheseStocks(mois, lignes, totaux);
    }

    // ---------------------------------------------------------------- Observations du controle de gestion

    /** Redige (ou efface, texte vide) l'observation d'une station pour un mois. */
    @Transactional
    public CommentaireVue commenter(java.util.UUID stationUuid, YearMonth mois, String texte, Integer version) {
        StationRef s = referentiel.station(stationUuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        boolean vide = texte == null || texte.isBlank();
        var existant = commentaires.findByStationIdAndMois(s.id(), mois.atDay(1));
        if (existant.isPresent()) {
            CommentaireMensuel c = existant.get();
            c.verifierVersion(version);
            if (vide) {
                commentaires.delete(c);
                commentaires.flush();
                return new CommentaireVue(s.uuid(), mois, null, null);
            }
            c.rediger(texte);
            commentaires.saveAndFlush(c);
            return new CommentaireVue(s.uuid(), mois, c.getTexte(), c.getVersion());
        }
        if (vide) {
            return new CommentaireVue(s.uuid(), mois, null, null);
        }
        CommentaireMensuel c = new CommentaireMensuel(s.id(), mois);
        c.rediger(texte);
        commentaires.saveAndFlush(c);
        return new CommentaireVue(s.uuid(), mois, c.getTexte(), c.getVersion());
    }

    // ---------------------------------------------------------------- Exports

    public ResponseEntity<byte[]> excel(YearMonth mois) {
        return fichier(exports.excel(ventes(mois), stocks(mois)), "analyse-" + mois + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    public ResponseEntity<byte[]> pdf(YearMonth mois) {
        return fichier(exports.pdf(ventes(mois), stocks(mois)), "analyse-" + mois + ".pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .contentType(MediaType.parseMediaType(type))
                .body(contenu);
    }

    // ---------------------------------------------------------------- Outils

    /** Stations du perimetre ouvertes a la fin du mois (actives, ou ayant de l'activite enregistree), par code. */
    private List<StationRef> stations(LocalDate finMois) {
        Set<Long> autorisees = perimetre.estReseau() ? null : perimetre.stationIds();
        return referentiel.stations().stream()
                .filter(s -> autorisees == null || autorisees.contains(s.id()))
                .filter(StationRef::actif)
                .filter(s -> s.dateOuverture() == null || !s.dateOuverture().isAfter(finMois))
                .sorted(java.util.Comparator.comparing(StationRef::code))
                .toList();
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
