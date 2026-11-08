package sn.digitalperf.jaugeo.depenses.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import sn.digitalperf.jaugeo.commun.PieceJointe;
import sn.digitalperf.jaugeo.depenses.application.DepenseService;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.DepenseAValider;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.DepensesJourneeVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.NatureVue;
import sn.digitalperf.jaugeo.depenses.application.VuesDepenses.SaisieDepense;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dépenses", description = "Dépenses de station payées sur la recette, plafonds et justificatifs")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class DepenseController {

    record NatureSaisie(@Size(max = 30) String code, @NotBlank @Size(max = 80) String libelle,
                        @Positive @Digits(integer = 15, fraction = 0) BigDecimal plafond, Boolean actif, Integer version) {
    }

    record DepenseSaisie(@NotNull Integer version, @NotNull UUID natureId, @NotBlank @Size(max = 200) String libelle,
                         @Size(max = 120) String beneficiaire,
                         @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal montant) {
        SaisieDepense saisie() {
            return new SaisieDepense(natureId, libelle, beneficiaire, montant);
        }
    }

    record Decision(@NotNull Integer version, @NotNull Boolean autoriser, @Size(max = 500) String motif) {
    }

    private final DepenseService service;

    DepenseController(DepenseService service) {
        this.service = service;
    }

    @Operation(summary = "Natures de dépense et plafonds")
    @GetMapping("/depenses/natures")
    List<NatureVue> natures() {
        return service.natures();
    }

    @Operation(summary = "Crée une nature de dépense")
    @PostMapping("/depenses/natures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REFERENTIEL_GERER')")
    NatureVue creerNature(@Valid @RequestBody NatureSaisie n) {
        return service.creerNature(n.code(), n.libelle(), n.plafond());
    }

    @Operation(summary = "Modifie le libellé, le plafond ou l'état d'une nature de dépense")
    @PutMapping("/depenses/natures/{id}")
    @PreAuthorize("hasAuthority('REFERENTIEL_GERER')")
    NatureVue modifierNature(@PathVariable UUID id, @Valid @RequestBody NatureSaisie n) {
        return service.modifierNature(id, n.version(), n.libelle(), n.plafond(), n.actif() == null || n.actif());
    }

    @Operation(summary = "Dépenses en attente de validation (au-delà du plafond) dans le périmètre")
    @GetMapping("/depenses/a-valider")
    List<DepenseAValider> aValider() {
        return service.aValider();
    }

    @Operation(summary = "Dépenses de la journée")
    @GetMapping("/journees/{id}/depenses")
    DepensesJourneeVue journee(@PathVariable UUID id) {
        return service.journee(id);
    }

    @Operation(summary = "Ajoute une dépense payée sur la recette de la journée")
    @PostMapping("/journees/{id}/depenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    DepensesJourneeVue ajouter(@PathVariable UUID id, @Valid @RequestBody DepenseSaisie d) {
        return service.ajouter(id, d.version(), d.saisie());
    }

    @Operation(summary = "Corrige une dépense (journée en saisie) ; le plafond est réappliqué")
    @PutMapping("/journees/{id}/depenses/{depenseId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    DepensesJourneeVue modifier(@PathVariable UUID id, @PathVariable UUID depenseId, @Valid @RequestBody DepenseSaisie d) {
        return service.modifier(id, depenseId, d.version(), d.saisie());
    }

    @Operation(summary = "Retire une dépense saisie par erreur (journée en saisie)")
    @DeleteMapping("/journees/{id}/depenses/{depenseId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    DepensesJourneeVue supprimer(@PathVariable UUID id, @PathVariable UUID depenseId, @RequestParam Integer version) {
        return service.supprimer(id, depenseId, version);
    }

    @Operation(summary = "Joint (ou remplace) le justificatif d'une dépense : photo JPEG, PNG, WebP ou PDF, 5 Mo au plus")
    @PutMapping(path = "/journees/{id}/depenses/{depenseId}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    DepensesJourneeVue joindre(@PathVariable UUID id, @PathVariable UUID depenseId, @RequestParam Integer version,
                               @RequestPart("fichier") MultipartFile fichier) {
        return service.joindre(id, depenseId, version, PieceJointe.lire(fichier));
    }

    @Operation(summary = "Justificatif d'une dépense")
    @GetMapping("/depenses/{depenseId}/justificatif")
    ResponseEntity<byte[]> justificatif(@PathVariable UUID depenseId) {
        return service.justificatif(depenseId);
    }

    @Operation(summary = "Autorise ou refuse une dépense au-delà du plafond (journée en saisie)")
    @PostMapping("/journees/{id}/depenses/{depenseId}/decision")
    @PreAuthorize("hasAuthority('DEPENSE_VALIDER')")
    DepensesJourneeVue decider(@PathVariable UUID id, @PathVariable UUID depenseId, @Valid @RequestBody Decision d) {
        return service.decider(id, depenseId, d.version(), d.autoriser(), d.motif());
    }
}
