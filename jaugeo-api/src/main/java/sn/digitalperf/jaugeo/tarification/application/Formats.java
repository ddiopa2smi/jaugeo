package sn.digitalperf.jaugeo.tarification.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Mise en forme des messages d'erreur (heure de Dakar, montants en F CFA). */
final class Formats {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(DAKAR);

    private Formats() {
    }

    static String dateHeure(Instant instant) {
        return DATE_HEURE.format(instant);
    }

    static String prix(BigDecimal montant) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.FRANCE);
        f.setMaximumFractionDigits(2);
        return f.format(montant) + " F";
    }
}
