package sn.digitalperf.jaugeo.stock.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
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
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.stock.application.ArticleService;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.EntreeVue;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.MouvementsArticles;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieComptage;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieEntree;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.SaisieVente;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.StockArticles;
import sn.digitalperf.jaugeo.stock.application.VuesArticles.VentesJournee;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Lubrifiants et boutique", description = "Stock des articles au CMUP, entrées, ventes de la journée, inventaire tournant")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class ArticleController {

    record Entree(@NotNull UUID produitId, @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantite,
                  @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal coutUnitaire,
                  @NotBlank @Size(max = 80) String fournisseur, @NotBlank @Size(max = 50) String reference) {
    }

    record ReceptionEntree(@NotNull Integer version, @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteRecue,
                           @Size(max = 500) String observation) {
    }

    record Annulation(@NotNull Integer version, @NotBlank @Size(max = 500) String motif) {
    }

    record Comptage(@NotNull UUID produitId, @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal compte,
                    @Size(max = 500) String observation) {
    }

    record Comptages(@NotNull @Size(min = 1, max = 500) List<@Valid @NotNull Comptage> comptages) {
    }

    record Vente(@NotNull UUID produitId, @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal quantite) {
    }

    record Ventes(@NotNull Integer version, @NotNull @Size(max = 500) List<@Valid @NotNull Vente> ventes) {
    }

    private final ArticleService service;

    ArticleController(ArticleService service) {
        this.service = service;
    }

    @Operation(summary = "Stock des lubrifiants et articles boutique d'une station : quantités, CMUP, valeur, "
            + "articles à compter cette semaine, entrées annoncées à confirmer")
    @GetMapping("/stations/{id}/articles")
    StockArticles stock(@PathVariable UUID id) {
        return service.stock(id);
    }

    @Operation(summary = "Annonce une entrée d'articles (livraison ou stock initial) avec son coût d'achat")
    @PostMapping("/stations/{id}/articles/entrees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    EntreeVue annoncer(@PathVariable UUID id, @Valid @RequestBody Entree e) {
        return service.annoncerEntree(id, new SaisieEntree(e.produitId(), e.quantite(), e.coutUnitaire(), e.fournisseur(), e.reference()));
    }

    @Operation(summary = "Confirme la réception d'une entrée à la station : la quantité reçue entre en stock au CMUP")
    @PostMapping("/articles/entrees/{id}/reception")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    EntreeVue recevoir(@PathVariable UUID id, @Valid @RequestBody ReceptionEntree r) {
        return service.recevoirEntree(id, r.version(), r.quantiteRecue(), r.observation());
    }

    @Operation(summary = "Annule une entrée annoncée qui ne sera pas livrée")
    @PostMapping("/articles/entrees/{id}/annulation")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    EntreeVue annuler(@PathVariable UUID id, @Valid @RequestBody Annulation a) {
        return service.annulerEntree(id, a.version(), a.motif());
    }

    @Operation(summary = "Enregistre des comptages d'inventaire : écart valorisé au CMUP, observation obligatoire si écart")
    @PostMapping("/stations/{id}/articles/comptages")
    @PreAuthorize("hasAnyAuthority('JOURNEE_SAISIR', 'JOURNEE_VALIDER')")
    StockArticles compter(@PathVariable UUID id, @Valid @RequestBody Comptages c) {
        return service.compter(id, c.comptages().stream()
                .map(x -> new SaisieComptage(x.produitId(), x.compte(), x.observation())).toList());
    }

    @Operation(summary = "Mouvements du mois (entrées, ventes, écarts d'inventaire) avec stock et CMUP après chacun")
    @GetMapping("/stations/{id}/articles/mouvements")
    MouvementsArticles mouvements(@PathVariable UUID id, @RequestParam String mois) {
        YearMonth m;
        try {
            m = YearMonth.parse(mois);
        } catch (java.time.format.DateTimeParseException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Mois attendu au format AAAA-MM.");
        }
        return service.mouvements(id, m);
    }

    @Operation(summary = "Ventes de lubrifiants et d'articles boutique de la journée, au prix du jour")
    @GetMapping("/journees/{id}/articles")
    VentesJournee ventes(@PathVariable UUID id) {
        return service.ventesJournee(id);
    }

    @Operation(summary = "Enregistre les ventes d'articles de la journée (quantité nulle = ligne retirée)")
    @PutMapping("/journees/{id}/articles")
    @PreAuthorize("hasAuthority('JOURNEE_SAISIR')")
    VentesJournee enregistrer(@PathVariable UUID id, @Valid @RequestBody Ventes v) {
        return service.enregistrerVentes(id, v.version(), v.ventes().stream()
                .map(x -> new SaisieVente(x.produitId(), x.quantite())).toList());
    }
}
