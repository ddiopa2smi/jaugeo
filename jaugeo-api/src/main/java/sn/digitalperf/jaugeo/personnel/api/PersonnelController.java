package sn.digitalperf.jaugeo.personnel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.personnel.application.FeuilleService;
import sn.digitalperf.jaugeo.personnel.application.PersonnelService;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.CreneauVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EcartsStation;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EmployeDetail;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.EmployeVue;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PersonnelStation;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PlanningSemaine;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.PostesJournee;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisieFeuille;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisieLigne;
import sn.digitalperf.jaugeo.personnel.application.VuesPersonnel.SaisiePoste;
import sn.digitalperf.jaugeo.personnel.domaine.Employe;
import sn.digitalperf.jaugeo.personnel.domaine.TypePoste;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Personnel", description = "Employés, planning par poste, feuilles de caisse par pompiste")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class PersonnelController {

    record EmployeSaisi(@Size(max = 20) String matricule, @NotBlank @Size(max = 60) String nom,
                        @NotBlank @Size(max = 60) String prenom, @Size(max = 30) String telephone, Employe.Fonction fonction,
                        LocalDate dateEntree, LocalDate dateSortie, @Size(max = 50) String identifiant, Boolean actif,
                        UUID stationId, Integer version) {
        Employe.Donnees donnees() {
            return new Employe.Donnees(matricule, nom, prenom, telephone, fonction, dateEntree, dateSortie, identifiant, actif);
        }
    }

    record Feuilles(@NotNull Boolean actif) {
    }

    record CreneauSaisi(@NotNull LocalDate date, @NotNull TypePoste poste, @NotNull UUID employeId) {
    }

    record PlanningSaisi(@NotNull LocalDate du, @NotNull @Size(max = 500) List<@Valid @NotNull CreneauSaisi> creneaux) {
    }

    record Reconduction(@NotNull LocalDate du, @NotNull @Min(1) @Max(8) Integer semaines, Boolean rotation) {
    }

    record LigneSaisie(@NotNull UUID pistoletId, @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal indexFin,
                       @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal remiseCuve) {
    }

    record FeuilleSaisie(@NotNull UUID employeId, @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal especes,
                         @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal electronique,
                         @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal bons,
                         @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal cartes, @Size(max = 500) String observation,
                         @NotNull @Size(min = 1, max = 60) List<@Valid @NotNull LigneSaisie> lignes) {
    }

    record PosteSaisi(@NotNull Integer version, @Size(max = 500) String observation,
                      @NotNull @Size(min = 1, max = 30) List<@Valid @NotNull FeuilleSaisie> feuilles) {
    }

    private final PersonnelService personnel;
    private final FeuilleService feuilles;

    PersonnelController(PersonnelService personnel, FeuilleService feuilles) {
        this.personnel = personnel;
        this.feuilles = feuilles;
    }

    // ---------------------------------------------------------------- Employes

    @Operation(summary = "Employés d'une station et réglage des feuilles de caisse")
    @GetMapping("/personnel/stations/{id}")
    PersonnelStation station(@PathVariable UUID id) {
        return personnel.station(id);
    }

    @Operation(summary = "Crée un employé dans la station")
    @PostMapping("/personnel/stations/{id}/employes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    EmployeVue creer(@PathVariable UUID id, @Valid @RequestBody EmployeSaisi e) {
        return personnel.creer(id, e.donnees());
    }

    @Operation(summary = "Modifie un employé ; un changement de station ouvre une nouvelle affectation")
    @PutMapping("/personnel/employes/{id}")
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    EmployeVue modifier(@PathVariable UUID id, @Valid @RequestBody EmployeSaisi e) {
        return personnel.modifier(id, e.version(), e.stationId(), e.donnees());
    }

    @Operation(summary = "Fiche employé : affectations et feuilles de caisse du mois (écarts individuels)")
    @GetMapping("/personnel/employes/{id}")
    EmployeDetail employe(@PathVariable UUID id, @RequestParam(required = false) String mois) {
        return personnel.detail(id, mois(mois));
    }

    @Operation(summary = "Écarts de caisse du mois par employé")
    @GetMapping("/personnel/stations/{id}/ecarts")
    EcartsStation ecarts(@PathVariable UUID id, @RequestParam(required = false) String mois) {
        return personnel.ecarts(id, mois(mois));
    }

    @Operation(summary = "Active ou désactive les feuilles de caisse par pompiste de la station")
    @PutMapping("/personnel/stations/{id}/feuilles-caisse")
    @PreAuthorize("hasAnyAuthority('REFERENTIEL_GERER', 'JOURNEE_VALIDER')")
    PersonnelStation reglerFeuilles(@PathVariable UUID id, @Valid @RequestBody Feuilles f) {
        return personnel.reglerFeuilles(id, f.actif());
    }

    // ---------------------------------------------------------------- Planning

    @Operation(summary = "Planning des 7 jours à partir d'une date")
    @GetMapping("/personnel/stations/{id}/planning")
    PlanningSemaine planning(@PathVariable UUID id, @RequestParam(required = false) LocalDate du) {
        return personnel.planning(id, du);
    }

    @Operation(summary = "Remplace le planning de la semaine")
    @PutMapping("/personnel/stations/{id}/planning")
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    PlanningSemaine planifier(@PathVariable UUID id, @Valid @RequestBody PlanningSaisi p) {
        return personnel.planifier(id, p.du(), p.creneaux().stream().map(c -> new CreneauVue(c.date(), c.poste(), c.employeId())).toList());
    }

    @Operation(summary = "Reconduit la semaine sur les suivantes, avec ou sans rotation des postes")
    @PostMapping("/personnel/stations/{id}/planning/reconduction")
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    PlanningSemaine reconduire(@PathVariable UUID id, @Valid @RequestBody Reconduction r) {
        return personnel.reconduire(id, r.du(), r.semaines(), Boolean.TRUE.equals(r.rotation()));
    }

    // ---------------------------------------------------------------- Feuilles de caisse de la journee

    @Operation(summary = "Postes et feuilles de caisse par pompiste de la journée")
    @GetMapping("/journees/{id}/postes")
    PostesJournee postes(@PathVariable UUID id) {
        return feuilles.journee(id);
    }

    @Operation(summary = "Enregistre les feuilles de caisse d'un poste (index de fin de poste par pistolet, caisse remise)")
    @PutMapping("/journees/{id}/postes/{poste}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    PostesJournee enregistrerPoste(@PathVariable UUID id, @PathVariable TypePoste poste, @Valid @RequestBody PosteSaisi p) {
        return feuilles.enregistrer(id, poste, new SaisiePoste(p.version(), p.observation(), p.feuilles().stream()
                .map(f -> new SaisieFeuille(f.employeId(), f.especes(), f.electronique(), f.bons(), f.cartes(), f.observation(),
                        f.lignes().stream().map(l -> new SaisieLigne(l.pistoletId(), l.indexFin(), l.remiseCuve())).toList()))
                .toList()));
    }

    @Operation(summary = "Retire un poste et ses feuilles de caisse")
    @DeleteMapping("/journees/{id}/postes/{poste}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    PostesJournee supprimerPoste(@PathVariable UUID id, @PathVariable TypePoste poste, @RequestParam Integer version) {
        return feuilles.supprimer(id, poste, version);
    }

    private static YearMonth mois(String mois) {
        if (mois == null || mois.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Le mois s'écrit AAAA-MM.");
        }
    }
}
