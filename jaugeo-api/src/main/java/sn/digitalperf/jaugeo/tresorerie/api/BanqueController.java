package sn.digitalperf.jaugeo.tresorerie.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import sn.digitalperf.jaugeo.tresorerie.application.BanqueService;
import sn.digitalperf.jaugeo.tresorerie.application.BanqueService.BanqueVue;

@RestController
@RequestMapping("/api/v1/tresorerie/banques")
@Tag(name = "Trésorerie", description = "Montant à verser, versements (BV), solde de caisse du gérant, arrêtés et manquants")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class BanqueController {

    record BanqueSaisie(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String nom) {
    }

    record Activation(Integer version, @NotNull Boolean actif) {
    }

    private final BanqueService service;

    BanqueController(BanqueService service) {
        this.service = service;
    }

    @Operation(summary = "Banques proposées à la saisie d'un versement (actives) ; toutes=true : toutes les banques")
    @GetMapping
    List<BanqueVue> liste(@RequestParam(defaultValue = "false") boolean toutes) {
        return service.liste(toutes);
    }

    @Operation(summary = "Ajoute une banque absente de la liste")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REFERENTIEL_GERER')")
    BanqueVue creer(@Valid @RequestBody BanqueSaisie b) {
        return service.creer(b.code(), b.nom());
    }

    @Operation(summary = "Active ou désactive une banque pour la compagnie")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('REFERENTIEL_GERER')")
    BanqueVue activer(@PathVariable UUID id, @Valid @RequestBody Activation a) {
        return service.activer(id, a.version(), a.actif());
    }
}
