package sn.digitalperf.jaugeo.referentiel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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
import sn.digitalperf.jaugeo.referentiel.application.ReferentielService;
import sn.digitalperf.jaugeo.referentiel.application.ReferentielService.DonneesProduit;
import sn.digitalperf.jaugeo.referentiel.application.ReferentielService.DonneesStation;
import sn.digitalperf.jaugeo.referentiel.application.Vues.CuveVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.EquipementsVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.PistoletVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.PompeVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.ProduitVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.RegionVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.StationVue;
import sn.digitalperf.jaugeo.referentiel.domaine.Produit;
import sn.digitalperf.jaugeo.referentiel.domaine.Station;

@Tag(name = "Référentiel")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class ReferentielController {

    private static final String GERER = "hasAuthority('REFERENTIEL_GERER')";
    private static final String CODE = "^[A-Za-z0-9_]+$";
    private static final String MSG_CODE = "Lettres, chiffres et souligné uniquement";

    record StationSaisie(
            @Size(min = 2, max = 10) @Pattern(regexp = CODE, message = MSG_CODE) String code,
            @NotBlank @Size(max = 100) String nom,
            @NotNull Station.TypeStation type,
            @NotBlank String regionCode,
            @Size(max = 100) String localite,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @NotNull Station.ModeExploitation modeExploitation,
            LocalDate dateOuverture,
            boolean actif,
            Integer version) {
        DonneesStation donnees() {
            return new DonneesStation(nom, type, regionCode, localite, latitude, longitude, modeExploitation,
                    dateOuverture, actif);
        }
    }

    record ProduitSaisie(
            @Size(min = 2, max = 20) @Pattern(regexp = CODE, message = MSG_CODE) String code,
            @NotBlank @Size(max = 100) String libelle,
            @NotNull Produit.Famille famille,
            @NotNull Produit.Unite unite,
            boolean suiviJaugeage,
            @DecimalMin("0") @DecimalMax("100") BigDecimal tauxTva,
            boolean actif,
            @DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal toleranceEcartPct,
            Integer version) {
        DonneesProduit donnees() {
            return new DonneesProduit(libelle, famille, unite, suiviJaugeage, tauxTva, actif, toleranceEcartPct);
        }
    }

    record CuveSaisie(@NotBlank @Size(max = 10) @Pattern(regexp = CODE, message = MSG_CODE) String code,
                      @NotBlank String produitCode,
                      @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal capaciteLitres,
                      @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal creuxSecuriteLitres,
                      @PositiveOrZero @jakarta.validation.constraints.Max(2000) Integer seuilEauMm,
                      @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal seuilReapproLitres) {
    }

    record CuveModification(@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal capaciteLitres,
                            @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal creuxSecuriteLitres,
                            boolean actif, @NotNull Integer version,
                            @PositiveOrZero @jakarta.validation.constraints.Max(2000) Integer seuilEauMm,
                            @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal seuilReapproLitres) {
    }

    record PompeSaisie(@NotBlank @Size(max = 10) @Pattern(regexp = CODE, message = MSG_CODE) String code,
                       @Size(max = 50) String marque, @Size(max = 50) String numeroSerie) {
    }

    record PistoletSaisie(@NotBlank @Size(max = 10) @Pattern(regexp = CODE, message = MSG_CODE) String code,
                          @NotBlank @Size(max = 50) String libelle,
                          @NotNull UUID cuveId,
                          @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal indexMiseEnService) {
    }

    record PistoletModification(@NotBlank @Size(max = 50) String libelle, boolean actif, @NotNull Integer version) {
    }

    private final ReferentielService service;

    ReferentielController(ReferentielService service) {
        this.service = service;
    }

    @Operation(summary = "Régions")
    @GetMapping("/regions")
    List<RegionVue> regions() {
        return service.regions();
    }

    @Operation(summary = "Stations du périmètre de l'utilisateur")
    @GetMapping("/stations")
    List<StationVue> stations() {
        return service.stations();
    }

    @GetMapping("/stations/{id}")
    StationVue station(@PathVariable UUID id) {
        return service.station(id);
    }

    @PreAuthorize(GERER)
    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    StationVue creerStation(@Valid @RequestBody StationSaisie s) {
        if (s.code() == null || s.code().isBlank()) {
            throw sn.digitalperf.jaugeo.commun.ErreurMetier.regle("CODE_OBLIGATOIRE", "Le code de la station est obligatoire.");
        }
        return service.creerStation(s.code(), s.donnees());
    }

    @PreAuthorize(GERER)
    @PutMapping("/stations/{id}")
    StationVue modifierStation(@PathVariable UUID id, @Valid @RequestBody StationSaisie s) {
        return service.modifierStation(id, s.donnees(), s.version());
    }

    @Operation(summary = "Cuves, pompes et pistolets d'une station")
    @GetMapping("/stations/{id}/equipements")
    EquipementsVue equipements(@PathVariable UUID id) {
        return service.equipements(id);
    }

    @PreAuthorize(GERER)
    @PostMapping("/stations/{id}/cuves")
    @ResponseStatus(HttpStatus.CREATED)
    CuveVue creerCuve(@PathVariable UUID id, @Valid @RequestBody CuveSaisie c) {
        return service.creerCuve(id, c.code(), c.produitCode(), c.capaciteLitres(), c.creuxSecuriteLitres(),
                c.seuilEauMm(), c.seuilReapproLitres());
    }

    @PreAuthorize(GERER)
    @PutMapping("/cuves/{id}")
    CuveVue modifierCuve(@PathVariable UUID id, @Valid @RequestBody CuveModification c) {
        return service.modifierCuve(id, c.capaciteLitres(), c.creuxSecuriteLitres(), c.actif(), c.version(),
                c.seuilEauMm(), c.seuilReapproLitres());
    }

    @PreAuthorize(GERER)
    @PostMapping("/stations/{id}/pompes")
    @ResponseStatus(HttpStatus.CREATED)
    PompeVue creerPompe(@PathVariable UUID id, @Valid @RequestBody PompeSaisie p) {
        return service.creerPompe(id, p.code(), p.marque(), p.numeroSerie());
    }

    @PreAuthorize(GERER)
    @PostMapping("/pompes/{id}/pistolets")
    @ResponseStatus(HttpStatus.CREATED)
    PistoletVue creerPistolet(@PathVariable UUID id, @Valid @RequestBody PistoletSaisie p) {
        return service.creerPistolet(id, p.cuveId(), p.code(), p.libelle(), p.indexMiseEnService());
    }

    @PreAuthorize(GERER)
    @PutMapping("/pistolets/{id}")
    PistoletVue modifierPistolet(@PathVariable UUID id, @Valid @RequestBody PistoletModification p) {
        return service.modifierPistolet(id, p.libelle(), p.actif(), p.version());
    }

    @Operation(summary = "Produits")
    @GetMapping("/produits")
    List<ProduitVue> produits() {
        return service.produits();
    }

    @PreAuthorize(GERER)
    @PostMapping("/produits")
    @ResponseStatus(HttpStatus.CREATED)
    ProduitVue creerProduit(@Valid @RequestBody ProduitSaisie p) {
        if (p.code() == null || p.code().isBlank()) {
            throw sn.digitalperf.jaugeo.commun.ErreurMetier.regle("CODE_OBLIGATOIRE", "Le code du produit est obligatoire.");
        }
        return service.creerProduit(p.code(), p.donnees());
    }

    @PreAuthorize(GERER)
    @PutMapping("/produits/{id}")
    ProduitVue modifierProduit(@PathVariable UUID id, @Valid @RequestBody ProduitSaisie p) {
        return service.modifierProduit(id, p.donnees(), p.version());
    }
}
