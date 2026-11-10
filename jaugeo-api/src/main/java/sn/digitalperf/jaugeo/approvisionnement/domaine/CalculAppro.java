package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Calculs de l'approvisionnement : proposition de commande et notation des transporteurs. */
public final class CalculAppro {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final BigDecimal MILLE = BigDecimal.valueOf(1000);

    private CalculAppro() {
    }

    // ---------------------------------------------------------------- Proposition de commande

    /** Dernier stock fige d'une cuve et ventes moyennes par jour (nulles sans historique). */
    public record Cuve(BigDecimal capacite, BigDecimal creux, BigDecimal seuil, BigDecimal stock, LocalDate date,
                       BigDecimal ventesMoyennes) {
    }

    /**
     * Besoin d'une station pour un produit. Stock projete a aujourd'hui puis a l'arrivee d'une citerne commandee
     * maintenant ; quantite proposee = capacite utile - stock a l'arrivee - livraisons en cours, au millier
     * inferieur ; nulle si rien n'est a commander.
     */
    public record Besoin(BigDecimal stockAujourdhui, BigDecimal ventesMoyennes, BigDecimal autonomieJours,
                         BigDecimal stockArrivee, BigDecimal capaciteUtile, BigDecimal quantite, LocalDate ruptureLe,
                         boolean aCommander, String motif) {
    }

    /**
     * Une commande est proposee quand l'autonomie, livraisons en cours comprises, ne couvre plus le delai de
     * livraison du depot plus la marge de securite, ou quand le stock (avec les livraisons en cours) passe sous
     * le seuil de reapprovisionnement des cuves.
     */
    public static Besoin besoin(List<Cuve> cuves, BigDecimal enCours, LocalDate aujourdhui, int delai, int marge,
                                BigDecimal tauxRemplissage) {
        BigDecimal stockAuj = BigDecimal.ZERO;
        BigDecimal stockArr = BigDecimal.ZERO;
        BigDecimal moyenne = null;
        BigDecimal creux = BigDecimal.ZERO;
        BigDecimal capacite = BigDecimal.ZERO;
        boolean capaciteConnue = true;
        BigDecimal seuil = BigDecimal.ZERO;
        boolean seuilConnu = !cuves.isEmpty();
        for (Cuve c : cuves) {
            BigDecimal moy = c.ventesMoyennes() == null ? BigDecimal.ZERO : c.ventesMoyennes();
            long jours = Math.max(0, ChronoUnit.DAYS.between(c.date(), aujourdhui));
            BigDecimal aujourdhuiCuve = c.stock().subtract(moy.multiply(BigDecimal.valueOf(jours))).max(BigDecimal.ZERO);
            stockAuj = stockAuj.add(aujourdhuiCuve);
            stockArr = stockArr.add(aujourdhuiCuve.subtract(moy.multiply(BigDecimal.valueOf(delai))).max(BigDecimal.ZERO));
            if (c.ventesMoyennes() != null) {
                moyenne = (moyenne == null ? BigDecimal.ZERO : moyenne).add(c.ventesMoyennes());
            }
            creux = creux.add(c.creux() == null ? BigDecimal.ZERO : c.creux());
            if (c.capacite() == null) {
                capaciteConnue = false;
            } else {
                capacite = capacite.add(c.capacite());
            }
            if (c.seuil() == null) {
                seuilConnu = false;
            } else {
                seuil = seuil.add(c.seuil());
            }
        }
        BigDecimal enRoute = enCours == null ? BigDecimal.ZERO : enCours;
        BigDecimal capaciteUtile = capaciteConnue && !cuves.isEmpty()
                ? capacite.multiply(tauxRemplissage).setScale(0, RoundingMode.DOWN) : null;
        boolean ventes = moyenne != null && moyenne.signum() > 0;
        BigDecimal autonomie = ventes ? jours(stockAuj.subtract(creux), moyenne) : null;
        BigDecimal autonomieAvecEnCours = ventes ? jours(stockAuj.add(enRoute).subtract(creux), moyenne) : null;
        LocalDate rupture = autonomie == null ? null : aujourdhui.plusDays(autonomie.longValue());

        boolean parAutonomie = autonomieAvecEnCours != null
                && autonomieAvecEnCours.compareTo(BigDecimal.valueOf((long) delai + marge)) <= 0;
        boolean sousSeuil = seuilConnu && stockAuj.add(enRoute).compareTo(seuil) < 0;
        String motif;
        if (parAutonomie) {
            motif = "Autonomie de " + autonomieAvecEnCours.toPlainString() + " j"
                    + (enRoute.signum() > 0 ? " (livraisons en cours comprises)" : "") + " pour un délai de livraison de "
                    + delai + " j et " + marge + " j de marge.";
        } else if (sousSeuil) {
            motif = "Stock sous le seuil de réapprovisionnement.";
        } else if (!ventes) {
            motif = "Pas de ventes connues sur les 7 derniers jours : autonomie inconnue.";
        } else {
            motif = null;
        }
        boolean aCommander = parAutonomie || sousSeuil;
        BigDecimal quantite = null;
        if (aCommander) {
            if (capaciteUtile == null) {
                motif = motif + " Capacité de cuve inconnue : quantité à fixer.";
            } else {
                BigDecimal q = capaciteUtile.subtract(stockArr).subtract(enRoute).divide(MILLE, 0, RoundingMode.DOWN).multiply(MILLE);
                if (q.compareTo(MILLE) < 0) {
                    aCommander = false;
                    motif = enRoute.signum() > 0 ? "Les livraisons en cours couvrent le besoin." : "Cuve presque pleine.";
                } else {
                    quantite = q;
                }
            }
        }
        return new Besoin(stockAuj.setScale(2, RoundingMode.HALF_UP), moyenne, autonomie, stockArr.setScale(2, RoundingMode.HALF_UP),
                capaciteUtile, quantite, rupture, aCommander, motif);
    }

    private static BigDecimal jours(BigDecimal stockUtile, BigDecimal moyenne) {
        return stockUtile.max(BigDecimal.ZERO).divide(moyenne, 1, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- Notation des transporteurs

    /** Livraison receptionnee : quantite livree (base de comparaison), manquant, ponctualite, tolerance appliquee. */
    public record Livree(BigDecimal quantiteLivree, BigDecimal manquant, boolean aLHeure, BigDecimal tolerancePct) {
    }

    /** Note sur 5 (1 decimale) : 40 % ponctualite, 40 % livraisons dans la tolerance, 20 % taux de manquant. */
    public record Note(BigDecimal note, int livraisons, BigDecimal ponctualitePct, BigDecimal conformitePct,
                       BigDecimal tauxManquantPct, int horsTolerance) {
    }

    public static Note noter(List<Livree> livrees) {
        if (livrees.isEmpty()) {
            return new Note(null, 0, null, null, null, 0);
        }
        int n = livrees.size();
        int aLHeure = (int) livrees.stream().filter(Livree::aLHeure).count();
        int horsTolerance = (int) livrees.stream().filter(l -> horsTolerance(l.quantiteLivree(), l.manquant(), l.tolerancePct())).count();
        BigDecimal livre = livrees.stream().map(Livree::quantiteLivree).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal manquant = livrees.stream().map(Livree::manquant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taux = pourcentage(manquant, livre);
        BigDecimal tolerance = livrees.stream().map(Livree::tolerancePct).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        double ponctualite = (double) aLHeure / n;
        double conformite = (double) (n - horsTolerance) / n;
        double manque = tolerance.signum() == 0 ? (taux.signum() == 0 ? 1 : 0)
                : Math.max(0, 1 - taux.doubleValue() / (2 * tolerance.doubleValue()));
        BigDecimal note = BigDecimal.valueOf(5 * (0.4 * ponctualite + 0.4 * conformite + 0.2 * manque)).setScale(1, RoundingMode.HALF_UP);
        return new Note(note, n, BigDecimal.valueOf(ponctualite * 100).setScale(0, RoundingMode.HALF_UP),
                BigDecimal.valueOf(conformite * 100).setScale(0, RoundingMode.HALF_UP), taux, horsTolerance);
    }

    /** Manquant en % de la quantite livree (2 decimales). */
    public static BigDecimal pourcentage(BigDecimal manquant, BigDecimal livre) {
        return livre.signum() == 0 ? BigDecimal.ZERO : manquant.multiply(CENT).divide(livre, 2, RoundingMode.HALF_UP);
    }

    /** Meme regle que le complement citerne : manquant strictement au-dela de la tolerance. */
    public static boolean horsTolerance(BigDecimal livre, BigDecimal manquant, BigDecimal tolerancePct) {
        BigDecimal seuil = livre.multiply(tolerancePct).divide(CENT, 2, RoundingMode.HALF_UP);
        return manquant.signum() > 0 && manquant.compareTo(seuil) > 0;
    }
}
