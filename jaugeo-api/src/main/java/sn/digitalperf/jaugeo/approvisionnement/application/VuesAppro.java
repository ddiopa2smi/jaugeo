package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrats JSON du module approvisionnement. */
public final class VuesAppro {

    private VuesAppro() {
    }

    // ---------------------------------------------------------------- Fournisseurs

    public record PrixAchatVue(UUID id, UUID produitId, String produitCode, String produitLibelle, LocalDate debut,
                               BigDecimal prix, boolean enVigueur) {
    }

    public record FournisseurVue(UUID id, String nom, String telephone, String adresse, int delaiLivraison,
                                 boolean principal, boolean factureA15, boolean actif, Integer version,
                                 List<PrixAchatVue> prix) {
    }

    // ---------------------------------------------------------------- Transporteurs

    public record CiterneVue(UUID id, String immatriculation, BigDecimal capaciteLitres, boolean actif, Integer version) {
    }

    /** Notation sur la periode glissante : note sur 5, nulle sans livraison receptionnee. */
    public record Notation(BigDecimal note, int livraisons, BigDecimal ponctualitePct, BigDecimal conformitePct,
                           BigDecimal tauxManquantPct, int horsTolerance, int jours) {
    }

    public record TransporteurVue(UUID id, String nom, String telephone, String contratReference, LocalDate contratDebut,
                                  LocalDate contratFin, BigDecimal tolerancePct, BigDecimal toleranceAppliquee,
                                  BigDecimal penalite, boolean actif, String observation, Integer version,
                                  List<CiterneVue> citernes, Notation notation, BigDecimal penalitesARegler) {
    }

    public record PenaliteVue(UUID id, UUID transporteurId, String transporteurNom, String tourneeNumero,
                              String stationCode, LocalDate date, UUID journeeId, String numeroBl,
                              BigDecimal manquantLitres, BigDecimal manquantPct, BigDecimal tolerancePct,
                              BigDecimal montant, String statut, String referenceReglement, LocalDate regleLe,
                              String reglePar, Integer version) {
    }

    public record TransporteurDetail(TransporteurVue transporteur, List<PenaliteVue> penalites,
                                     List<LivraisonNotee> livraisons) {
    }

    /** Livraison receptionnee prise en compte dans la notation. */
    public record LivraisonNotee(String tourneeNumero, String stationCode, String produitCode, LocalDate prevue,
                                 LocalDate recue, BigDecimal quantiteLivree, BigDecimal manquant,
                                 BigDecimal manquantPct, BigDecimal tolerancePct, boolean aLHeure,
                                 boolean horsTolerance) {
    }

    // ---------------------------------------------------------------- Proposition de commande

    public record PropositionLigne(UUID stationId, String stationCode, String stationNom, UUID produitId,
                                   String produitCode, String produitLibelle, LocalDate dateStock,
                                   BigDecimal stockAujourdhui, BigDecimal capaciteUtile, BigDecimal ventesMoyennes,
                                   BigDecimal autonomieJours, BigDecimal enCours, BigDecimal stockArrivee,
                                   BigDecimal quantiteProposee, LocalDate ruptureLe, boolean aCommander,
                                   String motif) {
    }

    /** Proposition calculee pour le depot principal (delai) ; sans depot principal, delai nul. */
    public record Propositions(UUID fournisseurId, String fournisseurNom, int delaiLivraison, int margeJours,
                               BigDecimal tauxRemplissagePct, LocalDate date, List<PropositionLigne> lignes) {
    }

    // ---------------------------------------------------------------- Tournees

    /** Reception rattachee a une livraison : quantites dans la base de comparaison (15 °C ou ambiante). */
    public record ReceptionLivraison(LocalDate date, String numeroBl, BigDecimal quantiteLivree,
                                     BigDecimal quantiteMesuree, BigDecimal manquant, BigDecimal manquantPct,
                                     BigDecimal tolerancePct, boolean horsTolerance, boolean compareeA15,
                                     boolean aLHeure) {
    }

    public record LivraisonVue(UUID id, int ordre, UUID stationId, String stationCode, String stationNom,
                               UUID produitId, String produitCode, BigDecimal quantiteCommandee, String numeroBl,
                               BigDecimal quantiteBl, BigDecimal quantiteBl15, ReceptionLivraison reception,
                               Integer version) {
    }

    /** Statut affiche : DEPOTEE quand chaque livraison est receptionnee. */
    public record TourneeVue(UUID id, String numero, String statut, UUID fournisseurId, String fournisseurNom,
                             UUID transporteurId, String transporteurNom, UUID citerneId, String immatriculation,
                             BigDecimal capaciteCiterne, String chauffeur, LocalDate dateChargementPrevue,
                             LocalDate dateLivraisonPrevue, LocalDate chargeLe, String scelles, Instant partiLe,
                             String motifAnnulation, String observation, BigDecimal quantiteCommandee,
                             int livraisons, int receptionnees, Integer version, List<LivraisonVue> lignes) {
    }

    // ---------------------------------------------------------------- Factures fournisseur

    public record FactureFournisseurVue(UUID id, UUID fournisseurId, String fournisseurNom, String numero,
                                        LocalDate dateFacture, String statut, BigDecimal montant,
                                        BigDecimal montantAttendu, BigDecimal ecart, int lignes, int anomalies,
                                        String motifDecision, String decidePar, Instant decideLe,
                                        String observation, Integer version) {
    }

    /**
     * Ligne controlee : quantite facturee comparee au BL (15 °C si le fournisseur facture a 15 °C), manquant impute
     * au fournisseur a deduire, prix compare au prix convenu a la date du BL ; montant attendu = (quantite du BL -
     * manquant fournisseur) x prix convenu.
     */
    public record LigneControlee(String numeroBl, UUID produitId, String produitCode, BigDecimal quantite,
                                 BigDecimal prixUnitaire, BigDecimal montant, LocalDate dateReception,
                                 String stationCode, String base, BigDecimal quantiteBl, BigDecimal quantiteRecue,
                                 BigDecimal manquantFournisseur, BigDecimal prixConvenu, BigDecimal montantAttendu,
                                 List<String> anomalies) {
    }

    public record FactureFournisseurDetail(FactureFournisseurVue facture, List<LigneControlee> lignes) {
    }

    // ---------------------------------------------------------------- Saisies

    public record SaisieLivraison(UUID stationId, UUID produitId, BigDecimal quantite) {
    }

    public record SaisieTournee(UUID fournisseurId, UUID transporteurId, UUID citerneId, String immatriculation,
                                String chauffeur, LocalDate dateChargementPrevue, LocalDate dateLivraisonPrevue,
                                String observation, List<SaisieLivraison> livraisons, Integer version) {
    }

    public record SaisieBl(UUID livraisonId, String numeroBl, BigDecimal quantiteBl, BigDecimal quantiteBl15) {
    }

    public record SaisieLigneFacture(String numeroBl, UUID produitId, BigDecimal quantite, BigDecimal prixUnitaire) {
    }

    public record SaisieFacture(UUID fournisseurId, String numero, LocalDate dateFacture, String observation,
                                List<SaisieLigneFacture> lignes, Integer version) {
    }
}
