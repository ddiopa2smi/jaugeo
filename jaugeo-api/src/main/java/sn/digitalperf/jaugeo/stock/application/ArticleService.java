package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.ArticleVue;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.EntreeVue;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.MouvementVue;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.MouvementsArticles;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieComptage;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieEntree;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieVente;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.StockArticles;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.VenteVue;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.VentesJournee;
import sn.digitalperf.jaugeo.stock.domaine.ArticleStation;
import sn.digitalperf.jaugeo.stock.domaine.ArticleStationDepot;
import sn.digitalperf.jaugeo.stock.domaine.CalculArticle;
import sn.digitalperf.jaugeo.stock.domaine.ComptageArticle;
import sn.digitalperf.jaugeo.stock.domaine.ComptageArticleDepot;
import sn.digitalperf.jaugeo.stock.domaine.EntreeArticle;
import sn.digitalperf.jaugeo.stock.domaine.EntreeArticleDepot;
import sn.digitalperf.jaugeo.stock.domaine.MouvementArticle;
import sn.digitalperf.jaugeo.stock.domaine.MouvementArticleDepot;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticle;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticleDepot;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Lubrifiants et articles boutique (M4) : produits non suivis en cuve, stockes par station et valorises au CMUP.
 * La logistique annonce les entrees (cout d'achat), le gerant les confirme a reception ; les ventes sont saisies
 * dans la journee et sortent du stock a la cloture ; un inventaire tournant fait compter chaque semaine une partie
 * des articles, chacun au moins une fois par mois.
 */
@Service
@Transactional(readOnly = true)
public class ArticleService implements sn.digitalperf.jaugeo.stock.ArticlesMensuels {

    static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    /** Inventaire tournant : un quart des articles par semaine, chacun au moins tous les 30 jours. */
    static final int ROTATION_SEMAINES = 4;
    static final int DELAI_MAX_JOURS = 30;
    static final int DELAI_MIN_JOURS = 7;

    private final ReferentielConsultation referentiel;
    private final JourneesExploitation exploitation;
    private final PerimetreCourant perimetre;
    private final Tarification tarification;
    private final ArticleStationDepot articles;
    private final EntreeArticleDepot entrees;
    private final VenteArticleDepot ventes;
    private final ComptageArticleDepot comptages;
    private final MouvementArticleDepot mouvements;
    private final Clock horloge;

    public ArticleService(ReferentielConsultation referentiel, JourneesExploitation exploitation, PerimetreCourant perimetre,
                          Tarification tarification, ArticleStationDepot articles, EntreeArticleDepot entrees,
                          VenteArticleDepot ventes, ComptageArticleDepot comptages, MouvementArticleDepot mouvements,
                          Clock horloge) {
        this.referentiel = referentiel;
        this.exploitation = exploitation;
        this.perimetre = perimetre;
        this.tarification = tarification;
        this.articles = articles;
        this.entrees = entrees;
        this.ventes = ventes;
        this.comptages = comptages;
        this.mouvements = mouvements;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Stock d'une station

    public StockArticles stock(UUID stationUuid) {
        return stock(stationAccessible(stationUuid));
    }

    private StockArticles stock(StationRef s) {
        LocalDate aujourdhui = aujourdhui();
        Map<Long, ArticleStation> stocks = articles.findByStationId(s.id()).stream()
                .collect(Collectors.toMap(ArticleStation::getProduitId, Function.identity()));
        Map<Long, BigDecimal> enCours = ventesEnCours(s.id(), null);
        Set<Long> aCompter = aCompter(stocks.values(), aujourdhui);
        List<ArticleVue> vues = new ArrayList<>();
        BigDecimal valeur = BigDecimal.ZERO;
        for (ProduitRef p : produitsArticles()) {
            ArticleStation a = stocks.get(p.id());
            if (a == null && !p.actif()) {
                continue;
            }
            BigDecimal q = a == null ? BigDecimal.ZERO : a.getQuantite();
            BigDecimal cmup = a == null ? BigDecimal.ZERO : a.getCmup();
            BigDecimal reserve = enCours.getOrDefault(p.id(), BigDecimal.ZERO);
            BigDecimal v = CalculArticle.valeur(q, cmup);
            valeur = valeur.add(v);
            LocalDate dernier = a == null ? null : a.getDernierComptage();
            vues.add(new ArticleVue(p.uuid(), p.code(), p.libelle(), p.unite(), q, reserve, q.subtract(reserve).max(BigDecimal.ZERO),
                    cmup, v, prixDuJour(s.id(), p.id()), dernier, a != null && aCompter.contains(a.getId()),
                    a != null && enRetard(dernier, aujourdhui)));
        }
        Map<Long, ProduitRef> produits = produitsParId();
        List<EntreeVue> annoncees = entrees.findByStationIdAndStatutOrderByIdAsc(s.id(), EntreeArticle.Statut.ANNONCEE).stream()
                .map(e -> vue(e, produits.get(e.getProduitId()))).toList();
        return new StockArticles(s.uuid(), s.code(), s.nom(), vues, valeur, aCompter.size(), annoncees,
                aPermission("RECEPTION_VALIDER"), aPermission("JOURNEE_SAISIR"),
                aPermission("JOURNEE_SAISIR") || aPermission("JOURNEE_VALIDER"));
    }

    /**
     * Inventaire tournant : articles jamais comptes ou comptes il y a plus de 30 jours, completes par les plus
     * anciennement comptes jusqu'au quart des articles ; un article compte depuis moins de 7 jours n'est pas repris.
     */
    static Set<Long> aCompter(java.util.Collection<ArticleStation> stocks, LocalDate aujourdhui) {
        int quota = (stocks.size() + ROTATION_SEMAINES - 1) / ROTATION_SEMAINES;
        Set<Long> choix = new HashSet<>();
        List<ArticleStation> tries = stocks.stream()
                .filter(a -> a.getDernierComptage() == null || a.getDernierComptage().isBefore(aujourdhui.minusDays(DELAI_MIN_JOURS - 1)))
                .sorted(Comparator.comparing(ArticleStation::getDernierComptage, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ArticleStation::getId))
                .toList();
        for (ArticleStation a : tries) {
            if (enRetard(a.getDernierComptage(), aujourdhui) || choix.size() < quota) {
                choix.add(a.getId());
            }
        }
        return choix;
    }

    static boolean enRetard(LocalDate dernier, LocalDate aujourdhui) {
        return dernier == null || dernier.isBefore(aujourdhui.minusDays(DELAI_MAX_JOURS));
    }

    // ---------------------------------------------------------------- Entrees

    @Transactional
    public EntreeVue annoncerEntree(UUID stationUuid, SaisieEntree e) {
        StationRef s = stationAccessible(stationUuid);
        ProduitRef p = article(e.produitId());
        if (!p.actif()) {
            throw ErreurMetier.regle("ARTICLE_INACTIF", p.libelle() + " n'est plus vendu.");
        }
        exigerQuantite(p, e.quantite(), "La quantité");
        EntreeArticle n = entrees.save(new EntreeArticle(s.id(), p.id(), e.quantite(), e.coutUnitaire(), e.fournisseur(), e.reference()));
        return vue(n, p);
    }

    @Transactional
    public EntreeVue recevoirEntree(UUID entreeUuid, Integer version, BigDecimal recue, String observation) {
        EntreeArticle e = entree(entreeUuid, version);
        ProduitRef p = produitsParId().get(e.getProduitId());
        exigerQuantite(p, recue, "La quantité reçue");
        LocalDate le = aujourdhui();
        e.recevoir(recue, le, perimetre.identifiant(), observation);
        entrees.saveAndFlush(e);
        ArticleStation a = verrouiller(e.getStationId(), e.getProduitId());
        a.appliquer(CalculArticle.entree(a.etat(), recue, e.getCoutUnitaire()));
        articles.save(a);
        mouvements.save(new MouvementArticle(a, le, MouvementArticle.Type.ENTREE, recue, e.getCoutUnitaire(),
                "Entrée " + e.getReference() + " (" + e.getFournisseur() + ")").entree(e.getId()));
        return vue(e, p);
    }

    @Transactional
    public EntreeVue annulerEntree(UUID entreeUuid, Integer version, String motif) {
        EntreeArticle e = entree(entreeUuid, version);
        e.annuler(motif);
        entrees.saveAndFlush(e);
        return vue(e, produitsParId().get(e.getProduitId()));
    }

    private EntreeArticle entree(UUID uuid, Integer version) {
        EntreeArticle e = entrees.findByUuid(uuid).orElseThrow(() -> ErreurMetier.introuvable("Entrée"));
        perimetre.exigerStation(e.getStationId());
        e.verifierVersion(version);
        return e;
    }

    // ---------------------------------------------------------------- Inventaire tournant

    /**
     * Comptage d'articles : le stock theorique est celui des journees cloturees moins les ventes deja saisies dans
     * une journee en cours (sorties physiquement) ; le stock devient la quantite comptee, ventes en cours comprises.
     */
    @Transactional
    public StockArticles compter(UUID stationUuid, List<SaisieComptage> saisies) {
        StationRef s = stationAccessible(stationUuid);
        LocalDate le = aujourdhui();
        Map<UUID, ProduitRef> produits = produitsArticles().stream().collect(Collectors.toMap(ProduitRef::uuid, Function.identity()));
        Map<Long, BigDecimal> enCours = ventesEnCours(s.id(), null);
        Set<UUID> vus = new HashSet<>();
        for (SaisieComptage c : saisies) {
            ProduitRef p = produits.get(c.produitId());
            if (p == null) {
                throw ErreurMetier.regle("ARTICLE_INCONNU", "Cet article n'est pas un lubrifiant ou un article boutique.");
            }
            if (!vus.add(p.uuid())) {
                throw ErreurMetier.regle("ARTICLE_EN_DOUBLE", p.libelle() + " est compté deux fois.");
            }
            if (c.compte().signum() < 0) {
                throw ErreurMetier.regle("QUANTITE_INVALIDE", p.libelle() + " : la quantité comptée ne peut pas être négative.");
            }
            exigerEntier(p, c.compte(), "La quantité comptée");
            ArticleStation a = verrouiller(s.id(), p.id());
            BigDecimal reserve = enCours.getOrDefault(p.id(), BigDecimal.ZERO);
            CalculArticle.Etat attendu = new CalculArticle.Etat(a.getQuantite().subtract(reserve), a.getCmup());
            ComptageArticle k = comptages.save(new ComptageArticle(s.id(), p.id(), le, attendu, c.compte(), c.observation(), p.libelle()));
            a.appliquer(CalculArticle.inventaire(a.etat(), c.compte().add(reserve)));
            a.compte(le);
            articles.save(a);
            if (k.getEcart().signum() != 0) {
                mouvements.save(new MouvementArticle(a, le, MouvementArticle.Type.INVENTAIRE, k.getEcart(), a.getCmup(),
                        "Inventaire : " + k.getObservation()).comptage(k.getId()));
            }
        }
        articles.flush();
        return stock(s);
    }

    // ---------------------------------------------------------------- Mouvements

    public MouvementsArticles mouvements(UUID stationUuid, YearMonth mois) {
        StationRef s = stationAccessible(stationUuid);
        Map<Long, ProduitRef> produits = produitsParId();
        List<MouvementVue> vues = new ArrayList<>();
        BigDecimal entreesV = BigDecimal.ZERO;
        BigDecimal ventesV = BigDecimal.ZERO;
        BigDecimal ecartsV = BigDecimal.ZERO;
        for (MouvementArticle m : mouvements.findByStationIdAndDateJourBetweenOrderByIdDesc(s.id(), mois.atDay(1), mois.atEndOfMonth())) {
            ProduitRef p = produits.get(m.getProduitId());
            vues.add(new MouvementVue(m.getDateJour(), p.code(), p.libelle(), m.getType().name(), m.getQuantite(),
                    m.getCoutUnitaire(), m.getValeur(), m.getStockApres(), m.getCmupApres(), m.getLibelle()));
            switch (m.getType()) {
                case ENTREE -> entreesV = entreesV.add(m.getValeur());
                case VENTE -> ventesV = ventesV.add(m.getValeur().negate());
                case INVENTAIRE -> ecartsV = ecartsV.add(m.getValeur());
            }
        }
        return new MouvementsArticles(s.uuid(), s.code(), mois, vues, entreesV, ventesV, ecartsV);
    }

    // ---------------------------------------------------------------- Ventes de la journee

    public VentesJournee ventesJournee(UUID journeeUuid) {
        JourneeRef j = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j.stationId());
        return vue(j);
    }

    /** Remplace les ventes d'articles de la journee ; une quantite nulle ou absente retire la ligne. */
    @Transactional
    public VentesJournee enregistrerVentes(UUID journeeUuid, Integer version, List<SaisieVente> saisies) {
        JourneeRef j0 = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j0.stationId());
        JourneeRef j = exploitation.verrouillerPourSaisie(journeeUuid, version);
        Map<UUID, ProduitRef> produits = produitsArticles().stream().collect(Collectors.toMap(ProduitRef::uuid, Function.identity()));
        Map<Long, VenteArticle> existantes = ventes.findByJourneeIdOrderByIdAsc(j.id()).stream()
                .collect(Collectors.toMap(VenteArticle::getProduitId, Function.identity()));
        Map<Long, BigDecimal> autres = ventesEnCours(j.stationId(), j.id());
        Map<Long, ArticleStation> stocks = articles.findByStationId(j.stationId()).stream()
                .collect(Collectors.toMap(ArticleStation::getProduitId, Function.identity()));
        Set<UUID> vus = new HashSet<>();
        for (SaisieVente s : saisies) {
            ProduitRef p = produits.get(s.produitId());
            if (p == null) {
                throw ErreurMetier.regle("ARTICLE_INCONNU", "Cet article n'est pas un lubrifiant ou un article boutique.");
            }
            if (!vus.add(p.uuid())) {
                throw ErreurMetier.regle("ARTICLE_EN_DOUBLE", p.libelle() + " figure deux fois.");
            }
            VenteArticle v = existantes.get(p.id());
            if (s.quantite() == null || s.quantite().signum() == 0) {
                if (v != null) {
                    ventes.delete(v);
                }
                continue;
            }
            exigerQuantite(p, s.quantite(), p.libelle() + " : la quantité vendue");
            ArticleStation a = stocks.get(p.id());
            BigDecimal disponible = (a == null ? BigDecimal.ZERO : a.getQuantite()).subtract(autres.getOrDefault(p.id(), BigDecimal.ZERO));
            if (s.quantite().compareTo(disponible) > 0) {
                throw ErreurMetier.regle("STOCK_ARTICLE_INSUFFISANT", p.libelle() + " : " + quantite(s.quantite())
                        + " vendu(s) pour " + quantite(disponible.max(BigDecimal.ZERO))
                        + " en stock. Faites confirmer la dernière livraison ou un comptage.");
            }
            BigDecimal prix = tarification.prixEnVigueur(j.stationId(), p.id(), j.fin().minusSeconds(1))
                    .orElseThrow(() -> ErreurMetier.regle("PRIX_ABSENT", p.libelle() + " n'a pas de prix de vente : "
                            + "demandez à la direction de le fixer."));
            if (v == null) {
                v = new VenteArticle(j.id(), j.stationId(), p.id(), j.date());
            }
            v.vendre(s.quantite(), prix);
            ventes.save(v);
        }
        ventes.flush();
        return vue(j);
    }

    private VentesJournee vue(JourneeRef j) {
        Map<Long, VenteArticle> saisies = ventes.findByJourneeIdOrderByIdAsc(j.id()).stream()
                .collect(Collectors.toMap(VenteArticle::getProduitId, Function.identity()));
        Map<Long, BigDecimal> autres = ventesEnCours(j.stationId(), j.id());
        Map<Long, ArticleStation> stocks = articles.findByStationId(j.stationId()).stream()
                .collect(Collectors.toMap(ArticleStation::getProduitId, Function.identity()));
        List<VenteVue> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProduitRef p : produitsArticles()) {
            VenteArticle v = saisies.get(p.id());
            ArticleStation a = stocks.get(p.id());
            if (v == null && (!j.brouillon() || !p.actif())) {
                continue;
            }
            BigDecimal stock = a == null ? BigDecimal.ZERO : a.getQuantite();
            // Journee cloturee : la vente est deja sortie du stock
            BigDecimal disponible = j.brouillon() ? stock.subtract(autres.getOrDefault(p.id(), BigDecimal.ZERO)).max(BigDecimal.ZERO) : stock;
            BigDecimal prix = v != null ? v.getPrixUnitaire()
                    : tarification.prixEnVigueur(j.stationId(), p.id(), j.fin().minusSeconds(1)).orElse(null);
            if (v != null) {
                total = total.add(v.getMontant());
            }
            lignes.add(new VenteVue(p.uuid(), p.code(), p.libelle(), p.unite(), disponible, prix,
                    v == null ? null : v.getQuantite(), v == null ? null : v.getMontant(), v == null ? null : v.getCmup()));
        }
        return new VentesJournee(j.uuid(), j.version(), j.statut(), j.brouillon() && aPermission("JOURNEE_SAISIR"), lignes, total);
    }

    // ---------------------------------------------------------------- Outils

    /** Quantites vendues dans des journees non cloturees de la station, par produit, hors journee donnee. */
    private Map<Long, BigDecimal> ventesEnCours(long stationId, Long saufJournee) {
        Map<Long, BigDecimal> r = new HashMap<>();
        for (VenteArticle v : ventes.findByStationIdAndCmupIsNull(stationId)) {
            if (!v.getJourneeId().equals(saufJournee)) {
                r.merge(v.getProduitId(), v.getQuantite(), BigDecimal::add);
            }
        }
        return r;
    }

    /** Stock d'un article verrouille, cree a zero au premier mouvement. */
    ArticleStation verrouiller(long stationId, long produitId) {
        return articles.verrouiller(stationId, produitId)
                .orElseGet(() -> articles.saveAndFlush(new ArticleStation(stationId, produitId)));
    }

    /** Lubrifiants et articles boutique : produits non suivis en cuve, par libelle. */
    List<ProduitRef> produitsArticles() {
        return referentiel.produits().stream().filter(p -> !p.suiviJaugeage())
                .sorted(Comparator.comparing(ProduitRef::libelle)).toList();
    }

    private Map<Long, ProduitRef> produitsParId() {
        return referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
    }

    private ProduitRef article(UUID uuid) {
        return produitsArticles().stream().filter(p -> p.uuid().equals(uuid)).findFirst()
                .orElseThrow(() -> ErreurMetier.regle("ARTICLE_INCONNU", "Choisissez un lubrifiant ou un article boutique."));
    }

    private BigDecimal prixDuJour(long stationId, long produitId) {
        return tarification.prixEnVigueur(stationId, produitId, horloge.instant()).orElse(null);
    }

    private static void exigerQuantite(ProduitRef p, BigDecimal q, String quoi) {
        if (q == null || q.signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_INVALIDE", quoi + " doit être positive.");
        }
        exigerEntier(p, q, quoi);
    }

    private static void exigerEntier(ProduitRef p, BigDecimal q, String quoi) {
        if ("UNITE".equals(p.unite()) && q.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("QUANTITE_ENTIERE", quoi + " de " + p.libelle() + " se compte à l'unité.");
        }
    }

    static String quantite(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }

    private EntreeVue vue(EntreeArticle e, ProduitRef p) {
        return new EntreeVue(e.getUuid(), e.getVersion(), p.uuid(), p.code(), p.libelle(), p.unite(), e.getQuantite(),
                e.getCoutUnitaire(), CalculArticle.valeur(e.getQuantite(), e.getCoutUnitaire()), e.getFournisseur(),
                e.getReference(), e.getStatut().name(), e.getQuantiteRecue(), e.getRecueLe(), e.getRecuePar(),
                e.getObservation(), e.getCreeLe(), e.getCreePar());
    }

    private StationRef stationAccessible(UUID uuid) {
        StationRef s = referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }

    @Override
    public VentesArticles ventesArticles(long stationId, YearMonth mois) {
        BigDecimal montant = BigDecimal.ZERO;
        BigDecimal cout = BigDecimal.ZERO;
        for (var v : ventes.findByStationIdAndDateJourBetweenAndCmupIsNotNull(stationId, mois.atDay(1), mois.atEndOfMonth())) {
            montant = montant.add(v.getMontant());
            cout = cout.add(v.getQuantite().multiply(v.getCmup()));
        }
        return new VentesArticles(montant, cout.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), DAKAR);
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
