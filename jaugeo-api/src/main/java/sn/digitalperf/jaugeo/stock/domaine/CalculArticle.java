package sn.digitalperf.jaugeo.stock.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stock d'un article valorise au cout moyen unitaire pondere (CMUP) :
 * <pre>
 *   entree      : CMUP = (stock x CMUP + quantite entree x cout d'achat) / (stock + quantite entree)
 *   sortie      : stock - quantite, CMUP inchange (valeur de sortie = quantite x CMUP)
 *   inventaire  : stock = quantite comptee, CMUP inchange (ecart valorise au CMUP)
 * </pre>
 */
public final class CalculArticle {

    public record Etat(BigDecimal quantite, BigDecimal cmup) {

        public BigDecimal valeur() {
            return CalculArticle.valeur(quantite, cmup);
        }
    }

    private CalculArticle() {
    }

    public static Etat entree(Etat e, BigDecimal quantite, BigDecimal cout) {
        BigDecimal total = e.quantite().add(quantite);
        if (total.signum() == 0) {
            return new Etat(total, cout);
        }
        BigDecimal cmup = e.quantite().multiply(e.cmup()).add(quantite.multiply(cout)).divide(total, 2, RoundingMode.HALF_UP);
        return new Etat(total, cmup);
    }

    /** Sortie ; quantite superieure au stock = null (sortie impossible). */
    public static Etat sortie(Etat e, BigDecimal quantite) {
        BigDecimal reste = e.quantite().subtract(quantite);
        return reste.signum() < 0 ? null : new Etat(reste, e.cmup());
    }

    public static Etat inventaire(Etat e, BigDecimal compte) {
        return new Etat(compte, e.cmup());
    }

    public static BigDecimal valeur(BigDecimal quantite, BigDecimal cout) {
        return quantite.multiply(cout).setScale(0, RoundingMode.HALF_UP);
    }
}
