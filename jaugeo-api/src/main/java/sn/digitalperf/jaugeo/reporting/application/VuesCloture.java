package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Vues de la cloture mensuelle : compte d'exploitation, etat des clotures, regularisations, budget. */
public final class VuesCloture {

    private VuesCloture() {
    }

    /** Un carburant dans le compte d'exploitation ; cout, marge ou valeur d'ecart nuls = prix d'achat inconnu. */
    public record ProduitCompte(String code, String libelle, BigDecimal litres, BigDecimal chiffreAffaires,
                                BigDecimal coutAchat, BigDecimal marge, BigDecimal ecartStockLitres,
                                BigDecimal ecartStockValeur) {
    }

    /**
     * Compte d'exploitation d'une station (ou du reseau) pour un mois :
     * marge brute = marge carburants + marge articles - remises ;
     * resultat = marge brute + ecarts de stock (au cout d'achat, negatif = perte) - depenses + regularisations.
     * coutComplet faux : un prix d'achat manque, la marge ne couvre que les produits dont le cout est connu.
     */
    public record CompteStation(UUID stationId, String stationCode, String stationNom, List<ProduitCompte> produits,
                                BigDecimal chiffreAffaires, BigDecimal coutAchat, BigDecimal margeCarburants,
                                BigDecimal ventesArticles, BigDecimal coutArticles, BigDecimal margeArticles,
                                BigDecimal remises, BigDecimal margeBrute, BigDecimal tauxMarge, BigDecimal ecartsStock,
                                BigDecimal depenses, BigDecimal regularisations, BigDecimal resultat,
                                boolean coutComplet, List<String> prixManquants, boolean clos, String cloturePar,
                                Instant clotureLe) {
    }

    public record CompteReseau(YearMonth mois, List<CompteStation> stations, CompteStation total) {
    }

    /** Etat de la cloture d'un mois pour une station : CLOS, PRET (aucun point bloquant) ou BLOQUE. */
    public record EtatStation(UUID stationId, String stationCode, String stationNom, String statut, List<String> blocages,
                              int journees, int journeesValidees, String cloturePar, Instant clotureLe) {
    }

    public record EtatMois(YearMonth mois, List<EtatStation> stations, int closes, int pretes) {
    }

    public record RegularisationVue(UUID id, UUID stationId, String stationCode, String stationNom, YearMonth moisConcerne,
                                    YearMonth moisComptable, String poste, BigDecimal montant, String motif,
                                    String reference, String par, Instant le) {
    }

    // ---------------------------------------------------------------- Budget

    public record MoisBudget(BigDecimal litres, BigDecimal montant) {
    }

    /** Budget d'un produit : douze mois, de janvier a decembre. */
    public record ProduitBudget(UUID produitId, String code, String libelle, List<MoisBudget> mois) {
    }

    /** Budget annuel d'une station : ventes par produit et depenses, douze mois chacun. */
    public record BudgetStation(UUID stationId, String stationCode, String stationNom, int annee,
                                List<ProduitBudget> produits, List<BigDecimal> depenses) {
    }

    /** Reel contre budget d'un mois (mois nul = cumul des mois ecoules). */
    public record SuiviMois(YearMonth mois, boolean ecoule, BigDecimal litresBudget, BigDecimal litresReel,
                            BigDecimal caBudget, BigDecimal caReel, BigDecimal depensesBudget, BigDecimal depensesReel) {
    }

    /** Reel contre budget d'un produit sur les mois ecoules de l'annee. */
    public record SuiviProduit(String code, String libelle, BigDecimal litresBudget, BigDecimal litresReel,
                               BigDecimal caBudget, BigDecimal caReel) {
    }

    /** Suivi budgetaire d'une station, ou du reseau du perimetre (station nulle). */
    public record SuiviBudget(int annee, UUID stationId, List<SuiviMois> mois, List<SuiviProduit> produits, SuiviMois cumul) {
    }
}
