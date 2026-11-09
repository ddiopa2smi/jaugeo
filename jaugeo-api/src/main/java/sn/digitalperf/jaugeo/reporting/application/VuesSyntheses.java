package sn.digitalperf.jaugeo.reporting.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vues des syntheses mensuelles (feuilles d'analyse). */
public final class VuesSyntheses {

    private VuesSyntheses() {
    }

    public record ProduitColonne(String code, String libelle) {
    }

    /**
     * Ligne de la feuille ANALYSE VENTES &amp; RECETTES pour une station (lettres des colonnes du classeur) :
     * H = E + F + G ; Q = I + J + K + L + M + O ; R = Q - H, egal aux ecarts des arretes de caisse du mois
     * quand toutes les journees sont cloturees.
     */
    public record LigneVentes(UUID stationId, String stationCode, String stationNom, Map<String, BigDecimal> litres,
                              BigDecimal recettes, BigDecimal soldeOuverture, BigDecimal creancesDebut, BigDecimal aVerser,
                              BigDecimal versements, BigDecimal soldeCaisse, BigDecimal creancesFin, BigDecimal depenses,
                              BigDecimal complements, BigDecimal horsCaisse, BigDecimal reglementsSiege, BigDecimal totalVersements,
                              BigDecimal ecart, BigDecimal bonsDirection, BigDecimal bonsGerant, BigDecimal recouvrements,
                              BigDecimal creancesGerantFin, BigDecimal ecartsArretes, int joursNonClotures,
                              String observation, String commentaire, Integer commentaireVersion) {
    }

    public record SyntheseVentes(YearMonth mois, List<ProduitColonne> produits, List<LigneVentes> lignes, LigneVentes cumul,
                                 boolean peutCommenter) {
    }

    /** Ligne de la feuille ANALYSE DES STOCKS : une cuve d'une station sur le mois. */
    public record LigneStocks(UUID stationId, String stationCode, String stationNom, String cuveCode, String produitCode,
                              String produitLibelle, BigDecimal stockDepart, BigDecimal receptions, BigDecimal sorties,
                              BigDecimal stockTheorique, BigDecimal stockPhysique, BigDecimal ecartLitres, BigDecimal ecartPct,
                              BigDecimal ecartValeur, BigDecimal tolerancePct, String niveau, int jours) {
    }

    /** Total du reseau pour un produit ; ecart en % des sorties. */
    public record TotalProduit(String produitCode, String produitLibelle, BigDecimal receptions, BigDecimal sorties,
                               BigDecimal ecartLitres, BigDecimal ecartPct, BigDecimal ecartValeur) {
    }

    public record SyntheseStocks(YearMonth mois, List<LigneStocks> lignes, List<TotalProduit> totaux) {
    }

    public record CommentaireVue(UUID stationId, YearMonth mois, String texte, Integer version) {
    }
}
