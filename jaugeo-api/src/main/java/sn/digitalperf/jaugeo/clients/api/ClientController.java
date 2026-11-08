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
import org.springframework.format.annotation.DateTimeFormat;
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
import sn.digitalperf.jaugeo.clients.application.ClientService;
import sn.digitalperf.jaugeo.clients.application.CreditJourneeService;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ClientVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CreditJourneeVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FicheClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.Releve;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieBon;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieClient;
import sn.digitalperf.jaugeo.clients.application.VuesClients.SaisieOperation;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clients", description = "Clients en compte, bons à crédit, créances et règlements")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class ClientController {

    record ClientSaisi(@Size(max = 20) String code, @NotBlank @Size(max = 120) String nom, @Size(max = 30) String telephone,
                       @NotBlank String recouvrement, @Positive @Digits(integer = 15, fraction = 0) BigDecimal plafond,
                       Boolean actif, @Size(max = 500) String observation, Integer version) {
        SaisieClient saisie() {
            return new SaisieClient(code, nom, telephone, recouvrement, plafond, actif, observation);
        }
    }

    record OperationSaisie(@NotNull UUID stationId, @NotBlank String type, String mode, @NotNull LocalDate date,
                           @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal montant,
                           @Size(max = 80) String reference, @Size(max = 500) String observation) {
        SaisieOperation saisie() {
            return new SaisieOperation(stationId, type, mode, date, montant, reference, observation);
        }
    }

    record BonSaisi(@NotNull Integer version, @NotNull UUID clientId, @NotBlank @Size(max = 30) String numero,
                    @NotNull UUID produitId, @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal litres,
                    @Size(max = 30) String immatriculation, @Size(max = 80) String porteur) {
        SaisieBon saisie() {
            return new SaisieBon(clientId, numero, produitId, litres, immatriculation, porteur);
        }
    }

    record RecouvrementSaisi(@NotNull Integer version, @NotNull UUID clientId,
                             @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal montant,
                             @Size(max = 80) String reference, @Size(max = 500) String observation) {
    }

    private final ClientService clients;
    private final CreditJourneeService credit;

    ClientController(ClientService clients, CreditJourneeService credit) {
        this.clients = clients;
        this.credit = credit;
    }

    @Operation(summary = "Clients en compte et encours")
    @GetMapping("/clients")
    List<ClientVue> liste() {
        return clients.liste();
    }

    @Operation(summary = "Fiche d'un client : encours et créance par station")
    @GetMapping("/clients/{id}")
    FicheClient fiche(@PathVariable UUID id) {
        return clients.fiche(id);
    }

    @Operation(summary = "Crée un client en compte")
    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    ClientVue creer(@Valid @RequestBody ClientSaisi c) {
        return clients.creer(c.saisie());
    }

    @Operation(summary = "Modifie un client (nom, recouvrement, plafond, état)")
    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    ClientVue modifier(@PathVariable UUID id, @Valid @RequestBody ClientSaisi c) {
        return clients.modifier(id, c.version(), c.saisie());
    }

    @Operation(summary = "Relevé du compte d'un client à une station")
    @GetMapping("/clients/{id}/releve")
    Releve releve(@PathVariable UUID id, @RequestParam UUID station,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return clients.releve(id, station, du, au);
    }

    @Operation(summary = "Reprise d'une créance existante ou règlement encaissé par le siège")
    @PostMapping("/clients/{id}/operations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    FicheClient operation(@PathVariable UUID id, @Valid @RequestBody OperationSaisie o) {
        return clients.enregistrerOperation(id, o.saisie());
    }

    @Operation(summary = "Retire une reprise ou un règlement du siège saisi par erreur")
    @DeleteMapping("/clients/operations/{operationId}")
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    FicheClient supprimerOperation(@PathVariable UUID operationId, @RequestParam Integer version) {
        return clients.supprimerOperation(operationId, version);
    }

    // ---------------------------------------------------------------- Journee

    @Operation(summary = "Ventes à crédit (bons) et recouvrements en espèces de la journée")
    @GetMapping("/journees/{id}/credit")
    CreditJourneeVue journee(@PathVariable UUID id) {
        return credit.journee(id);
    }

    @Operation(summary = "Ajoute un bon à crédit (client actif, sous plafond, numéro jamais utilisé)")
    @PostMapping("/journees/{id}/bons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    CreditJourneeVue ajouterBon(@PathVariable UUID id, @Valid @RequestBody BonSaisi b) {
        return credit.ajouterBon(id, b.version(), b.saisie());
    }

    @Operation(summary = "Corrige un bon (journée en saisie)")
    @PutMapping("/journees/{id}/bons/{bonId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    CreditJourneeVue modifierBon(@PathVariable UUID id, @PathVariable UUID bonId, @Valid @RequestBody BonSaisi b) {
        return credit.modifierBon(id, bonId, b.version(), b.saisie());
    }

    @Operation(summary = "Retire un bon saisi par erreur (journée en saisie)")
    @DeleteMapping("/journees/{id}/bons/{bonId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    CreditJourneeVue supprimerBon(@PathVariable UUID id, @PathVariable UUID bonId, @RequestParam Integer version) {
        return credit.supprimerBon(id, bonId, version);
    }

    @Operation(summary = "Enregistre une créance recouvrée en espèces par le gérant (ajoutée au montant à verser)")
    @PostMapping("/journees/{id}/recouvrements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    CreditJourneeVue ajouterRecouvrement(@PathVariable UUID id, @Valid @RequestBody RecouvrementSaisi r) {
        return credit.ajouterRecouvrement(id, r.version(), r.clientId(), r.montant(), r.reference(), r.observation());
    }

    @Operation(summary = "Retire un recouvrement saisi par erreur (journée en saisie)")
    @DeleteMapping("/journees/{id}/recouvrements/{operationId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    CreditJourneeVue supprimerRecouvrement(@PathVariable UUID id, @PathVariable UUID operationId, @RequestParam Integer version) {
        return credit.supprimerRecouvrement(id, operationId, version);
    }
}
