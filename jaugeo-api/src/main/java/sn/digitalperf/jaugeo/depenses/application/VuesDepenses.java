package sn.digitalperf.jaugeo.depenses.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vues de l'API des depenses. */
public final class VuesDepenses {

    private VuesDepenses() {
    }

    public record NatureVue(UUID id, String code, String libelle, BigDecimal plafond, boolean actif, Integer version) {
    }

    public record JustificatifVue(String nomFichier, String typeMime, int taille) {
    }

    public record DepenseVue(UUID id, UUID natureId, String natureCode, String natureLibelle, String libelle,
                             String beneficiaire, BigDecimal montant, BigDecimal plafond, String statut,
                             JustificatifVue justificatif, String decisionPar, Instant decisionLe, String motifDecision,
                             String creePar, Instant modifieLe, boolean decidable) {
    }

    /** Depenses d'une journee ; anomalies = points bloquants pour la cloture. */
    public record DepensesJourneeVue(UUID journeeId, UUID stationId, String stationCode, LocalDate date, String statut,
                                     Integer version, boolean modifiable, boolean justificatifObligatoire,
                                     List<DepenseVue> depenses, BigDecimal totalAutorise, BigDecimal totalEnAttente,
                                     BigDecimal totalRefuse, List<String> anomalies) {
    }

    /** Depense en attente de decision, pour la file du superviseur. */
    public record DepenseAValider(UUID id, UUID journeeId, Integer journeeVersion, UUID stationId, String stationCode,
                                  LocalDate date, String natureLibelle, String libelle, String beneficiaire,
                                  BigDecimal montant, BigDecimal plafond, String creePar, boolean justificatif,
                                  boolean decidable) {
    }

    /** Saisie d'une depense. */
    public record SaisieDepense(UUID natureId, String libelle, String beneficiaire, BigDecimal montant) {
    }
}
