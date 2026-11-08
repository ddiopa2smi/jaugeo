package sn.digitalperf.jaugeo.referentiel.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Bareme de jaugeage d'une cuve : table hauteur (mm) -> volume (litres) issue du certificat.
 * Entre deux points, le volume est interpole lineairement (pratique des certificats de jaugeage,
 * dont le pas est fin : 1 cm en general).
 */
public final class Bareme {

    public record Point(int hauteurMm, BigDecimal volumeLitres) {
    }

    public static final int POINTS_MIN = 2;
    public static final int POINTS_MAX = 5_000;

    private final List<Point> points;

    private Bareme(List<Point> points) {
        this.points = points;
    }

    /** Valide un bareme saisi : tries par hauteur, hauteurs et volumes strictement croissants. */
    public static Bareme valider(List<Point> saisis) {
        if (saisis == null || saisis.size() < POINTS_MIN) {
            throw ErreurMetier.regle("BAREME_TROP_COURT", "Un barème doit comporter au moins " + POINTS_MIN + " points.");
        }
        if (saisis.size() > POINTS_MAX) {
            throw ErreurMetier.regle("BAREME_TROP_LONG", "Un barème ne peut pas dépasser " + POINTS_MAX + " points.");
        }
        List<Point> tries = saisis.stream()
                .sorted((a, b) -> Integer.compare(a.hauteurMm(), b.hauteurMm()))
                .toList();
        for (int i = 0; i < tries.size(); i++) {
            Point p = tries.get(i);
            if (p.hauteurMm() < 0 || p.volumeLitres() == null || p.volumeLitres().signum() < 0) {
                throw ErreurMetier.regle("BAREME_VALEUR_NEGATIVE",
                        "Hauteurs et volumes doivent être positifs (ligne " + p.hauteurMm() + " mm).");
            }
            if (i > 0) {
                Point precedent = tries.get(i - 1);
                if (p.hauteurMm() == precedent.hauteurMm()) {
                    throw ErreurMetier.regle("BAREME_HAUTEUR_EN_DOUBLE",
                            "La hauteur " + formaterMm(p.hauteurMm()) + " apparaît deux fois.");
                }
                if (p.volumeLitres().compareTo(precedent.volumeLitres()) <= 0) {
                    throw ErreurMetier.regle("BAREME_NON_CROISSANT", "Le volume doit augmenter avec la hauteur : "
                            + formaterMm(p.hauteurMm()) + " donne " + p.volumeLitres().stripTrailingZeros().toPlainString()
                            + " L, pas plus que " + formaterMm(precedent.hauteurMm()) + ".");
                }
            }
        }
        return new Bareme(tries);
    }

    /** Volume en litres pour une hauteur mesuree, arrondi au centilitre. */
    public BigDecimal volumePour(int hauteurMm) {
        Point premier = points.get(0);
        Point dernier = points.get(points.size() - 1);
        if (hauteurMm < premier.hauteurMm() || hauteurMm > dernier.hauteurMm()) {
            throw ErreurMetier.regle("HAUTEUR_HORS_BAREME", "La hauteur " + formaterMm(hauteurMm)
                    + " est hors du barème (" + formaterMm(premier.hauteurMm()) + " à " + formaterMm(dernier.hauteurMm()) + ").");
        }
        int bas = 0;
        int haut = points.size() - 1;
        while (haut - bas > 1) {
            int milieu = (bas + haut) >>> 1;
            if (points.get(milieu).hauteurMm() <= hauteurMm) {
                bas = milieu;
            } else {
                haut = milieu;
            }
        }
        Point a = points.get(bas);
        Point b = points.get(haut);
        if (hauteurMm == a.hauteurMm()) {
            return a.volumeLitres().setScale(2, RoundingMode.HALF_UP);
        }
        if (hauteurMm == b.hauteurMm()) {
            return b.volumeLitres().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal fraction = BigDecimal.valueOf(hauteurMm - a.hauteurMm())
                .divide(BigDecimal.valueOf(b.hauteurMm() - a.hauteurMm()), 10, RoundingMode.HALF_UP);
        return a.volumeLitres()
                .add(b.volumeLitres().subtract(a.volumeLitres()).multiply(fraction))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public List<Point> points() {
        return points;
    }

    public BigDecimal volumeMax() {
        return points.get(points.size() - 1).volumeLitres();
    }

    public int hauteurMax() {
        return points.get(points.size() - 1).hauteurMm();
    }

    private static String formaterMm(int mm) {
        return mm % 10 == 0 ? (mm / 10) + " cm" : mm + " mm";
    }
}
