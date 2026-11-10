package sn.digitalperf.jaugeo.reporting.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Cout d'achat des carburants vendus : litres vendus chaque jour x prix d'achat convenu en vigueur ce jour-la
 * (celui de la plus grande date d'effet anterieure ou egale). Calcul pur, sans acces aux donnees.
 */
public final class CalculMarge {

    private CalculMarge() {
    }

    /** Litres vendus un jour donne. */
    public record VenteDuJour(LocalDate date, BigDecimal litres) {
    }

    /** Prix d'achat en vigueur a une date ; null si aucun prix convenu n'a encore pris effet. */
    public static BigDecimal prixEnVigueur(NavigableMap<LocalDate, BigDecimal> prix, LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> e = prix == null ? null : prix.floorEntry(date);
        return e == null ? null : e.getValue();
    }

    /**
     * Cout d'achat des ventes, arrondi au franc ; null des qu'un jour de vente n'a pas de prix d'achat en vigueur
     * (un cout partiel fausserait la marge).
     */
    public static BigDecimal coutDesVentes(List<VenteDuJour> ventes, NavigableMap<LocalDate, BigDecimal> prix) {
        BigDecimal cout = BigDecimal.ZERO;
        for (VenteDuJour v : ventes) {
            if (v.litres().signum() == 0) {
                continue;
            }
            BigDecimal p = prixEnVigueur(prix, v.date());
            if (p == null) {
                return null;
            }
            cout = cout.add(v.litres().multiply(p));
        }
        return cout.setScale(0, RoundingMode.HALF_UP);
    }

    /** Valeur d'un ecart de stock (litres signes) au prix d'achat en vigueur a une date ; null sans prix. */
    public static BigDecimal valeurEcart(BigDecimal ecartLitres, NavigableMap<LocalDate, BigDecimal> prix, LocalDate date) {
        if (ecartLitres.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal p = prixEnVigueur(prix, date);
        return p == null ? null : ecartLitres.multiply(p).setScale(0, RoundingMode.HALF_UP);
    }

    /** Taux en % (2 decimales) ; null si la base est nulle ou la valeur inconnue. */
    public static BigDecimal taux(BigDecimal valeur, BigDecimal base) {
        if (valeur == null || base == null || base.signum() == 0) {
            return null;
        }
        return valeur.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
