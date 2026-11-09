package sn.digitalperf.jaugeo.tresorerie.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService.BilanImport;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService.FormatVue;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService.OperationVue;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService.Rapprochement;
import sn.digitalperf.jaugeo.tresorerie.application.RapprochementService.RapprochementElectronique;
import sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleve;

@RestController
@RequestMapping("/api/v1/tresorerie")
@Tag(name = "Rapprochement", description = "Relevés bancaires et marchands, lettrage des versements, écarts Wave / Orange Money / carte")
@PreAuthorize("hasAnyAuthority('VERSEMENT_VALIDER', 'CAISSE_CONTROLER')")
class RapprochementController {

    record FormatSaisi(Integer version, @NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 80) String libelle,
                       @NotNull FormatReleve.Nature nature, @NotNull @Size(min = 1, max = 1) String separateur,
                       @NotBlank @Size(max = 20) String formatDate, @Min(0) @Max(50) int lignesIgnorees,
                       @Min(1) @Max(50) int colDate, @Min(1) @Max(50) int colLibelle, @Min(1) @Max(50) Integer colReference,
                       @Min(1) @Max(50) int colCredit, Boolean actif) {
        FormatReleve.Parametres parametres() {
            return new FormatReleve.Parametres(code, libelle, nature, separateur, formatDate, lignesIgnorees, colDate, colLibelle,
                    colReference, colCredit, actif == null || actif);
        }
    }

    record Lettrage(@NotNull UUID versementId) {
    }

    private final RapprochementService service;

    RapprochementController(RapprochementService service) {
        this.service = service;
    }

    @Operation(summary = "Formats de relevé (colonnes, séparateur, format de date) par banque ou opérateur")
    @GetMapping("/formats-releve")
    List<FormatVue> formats() {
        return service.formats();
    }

    @Operation(summary = "Paramètre un nouveau format de relevé")
    @PostMapping("/formats-releve")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    FormatVue creerFormat(@Valid @RequestBody FormatSaisi f) {
        return service.enregistrerFormat(null, null, f.parametres());
    }

    @Operation(summary = "Modifie un format de relevé")
    @PutMapping("/formats-releve/{id}")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    FormatVue modifierFormat(@PathVariable UUID id, @Valid @RequestBody FormatSaisi f) {
        return service.enregistrerFormat(id, f.version(), f.parametres());
    }

    @Operation(summary = "Importe un relevé CSV ou Excel ; un relevé bancaire est aussitôt lettré automatiquement")
    @PostMapping(path = "/releves", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    BilanImport importer(@RequestParam UUID format, @RequestParam(required = false) UUID station,
                         @RequestPart("fichier") MultipartFile fichier) {
        try {
            return service.importer(format, station, fichier.getOriginalFilename(), fichier.getBytes());
        } catch (IOException e) {
            throw ErreurMetier.regle("FICHIER_ILLISIBLE", "Le fichier n'a pas pu être lu.");
        }
    }

    @Operation(summary = "Crédits bancaires du mois, lettrage avec les versements, taux de rapprochement")
    @GetMapping("/rapprochement")
    Rapprochement rapprochement(@RequestParam String mois) {
        return service.rapprochement(mois(mois));
    }

    @Operation(summary = "Relevés Wave, Orange Money et carte comparés aux encaissements déclarés par les stations")
    @GetMapping("/rapprochement/electronique")
    RapprochementElectronique electronique(@RequestParam String mois) {
        return service.electronique(mois(mois));
    }

    @Operation(summary = "Relance le lettrage automatique des crédits bancaires non lettrés")
    @PostMapping("/rapprochement/lettrage-automatique")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    Map<String, Integer> lettrageAutomatique() {
        return Map.of("lettrees", service.lettrerAutomatiquement());
    }

    @Operation(summary = "Lettre manuellement une opération du relevé bancaire avec un versement de même montant")
    @PutMapping("/rapprochement/operations/{id}/lettrage")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    OperationVue lettrer(@PathVariable UUID id, @Valid @RequestBody Lettrage l) {
        return service.lettrer(id, l.versementId());
    }

    @Operation(summary = "Défait le lettrage d'une opération du relevé bancaire")
    @DeleteMapping("/rapprochement/operations/{id}/lettrage")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    OperationVue delettrer(@PathVariable UUID id) {
        return service.delettrer(id);
    }

    private static YearMonth mois(String mois) {
        try {
            return YearMonth.parse(mois);
        } catch (java.time.format.DateTimeParseException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Mois attendu au format AAAA-MM.");
        }
    }
}
