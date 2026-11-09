package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.digitalperf.jaugeo.clients.domaine.Client.Contrat;

/** Vues de l'API des clients en compte. */
public final class VuesClients {

    private VuesClients() {
    }

    /**
     * Client avec son encours sur tout le reseau, le disponible sous plafond (nul sans plafond) et le montant echu
     * non regle (factures et reprises dont l'echeance est passee).
     */
    public record ClientVue(UUID id, String code, String nom, String telephone, String recouvrement, BigDecimal plafond,
                            boolean actif, String observation, BigDecimal encours, BigDecimal disponible, BigDecimal echu,
                            Contrat contrat, Integer version) {
    }

    public record CompteStation(UUID stationId, String stationCode, String stationNom, BigDecimal creance,
                                LocalDate dernierBon, LocalDate dernierReglement) {
    }

    public record EnginVue(UUID id, String type, String immatriculation, String libelle, boolean actif, Integer version) {
    }

    /** Remise au litre d'un produit a partir d'une date ; en vigueur = la plus recente commencee a ce jour. */
    public record RemiseVue(UUID id, UUID produitId, String produitCode, String produitLibelle, LocalDate debut, BigDecimal remise,
                            boolean enVigueur) {
    }

    /** Fiche client : comptes par station du perimetre, engins autorises, remises, factures, cartes et relances. */
    public record FicheClient(ClientVue client, List<CompteStation> comptes, List<EnginVue> engins, List<RemiseVue> remises,
                              List<FactureVue> factures, List<CarteVue> cartes, List<RelanceVue> relances, boolean peutGerer) {
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

    /**
     * Bon a credit ; prixUnitaire = prix pompe - remise, montant du par le client, montantPompe = valeur sortie de la
     * recette. Statut VALIDE, EN_ATTENTE (motif de blocage), ACCORDE ou REFUSE (decision de la direction).
     */
    public record BonVue(UUID id, String numero, UUID clientId, String clientCode, String clientNom, UUID produitId,
                         String produitCode, String produitLibelle, BigDecimal litres, BigDecimal prixUnitaire,
                         BigDecimal remiseUnitaire, BigDecimal montant, BigDecimal montantPompe, String recouvrement,
                         String immatriculation, String porteur, String statut, String motifBlocage, String derogationPar,
                         String derogationMotif, boolean facture, String creePar, Instant modifieLe, Integer version) {
    }

    public record RecouvrementVue(UUID id, UUID clientId, String clientCode, String clientNom, BigDecimal montant,
                                  String reference, String observation, String creePar) {
    }

    /** Plein paye par carte prepayee dans la journee. */
    public record PleinCarteVue(UUID id, String carteNumero, String porteur, String clientCode, String clientNom,
                                String immatriculation, UUID produitId, String produitCode, BigDecimal litres,
                                BigDecimal prixUnitaire, BigDecimal montant, BigDecimal montantPompe, String reference,
                                String creePar) {
    }

    /** Produit vendable a la station, au prix de l'ouverture de la journee (nul si aucun prix). */
    public record ProduitCredit(UUID id, String code, String libelle, BigDecimal prixUnitaire) {
    }

    /**
     * Ventes a credit, pleins payes par carte et recouvrements d'une journee ; anomalies = points bloquants pour la
     * cloture (bons en attente de derogation compris). Les totaux de bons et de cartes sont a la valeur pompe.
     */
    public record CreditJourneeVue(UUID journeeId, UUID stationId, String stationCode, LocalDate date, String statut,
                                   Integer version, boolean modifiable, List<ProduitCredit> produits, List<BonVue> bons,
                                   List<PleinCarteVue> cartes, List<RecouvrementVue> recouvrements,
                                   BigDecimal totalBonsDirection, BigDecimal totalBonsGerant, BigDecimal totalCartes,
                                   BigDecimal totalRecouvrements, int bonsEnAttente, List<String> anomalies) {
    }

    /** Bon en attente de la decision de la direction. */
    public record BonEnAttenteVue(UUID id, Integer version, UUID journeeId, UUID stationId, String stationCode,
                                  LocalDate date, String journeeStatut, UUID clientId, String clientCode, String clientNom,
                                  String numero, String produitCode, BigDecimal litres, BigDecimal montant,
                                  String immatriculation, String motifBlocage, String creePar) {
    }

    public record SaisieClient(String code, String nom, String telephone, String recouvrement, BigDecimal plafond,
                               Boolean actif, String observation, Contrat contrat) {
    }

    public record SaisieBon(UUID clientId, String numero, UUID produitId, BigDecimal litres, String immatriculation,
                            String porteur) {
    }

    public record SaisieOperation(UUID stationId, String type, String mode, LocalDate date, BigDecimal montant,
                                  String reference, String observation) {
    }

    // ---------------------------------------------------------------- Facturation

    /** Facture ; reste = part non reglee apres imputation des reglements (nul pour une facture annulee). */
    public record FactureVue(UUID id, String numero, UUID clientId, String clientCode, String clientNom, LocalDate periodeDebut,
                             LocalDate periodeFin, LocalDate dateFacture, LocalDate dateEcheance, BigDecimal montant,
                             BigDecimal reste, String statut, String motifAnnulation, int bons, String creePar,
                             Integer version) {
    }

    public record LigneFacture(LocalDate date, String stationCode, String numero, String produitCode, BigDecimal litres,
                               BigDecimal prixPompe, BigDecimal remise, BigDecimal prixUnitaire, BigDecimal montant,
                               String immatriculation) {
    }

    public record FactureDetail(FactureVue facture, List<LigneFacture> lignes) {
    }

    /** Factures emises par une facturation, et clients sans bon a facturer ignores. */
    public record ResultatFacturation(List<FactureVue> factures, int bonsFactures, List<String> remarques) {
    }

    /** Balance agee d'un client a une date : tranches selon l'anciennete du document, echu = echeance passee. */
    public record LigneBalance(UUID clientId, String code, String nom, String recouvrement, Integer delaiPaiement,
                               BigDecimal plafond, BigDecimal nonFacture, BigDecimal jours0a30, BigDecimal jours31a60,
                               BigDecimal jours61a90, BigDecimal plusDe90, BigDecimal total, BigDecimal echu,
                               LocalDate plusAncienneEcheance, LocalDate derniereRelance, Integer dernierNiveau,
                               int niveauSuggere) {
    }

    public record BalanceAgee(LocalDate au, List<LigneBalance> lignes, BigDecimal nonFacture, BigDecimal jours0a30,
                              BigDecimal jours31a60, BigDecimal jours61a90, BigDecimal plusDe90, BigDecimal total,
                              BigDecimal echu) {
    }

    public record RelanceVue(UUID id, LocalDate date, int niveau, String mode, BigDecimal montantEchu, String commentaire,
                             String creePar) {
    }

    // ---------------------------------------------------------------- Cartes prepayees

    public record CarteVue(UUID id, String numero, UUID clientId, String clientCode, String clientNom, String porteur,
                           String immatriculation, String statut, BigDecimal solde, Integer version) {
    }

    public record MouvementCarteVue(UUID id, String type, LocalDate date, BigDecimal montant, String mode, String reference,
                                    String stationCode, String produitCode, BigDecimal litres, BigDecimal prixUnitaire,
                                    String creePar) {
    }

    public record CarteDetail(CarteVue carte, List<MouvementCarteVue> mouvements, boolean peutGerer) {
    }
}
