package sn.digitalperf.jaugeo.stock;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Interface publique du stock d'articles (lubrifiants, boutique) pour le compte d'exploitation mensuel. */
public interface ArticlesMensuels {

    /** Montant vendu et cout de sortie au CMUP. */
    record VentesArticles(BigDecimal montant, BigDecimal cout) {
    }

    /** Ventes d'articles des journees cloturees du mois (CMUP de sortie fige) ; aucun filtrage par perimetre. */
    VentesArticles ventesArticles(long stationId, YearMonth mois);
}
