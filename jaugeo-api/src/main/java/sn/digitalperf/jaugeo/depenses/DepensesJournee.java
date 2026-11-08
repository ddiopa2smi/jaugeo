package sn.digitalperf.jaugeo.depenses;

import java.math.BigDecimal;

/** Interface publique des depenses pour la tresorerie. */
public interface DepensesJournee {

    /** Total (F CFA) des depenses autorisees de la journee, deduit du montant a verser. */
    BigDecimal totalAutorise(long journeeId);
}
