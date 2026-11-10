package sn.digitalperf.jaugeo.approvisionnement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import sn.digitalperf.jaugeo.approvisionnement.application.FactureFournisseurService;
import sn.digitalperf.jaugeo.approvisionnement.application.PartenairesService;
import sn.digitalperf.jaugeo.approvisionnement.application.TourneeService;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FactureFournisseurDetail;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FactureFournisseurVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FournisseurVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.PenaliteVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.Propositions;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieBl;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieFacture;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieLigneFacture;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieLivraison;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieTournee;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TourneeVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TransporteurDetail;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TransporteurVue;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;

@RestController
@RequestMapping("/api/v1/appro")
@Tag(name = "Approvisionnement", description = "Propositions de commande, tournées citernes, transporteurs, factures fournisseur")
@PreAuthorize("hasAuthority('TABLEAU_BORD_RESEAU')")
class ApproController {

    record FournisseurSaisi(@NotBlank @Size(max = 80) String nom, @Size(max = 30) String telephone,
                            @Size(max = 200) String adresse, @NotNull @Min(0) @Max(30) Integer delaiLivraison,
                            Boolean principal, Boolean factureA15, Boolean actif, Integer version) {
    }

    record PrixSaisi(@NotNull UUID produitId, @NotNull LocalDate debut,
                     @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal prix) {
    }

    record TransporteurSaisi(@NotBlank @Size(max = 80) String nom, @Size(max = 30) String telephone,
                             @Size(max = 80) String contratReference, LocalDate contratDebut, LocalDate contratFin,
                             @DecimalMin("0") @DecimalMax("5") @Digits(integer = 1, fraction = 2) BigDecimal tolerancePct,
                             @Positive @Digits(integer = 15, fraction = 0) BigDecimal penalite, Boolean actif,
                             @Size(max = 500) String observation, Integer version) {
        Transporteur.Donnees donnees() {
            return new Transporteur.Donnees(nom, telephone, contratReference, contratDebut, contratFin, tolerancePct, penalite,
                    actif, observation);
        }
    }

    record CiterneSaisie(@NotBlank @Size(max = 20) String immatriculation,
                         @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal capaciteLitres, Boolean actif,
                         Integer version) {
    }

    record LivraisonSaisie(@NotNull UUID stationId, @NotNull UUID produitId,
                           @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantite) {
    }

    record TourneeSaisie(@NotNull UUID fournisseurId, @NotNull UUID transporteurId, UUID citerneId,
                         @Size(max = 20) String immatriculation, @Size(max = 80) String chauffeur,
                         @NotNull LocalDate dateChargementPrevue, @NotNull LocalDate dateLivraisonPrevue,
                         @Size(max = 500) String observation, @Size(max = 20) List<@Valid @NotNull LivraisonSaisie> livraisons,
                         Integer version) {
        SaisieTournee saisie() {
            return new SaisieTournee(fournisseurId, transporteurId, citerneId, immatriculation, chauffeur, dateChargementPrevue,
                    dateLivraisonPrevue, observation, livraisons == null ? null
                    : livraisons.stream().map(l -> new SaisieLivraison(l.stationId(), l.produitId(), l.quantite())).toList(),
                    version);
        }
    }

    record BlSaisi(@NotNull UUID livraisonId, @NotBlank @Size(max = 30) String numeroBl,
                   @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteBl,
                   @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantiteBl15) {
    }

    record Chargement(@NotNull Integer version, @NotNull LocalDate date, @Size(max = 120) String scelles,
                      @NotNull @Size(max = 20) List<@Valid @NotNull BlSaisi> livraisons) {
    }

    record Version(@NotNull Integer version) {
    }

    record Motif(@NotNull Integer version, @NotBlank @Size(max = 500) String motif) {
    }

    record Reglement(@NotNull Integer version, @NotBlank @Size(max = 80) String reference, @NotNull LocalDate date) {
    }

    record LigneSaisie(@NotBlank @Size(max = 30) String numeroBl, @NotNull UUID produitId,
                       @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal quantite,
                       @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal prixUnitaire) {
    }

    record FactureSaisie(@NotNull UUID fournisseurId, @NotBlank @Size(max = 40) String numero, @NotNull LocalDate dateFacture,
                         @Size(max = 500) String observation,
                         @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull LigneSaisie> lignes, Integer version) {
        SaisieFacture saisie() {
            return new SaisieFacture(fournisseurId, numero, dateFacture, observation, lignes.stream()
                    .map(l -> new SaisieLigneFacture(l.numeroBl(), l.produitId(), l.quantite(), l.prixUnitaire())).toList(),
                    version);
        }
    }

    record Decision(@NotNull Integer version, @NotNull Boolean bonAPayer, @Size(max = 500) String motif) {
    }

    private final PartenairesService partenaires;
    private final TourneeService tournees;
    private final FactureFournisseurService factures;

    ApproController(PartenairesService partenaires, TourneeService tournees, FactureFournisseurService factures) {
        this.partenaires = partenaires;
        this.tournees = tournees;
        this.factures = factures;
    }

    // ---------------------------------------------------------------- Fournisseurs

    @Operation(summary = "Fournisseurs (dépôts), délai de livraison et prix d'achat convenus")
    @GetMapping("/fournisseurs")
    List<FournisseurVue> fournisseurs() {
        return partenaires.fournisseurs();
    }

    @Operation(summary = "Crée un fournisseur")
    @PostMapping("/fournisseurs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    FournisseurVue creerFournisseur(@Valid @RequestBody FournisseurSaisi f) {
        return partenaires.enregistrerFournisseur(null, null, f.nom(), f.telephone(), f.adresse(), f.delaiLivraison(),
                f.principal(), f.factureA15(), f.actif());
    }

    @Operation(summary = "Modifie un fournisseur")
    @PutMapping("/fournisseurs/{id}")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    FournisseurVue modifierFournisseur(@PathVariable UUID id, @Valid @RequestBody FournisseurSaisi f) {
        return partenaires.enregistrerFournisseur(id, f.version(), f.nom(), f.telephone(), f.adresse(), f.delaiLivraison(),
                f.principal(), f.factureA15(), f.actif());
    }

    @Operation(summary = "Prix d'achat convenu d'un produit à partir d'une date")
    @PostMapping("/fournisseurs/{id}/prix")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    FournisseurVue ajouterPrix(@PathVariable UUID id, @Valid @RequestBody PrixSaisi p) {
        return partenaires.ajouterPrix(id, p.produitId(), p.debut(), p.prix());
    }

    @Operation(summary = "Retire un prix d'achat saisi par erreur")
    @DeleteMapping("/prix/{id}")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    FournisseurVue supprimerPrix(@PathVariable UUID id) {
        return partenaires.supprimerPrix(id);
    }

    // ---------------------------------------------------------------- Transporteurs

    @Operation(summary = "Transporteurs : contrat, tolérance, pénalité, citernes et note")
    @GetMapping("/transporteurs")
    List<TransporteurVue> transporteurs() {
        return partenaires.transporteurs();
    }

    @Operation(summary = "Fiche transporteur : livraisons notées et pénalités")
    @GetMapping("/transporteurs/{id}")
    TransporteurDetail transporteur(@PathVariable UUID id) {
        return partenaires.transporteur(id);
    }

    @Operation(summary = "Crée un transporteur")
    @PostMapping("/transporteurs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TransporteurDetail creerTransporteur(@Valid @RequestBody TransporteurSaisi t) {
        return partenaires.enregistrerTransporteur(null, null, t.donnees());
    }

    @Operation(summary = "Modifie un transporteur et son contrat")
    @PutMapping("/transporteurs/{id}")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TransporteurDetail modifierTransporteur(@PathVariable UUID id, @Valid @RequestBody TransporteurSaisi t) {
        return partenaires.enregistrerTransporteur(id, t.version(), t.donnees());
    }

    @Operation(summary = "Ajoute une citerne au transporteur")
    @PostMapping("/transporteurs/{id}/citernes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TransporteurDetail ajouterCiterne(@PathVariable UUID id, @Valid @RequestBody CiterneSaisie c) {
        return partenaires.enregistrerCiterne(id, null, null, c.immatriculation(), c.capaciteLitres(), c.actif());
    }

    @Operation(summary = "Modifie ou retire une citerne")
    @PutMapping("/transporteurs/{id}/citernes/{citerneId}")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TransporteurDetail modifierCiterne(@PathVariable UUID id, @PathVariable UUID citerneId, @Valid @RequestBody CiterneSaisie c) {
        return partenaires.enregistrerCiterne(id, citerneId, c.version(), c.immatriculation(), c.capaciteLitres(), c.actif());
    }

    @Operation(summary = "Pénalités de transport (livraisons hors tolérance)")
    @GetMapping("/penalites")
    List<PenaliteVue> penalites(@RequestParam(required = false) String statut) {
        return partenaires.penalites(statut);
    }

    @Operation(summary = "Enregistre le règlement d'une pénalité (retenue sur facture, reçu, virement)")
    @PostMapping("/penalites/{id}/reglement")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    PenaliteVue reglerPenalite(@PathVariable UUID id, @Valid @RequestBody Reglement r) {
        return partenaires.reglerPenalite(id, r.version(), r.reference(), r.date());
    }

    // ---------------------------------------------------------------- Propositions et tournees

    @Operation(summary = "Proposition de commande par station et produit (autonomie, délai du dépôt principal)")
    @GetMapping("/propositions")
    Propositions propositions() {
        return tournees.propositions();
    }

    @Operation(summary = "Tournées citernes (toutes, ou en cours seulement)")
    @GetMapping("/tournees")
    List<TourneeVue> tournees(@RequestParam(defaultValue = "false") boolean enCours) {
        return tournees.liste(enCours);
    }

    @Operation(summary = "Tournée, ses livraisons et leurs réceptions")
    @GetMapping("/tournees/{id}")
    TourneeVue tournee(@PathVariable UUID id) {
        return tournees.detail(id);
    }

    @Operation(summary = "Commande une tournée : dépôt, transporteur, citerne, livraisons")
    @PostMapping("/tournees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TourneeVue creerTournee(@Valid @RequestBody TourneeSaisie t) {
        return tournees.creer(t.saisie());
    }

    @Operation(summary = "Modifie une tournée (livraisons modifiables tant que la citerne n'est pas chargée)")
    @PutMapping("/tournees/{id}")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TourneeVue modifierTournee(@PathVariable UUID id, @Valid @RequestBody TourneeSaisie t) {
        return tournees.modifier(id, t.saisie());
    }

    @Operation(summary = "Chargement au dépôt : BL et quantités de chaque livraison")
    @PostMapping("/tournees/{id}/chargement")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TourneeVue charger(@PathVariable UUID id, @Valid @RequestBody Chargement c) {
        return tournees.charger(id, c.version(), c.date(), c.scelles(), c.livraisons().stream()
                .map(b -> new SaisieBl(b.livraisonId(), b.numeroBl(), b.quantiteBl(), b.quantiteBl15())).toList());
    }

    @Operation(summary = "Départ de la citerne chargée")
    @PostMapping("/tournees/{id}/depart")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TourneeVue partir(@PathVariable UUID id, @Valid @RequestBody Version v) {
        return tournees.partir(id, v.version());
    }

    @Operation(summary = "Annule une tournée dont aucune livraison n'est réceptionnée")
    @PostMapping("/tournees/{id}/annulation")
    @PreAuthorize("hasAuthority('RECEPTION_VALIDER')")
    TourneeVue annuler(@PathVariable UUID id, @Valid @RequestBody Motif m) {
        return tournees.annuler(id, m.version(), m.motif());
    }

    // ---------------------------------------------------------------- Factures fournisseur

    @Operation(summary = "Factures fournisseur et résultat de leur contrôle")
    @GetMapping("/factures")
    List<FactureFournisseurVue> factures() {
        return factures.liste();
    }

    @Operation(summary = "Facture fournisseur contrôlée ligne à ligne (BL, quantité reçue, prix convenu)")
    @GetMapping("/factures/{id}")
    FactureFournisseurDetail facture(@PathVariable UUID id) {
        return factures.detail(id);
    }

    @Operation(summary = "Saisit une facture fournisseur (une ligne par BL)")
    @PostMapping("/factures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('RECEPTION_VALIDER', 'VERSEMENT_VALIDER')")
    FactureFournisseurDetail creerFacture(@Valid @RequestBody FactureSaisie f) {
        return factures.creer(f.saisie());
    }

    @Operation(summary = "Corrige une facture saisie ou en litige")
    @PutMapping("/factures/{id}")
    @PreAuthorize("hasAnyAuthority('RECEPTION_VALIDER', 'VERSEMENT_VALIDER')")
    FactureFournisseurDetail modifierFacture(@PathVariable UUID id, @Valid @RequestBody FactureSaisie f) {
        return factures.modifier(id, f.saisie());
    }

    @Operation(summary = "Bon à payer ou litige (motif exigé pour un litige, ou un bon à payer malgré des anomalies)")
    @PostMapping("/factures/{id}/decision")
    @PreAuthorize("hasAnyAuthority('RECEPTION_VALIDER', 'VERSEMENT_VALIDER')")
    FactureFournisseurDetail decider(@PathVariable UUID id, @Valid @RequestBody Decision d) {
        return factures.decider(id, d.version(), d.bonAPayer(), d.motif());
    }

    @Operation(summary = "Annule une facture saisie par erreur")
    @PostMapping("/factures/{id}/annulation")
    @PreAuthorize("hasAnyAuthority('RECEPTION_VALIDER', 'VERSEMENT_VALIDER')")
    FactureFournisseurDetail annulerFacture(@PathVariable UUID id, @Valid @RequestBody Motif m) {
        return factures.annuler(id, m.version(), m.motif());
    }
}
