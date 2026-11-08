package sn.digitalperf.jaugeo.referentiel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.referentiel.application.EquipementService;
import sn.digitalperf.jaugeo.referentiel.application.Vues.BaremeVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.EvenementVue;
import sn.digitalperf.jaugeo.referentiel.domaine.Bareme;
import sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement;

@Tag(name = "Équipements : barèmes et historique")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class EquipementController {

    private static final String GERER = "hasAuthority('REFERENTIEL_GERER')";

    record PointSaisi(@NotNull @Min(0) @Max(10_000) Integer hauteurMm,
                      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal volumeLitres) {
    }

    record BaremeSaisi(@Size(max = 100) String certificat,
                       LocalDate dateCertificat,
                       @NotEmpty @Size(max = Bareme.POINTS_MAX) List<@Valid @NotNull PointSaisi> points,
                       @NotNull Integer version) {
    }

    record EvenementSaisi(@NotNull EvenementEquipement.Type type,
                          @NotNull Instant date,
                          @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal indexAvant,
                          @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal indexApres,
                          @Size(max = 100) String referenceScelles,
                          @Size(max = 100) String intervenant,
                          LocalDate prochaineVerification,
                          @Size(max = 500) String commentaire) {
        EvenementEquipement.Saisie saisie() {
            return new EvenementEquipement.Saisie(type, date, indexAvant, indexApres, referenceScelles, intervenant,
                    prochaineVerification, commentaire);
        }
    }

    private final EquipementService service;

    EquipementController(EquipementService service) {
        this.service = service;
    }

    @Operation(summary = "Barème de jaugeage d'une cuve (hauteur en mm → volume en litres)")
    @GetMapping("/cuves/{id}/bareme")
    BaremeVue bareme(@PathVariable UUID id) {
        return service.bareme(id);
    }

    @Operation(summary = "Remplacer le barème d'une cuve (nouveau certificat de jaugeage)")
    @PreAuthorize(GERER)
    @PutMapping("/cuves/{id}/bareme")
    BaremeVue remplacerBareme(@PathVariable UUID id, @Valid @RequestBody BaremeSaisi b) {
        return service.remplacerBareme(id, b.certificat(), b.dateCertificat(),
                b.points().stream().map(p -> new Bareme.Point(p.hauteurMm(), p.volumeLitres())).toList(), b.version());
    }

    @Operation(summary = "Volume correspondant à une hauteur de jauge")
    @GetMapping("/cuves/{id}/bareme/volume")
    Map<String, Object> volume(@PathVariable UUID id, @RequestParam @Min(0) @Max(10_000) int hauteurMm) {
        return Map.of("hauteurMm", hauteurMm, "volumeLitres", service.volume(id, hauteurMm));
    }

    @Operation(summary = "Historique des équipements d'une station, du plus récent au plus ancien")
    @GetMapping("/stations/{id}/evenements")
    List<EvenementVue> historique(@PathVariable UUID id) {
        return service.historique(id);
    }

    @Operation(summary = "Événement sur un pistolet (changement de compteur, étalonnage, scellés…)")
    @PreAuthorize(GERER)
    @PostMapping("/pistolets/{id}/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    EvenementVue surPistolet(@PathVariable UUID id, @Valid @RequestBody EvenementSaisi e) {
        return service.surPistolet(id, e.saisie());
    }

    @Operation(summary = "Événement sur une pompe")
    @PreAuthorize(GERER)
    @PostMapping("/pompes/{id}/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    EvenementVue surPompe(@PathVariable UUID id, @Valid @RequestBody EvenementSaisi e) {
        return service.surPompe(id, e.saisie());
    }

    @Operation(summary = "Événement sur une cuve")
    @PreAuthorize(GERER)
    @PostMapping("/cuves/{id}/evenements")
    @ResponseStatus(HttpStatus.CREATED)
    EvenementVue surCuve(@PathVariable UUID id, @Valid @RequestBody EvenementSaisi e) {
        return service.surCuve(id, e.saisie());
    }
}
