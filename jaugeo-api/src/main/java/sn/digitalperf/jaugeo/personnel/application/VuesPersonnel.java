package sn.digitalperf.jaugeo.personnel.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.digitalperf.jaugeo.personnel.domaine.TypePoste;

/** Contrats JSON du personnel : employes, planning, feuilles de caisse. */
public final class VuesPersonnel {

    private VuesPersonnel() {
    }

    // ---------------------------------------------------------------- Employes

    public record EmployeVue(UUID id, UUID stationId, String stationCode, String matricule, String nom, String prenom,
                             String nomComplet, String telephone, String fonction, LocalDate dateEntree,
                             LocalDate dateSortie, String identifiant, boolean actif, Integer version) {
    }

    /** gerer : fiches et planning modifiables ; reglerFeuilles : peut activer les feuilles de caisse. */
    public record PersonnelStation(UUID stationId, String stationCode, String stationNom, boolean feuillesCaisse,
                                   boolean gerer, boolean reglerFeuilles, List<EmployeVue> employes) {
    }

    public record AffectationVue(String stationCode, String stationNom, LocalDate debut, LocalDate fin) {
    }

    /** Feuille de caisse figee : ecart = remis - du (negatif = manquant). */
    public record FeuilleResume(LocalDate date, UUID journeeId, String stationCode, TypePoste poste, BigDecimal du,
                                BigDecimal remis, BigDecimal ecart, String observation) {
    }

    public record EmployeDetail(EmployeVue employe, List<AffectationVue> affectations, LocalDate du, LocalDate au,
                                List<FeuilleResume> feuilles, BigDecimal manquants, BigDecimal excedents) {
    }

    public record EcartEmploye(UUID employeId, String employe, String fonction, boolean actif, int feuilles,
                               BigDecimal du, BigDecimal remis, BigDecimal manquants, BigDecimal excedents, BigDecimal net) {
    }

    public record EcartsStation(UUID stationId, String mois, List<EcartEmploye> lignes, int feuilles, BigDecimal du,
                                BigDecimal remis, BigDecimal manquants, BigDecimal excedents, BigDecimal net) {
    }

    // ---------------------------------------------------------------- Planning

    public record CreneauVue(LocalDate date, TypePoste poste, UUID employeId) {
    }

    public record PlanningSemaine(UUID stationId, LocalDate du, LocalDate au, boolean modifiable,
                                  List<EmployeVue> employes, List<CreneauVue> creneaux) {
    }

    // ---------------------------------------------------------------- Feuilles de caisse de la journee

    public record PistoletVue(UUID pistoletId, String code, String pompeCode, String produitCode, BigDecimal indexDepart,
                              BigDecimal indexArrivee, BigDecimal litresJournee, BigDecimal prixUnitaire) {
    }

    public record LigneVue(UUID pistoletId, String pistoletCode, String pompeCode, String produitCode,
                           BigDecimal indexDebut, BigDecimal indexFin, BigDecimal remiseCuve, BigDecimal litres,
                           BigDecimal prixUnitaire, BigDecimal montant, String anomalie) {
    }

    public record FeuilleVue(UUID id, UUID employeId, String employe, List<LigneVue> lignes, BigDecimal litres,
                             BigDecimal du, BigDecimal especes, BigDecimal electronique, BigDecimal bons,
                             BigDecimal cartes, BigDecimal remis, BigDecimal ecart, String observation) {
    }

    public record PosteVue(TypePoste poste, String libelle, String observation, List<FeuilleVue> feuilles,
                           BigDecimal du, BigDecimal remis, BigDecimal ecart) {
    }

    public record Totaux(BigDecimal litres, BigDecimal du, BigDecimal especes, BigDecimal electronique, BigDecimal bons,
                         BigDecimal cartes, BigDecimal remis, BigDecimal ecart) {
    }

    /**
     * actif : la station tient des feuilles de caisse ; problemes : ce qui s'oppose a la cloture ; planning :
     * pompistes prevus ce jour-la, pour pre-remplir les feuilles.
     */
    public record PostesJournee(UUID journeeId, Integer version, String statut, boolean actif, boolean modifiable,
                                List<PistoletVue> pistolets, List<EmployeVue> employes, List<CreneauVue> planning,
                                List<PosteVue> postes, Totaux totaux, List<String> problemes) {
    }

    // ---------------------------------------------------------------- Saisies

    public record SaisieLigne(UUID pistoletId, BigDecimal indexFin, BigDecimal remiseCuve) {
    }

    public record SaisieFeuille(UUID employeId, BigDecimal especes, BigDecimal electronique, BigDecimal bons,
                                BigDecimal cartes, String observation, List<SaisieLigne> lignes) {
    }

    public record SaisiePoste(Integer version, String observation, List<SaisieFeuille> feuilles) {
    }
}
