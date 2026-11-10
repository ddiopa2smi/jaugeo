package sn.digitalperf.jaugeo.personnel.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Calcul des feuilles de caisse : litres vendus par pistolet et par poste (index de fin - index de debut, l'index
 * de debut etant la fin du poste precedent ou le depart de la journee), valorises au prix de la journee, et
 * controle que les postes totalisent bien les litres du releve de la journee.
 */
public final class CalculFeuille {

    private CalculFeuille() {
    }

    /** Changement de compteur survenu dans la journee : index lu sur l'ancien, index de depart du nouveau. */
    public record Rupture(BigDecimal indexAvant, BigDecimal indexApres) {
    }

    /** Pistolet de la journee : index de depart, sortie nette du releve (nulle si incomplet), prix (nul si absent). */
    public record Pistolet(long id, String code, BigDecimal indexDepart, BigDecimal sortieNetteJournee, BigDecimal prix,
                           List<Rupture> ruptures) {
    }

    /** Releve de fin de poste saisi sur une feuille. */
    public record Saisie(TypePoste poste, long feuilleId, long pistoletId, BigDecimal indexFin, BigDecimal remiseCuve) {
    }

    /** Ligne calculee ; litres et montant nuls quand la ligne est en anomalie. */
    public record Ligne(Saisie saisie, BigDecimal indexDebut, BigDecimal litres, BigDecimal prix, BigDecimal montant,
                        String anomalie) {
    }

    public static List<Ligne> lignes(Map<Long, Pistolet> pistolets, List<Saisie> saisies) {
        Map<Long, List<Saisie>> parPistolet = new HashMap<>();
        saisies.forEach(s -> parPistolet.computeIfAbsent(s.pistoletId(), k -> new ArrayList<>()).add(s));
        List<Ligne> lignes = new ArrayList<>();
        parPistolet.forEach((id, liste) -> {
            Pistolet p = pistolets.get(id);
            liste.sort(Comparator.comparing(Saisie::poste));
            BigDecimal debut = p.indexDepart();
            List<Rupture> ruptures = new ArrayList<>(p.ruptures() == null ? List.of() : p.ruptures());
            for (Saisie s : liste) {
                lignes.add(ligne(p, s, debut, ruptures));
                debut = s.indexFin();
            }
        });
        lignes.sort(Comparator.comparing((Ligne l) -> l.saisie().poste()).thenComparing(l -> pistolets.get(l.saisie().pistoletId()).code()));
        return lignes;
    }

    private static Ligne ligne(Pistolet p, Saisie s, BigDecimal debut, List<Rupture> ruptures) {
        BigDecimal brut = s.indexFin().subtract(debut);
        if (brut.signum() < 0 && !ruptures.isEmpty()) {
            // Compteur change pendant le poste : fin de l'ancien compteur, puis debut du nouveau
            Rupture r = ruptures.remove(0);
            brut = r.indexAvant().subtract(debut).add(s.indexFin().subtract(r.indexApres()));
        }
        if (brut.signum() < 0) {
            return new Ligne(s, debut, null, p.prix(), null, "l'index de fin (" + nombre(s.indexFin())
                    + ") est inférieur à l'index de début (" + nombre(debut) + ").");
        }
        BigDecimal remise = s.remiseCuve() == null ? BigDecimal.ZERO : s.remiseCuve();
        if (remise.compareTo(brut) > 0) {
            return new Ligne(s, debut, null, p.prix(), null, "la remise en cuve dépasse les litres sortis.");
        }
        BigDecimal litres = brut.subtract(remise);
        if (p.prix() == null) {
            return new Ligne(s, debut, litres, null, null, litres.signum() == 0 ? null : "aucun prix en vigueur.");
        }
        return new Ligne(s, debut, litres, p.prix(), litres.multiply(p.prix()).setScale(0, RoundingMode.HALF_UP), null);
    }

    /** Montant du par feuille : ventes de ses pistolets (les lignes en anomalie ne comptent pas). */
    public static Map<Long, BigDecimal> duParFeuille(List<Ligne> lignes) {
        Map<Long, BigDecimal> du = new HashMap<>();
        for (Ligne l : lignes) {
            du.merge(l.saisie().feuilleId(), l.montant() == null ? BigDecimal.ZERO : l.montant(), BigDecimal::add);
        }
        return du;
    }

    /**
     * Points qui s'opposent a la cloture : lignes en anomalie, et pistolets dont les postes ne totalisent pas les
     * litres du releve de la journee (un pistolet absent des feuilles doit n'avoir rien vendu).
     */
    public static List<String> problemes(Map<Long, Pistolet> pistolets, List<Ligne> lignes) {
        List<String> problemes = new ArrayList<>();
        Map<Long, BigDecimal> litres = new HashMap<>();
        for (Ligne l : lignes) {
            Pistolet p = pistolets.get(l.saisie().pistoletId());
            if (l.anomalie() != null) {
                problemes.add("Poste du " + l.saisie().poste().libelle().toLowerCase() + ", pistolet " + p.code() + " : " + l.anomalie());
            }
            if (l.litres() != null) {
                litres.merge(p.id(), l.litres(), BigDecimal::add);
            }
        }
        pistolets.values().stream().sorted(Comparator.comparing(Pistolet::code)).forEach(p -> {
            if (p.sortieNetteJournee() == null) {
                return;
            }
            BigDecimal feuilles = litres.getOrDefault(p.id(), BigDecimal.ZERO);
            if (feuilles.compareTo(p.sortieNetteJournee()) != 0) {
                problemes.add("Pistolet " + p.code() + " : " + Libelles.litres(feuilles) + " sur les feuilles de caisse pour "
                        + Libelles.litres(p.sortieNetteJournee()) + " au relevé de la journée.");
            }
        });
        return problemes;
    }

    private static String nombre(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
