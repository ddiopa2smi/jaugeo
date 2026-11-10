package sn.digitalperf.jaugeo.stock.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import sn.digitalperf.jaugeo.stock.application.StockService;
import sn.digitalperf.jaugeo.stock.application.VuesStock.AnalyseMensuelle;
import sn.digitalperf.jaugeo.stock.application.VuesStock.ComplementVue;
import sn.digitalperf.jaugeo.stock.application.VuesStock.SaisieJaugeage;
import sn.digitalperf.jaugeo.stock.application.VuesStock.Situation;
import sn.digitalperf.jaugeo.stock.application.VuesStock.StockJournee;
import sn.digitalperf.jaugeo.stock.domaine.Reception;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Stocks", description = "Jaugeages, réceptions (BL), stock théorique, écarts de stock et compléments citerne")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class StockController {

    record JaugeageSaisi(@NotNull UUID cuveId, @Size(max = 10) String moment,
                         @PositiveOrZero @Max(100_000) Integer hauteurMm,
                         @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal volumeLitres,
                         @Size(max = 500) String observation,
                         @PositiveOrZero @Max(5_000) Integer hauteurEauMm) {
    }

    record Jaugeages(@NotNull Integer version, @NotNull @Size(max = 50) List<@Valid @NotNull JaugeageSaisi> jaugeages) {
    }

    record ReceptionSaisie(@NotNull Integer version, @NotNull UUID cuveId,
                           @NotBlank @Size(max = 30) String numeroBl, @NotBlank @Size(max = 80) String fournisseur,
                           @Size(max = 80) String transporteur, @Size(max = 80) String chauffeur,
                           @Size(max = 20) String immatriculation, @Size(max = 120) String scelles,
                           @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteBl,
                           @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal jaugeAvant,
                           @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal jaugeApres,
                           @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteRecue,
                           @DecimalMin("-20") @DecimalMax("80") @Digits(integer = 2, fraction = 1) BigDecimal temperatureC,
                           @DecimalMin("0.5") @DecimalMax("1.2") @Digits(integer = 1, fraction = 4) BigDecimal densite,
                           @Size(max = 500) String observation,
                           @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteBl15,
                           Boolean scellesIntacts, Reception.Imputation imputation, UUID livraisonId) {
        Reception.Donnees donnees() {
            return new Reception.Donnees(numeroBl, fournisseur, transporteur, chauffeur, immatriculation, scelles,
                    quantiteBl, jaugeAvant, jaugeApres, quantiteRecue, temperatureC, densite, observation,
                    quantiteBl15, scellesIntacts, imputation);
        }
    }

    record Reglement(@NotNull Integer version, @NotBlank @Size(max = 80) String reference, @NotNull LocalDate date) {
    }

    private final StockService service;

    StockController(StockService service) {
        this.service = service;
    }

    @Operation(summary = "Stock de la journée par cuve : départ, réceptions, sorties, théorique, jaugeage, écart")
    @GetMapping("/journees/{id}/stock")
    StockJournee stock(@PathVariable UUID id) {
        return service.journee(id);
    }

    @Operation(summary = "Enregistre les jaugeages de la journée (hauteur convertie par le barème, ou volume lu)")
    @PutMapping("/journees/{id}/jaugeages")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    StockJournee jaugeages(@PathVariable UUID id, @Valid @RequestBody Jaugeages j) {
        return service.enregistrerJaugeages(id, j.version(), j.jaugeages().stream()
                .map(x -> new SaisieJaugeage(x.cuveId(), x.moment(), x.hauteurMm(), x.volumeLitres(), x.observation(),
                        x.hauteurEauMm()))
                .toList());
    }

    @Operation(summary = "Enregistre une réception citerne (BL) dans la journée")
    @PostMapping("/journees/{id}/receptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    StockJournee ajouterReception(@PathVariable UUID id, @Valid @RequestBody ReceptionSaisie r) {
        return service.ajouterReception(id, r.version(), r.cuveId(), r.livraisonId(), r.donnees());
    }

    @Operation(summary = "Corrige une réception de la journée en saisie")
    @PutMapping("/journees/{id}/receptions/{receptionId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    StockJournee modifierReception(@PathVariable UUID id, @PathVariable UUID receptionId, @Valid @RequestBody ReceptionSaisie r) {
        return service.modifierReception(id, receptionId, r.version(), r.cuveId(), r.livraisonId(), r.donnees());
    }

    @Operation(summary = "Retire une réception saisie par erreur (journée en saisie)")
    @DeleteMapping("/journees/{id}/receptions/{receptionId}")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    StockJournee supprimerReception(@PathVariable UUID id, @PathVariable UUID receptionId, @RequestParam Integer version) {
        return service.supprimerReception(id, receptionId, version);
    }

    @Operation(summary = "Analyse mensuelle des stocks d'une station, jour par jour et par cuve")
    @GetMapping("/stations/{id}/stock")
    AnalyseMensuelle mensuel(@PathVariable UUID id, @RequestParam String mois) {
        YearMonth m;
        try {
            m = YearMonth.parse(mois);
        } catch (java.time.format.DateTimeParseException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Mois attendu au format AAAA-MM.");
        }
        return service.mensuel(id, m);
    }

    @Operation(summary = "Dernier stock connu de chaque cuve du périmètre : niveau d'alerte, eau en fond de cuve, "
            + "autonomie en jours et réapprovisionnement ; compléments à régler")
    @GetMapping("/stock/situation")
    Situation situation() {
        return service.situation();
    }

    @Operation(summary = "Compléments citerne (manquants de livraison à la charge du transporteur ou du fournisseur)")
    @GetMapping("/stock/complements")
    List<ComplementVue> complements(@RequestParam(required = false) String statut) {
        return service.complements(statut);
    }

    @Operation(summary = "Enregistre le règlement d'un complément citerne par le transporteur ou le fournisseur")
    @PostMapping("/stock/complements/{id}/reglement")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    ComplementVue regler(@PathVariable UUID id, @Valid @RequestBody Reglement r) {
        return service.reglerComplement(id, r.version(), r.reference(), r.date());
    }
}
