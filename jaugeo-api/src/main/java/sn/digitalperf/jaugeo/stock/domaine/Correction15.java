package sn.digitalperf.jaugeo.stock.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ramene un volume mesure a temperature ambiante au volume a 15 °C (ASTM D1250 / API MPMS 11.1, table 54B,
 * produits raffines). Le facteur de correction (VCF) depend de la temperature du produit et de sa masse
 * volumique a 15 °C :
 * <pre>
 *   alpha15 = K0 / rho15² + K1 / rho15            (rho15 en kg/m³)
 *   VCF     = exp(-alpha15 · dT · (1 + 0,8 · alpha15 · dT)),   dT = T - 15
 * </pre>
 * Les constantes K0 et K1 dependent de la famille de produit, deduite de la masse volumique (essences,
 * zone de transition, kerosenes, gasoils et fiouls).
 */
public final class Correction15 {

    /** Masse volumique a 15 °C admise (kg/L) : 0,6105 a 1,075, domaine de la table 54B. */
    public static final BigDecimal DENSITE_MIN = new BigDecimal("0.6105");
    public static final BigDecimal DENSITE_MAX = new BigDecimal("1.0750");

    private Correction15() {
    }

    /** Facteur de correction a 15 °C (5 decimales), pour une temperature en °C et une densite a 15 °C en kg/L. */
    public static BigDecimal facteur(BigDecimal temperatureC, BigDecimal densite15) {
        double rho = densite15.doubleValue() * 1000;
        if (rho < 610.5 || rho > 1075) {
            throw new IllegalArgumentException("Densité à 15 °C hors du domaine de la table 54B : " + densite15);
        }
        double alpha = alpha(rho);
        double dt = temperatureC.doubleValue() - 15;
        double vcf = Math.exp(-alpha * dt * (1 + 0.8 * alpha * dt));
        return BigDecimal.valueOf(vcf).setScale(5, RoundingMode.HALF_UP);
    }

    /** Volume a 15 °C (litres, 2 decimales). */
    public static BigDecimal volume15(BigDecimal volume, BigDecimal facteur) {
        return volume.multiply(facteur).setScale(2, RoundingMode.HALF_UP);
    }

    static double alpha(double rho) {
        if (rho < 770.5) {
            return 346.4228 / (rho * rho) + 0.4388 / rho;           // essences
        }
        if (rho < 787.5) {
            return -0.00336312 + 2680.3206 / (rho * rho);           // zone de transition
        }
        if (rho < 839.0) {
            return 594.5418 / (rho * rho);                          // kerosenes
        }
        return 186.9696 / (rho * rho) + 0.4862 / rho;               // gasoils et fiouls
    }
}
