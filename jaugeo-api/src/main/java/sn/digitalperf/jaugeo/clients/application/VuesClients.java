package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vues de l'API des clients en compte. */
public final class VuesClients {

    private VuesClients() {
    }

    /** Client avec son encours sur tout le reseau et le disponible sous plafond (nul sans plafond). */
    public record ClientVue(UUID id, String code, String nom, String telephone, String recouvrement, BigDecimal plafond,
                            boolean actif, String observation, BigDecimal encours, BigDecimal disponible, Integer version) {
    }

    public record CompteStation(UUID stationId, String stationCode, String stationNom, BigDecimal creance,
                                LocalDate dernierBon, LocalDate dernierReglement) {
    }

    /** Fiche client : comptes par station du perimetre. */
    public record FicheClient(ClientVue client, List<CompteStation> comptes, boolean peutGerer) {
    }

    /** Mouvement du releve : debit = bon ou reprise, credit = reglement. */
    public record LigneReleve(LocalDate date, String type, String mode, String numero, String produitCode, BigDecimal litres,
                              BigDecimal prixUnitaire, String reference, String observation, BigDecimal debit,
                              BigDecimal credit, BigDecimal solde, UUID operationId, Integer version, boolean supprimable,
                              String creePar) {
    }

    public record Releve(UUID clientId, String clientCode, String clientNom, UUID stationId, String stationCode,
                         LocalDate du, LocalDate au, BigDecimal soldeDebut, BigDecimal totalDebit, BigDecimal totalCredit,
                         BigDecimal soldeFin, List<LigneReleve> lignes) {
    }

    public record BonVue(UUID id, String numero, UUID clientId, String clientCode, String clientNom, UUID produitId,
                         String produitCode, String produitLibelle, BigDecimal litres, BigDecimal prixUnitaire,
                         BigDecimal montant, String recouvrement, String immatriculation, String porteur, String creePar,
                         Instant modifieLe) {
    }

    public record RecouvrementVue(UUID id, UUID clientId, String clientCode, String clientNom, BigDecimal montant,
                                  String reference, String observation, String creePar) {
    }

    /** Produit vendable a la station, au prix de l'ouverture de la journee (nul si aucun prix). */
    public record ProduitCredit(UUID id, String code, String libelle, BigDecimal prixUnitaire) {
    }

    /** Ventes a credit et recouvrements d'une journee ; anomalies = points bloquants pour la cloture. */
    public record CreditJourneeVue(UUID journeeId, UUID stationId, String stationCode, LocalDate date, String statut,
                                   Integer version, boolean modifiable, List<ProduitCredit> produits, List<BonVue> bons,
                                   List<RecouvrementVue> recouvrements, BigDecimal totalBonsDirection,
                                   BigDecimal totalBonsGerant, BigDecimal totalRecouvrements, List<String> anomalies) {
    }

    public record SaisieClient(String code, String nom, String telephone, String recouvrement, BigDecimal plafond,
                               Boolean actif, String observation) {
    }

    public record SaisieBon(UUID clientId, String numero, UUID produitId, BigDecimal litres, String immatriculation,
                            String porteur) {
    }

    public record SaisieOperation(UUID stationId, String type, String mode, LocalDate date, BigDecimal montant,
                                  String reference, String observation) {
    }
}
