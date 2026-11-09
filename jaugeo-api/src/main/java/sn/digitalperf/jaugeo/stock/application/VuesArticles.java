package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Vues des lubrifiants et articles boutique (stock au CMUP, entrees, ventes de la journee, inventaire tournant). */
public final class VuesArticles {

    private VuesArticles() {
    }

    /**
     * Article d'une station : stock (ventes des journees cloturees deduites), ventes saisies dans une journee
     * en cours, CMUP et valeur du stock, prix de vente du jour, inventaire tournant.
     */
    public record ArticleVue(UUID produitId, String code, String libelle, String unite, BigDecimal quantite,
                             BigDecimal ventesEnCours, BigDecimal disponible, BigDecimal cmup, BigDecimal valeur,
                             BigDecimal prixVente, LocalDate dernierComptage, boolean aCompter, boolean enRetard) {
    }

    public record EntreeVue(UUID id, Integer version, UUID produitId, String produitCode, String produitLibelle,
                            String unite, BigDecimal quantite, BigDecimal coutUnitaire, BigDecimal valeur,
                            String fournisseur, String reference, String statut, BigDecimal quantiteRecue,
                            LocalDate recueLe, String recuePar, String observation, Instant annonceeLe, String annonceePar) {
    }

    public record StockArticles(UUID stationId, String stationCode, String stationNom, List<ArticleVue> articles,
                                BigDecimal valeurStock, int nombreACompter, List<EntreeVue> entreesAnnoncees,
                                boolean peutAnnoncer, boolean peutRecevoir, boolean peutCompter) {
    }

    public record MouvementVue(LocalDate date, String produitCode, String produitLibelle, String type,
                               BigDecimal quantite, BigDecimal coutUnitaire, BigDecimal valeur, BigDecimal stockApres,
                               BigDecimal cmupApres, String libelle) {
    }

    public record MouvementsArticles(UUID stationId, String stationCode, YearMonth mois, List<MouvementVue> mouvements,
                                     BigDecimal valeurEntrees, BigDecimal valeurVentes, BigDecimal valeurEcarts) {
    }

    /** Ligne de vente d'une journee ; disponible = stock moins les ventes d'autres journees en cours. */
    public record VenteVue(UUID produitId, String code, String libelle, String unite, BigDecimal disponible,
                           BigDecimal prixUnitaire, BigDecimal quantite, BigDecimal montant, BigDecimal cmup) {
    }

    public record VentesJournee(UUID journeeId, Integer version, String statut, boolean modifiable, List<VenteVue> articles,
                                BigDecimal total) {
    }

    public record SaisieVente(UUID produitId, BigDecimal quantite) {
    }

    public record SaisieComptage(UUID produitId, BigDecimal compte, String observation) {
    }

    public record SaisieEntree(UUID produitId, BigDecimal quantite, BigDecimal coutUnitaire, String fournisseur,
                               String reference) {
    }
}
