package sn.digitalperf.jaugeo.clients.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.clients.application.FacturationService;
import sn.digitalperf.jaugeo.clients.application.VuesClients.BalanceAgee;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureDetail;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RelanceVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ResultatFacturation;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Facturation des clients", description = "Factures périodiques, balance âgée et relances")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class FacturationController {

    record Facturation(@NotNull LocalDate du, @NotNull LocalDate au, LocalDate dateFacture, UUID clientId) {
    }

    record Annulation(@NotNull Integer version, @NotBlank @Size(max = 500) String motif) {
    }

    record RelanceSaisie(String mode, @Size(max = 500) String commentaire) {
    }

    private final FacturationService service;

    FacturationController(FacturationService service) {
        this.service = service;
    }

    @Operation(summary = "Factures émises (filtre facultatif sur la date de facture)")
    @GetMapping("/factures")
    List<FactureVue> factures(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return service.liste(du, au);
    }

    @Operation(summary = "Facture et ses bons")
    @GetMapping("/factures/{id}")
    FactureDetail facture(@PathVariable UUID id) {
        return service.detail(id);
    }

    @Operation(summary = "Facture en PDF")
    @GetMapping("/factures/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return service.pdf(id);
    }

    @Operation(summary = "Facture les bons non facturés d'une période : une facture par client")
    @PostMapping("/factures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    ResultatFacturation facturer(@Valid @RequestBody Facturation f) {
        return service.facturer(f.du(), f.au(), f.dateFacture(), f.clientId());
    }

    @Operation(summary = "Annule une facture : ses bons redeviennent à facturer")
    @PostMapping("/factures/{id}/annulation")
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    FactureDetail annuler(@PathVariable UUID id, @Valid @RequestBody Annulation a) {
        return service.annuler(id, a.version(), a.motif());
    }

    @Operation(summary = "Balance âgée des créances (0-30, 31-60, 61-90, plus de 90 jours) et montants échus")
    @GetMapping("/balance-agee")
    BalanceAgee balance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return service.balance(au);
    }

    @Operation(summary = "Enregistre une relance du client (niveau suivant, montant échu à ce jour)")
    @PostMapping("/{id}/relances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    RelanceVue relancer(@PathVariable UUID id, @Valid @RequestBody RelanceSaisie r) {
        return service.relancer(id, r.mode(), r.commentaire());
    }

    @Operation(summary = "Lettre de relance en PDF")
    @GetMapping("/relances/{id}/lettre")
    ResponseEntity<byte[]> lettre(@PathVariable UUID id) {
        return service.lettre(id);
    }
}
