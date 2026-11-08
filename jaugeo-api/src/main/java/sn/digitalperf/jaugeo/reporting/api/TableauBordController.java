package sn.digitalperf.jaugeo.reporting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.reporting.application.TableauBordService;
import sn.digitalperf.jaugeo.reporting.application.TableauBordService.TableauReseau;

@RestController
@RequestMapping("/api/v1/tableau-bord")
@Tag(name = "Tableau de bord", description = "Situation du réseau : niveau d'alerte, volumes, écarts et caisse par station")
@PreAuthorize("hasAuthority('TABLEAU_BORD_RESEAU')")
class TableauBordController {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final TableauBordService service;
    private final Clock horloge;

    TableauBordController(TableauBordService service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    @Operation(summary = "Stations du périmètre au jour donné (par défaut aujourd'hui, heure de Dakar)")
    @GetMapping("/reseau")
    TableauReseau reseau(@RequestParam(required = false) String date) {
        if (date == null || date.isBlank()) {
            return service.reseau(LocalDate.now(horloge.withZone(DAKAR)));
        }
        try {
            return service.reseau(LocalDate.parse(date));
        } catch (DateTimeParseException e) {
            throw ErreurMetier.regle("DATE_INVALIDE", "La date s'écrit AAAA-MM-JJ.");
        }
    }
}
