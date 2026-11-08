package sn.digitalperf.jaugeo.tresorerie;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Interface publique de la tresorerie pour les syntheses mensuelles (feuille ANALYSE VENTES & RECETTES). */
public interface SyntheseCaisse {

    /**
     * Caisse du gerant sur un mois : solde d'ouverture, composantes des recettes figees des journees cloturees,
     * versements non rejetes, ecarts constates aux arretes de caisse, solde de fin de mois.
     */
    record MoisCaisse(BigDecimal soldeDebut, BigDecimal ventes, BigDecimal bonsDirection, BigDecimal bonsGerant,
                      BigDecimal depenses, BigDecimal complements, BigDecimal recouvrements, BigDecimal montants,
                      BigDecimal versements, BigDecimal ecarts, BigDecimal soldeFin, int joursNonClotures) {
    }

    /** Aucun filtrage par perimetre : c'est au module appelant de l'appliquer. */
    MoisCaisse mois(long stationId, YearMonth mois);
}
