package sn.digitalperf.jaugeo.clients.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.clients.application.CarteService;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CarteDetail;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CarteVue;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Cartes carburant", description = "Cartes prépayées nominatives à QR code, utilisables dans tout le réseau")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class CarteController {

    record CarteSaisie(@NotBlank @Size(max = 80) String porteur, @Size(max = 30) String immatriculation, String statut,
                       Integer version) {
    }

    record Recharge(@NotNull Integer version, LocalDate date, @NotBlank String mode,
                    @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal montant, @Size(max = 80) String reference) {
    }

    private final CarteService service;

    CarteController(CarteService service) {
        this.service = service;
    }

    @Operation(summary = "Cartes prépayées du réseau")
    @GetMapping("/cartes")
    List<CarteVue> liste() {
        return service.liste();
    }

    @Operation(summary = "Carte et ses mouvements")
    @GetMapping("/cartes/{id}")
    CarteDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @Operation(summary = "Carte lue par son numéro ou son QR code (solde, porteur)")
    @GetMapping("/cartes/numero/{numero}")
    CarteDetail parNumero(@PathVariable String numero) {
        return service.parNumero(numero);
    }

    @Operation(summary = "Émet une carte prépayée nominative pour le client")
    @PostMapping("/{id}/cartes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    CarteDetail emettre(@PathVariable UUID id, @Valid @RequestBody CarteSaisie c) {
        return service.emettre(id, c.porteur(), c.immatriculation());
    }

    @Operation(summary = "Modifie le porteur, l'immatriculation ou l'état d'une carte (bloquer, réactiver, annuler)")
    @PutMapping("/cartes/{id}")
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    CarteDetail modifier(@PathVariable UUID id, @Valid @RequestBody CarteSaisie c) {
        return service.modifier(id, c.version(), c.porteur(), c.immatriculation(), c.statut());
    }

    @Operation(summary = "Recharge une carte (paiement encaissé par le siège)")
    @PostMapping("/cartes/{id}/recharges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    CarteDetail recharger(@PathVariable UUID id, @Valid @RequestBody Recharge r) {
        return service.recharger(id, r.version(), r.date(), r.mode(), r.montant(), r.reference());
    }
}
