package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.stock.StocksJournee;
import sn.digitalperf.jaugeo.stock.domaine.CalculStock;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterne;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterneDepot;
import sn.digitalperf.jaugeo.stock.domaine.Reception;
import sn.digitalperf.jaugeo.stock.domaine.ReceptionDepot;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticle;
import sn.digitalperf.jaugeo.stock.domaine.VenteArticleDepot;
import sn.digitalperf.jaugeo.tarification.Tarification;

@Component
@Transactional(readOnly = true)
class ConsultationStock implements StocksJournee {

    private final JourneesExploitation exploitation;
    private final ComplementCiterneDepot complements;
    private final ReceptionDepot receptions;
    private final Tarification tarification;
    private final ClotureStock cloture;
    private final VenteArticleDepot ventesArticles;

    ConsultationStock(JourneesExploitation exploitation, ComplementCiterneDepot complements, ReceptionDepot receptions,
                      Tarification tarification, ClotureStock cloture, VenteArticleDepot ventesArticles) {
        this.ventesArticles = ventesArticles;
        this.exploitation = exploitation;
        this.complements = complements;
        this.receptions = receptions;
        this.tarification = tarification;
        this.cloture = cloture;
    }

    @Override
    public BigDecimal montantComplements(long journeeId) {
        List<ComplementCiterne> figes = complements.findByJourneeId(journeeId);
        JourneeRef j = exploitation.journee(journeeId).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        if (!figes.isEmpty() || !j.brouillon()) {
            return figes.stream().map(ComplementCiterne::getMontant).filter(m -> m != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Reception r : receptions.findByJourneeIdOrderByIdAsc(journeeId)) {
            BigDecimal manquant = CalculStock.complement(r.quantiteLivree(), r.quantiteMesuree(), cloture.toleranceTransportPct());
            if (manquant.signum() > 0) {
                BigDecimal prix = tarification.prixEnVigueur(j.stationId(), r.getProduitId(), j.fin().minusSeconds(1)).orElse(null);
                if (prix != null) {
                    total = total.add(manquant.multiply(prix).setScale(0, RoundingMode.HALF_UP));
                }
            }
        }
        return total;
    }

    @Override
    public BigDecimal montantVentesArticles(long journeeId) {
        return ventesArticles.findByJourneeIdOrderByIdAsc(journeeId).stream().map(VenteArticle::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
