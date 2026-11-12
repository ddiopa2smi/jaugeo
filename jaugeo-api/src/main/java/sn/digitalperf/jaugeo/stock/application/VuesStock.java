package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Contrats JSON du module stocks. */
public final class VuesStock {

    private VuesStock() {
    }

    public record AnomalieVue(String code, String message) {
    }

    /** Volume = produit, eau deduite ; volume d'eau lu sur le bareme (nul sans bareme). */
    public record JaugeageVue(Integer hauteurMm, BigDecimal volumeLitres, String observation, String par, Instant le,
                              Integer hauteurEauMm, BigDecimal volumeEauLitres) {
    }

    /**
     * Stock d'une cuve pour une journee. Le depart est reporte du jaugeage de cloture de la veille ;
     * a defaut (premiere journee suivie), un jaugeage d'ouverture est demande.
     */
    public record CuveStock(UUID cuveId, String cuveCode, String produitCode, String produitLibelle,
                            BigDecimal capaciteLitres, boolean bareme, boolean departReporte, boolean ouvertureRequise,
                            BigDecimal stockDepart, JaugeageVue ouverture, BigDecimal receptions, BigDecimal sorties,
                            BigDecimal stockTheorique, JaugeageVue cloture, BigDecimal ecartLitres,
                            BigDecimal ecartPctJour, BigDecimal prixUnitaire, BigDecimal ecartValeur,
                            BigDecimal cumulSortiesMois, BigDecimal cumulEcartMois, BigDecimal ecartPctMois,
                            BigDecimal tolerancePct, String niveau, boolean observationRequise,
                            List<AnomalieVue> anomalies, Integer seuilEauMm, List<AnomalieVue> alertes) {
    }

    public record ReceptionVue(UUID id, UUID cuveId, String cuveCode, String produitCode, String numeroBl,
                               String fournisseur, String transporteur, String chauffeur, String immatriculation,
                               String scelles, BigDecimal quantiteBl, BigDecimal jaugeAvant, BigDecimal jaugeApres,
                               BigDecimal quantiteRecue, BigDecimal temperatureC, BigDecimal densite,
                               String observation, BigDecimal ecartLivraison, BigDecimal ecartLivraisonPct,
                               BigDecimal complementLitres, String complementStatut, String par, Instant le,
                               BigDecimal quantiteBl15, BigDecimal facteur15, BigDecimal quantiteRecue15,
                               boolean compareeA15, Boolean scellesIntacts, String imputation, UUID livraisonId,
                               String tourneeNumero, BigDecimal tolerancePct) {
    }

    /** Livraison d'une tournee citerne attendue a la station : la reception s'y rattache et en reprend le BL. */
    public record LivraisonAttendueVue(UUID id, String tourneeNumero, String tourneeStatut, String produitCode,
                                       BigDecimal quantiteCommandee, String numeroBl, BigDecimal quantiteBl,
                                       BigDecimal quantiteBl15, String fournisseur, String transporteur,
                                       String chauffeur, String immatriculation, String scelles,
                                       LocalDate dateLivraisonPrevue, BigDecimal tolerancePct) {
    }

    public record StockJournee(UUID journeeId, UUID stationId, String stationCode, LocalDate date, String statut,
                               Integer version, boolean modifiable, List<CuveStock> cuves,
                               List<ReceptionVue> receptions, int anomalies, BigDecimal toleranceTransportPct,
                               List<LivraisonAttendueVue> livraisonsAttendues, List<String> fournisseurs) {
    }

    // ---------------------------------------------------------------- Analyse mensuelle

    public record LigneStock(LocalDate date, UUID journeeId, UUID cuveId, String cuveCode, String produitCode,
                             BigDecimal stockDepart, BigDecimal receptions, BigDecimal sorties,
                             BigDecimal stockTheorique, BigDecimal stockPhysique, BigDecimal ecartLitres,
                             BigDecimal ecartValeur, BigDecimal cumulEcartMois, BigDecimal ecartPctMois,
                             String niveau, String observation) {
    }

    public record TotalCuve(UUID cuveId, String cuveCode, String produitCode, String produitLibelle,
                            BigDecimal stockDepart, BigDecimal receptions, BigDecimal sorties,
                            BigDecimal stockTheorique, BigDecimal stockPhysique, BigDecimal ecartLitres,
                            BigDecimal ecartPct, BigDecimal ecartValeur, BigDecimal tolerancePct, String niveau,
                            int jours) {
    }

    public record AnalyseMensuelle(UUID stationId, String stationCode, String stationNom, YearMonth mois,
                                   List<TotalCuve> totaux, List<LigneStock> lignes) {
    }

    // ---------------------------------------------------------------- Situation et alertes

    public record SituationCuve(UUID stationId, String stationCode, String stationNom, UUID cuveId, String cuveCode,
                                String produitCode, String produitLibelle, LocalDate date, BigDecimal stockPhysique,
                                BigDecimal capaciteLitres, BigDecimal cumulEcartMois, BigDecimal ecartPctMois,
                                BigDecimal tolerancePct, String niveau, String observation,
                                BigDecimal creuxSecuriteLitres, Integer hauteurEauMm, Integer seuilEauMm,
                                boolean eauAlerte, BigDecimal ventesMoyennes, int joursMoyenne,
                                BigDecimal autonomieJours, BigDecimal seuilReapproLitres, boolean aReapprovisionner) {
    }

    /** Alertes = cuves hors tolerance d'ecart ; eau et reapprovisionnement comptes a part. */
    public record Situation(List<SituationCuve> cuves, int alertes, int complementsARegler,
                            BigDecimal montantComplementsARegler, int cuvesEau, int cuvesAReapprovisionner,
                            BigDecimal autonomieAlerteJours) {
    }

    public record ComplementVue(UUID id, UUID stationId, String stationCode, LocalDate date, UUID journeeId,
                                String numeroBl, String fournisseur, String transporteur, String immatriculation,
                                String produitCode, BigDecimal quantiteBl, BigDecimal quantiteRecue,
                                BigDecimal litres, BigDecimal prixUnitaire, BigDecimal montant, String statut,
                                String referenceReglement, LocalDate regleLe, String reglePar, Integer version,
                                boolean reglable, String imputeA, boolean a15) {
    }

    // ---------------------------------------------------------------- Saisies

    /**
     * Hauteur (mm, convertie par le bareme) ou volume lu ; les deux nuls = jaugeage retire. Hauteur d'eau en fond
     * de cuve : deduite du volume lu sur le bareme ; un volume lu directement (jauge electronique) est deja net.
     */
    public record SaisieJaugeage(UUID cuveId, String moment, Integer hauteurMm, BigDecimal volumeLitres,
                                 String observation, Integer hauteurEauMm) {
    }
}
