package sn.digitalperf.jaugeo.tresorerie.api;

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
import java.time.LocalDate;
import java.time.YearMonth;
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
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.PieceJointe;
import sn.digitalperf.jaugeo.tresorerie.application.TresorerieService;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.ArreteVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.LivreCaisse;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.ManquantVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.RecetteVue;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.Situation;
import sn.digitalperf.jaugeo.tresorerie.application.VuesTresorerie.VersementVue;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisse;
import sn.digitalperf.jaugeo.tresorerie.domaine.ManquantGerant;
import sn.digitalperf.jaugeo.tresorerie.domaine.Versement;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Trésorerie", description = "Montant à verser, versements (BV), solde de caisse du gérant, arrêtés et manquants")
@PreAuthorize("hasAuthority('REFERENTIEL_CONSULTER')")
class TresorerieController {

    record VersementSaisi(Integer version, UUID stationId, @NotNull LocalDate dateDepot,
                          @NotBlank @Size(max = 60) String banque, @NotBlank @Size(max = 40) String referenceBv,
                          @NotNull @Positive @Digits(integer = 15, fraction = 0) BigDecimal montant,
                          @Size(max = 80) String deposant, @Size(max = 500) String observation) {
        Versement.Donnees donnees() {
            return new Versement.Donnees(dateDepot, banque, referenceBv, montant, deposant, observation);
        }
    }

    record Verification(@NotNull Integer version, @NotNull Boolean valide, @Size(max = 500) String motif) {
    }

    record ArreteSaisi(@NotNull LocalDate date, @NotNull ArreteCaisse.Type type,
                       @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 0) BigDecimal especesComptees,
                       @Size(max = 80) String responsable, @Size(max = 500) String observation) {
    }

    record Reglement(@NotNull Integer version, @NotNull ManquantGerant.Mode mode, @Size(max = 80) String reference,
                     @NotNull LocalDate date) {
    }

    private final TresorerieService service;

    TresorerieController(TresorerieService service) {
        this.service = service;
    }

    @Operation(summary = "Recette de la journée : montant à verser, solde de caisse du gérant, versements du jour")
    @GetMapping("/journees/{id}/tresorerie")
    RecetteVue recette(@PathVariable UUID id) {
        return service.recette(id);
    }

    @Operation(summary = "Livre de caisse du mois : montant à verser, versements et solde non versé jour par jour")
    @GetMapping("/stations/{id}/caisse")
    LivreCaisse livre(@PathVariable UUID id, @RequestParam String mois) {
        return service.livre(id, mois(mois));
    }

    @Operation(summary = "Versements d'une station sur un mois ; sans station : versements à vérifier du périmètre")
    @GetMapping("/tresorerie/versements")
    List<VersementVue> versements(@RequestParam(required = false) UUID station, @RequestParam(required = false) String mois) {
        return service.versements(station, mois == null || mois.isBlank() ? null : mois(mois));
    }

    @Operation(summary = "Enregistre un versement bancaire (bordereau)")
    @PostMapping("/tresorerie/versements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VERSEMENT_SAISIR')")
    VersementVue creer(@Valid @RequestBody VersementSaisi v) {
        if (v.stationId() == null) {
            throw ErreurMetier.regle("STATION_OBLIGATOIRE", "Indiquez la station du versement.");
        }
        return service.creerVersement(v.stationId(), v.donnees());
    }

    @Operation(summary = "Corrige un versement pas encore vérifié")
    @PutMapping("/tresorerie/versements/{id}")
    @PreAuthorize("hasAuthority('VERSEMENT_SAISIR')")
    VersementVue modifier(@PathVariable UUID id, @Valid @RequestBody VersementSaisi v) {
        return service.modifierVersement(id, v.version(), v.donnees());
    }

    @Operation(summary = "Retire un versement saisi par erreur (pas encore vérifié)")
    @DeleteMapping("/tresorerie/versements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VERSEMENT_SAISIR')")
    void supprimer(@PathVariable UUID id, @RequestParam Integer version) {
        service.supprimerVersement(id, version);
    }

    @Operation(summary = "Joint (ou remplace) la photo du bordereau : JPEG, PNG, WebP ou PDF, 5 Mo au plus")
    @PutMapping(path = "/tresorerie/versements/{id}/piece", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('VERSEMENT_SAISIR')")
    VersementVue joindre(@PathVariable UUID id, @RequestParam Integer version, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindrePiece(id, version, PieceJointe.lire(fichier));
    }

    @Operation(summary = "Photo du bordereau de versement")
    @GetMapping("/tresorerie/versements/{id}/piece")
    ResponseEntity<byte[]> piece(@PathVariable UUID id) {
        return service.piece(id);
    }

    @Operation(summary = "Vérifie ou rejette un versement (comptabilité)")
    @PostMapping("/tresorerie/versements/{id}/verification")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    VersementVue verifier(@PathVariable UUID id, @Valid @RequestBody Verification v) {
        return service.verifier(id, v.version(), v.valide(), v.motif());
    }

    @Operation(summary = "Arrêtés de caisse d'une station, du plus récent au plus ancien")
    @GetMapping("/stations/{id}/arretes")
    List<ArreteVue> arretes(@PathVariable UUID id) {
        return service.arretes(id);
    }

    @Operation(summary = "Arrête la caisse du gérant en fin de journée (espèces comptées) ; un écart négatif ouvre un manquant")
    @PostMapping("/stations/{id}/arretes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAISSE_CONTROLER')")
    ArreteVue arreter(@PathVariable UUID id, @Valid @RequestBody ArreteSaisi a) {
        return service.arreter(id, a.date(), a.type(), a.especesComptees(), a.responsable(), a.observation());
    }

    @Operation(summary = "Manquants de caisse des gérants")
    @GetMapping("/tresorerie/manquants")
    List<ManquantVue> manquants(@RequestParam(required = false) String statut) {
        return service.manquants(statut);
    }

    @Operation(summary = "Solde un manquant (remboursement, retenue ou perte)")
    @PostMapping("/tresorerie/manquants/{id}/reglement")
    @PreAuthorize("hasAuthority('VERSEMENT_VALIDER')")
    ManquantVue regler(@PathVariable UUID id, @Valid @RequestBody Reglement r) {
        return service.reglerManquant(id, r.version(), r.mode(), r.reference(), r.date());
    }

    @Operation(summary = "Solde non versé de chaque station, retards de versement, versements à vérifier, manquants")
    @GetMapping("/tresorerie/situation")
    Situation situation() {
        return service.situation();
    }

    private static YearMonth mois(String mois) {
        try {
            return YearMonth.parse(mois);
        } catch (java.time.format.DateTimeParseException e) {
            throw ErreurMetier.regle("MOIS_INVALIDE", "Mois attendu au format AAAA-MM.");
        }
    }
}
