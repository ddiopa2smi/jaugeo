package sn.digitalperf.jaugeo.reporting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.reporting.application.BudgetService;
import sn.digitalperf.jaugeo.reporting.application.BudgetService.SaisieProduit;
import sn.digitalperf.jaugeo.reporting.application.ClotureMensuelleService;
import sn.digitalperf.jaugeo.reporting.application.CompteExploitationService;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.BudgetStation;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.CompteReseau;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.EtatMois;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.EtatStation;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.RegularisationVue;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviBudget;

@RestController
@RequestMapping("/api/v1/cloture")
@Tag(name = "Clôture mensuelle", description = "Compte d'exploitation, clôture des mois par station, régularisations, budget")
@PreAuthorize("hasAuthority('CLOTURE_MENSUELLE')")
class ClotureController {

    record RegularisationSaisie(@NotNull UUID stationId, @NotBlank String moisConcerne, @NotBlank String poste,
                                @NotNull @Digits(integer = 13, fraction = 0) BigDecimal montant,
                                @NotBlank @Size(max = 500) String motif, @Size(max = 80) String reference) {
    }

    record BudgetSaisi(@NotNull UUID stationId, @NotNull Integer annee, List<SaisieProduit> produits, List<BigDecimal> depenses) {
    }

    private final CompteExploitationService comptes;
    private final ClotureMensuelleService clotures;
    private final BudgetService budgets;

    ClotureController(CompteExploitationService comptes, ClotureMensuelleService clotures, BudgetService budgets) {
        this.comptes = comptes;
        this.clotures = clotures;
        this.budgets = budgets;
    }

    @Operation(summary = "Compte d'exploitation du mois par station : chiffre d'affaires, coût d'achat, marge, dépenses, résultat")
    @GetMapping("/compte")
    CompteReseau compte(@RequestParam String mois) {
        return comptes.compte(mois(mois));
    }

    @Operation(summary = "État de la clôture du mois pour chaque station : clos, prêt ou bloqué (avec les points bloquants)")
    @GetMapping("/etat")
    EtatMois etat(@RequestParam String mois) {
        return clotures.etat(mois(mois));
    }

    @Operation(summary = "Clôture le mois d'une station : fige le compte d'exploitation et verrouille le mois")
    @PostMapping("/stations/{id}/mois/{mois}")
    EtatStation cloturer(@PathVariable UUID id, @PathVariable String mois) {
        return clotures.cloturer(id, mois(mois));
    }

    @Operation(summary = "Clôture toutes les stations prêtes pour ce mois")
    @PostMapping("/mois/{mois}")
    EtatMois cloturerPretes(@PathVariable String mois) {
        return clotures.cloturerPretes(mois(mois));
    }

    @Operation(summary = "Régularisations comptées dans l'année")
    @GetMapping("/regularisations")
    List<RegularisationVue> regularisations(@RequestParam int annee) {
        return clotures.regularisations(annee);
    }

    @Operation(summary = "Corrige un mois clos par une régularisation, comptée dans le résultat du mois en cours")
    @PostMapping("/regularisations")
    @ResponseStatus(HttpStatus.CREATED)
    RegularisationVue regulariser(@Valid @RequestBody RegularisationSaisie r) {
        return clotures.regulariser(r.stationId(), mois(r.moisConcerne()), r.poste(), r.montant(), r.motif(), r.reference());
    }

    @Operation(summary = "Budget annuel d'une station : litres et chiffre d'affaires par produit et par mois, dépenses par mois")
    @GetMapping("/budget")
    BudgetStation budget(@RequestParam UUID station, @RequestParam int annee) {
        return budgets.budget(station, annee);
    }

    @Operation(summary = "Enregistre le budget annuel d'une station")
    @PutMapping("/budget")
    BudgetStation enregistrerBudget(@Valid @RequestBody BudgetSaisi b) {
        return budgets.enregistrer(b.stationId(), b.annee(), b.produits(), b.depenses());
    }

    @Operation(summary = "Réel contre budget de l'année, pour une station ou pour le réseau du périmètre")
    @GetMapping("/budget/suivi")
    SuiviBudget suivi(@RequestParam(required = false) UUID station, @RequestParam int annee) {
        return budgets.suivi(station, annee);
    }

    @Operation(summary = "Classeur Excel : compte d'exploitation du mois et réel contre budget de l'année")
    @GetMapping("/export.xlsx")
    ResponseEntity<byte[]> excel(@RequestParam String mois) {
        return clotures.excel(mois(mois));
    }

    @Operation(summary = "PDF du compte d'exploitation du mois")
    @GetMapping("/export.pdf")
    ResponseEntity<byte[]> pdf(@RequestParam String mois) {
        return clotures.pdf(mois(mois));
    }

    private static YearMonth mois(String mois) {
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Le mois s'écrit AAAA-MM.");
        }
    }
}
