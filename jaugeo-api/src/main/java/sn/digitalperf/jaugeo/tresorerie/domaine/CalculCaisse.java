package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculs de la caisse du gerant (feuille station : MONTANT A VERSER et ECART (solde non verse)).
 * <ul>
 *   <li>montant a verser = ventes - bons direction - bons gerant - depenses autorisees - complements citerne
 *       + creances recouvrees en especes a la station ;</li>
 *   <li>solde du jour = solde de la veille + montant a verser - versements deposes ce jour ;</li>
 *   <li>un arrete de caisse remplace le solde par les especes comptees ; l'ecart (comptees - theorique) est
 *       un surplus s'il est positif, un manquant du gerant s'il est negatif.</li>
 * </ul>
 */
public final class CalculCaisse {

    private CalculCaisse() {
    }

    /** Arrete de caisse ; une reprise fixe le solde sans calcul d'ecart. */
    public record Arrete(boolean reprise, BigDecimal comptees) {
    }

    /** Mouvements d'une date ; montant a verser nul si la journee n'est pas cloturee. */
    public record Jour(LocalDate date, BigDecimal montantAVerser, BigDecimal versements, Arrete arrete) {
    }

    /** Ligne du livre de caisse : theorique avant arrete, ecart d'arrete eventuel, solde de fin de journee. */
    public record Ligne(LocalDate date, BigDecimal ouverture, BigDecimal montantAVerser, BigDecimal versements,
                        BigDecimal theorique, BigDecimal ecart, BigDecimal solde) {
    }

    public static BigDecimal montantAVerser(BigDecimal ventes, BigDecimal bonsDirection, BigDecimal bonsGerant,
                                            BigDecimal depenses, BigDecimal complements, BigDecimal recouvrements) {
        return ventes.subtract(bonsDirection).subtract(bonsGerant).subtract(depenses).subtract(complements).add(recouvrements);
    }

    /** Deroule le livre de caisse a partir d'un solde, jour apres jour (jours tries par date croissante). */
    public static List<Ligne> derouler(BigDecimal soldeInitial, List<Jour> jours) {
        List<Ligne> lignes = new ArrayList<>();
        BigDecimal solde = soldeInitial;
        for (Jour j : jours) {
            BigDecimal montant = j.montantAVerser() == null ? BigDecimal.ZERO : j.montantAVerser();
            BigDecimal versements = j.versements() == null ? BigDecimal.ZERO : j.versements();
            BigDecimal theorique = solde.add(montant).subtract(versements);
            BigDecimal ecart = null;
            BigDecimal fin = theorique;
            if (j.arrete() != null) {
                fin = j.arrete().comptees();
                ecart = j.arrete().reprise() ? null : fin.subtract(theorique);
            }
            lignes.add(new Ligne(j.date(), solde, j.montantAVerser(), versements, theorique, ecart, fin));
            solde = fin;
        }
        return lignes;
    }

    /**
     * Retard de versement : le solde non verse depasse ce que le gerant a encaisse sur les derniers jours
     * (il garde donc des especes plus anciennes que ce delai).
     */
    public static boolean retard(BigDecimal solde, List<BigDecimal> derniersMontants, int jours) {
        if (solde == null || solde.signum() <= 0 || derniersMontants.size() < jours) {
            return false;
        }
        BigDecimal recent = derniersMontants.stream().limit(jours).map(m -> m.max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return solde.compareTo(recent) > 0;
    }
}
