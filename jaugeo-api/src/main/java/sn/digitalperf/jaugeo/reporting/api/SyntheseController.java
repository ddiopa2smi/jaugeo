package sn.digitalperf.jaugeo.reporting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.reporting.application.SyntheseService;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.CommentaireVue;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseStocks;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseVentes;

@RestController
@RequestMapping("/api/v1/syntheses")
@Tag(name = "Synthèses", description = "Synthèses mensuelles des ventes, recettes et stocks ; exports Excel et PDF")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class SyntheseController {

    record Commentaire(@NotNull UUID stationId, @NotBlank String mois, @Size(max = 1000) String texte, Integer version) {
    }

    private final SyntheseService service;

    SyntheseController(SyntheseService service) {
        this.service = service;
    }

    @Operation(summary = "Analyse des ventes et recettes du mois, par station (H = E+F+G, Q = I+J+K+L+M+O, R = Q−H)")
    @GetMapping("/ventes")
    SyntheseVentes ventes(@RequestParam String mois) {
        return service.ventes(mois(mois));
    }

    @Operation(summary = "Analyse des stocks du mois, par station et par cuve")
    @GetMapping("/stocks")
    SyntheseStocks stocks(@RequestParam String mois) {
        return service.stocks(mois(mois));
    }

    @Operation(summary = "Classeur Excel des deux feuilles d'analyse du mois")
    @GetMapping("/export.xlsx")
    ResponseEntity<byte[]> excel(@RequestParam String mois) {
        return service.excel(mois(mois));
    }

    @Operation(summary = "PDF des deux feuilles d'analyse du mois")
    @GetMapping("/export.pdf")
    ResponseEntity<byte[]> pdf(@RequestParam String mois) {
        return service.pdf(mois(mois));
    }

    @Operation(summary = "Rédige ou efface (texte vide) l'observation du contrôle de gestion sur une station")
    @PutMapping("/commentaires")
    @PreAuthorize("hasAuthority('CLOTURE_MENSUELLE')")
    CommentaireVue commenter(@Valid @RequestBody Commentaire c) {
        return service.commenter(c.stationId(), mois(c.mois()), c.texte(), c.version());
    }

    private static YearMonth mois(String mois) {
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Le mois s'écrit AAAA-MM.");
        }
    }
}
