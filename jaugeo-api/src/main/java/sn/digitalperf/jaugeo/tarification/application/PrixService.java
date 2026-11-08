package sn.digitalperf.jaugeo.tarification.application;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tarification.Tarification;
import sn.digitalperf.jaugeo.tarification.domaine.ChangementPrix;
import sn.digitalperf.jaugeo.tarification.domaine.ChangementPrixDepot;
import sn.digitalperf.jaugeo.tarification.domaine.PrixProduit;
import sn.digitalperf.jaugeo.tarification.domaine.PrixProduitDepot;

/**
 * Prix dates. Regles :
 * <ul>
 *   <li>un nouveau prix s'ajoute a la suite de l'historique : sa date d'effet est posterieure au dernier prix
 *       du couple station/produit, qui est alors clos a cette date (jamais de trou ni de chevauchement) ;</li>
 *   <li>un changement s'applique en une fois a toutes les stations visees qui vendent le produit ;</li>
 *   <li>une variation de plus de 20 % doit etre confirmee (protection contre les fautes de frappe) ;</li>
 *   <li>un changement deja entre en vigueur ne s'annule pas : on saisit un nouveau changement.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PrixService implements Tarification {

    static final BigDecimal SEUIL_VARIATION = new BigDecimal("0.20");

    public record LignePrix(String produitCode, BigDecimal prixUnitaire) {
    }

    public record Demande(Instant debut, String motif, String reference, List<UUID> stations,
                          List<LignePrix> prix, boolean confirmerVariation) {
    }

    public record LigneChangement(UUID stationId, String stationCode, String stationNom, String produitCode,
                                  String produitLibelle, BigDecimal ancienPrix, BigDecimal nouveauPrix) {
    }

    public record ChangementVue(UUID id, Instant debut, String motif, String reference, boolean annule,
                                boolean annulable, String creePar, Instant creeLe, String annulePar,
                                Instant annuleLe, List<LigneChangement> lignes, List<String> ignores) {
    }

    public record LigneGrille(UUID stationId, String stationCode, String stationNom, String produitCode,
                              String produitLibelle, BigDecimal prix, Instant depuis, BigDecimal prochainPrix,
                              Instant prochainDebut) {
    }

    public record PrixVue(UUID id, String produitCode, String produitLibelle, BigDecimal prixUnitaire,
                          Instant debut, Instant fin, UUID changementId, String motif, String reference,
                          String creePar) {
    }

    private final PrixProduitDepot prix;
    private final ChangementPrixDepot changements;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final EntityManager em;
    private final Clock horloge;

    public PrixService(PrixProduitDepot prix, ChangementPrixDepot changements, ReferentielConsultation referentiel,
                       PerimetreCourant perimetre, EntityManager em, Clock horloge) {
        this.prix = prix;
        this.changements = changements;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.em = em;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------ Interface publique (autres modules)

    @Override
    public Optional<BigDecimal> prixEnVigueur(long stationId, long produitId, Instant instant) {
        return prix.enVigueur(stationId, produitId, instant).map(PrixProduit::getPrixUnitaire);
    }

    @Override
    public List<PeriodePrix> periodes(long stationId, long produitId, Instant debut, Instant fin) {
        return prix.chevauchant(stationId, produitId, debut, fin).stream()
                .map(p -> new PeriodePrix(p.getUuid(), p.getPrixUnitaire(), p.getDebut(), p.getFin()))
                .toList();
    }

    // ------------------------------------------------------------ Consultation

    /**
     * Grille des prix des stations du perimetre : pour chaque produit vendu (en cuve ou deja tarife),
     * le prix en vigueur et le prochain prix programme. Un prix absent apparait avec prix = null.
     */
    public List<LigneGrille> grille() {
        Instant maintenant = horloge.instant();
        List<StationRef> stations = stationsDuPerimetre().stream().filter(StationRef::actif).toList();
        Map<Long, ProduitRef> produits = produitsParId();
        Map<Long, Set<Long>> enCuve = referentiel.produitsEnCuve();
        Map<Long, Map<Long, List<PrixProduit>>> parStation = stations.isEmpty() ? Map.of()
                : prix.courantsEtFuturs(stations.stream().map(StationRef::id).toList(), maintenant).stream()
                .collect(Collectors.groupingBy(PrixProduit::getStationId, Collectors.groupingBy(PrixProduit::getProduitId)));

        List<LigneGrille> lignes = new ArrayList<>();
        for (StationRef s : stations) {
            Map<Long, List<PrixProduit>> prixStation = parStation.getOrDefault(s.id(), Map.of());
            Set<Long> produitIds = new HashSet<>(enCuve.getOrDefault(s.id(), Set.of()));
            produitIds.addAll(prixStation.keySet());
            produitIds.stream()
                    .map(produits::get)
                    .filter(p -> p != null && (p.actif() || prixStation.containsKey(p.id())))
                    .sorted(Comparator.comparing(ProduitRef::libelle))
                    .forEach(p -> {
                        List<PrixProduit> liste = prixStation.getOrDefault(p.id(), List.of());
                        PrixProduit actuel = liste.stream().filter(x -> x.enVigueur(maintenant)).findFirst().orElse(null);
                        PrixProduit prochain = liste.stream().filter(x -> x.getDebut().isAfter(maintenant)).findFirst().orElse(null);
                        lignes.add(new LigneGrille(s.uuid(), s.code(), s.nom(), p.code(), p.libelle(),
                                actuel == null ? null : actuel.getPrixUnitaire(), actuel == null ? null : actuel.getDebut(),
                                prochain == null ? null : prochain.getPrixUnitaire(), prochain == null ? null : prochain.getDebut()));
                    });
        }
        return lignes;
    }

    public List<PrixVue> historique(UUID stationUuid) {
        StationRef s = stationAccessible(stationUuid);
        Map<Long, ProduitRef> produits = produitsParId();
        return prix.historiqueStation(s.id()).stream().map(p -> {
            ProduitRef pr = produits.get(p.getProduitId());
            return new PrixVue(p.getUuid(), pr.code(), pr.libelle(), p.getPrixUnitaire(), p.getDebut(), p.getFin(),
                    p.getChangement().getUuid(), p.getChangement().getMotif(), p.getChangement().getReference(),
                    p.getCreePar());
        }).toList();
    }

    /** Changements de prix touchant au moins une station du perimetre, du plus recent au plus ancien. */
    public List<ChangementVue> changements() {
        List<ChangementPrix> liste = changements.findAllByOrderByDebutDescIdDesc();
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PrixProduit>> lignes = prix.parChangements(liste.stream().map(ChangementPrix::getId).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getChangement().getId()));
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        Map<Long, ProduitRef> produits = produitsParId();
        Instant maintenant = horloge.instant();
        List<ChangementVue> vues = new ArrayList<>();
        for (ChangementPrix c : liste) {
            List<PrixProduit> visibles = lignes.getOrDefault(c.getId(), List.of()).stream()
                    .filter(p -> perimetre.peutVoirStation(p.getStationId()))
                    .toList();
            if (visibles.isEmpty() && !(c.isAnnule() && perimetre.estReseau())) {
                continue;
            }
            vues.add(vue(c, visibles, stations, produits, List.of(), maintenant));
        }
        return vues;
    }

    // ------------------------------------------------------------ Changement de prix

    @Transactional
    public ChangementVue appliquer(Demande d) {
        Instant maintenant = horloge.instant();
        if (d.prix() == null || d.prix().isEmpty()) {
            throw ErreurMetier.regle("PRIX_OBLIGATOIRES", "Indiquez au moins un produit et son nouveau prix.");
        }
        Set<String> codes = new HashSet<>();
        Map<ProduitRef, BigDecimal> nouveaux = new LinkedHashMap<>();
        for (LignePrix l : d.prix()) {
            ProduitRef p = referentiel.produit(l.produitCode())
                    .orElseThrow(() -> ErreurMetier.regle("PRODUIT_INCONNU", "Produit inconnu : " + l.produitCode()));
            if (!codes.add(p.code())) {
                throw ErreurMetier.regle("PRODUIT_EN_DOUBLE", "Le produit « " + p.libelle() + " » est indiqué deux fois.");
            }
            if (!p.actif()) {
                throw ErreurMetier.regle("PRODUIT_INACTIF", "Le produit « " + p.libelle() + " » est désactivé.");
            }
            if (l.prixUnitaire() == null || l.prixUnitaire().signum() <= 0) {
                throw ErreurMetier.regle("PRIX_INVALIDE", "Le prix de « " + p.libelle() + " » doit être strictement positif.");
            }
            nouveaux.put(p, l.prixUnitaire().setScale(2, RoundingMode.UNNECESSARY));
        }

        List<StationRef> cibles = ciblesStations(d.stations());
        Map<Long, Set<Long>> enCuve = referentiel.produitsEnCuve();
        ChangementPrix changement = new ChangementPrix(d.debut(), d.motif(), d.reference());

        record Cible(StationRef station, ProduitRef produit, BigDecimal nouveau, PrixProduit precedent) {
        }
        List<Cible> aAppliquer = new ArrayList<>();
        List<String> ignores = new ArrayList<>();
        List<String> variations = new ArrayList<>();
        for (StationRef s : cibles) {
            for (Map.Entry<ProduitRef, BigDecimal> e : nouveaux.entrySet()) {
                ProduitRef p = e.getKey();
                boolean vendu = !p.suiviJaugeage() || enCuve.getOrDefault(s.id(), Set.of()).contains(p.id());
                if (!vendu) {
                    ignores.add(s.code() + " : pas de cuve de " + p.libelle());
                    continue;
                }
                PrixProduit precedent = prix.dernierPourMiseAJour(s.id(), p.id()).orElse(null);
                if (precedent != null && !d.debut().isAfter(precedent.getDebut())) {
                    throw ErreurMetier.regle("PRIX_POSTERIEUR_EXISTANT", s.code() + " · " + p.libelle()
                            + " : un prix prend déjà effet le " + Formats.dateHeure(precedent.getDebut())
                            + ". La date d'effet doit être postérieure.");
                }
                if (precedent != null && variationExcessive(precedent.getPrixUnitaire(), e.getValue())) {
                    variations.add(s.code() + " · " + p.libelle() + " : " + Formats.prix(precedent.getPrixUnitaire())
                            + " → " + Formats.prix(e.getValue()));
                }
                aAppliquer.add(new Cible(s, p, e.getValue(), precedent));
            }
        }
        if (aAppliquer.isEmpty()) {
            throw ErreurMetier.regle("AUCUN_PRIX_APPLICABLE",
                    "Aucune des stations choisies ne vend ces produits : aucun prix n'a été créé.");
        }
        if (!variations.isEmpty() && !d.confirmerVariation()) {
            throw ErreurMetier.regle("VARIATION_A_CONFIRMER", "Variation de plus de 20 % : "
                    + String.join(" ; ", variations) + ". Vérifiez les montants puis confirmez.");
        }

        changements.save(changement);
        List<PrixProduit> crees = new ArrayList<>();
        for (Cible c : aAppliquer) {
            if (c.precedent() != null && c.precedent().getFin() == null) {
                c.precedent().cloturer(changement.getDebut());
            }
            crees.add(new PrixProduit(changement, c.station().id(), c.produit().id(), c.nouveau()));
        }
        // Clore les anciens prix avant d'inserer les nouveaux : la contrainte d'exclusion le verifie ligne a ligne
        em.flush();
        prix.saveAll(crees);
        em.flush();

        Map<Long, StationRef> stations = cibles.stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        return vue(changement, crees, stations, produitsParId(), ignores, maintenant);
    }

    /** Annule un changement programme (pas encore en vigueur) : les prix precedents redeviennent valables. */
    @Transactional
    public ChangementVue annuler(UUID id) {
        Instant maintenant = horloge.instant();
        ChangementPrix c = changements.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Changement de prix"));
        List<PrixProduit> lignes = prix.findByChangementId(c.getId());
        for (PrixProduit p : lignes) {
            perimetre.exigerStation(p.getStationId());
            if (p.getFin() != null) {
                throw ErreurMetier.regle("CHANGEMENT_SUIVI",
                        "Un changement de prix postérieur existe pour ces produits : annulez-le d'abord.");
            }
        }
        c.annuler(maintenant, perimetre.identifiant());
        List<PrixProduit> precedents = lignes.stream()
                .map(p -> prix.precedent(p.getStationId(), p.getProduitId(), p.getDebut()))
                .flatMap(Optional::stream)
                .toList();
        // Supprimer les prix annules avant de rouvrir les precedents (sinon chevauchement)
        prix.deleteAll(lignes);
        em.flush();
        precedents.forEach(PrixProduit::rouvrir);
        em.flush();
        Map<Long, StationRef> stations = referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
        return vue(c, List.of(), stations, produitsParId(), List.of(), maintenant);
    }

    // ------------------------------------------------------------ Outils

    static boolean variationExcessive(BigDecimal ancien, BigDecimal nouveau) {
        BigDecimal ecart = nouveau.subtract(ancien).abs().divide(ancien, 4, RoundingMode.HALF_UP);
        return ecart.compareTo(SEUIL_VARIATION) > 0;
    }

    private List<StationRef> ciblesStations(List<UUID> demandees) {
        if (demandees == null || demandees.isEmpty()) {
            List<StationRef> toutes = stationsDuPerimetre().stream().filter(StationRef::actif).toList();
            if (toutes.isEmpty()) {
                throw ErreurMetier.regle("AUCUNE_STATION", "Aucune station active dans votre périmètre.");
            }
            return toutes;
        }
        List<StationRef> cibles = new ArrayList<>();
        for (UUID id : new java.util.LinkedHashSet<>(demandees)) {
            StationRef s = stationAccessible(id);
            if (!s.actif()) {
                throw ErreurMetier.regle("STATION_FERMEE", "La station " + s.code() + " est fermée.");
            }
            cibles.add(s);
        }
        return cibles;
    }

    private List<StationRef> stationsDuPerimetre() {
        return referentiel.stations().stream().filter(s -> perimetre.peutVoirStation(s.id())).toList();
    }

    private StationRef stationAccessible(UUID id) {
        StationRef s = referentiel.station(id).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.id());
        return s;
    }

    private Map<Long, ProduitRef> produitsParId() {
        return referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
    }

    private ChangementVue vue(ChangementPrix c, List<PrixProduit> lignes, Map<Long, StationRef> stations,
                              Map<Long, ProduitRef> produits, List<String> ignores, Instant maintenant) {
        List<LigneChangement> lc = lignes.stream().map(p -> {
            StationRef s = stations.get(p.getStationId());
            ProduitRef pr = produits.get(p.getProduitId());
            BigDecimal ancien = prix.precedent(p.getStationId(), p.getProduitId(), p.getDebut())
                    .map(PrixProduit::getPrixUnitaire).orElse(null);
            return new LigneChangement(s == null ? null : s.uuid(), s == null ? "?" : s.code(), s == null ? "?" : s.nom(),
                    pr.code(), pr.libelle(), ancien, p.getPrixUnitaire());
        }).sorted(Comparator.comparing(LigneChangement::stationCode).thenComparing(LigneChangement::produitLibelle)).toList();
        boolean annulable = !c.isAnnule() && c.getDebut().isAfter(maintenant);
        return new ChangementVue(c.getUuid(), c.getDebut(), c.getMotif(), c.getReference(), c.isAnnule(), annulable,
                c.getCreePar(), c.getCreeLe(), c.getAnnulePar(), c.getAnnuleLe(), lc, ignores);
    }
}
