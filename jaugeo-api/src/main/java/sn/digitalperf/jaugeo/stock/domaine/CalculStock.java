package sn.digitalperf.jaugeo.stock.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stock d'une cuve sur une journee (cahier des charges, M4 et regles 1-2) :
 * <pre>
 *   theorique = physique de la veille + receptions - sorties nettes
 *   ecart     = physique - theorique            (negatif = manquant)
 *   ecart % du mois = cumul des ecarts du mois / cumul des sorties du mois
 * </pre>
 * Le niveau d'alerte se juge sur le mois en cours, comme la feuille du classeur : un jaugeage au pas de 50 L
 * rend l'ecart d'un seul jour trop bruite pour etre compare a une tolerance de 0,5 %.
 */
public final class CalculStock {

    public enum Niveau { NORMAL, ALERTE, ENQUETE }

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /**
     * Depart, sorties ou physique nuls = inconnus (pas encore saisis) ; les cumuls portent sur les jours
     * precedents du mois.
     */
    public record Entree(BigDecimal depart, BigDecimal receptions, BigDecimal sorties, BigDecimal physique,
                         BigDecimal cumulSortiesAvant, BigDecimal cumulEcartAvant, BigDecimal prixUnitaire,
                         BigDecimal tolerancePct, BigDecimal seuilEnquetePct) {
    }

    /** Valeurs nulles quand une donnee manque pour les calculer. */
    public record Resultat(BigDecimal theorique, BigDecimal ecart, BigDecimal ecartPctJour, BigDecimal ecartValeur,
                           BigDecimal cumulSorties, BigDecimal cumulEcart, BigDecimal ecartPctMois, Niveau niveau) {

        public boolean horsTolerance() {
            return niveau != null && niveau != Niveau.NORMAL;
        }
    }

    private CalculStock() {
    }

    public static Resultat calculer(Entree e) {
        BigDecimal receptions = e.receptions() == null ? BigDecimal.ZERO : e.receptions();
        BigDecimal theorique = e.depart() == null || e.sorties() == null ? null
                : e.depart().add(receptions).subtract(e.sorties()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ecart = theorique == null || e.physique() == null ? null
                : e.physique().subtract(theorique).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pctJour = ecart == null ? null : pourcentage(ecart, e.sorties());
        BigDecimal valeur = ecart == null || e.prixUnitaire() == null ? null
                : ecart.multiply(e.prixUnitaire()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal cumulSorties = e.sorties() == null ? null : e.cumulSortiesAvant().add(e.sorties());
        BigDecimal cumulEcart = ecart == null ? null : e.cumulEcartAvant().add(ecart);
        BigDecimal pctMois = cumulEcart == null ? null : pourcentage(cumulEcart, cumulSorties);
        Niveau niveau = cumulEcart == null ? null : niveau(cumulEcart, pctMois, e.tolerancePct(), e.seuilEnquetePct());
        return new Resultat(theorique, ecart, pctJour, valeur, cumulSorties, cumulEcart, pctMois, niveau);
    }

    /** Sans sortie, tout ecart non nul est anormal (fuite, vol, erreur de jaugeage). */
    static Niveau niveau(BigDecimal ecart, BigDecimal pct, BigDecimal tolerance, BigDecimal seuilEnquete) {
        if (pct == null) {
            return ecart.signum() == 0 ? Niveau.NORMAL : Niveau.ALERTE;
        }
        BigDecimal abs = pct.abs();
        if (abs.compareTo(tolerance) <= 0) {
            return Niveau.NORMAL;
        }
        return abs.compareTo(seuilEnquete) <= 0 ? Niveau.ALERTE : Niveau.ENQUETE;
    }

    static BigDecimal pourcentage(BigDecimal ecart, BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return null;
        }
        return ecart.multiply(CENT).divide(base, 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- Reception

    /**
     * Quantite reellement recue : saisie explicite, sinon difference des jauges avant/apres le depotage,
     * sinon quantite du BL (aucune mesure).
     */
    public static BigDecimal quantiteRecue(BigDecimal saisie, BigDecimal jaugeAvant, BigDecimal jaugeApres, BigDecimal quantiteBl) {
        if (saisie != null) {
            return saisie;
        }
        if (jaugeAvant != null && jaugeApres != null) {
            return jaugeApres.subtract(jaugeAvant);
        }
        return quantiteBl;
    }

    /**
     * Complement citerne (litres) : tout le manquant de livraison des qu'il depasse la tolerance transport
     * (en % de la quantite du BL) ; zero sinon, ou en cas d'excedent.
     */
    public static BigDecimal complement(BigDecimal quantiteBl, BigDecimal quantiteRecue, BigDecimal tolerancePct) {
        BigDecimal manquant = quantiteBl.subtract(quantiteRecue);
        if (manquant.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal seuil = quantiteBl.multiply(tolerancePct).divide(CENT, 2, RoundingMode.HALF_UP);
        return manquant.compareTo(seuil) > 0 ? manquant.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public static BigDecimal pourcentageLivraison(BigDecimal quantiteBl, BigDecimal quantiteRecue) {
        return pourcentage(quantiteRecue.subtract(quantiteBl), quantiteBl);
    }
}
