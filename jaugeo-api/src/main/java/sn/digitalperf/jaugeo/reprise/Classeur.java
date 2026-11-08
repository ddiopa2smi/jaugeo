package sn.digitalperf.jaugeo.reprise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Contenu utile du classeur « SUIVI DES STATIONS » : feuilles station jour par jour, feuilles d'analyse, constats. */
public record Classeur(Map<String, Feuille> feuilles, List<LigneAnalyse> analyses, List<Constat> constats) {

    /** Feuille d'une station ; stockDepart = stock de depart du premier jour, par produit. */
    public record Feuille(String nom, String station, Disposition disposition, Map<String, BigDecimal> stockDepart,
                          List<Jour> jours) {
    }

    /** Livraison du jour d'un produit (colonnes REF BL et LITRAGE). */
    public record Reception(String numeroBl, String produit, BigDecimal litres) {
    }

    /** Versement inscrit sur la ligne du jour ; dateBordereau lue dans la reference (« ... DU 05/01/2026 »). */
    public record Depot(BigDecimal montant, String reference, String numero, String banque, LocalDate dateBordereau) {
    }

    /** Valeurs calculees par le classeur pour la journee, a retrouver dans Jaugeo. */
    public record Attendu(Map<String, BigDecimal> litres, BigDecimal ventes, BigDecimal aVerser,
                          Map<String, BigDecimal> theorique, Map<String, BigDecimal> ecartStock, BigDecimal ecartCaisse) {
    }

    /**
     * Journee lue dans une feuille station. Index par code de pistolet ; les autres grandeurs par produit.
     * lignes = numeros de ligne Excel (plusieurs si la date figure deux fois).
     */
    public record Jour(LocalDate date, List<Integer> lignes, Map<String, BigDecimal> departs,
                       Map<String, BigDecimal> arrivees, Map<String, BigDecimal> prix, Map<String, BigDecimal> remises,
                       Map<String, BigDecimal> jauges, List<Reception> receptions, Map<String, BigDecimal> bonsDirection,
                       Map<String, BigDecimal> bonsGerant, Map<String, BigDecimal> complements, List<Depot> versements,
                       BigDecimal depenses, BigDecimal creances, String observation, Attendu attendu) {

        public String libelleLignes() {
            return lignes.size() == 1 ? "ligne " + lignes.get(0)
                    : "lignes " + String.join(", ", lignes.stream().map(String::valueOf).toList());
        }
    }

    /** Ligne station d'un bloc mensuel de la feuille ANALYSE VENTES & RECETTES (colonnes B a S). */
    public record LigneAnalyse(YearMonth mois, String station, Map<String, BigDecimal> litres, BigDecimal recettes,
                               BigDecimal soldeOuverture, BigDecimal creancesDebut, BigDecimal aVerser,
                               BigDecimal versements, BigDecimal soldeCaisse, BigDecimal creancesFin,
                               BigDecimal depenses, BigDecimal complements, BigDecimal ecart, String observation) {
    }

    /** Particularite du classeur relevee a la lecture ou a l'import, avec sa localisation. */
    public record Constat(String station, LocalDate date, String ligne, String code, String message) {
    }
}
