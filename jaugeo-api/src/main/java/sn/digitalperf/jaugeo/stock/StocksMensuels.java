package sn.digitalperf.jaugeo.stock;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Interface publique du stock pour les syntheses mensuelles (feuille ANALYSE DES STOCKS). */
public interface StocksMensuels {

    /** Bilan d'une cuve sur le mois, d'apres les ecarts figes des journees cloturees. */
    record BilanCuve(String cuveCode, String produitCode, String produitLibelle, BigDecimal stockDepart,
                     BigDecimal receptions, BigDecimal sorties, BigDecimal stockTheorique, BigDecimal stockPhysique,
                     BigDecimal ecartLitres, BigDecimal ecartPct, BigDecimal ecartValeur, BigDecimal tolerancePct,
                     String niveau, int jours) {
    }

    /** Cuves de la station ayant au moins une journee cloturee dans le mois, par code ; aucun filtrage par perimetre. */
    List<BilanCuve> bilans(long stationId, YearMonth mois);
}
