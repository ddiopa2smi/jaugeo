package sn.digitalperf.jaugeo.stock;

import java.math.BigDecimal;

/** Interface publique du stock pour les modules qui se rattachent a la journee (tresorerie). */
public interface StocksJournee {

    /**
     * Montant (F CFA) des complements citerne nes des receptions de la journee : montants figes a la cloture,
     * sinon apercu calcule au prix du jour. Un complement sans prix compte pour zero.
     */
    BigDecimal montantComplements(long journeeId);
}
