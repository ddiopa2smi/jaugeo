package sn.digitalperf.jaugeo.tarification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.tarification.application.PrixService;
import sn.digitalperf.jaugeo.tarification.application.PrixService.ChangementVue;
import sn.digitalperf.jaugeo.tarification.application.PrixService.LigneGrille;
import sn.digitalperf.jaugeo.tarification.application.PrixService.PrixVue;

@Tag(name = "Prix")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class PrixController {

    private static final String MODIFIER = "hasAuthority('PRIX_MODIFIER')";

    record LigneSaisie(@NotBlank String produitCode,
                       @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000")
                       @Digits(integer = 8, fraction = 2) BigDecimal prixUnitaire) {
    }

    record ChangementSaisi(@NotNull Instant debut,
                           @NotBlank @Size(max = 200) String motif,
                           @Size(max = 100) String reference,
                           List<@NotNull UUID> stations,
                           @NotEmpty List<@Valid @NotNull LigneSaisie> prix,
                           boolean confirmerVariation) {
    }

    private final PrixService service;

    PrixController(PrixService service) {
        this.service = service;
    }

    @Operation(summary = "Prix en vigueur et prochains prix programmés, par station et par produit")
    @GetMapping("/prix/grille")
    List<LigneGrille> grille() {
        return service.grille();
    }

    @Operation(summary = "Changements de prix, du plus récent au plus ancien")
    @GetMapping("/prix/changements")
    List<ChangementVue> changements() {
        return service.changements();
    }

    @Operation(summary = "Nouveaux prix à une date et heure d'effet, pour une ou plusieurs stations "
            + "(toutes les stations actives si la liste est vide)")
    @PreAuthorize(MODIFIER)
    @PostMapping("/prix/changements")
    @ResponseStatus(HttpStatus.CREATED)
    ChangementVue appliquer(@Valid @RequestBody ChangementSaisi c) {
        return service.appliquer(new PrixService.Demande(c.debut(), c.motif(), c.reference(), c.stations(),
                c.prix().stream().map(l -> new PrixService.LignePrix(l.produitCode(), l.prixUnitaire())).toList(),
                c.confirmerVariation()));
    }

    @Operation(summary = "Annuler un changement de prix qui n'est pas encore entré en vigueur")
    @PreAuthorize(MODIFIER)
    @PostMapping("/prix/changements/{id}/annulation")
    ChangementVue annuler(@PathVariable UUID id) {
        return service.annuler(id);
    }

    @Operation(summary = "Historique des prix d'une station")
    @GetMapping("/stations/{id}/prix")
    List<PrixVue> historique(@PathVariable UUID id) {
        return service.historique(id);
    }
}
