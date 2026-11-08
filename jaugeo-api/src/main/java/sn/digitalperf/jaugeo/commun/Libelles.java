package sn.digitalperf.jaugeo.commun;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Mise en forme des montants et des dates dans les messages lus par les utilisateurs. */
public final class Libelles {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Libelles() {
    }

    /** 1 250 000 F */
    public static String francs(BigDecimal montant) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", s).format(montant) + " F";
    }

    public static String date(LocalDate d) {
        return DATE.format(d);
    }
}
