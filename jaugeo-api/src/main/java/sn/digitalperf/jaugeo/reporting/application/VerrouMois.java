package sn.digitalperf.jaugeo.reporting.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.VerrouPeriode;
import sn.digitalperf.jaugeo.reporting.domaine.ClotureMoisDepot;

/**
 * Verrou des mois clotures. Ne depend que du depot des clotures : les modules d'ecriture (journees, tresorerie,
 * clients) le recoivent sans creer de cycle avec les services de reporting, qui dependent d'eux.
 */
@Component
class VerrouMois implements VerrouPeriode {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final ClotureMoisDepot clotures;

    VerrouMois(ClotureMoisDepot clotures) {
        this.clotures = clotures;
    }

    @Override
    @Transactional(readOnly = true)
    public void exigerOuvert(long stationId, LocalDate date) {
        if (date != null && clotures.existsByStationIdAndMois(stationId, date.withDayOfMonth(1))) {
            throw ErreurMetier.regle("MOIS_CLOS", "Le mois de " + libelle(YearMonth.from(date)) + " est clôturé pour cette "
                    + "station : plus aucune écriture ne peut y être datée. Une erreur se corrige par une régularisation.");
        }
    }

    static String libelle(YearMonth mois) {
        return MOIS.format(mois.atDay(1));
    }
}
