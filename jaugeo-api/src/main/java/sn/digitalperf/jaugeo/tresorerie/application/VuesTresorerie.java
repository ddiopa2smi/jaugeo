package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Vues de l'API de tresorerie. */
public final class VuesTresorerie {

    private VuesTresorerie() {
    }

    public record PieceVue(String nomFichier, String typeMime, int taille) {
    }

    public record VersementVue(UUID id, UUID stationId, String stationCode, LocalDate dateDepot, String banque,
                               String referenceBv, BigDecimal montant, String deposant, String observation, String statut,
                               PieceVue piece, String verifiePar, Instant verifieLe, String motifRejet, String creePar,
                               Instant creeLe, Integer version, boolean modifiable, boolean verifiable) {
    }

    /**
     * Recette d'une journee : montant a verser (fige a la cloture, sinon apercu), solde de caisse du gerant
     * a l'ouverture, versements deposes ce jour et solde de fin de journee.
     */
    public record RecetteVue(UUID journeeId, UUID stationId, String stationCode, LocalDate date, String statut, boolean fige,
                             BigDecimal ventes, BigDecimal bonsDirection, BigDecimal bonsGerant, BigDecimal depenses,
                             BigDecimal complements, BigDecimal montantAVerser, BigDecimal soldeOuverture,
                             List<VersementVue> versements, BigDecimal totalVersements, BigDecimal soldeFin,
                             ArreteVue arrete, boolean peutVerser) {
    }

    public record ArreteVue(UUID id, UUID stationId, String stationCode, LocalDate date, String type,
                            BigDecimal soldeTheorique, BigDecimal especesComptees, BigDecimal ecart, String responsable,
                            String observation, String creePar, Instant creeLe, UUID manquantId, String manquantStatut) {
    }

    /** Ligne du livre de caisse ; montant a verser nul tant que la journee n'est pas cloturee. */
    public record LigneCaisse(LocalDate date, UUID journeeId, String statutJournee, BigDecimal ventes, BigDecimal bons,
                              BigDecimal depenses, BigDecimal complements, BigDecimal montantAVerser,
                              BigDecimal soldeOuverture, BigDecimal versements, BigDecimal soldeTheorique,
                              ArreteVue arrete, BigDecimal ecart, BigDecimal solde) {
    }

    /** Livre de caisse mensuel d'une station : ecart de versement (solde non verse) jour par jour. */
    public record LivreCaisse(UUID stationId, String stationCode, String stationNom, YearMonth mois, BigDecimal soldeDebut,
                              BigDecimal totalVentes, BigDecimal totalDepenses, BigDecimal totalComplements,
                              BigDecimal totalMontants, BigDecimal totalVersements, BigDecimal totalEcarts,
                              BigDecimal soldeFin, int joursNonClotures, List<LigneCaisse> lignes, boolean peutArreter) {
    }

    public record ManquantVue(UUID id, UUID stationId, String stationCode, LocalDate date, BigDecimal montant,
                              String responsable, String observation, String statut, String modeReglement,
                              String referenceReglement, LocalDate regleLe, String reglePar, Integer version,
                              boolean reglable) {
    }

    /** Situation de caisse d'une station au jour. */
    public record SituationStation(UUID stationId, String stationCode, String stationNom, BigDecimal soldeNonVerse,
                                   LocalDate derniereRecette, LocalDate dernierVersement, Long joursSansVersement,
                                   boolean retard, int versementsAVerifier, int manquantsOuverts,
                                   BigDecimal montantManquants, LocalDate dernierArrete, BigDecimal dernierEcart) {
    }

    public record Situation(List<SituationStation> stations, int retards, int versementsAVerifier, int manquantsOuverts,
                            BigDecimal montantManquants, int delaiVersementJours) {
    }
}
