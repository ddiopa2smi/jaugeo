package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.stock.domaine.ArticleStation;
import sn.digitalperf.jaugeo.stock.domaine.ArticleStationDepot;
import sn.digitalperf.jaugeo.stock.domaine.CalculArticle;
import sn.digitalperf.jaugeo.stock.domaine.MouvementArticle;
import sn.digitalperf.jaugeo.stock.domaine.MouvementArticleDepot;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticle;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticleDepot;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Part des lubrifiants et articles boutique dans la cloture : les ventes de la journee sortent du stock au CMUP
 * (stock suffisant exige, prix revalide) ; au renvoi, elles y rentrent a leur CMUP de sortie.
 * Avant la tresorerie, qui reprend le montant des ventes dans le montant a verser.
 */
@Component
@Order(12)
class ClotureArticles implements ParticipantCloture {

    private final VenteArticleDepot ventes;
    private final ArticleStationDepot articles;
    private final MouvementArticleDepot mouvements;
    private final ArticleService service;
    private final Tarification tarification;

    ClotureArticles(VenteArticleDepot ventes, ArticleStationDepot articles, MouvementArticleDepot mouvements,
                    ArticleService service, Tarification tarification) {
        this.ventes = ventes;
        this.articles = articles;
        this.mouvements = mouvements;
        this.service = service;
        this.tarification = tarification;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        Map<Long, ProduitRef> produits = produits();
        Map<Long, ArticleStation> stocks = articles.findByStationId(j.stationId()).stream()
                .collect(Collectors.toMap(ArticleStation::getProduitId, Function.identity()));
        List<String> problemes = new ArrayList<>();
        for (VenteArticle v : ventes.findByJourneeIdOrderByIdAsc(j.id())) {
            ProduitRef p = produits.get(v.getProduitId());
            ArticleStation a = stocks.get(v.getProduitId());
            BigDecimal stock = a == null ? BigDecimal.ZERO : a.getQuantite();
            if (v.getQuantite().compareTo(stock) > 0) {
                problemes.add(p.libelle() + " : " + ArticleService.quantite(v.getQuantite()) + " vendu(s) pour "
                        + ArticleService.quantite(stock) + " en stock. Faites confirmer la livraison reçue ou corrigez la vente.");
            }
            if (tarification.prixEnVigueur(j.stationId(), v.getProduitId(), j.fin().minusSeconds(1)).isEmpty()) {
                problemes.add(p.libelle() + " n'a plus de prix de vente pour ce jour.");
            }
        }
        return problemes;
    }

    @Override
    public void figer(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        Map<Long, ProduitRef> produits = produits();
        for (VenteArticle v : ventes.findByJourneeIdOrderByIdAsc(j.id())) {
            BigDecimal prix = tarification.prixEnVigueur(j.stationId(), v.getProduitId(), j.fin().minusSeconds(1)).orElseThrow();
            v.vendre(v.getQuantite(), prix);
            ArticleStation a = service.verrouiller(j.stationId(), v.getProduitId());
            CalculArticle.Etat apres = CalculArticle.sortie(a.etat(), v.getQuantite());
            if (apres == null) {
                throw new IllegalStateException("Stock insuffisant a la cloture pour " + produits.get(v.getProduitId()).code());
            }
            v.figer(a.getCmup());
            a.appliquer(apres);
            articles.save(a);
            ventes.save(v);
            mouvements.save(new MouvementArticle(a, j.date(), MouvementArticle.Type.VENTE, v.getQuantite().negate(), v.getCmup(),
                    "Ventes du " + EvaluationStock.date(j.date())).journee(j.id()));
        }
        ventes.flush();
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        for (MouvementArticle m : mouvements.findByJourneeIdAndType(journee.id(), MouvementArticle.Type.VENTE)) {
            ArticleStation a = service.verrouiller(m.getStationId(), m.getProduitId());
            a.appliquer(CalculArticle.entree(a.etat(), m.getQuantite().negate(), m.getCoutUnitaire()));
            articles.save(a);
        }
        mouvements.supprimer(journee.id(), MouvementArticle.Type.VENTE);
        for (VenteArticle v : ventes.findByJourneeIdOrderByIdAsc(journee.id())) {
            v.figer(null);
            ventes.save(v);
        }
    }

    private Map<Long, ProduitRef> produits() {
        return service.produitsArticles().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
    }
}
