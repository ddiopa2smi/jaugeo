package sn.digitalperf.jaugeo.referentiel.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.application.Vues.CuveVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.EquipementsVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.PistoletVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.PompeVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.ProduitVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.RegionVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.StationVue;
import sn.digitalperf.jaugeo.referentiel.domaine.Cuve;
import sn.digitalperf.jaugeo.referentiel.domaine.CuveDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Pistolet;
import sn.digitalperf.jaugeo.referentiel.domaine.PistoletDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Pompe;
import sn.digitalperf.jaugeo.referentiel.domaine.PompeDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Produit;
import sn.digitalperf.jaugeo.referentiel.domaine.ProduitDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Region;
import sn.digitalperf.jaugeo.referentiel.domaine.RegionDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Station;
import sn.digitalperf.jaugeo.referentiel.domaine.StationDepot;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;

/** Cas d'usage du referentiel. Toute lecture ou ecriture d'une station respecte le perimetre de l'utilisateur. */
@Service
@Transactional(readOnly = true)
public class ReferentielService {

    public record DonneesStation(String nom, Station.TypeStation type, String regionCode, String localite,
                                 BigDecimal latitude, BigDecimal longitude, Station.ModeExploitation modeExploitation,
                                 LocalDate dateOuverture, boolean actif) {
    }

    public record DonneesProduit(String libelle, Produit.Famille famille, Produit.Unite unite,
                                 boolean suiviJaugeage, BigDecimal tauxTva, boolean actif) {
    }

    private final StationDepot stations;
    private final RegionDepot regions;
    private final ProduitDepot produits;
    private final CuveDepot cuves;
    private final PompeDepot pompes;
    private final PistoletDepot pistolets;
    private final PerimetreCourant perimetre;

    public ReferentielService(StationDepot stations, RegionDepot regions, ProduitDepot produits, CuveDepot cuves,
                              PompeDepot pompes, PistoletDepot pistolets, PerimetreCourant perimetre) {
        this.stations = stations;
        this.regions = regions;
        this.produits = produits;
        this.cuves = cuves;
        this.pompes = pompes;
        this.pistolets = pistolets;
        this.perimetre = perimetre;
    }

    // ---------------------------------------------------------------- Regions

    public List<RegionVue> regions() {
        return regions.findAllByOrderByNomAsc().stream().map(RegionVue::de).toList();
    }

    // ---------------------------------------------------------------- Stations

    public List<StationVue> stations() {
        List<Station> liste = perimetre.estReseau()
                ? stations.findAllByOrderByCodeAsc()
                : stations.findByIdInOrderByCodeAsc(perimetre.stationIds());
        return liste.stream().map(StationVue::de).toList();
    }

    public StationVue station(UUID id) {
        return StationVue.de(stationAccessible(id));
    }

    @Transactional
    public StationVue creerStation(String code, DonneesStation d) {
        String c = code.trim().toUpperCase();
        if (stations.existsByCodeIgnoreCase(c)) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Une station avec le code « " + c + " » existe déjà.");
        }
        Station s = new Station(c);
        appliquer(s, d);
        return StationVue.de(stations.saveAndFlush(s));
    }

    @Transactional
    public StationVue modifierStation(UUID id, DonneesStation d, Integer version) {
        Station s = stationAccessible(id);
        s.verifierVersion(version);
        appliquer(s, d);
        return StationVue.de(stations.saveAndFlush(s));
    }

    private void appliquer(Station s, DonneesStation d) {
        Region region = regions.findByCode(d.regionCode())
                .orElseThrow(() -> ErreurMetier.regle("REGION_INCONNUE", "Région inconnue : " + d.regionCode()));
        s.modifier(d.nom().trim(), d.type(), region, vide(d.localite()), d.latitude(), d.longitude(),
                d.modeExploitation(), d.dateOuverture(), d.actif());
    }

    // ---------------------------------------------------------------- Produits

    public List<ProduitVue> produits() {
        return produits.findAllByOrderByFamilleAscLibelleAsc().stream().map(ProduitVue::de).toList();
    }

    @Transactional
    public ProduitVue creerProduit(String code, DonneesProduit d) {
        String c = code.trim().toUpperCase();
        if (produits.existsByCodeIgnoreCase(c)) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Un produit avec le code « " + c + " » existe déjà.");
        }
        Produit p = new Produit(c);
        p.modifier(d.libelle().trim(), d.famille(), d.unite(), d.suiviJaugeage(), d.tauxTva(), d.actif());
        return ProduitVue.de(produits.saveAndFlush(p));
    }

    @Transactional
    public ProduitVue modifierProduit(UUID id, DonneesProduit d, Integer version) {
        Produit p = produits.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Produit"));
        p.verifierVersion(version);
        p.modifier(d.libelle().trim(), d.famille(), d.unite(), d.suiviJaugeage(), d.tauxTva(), d.actif());
        return ProduitVue.de(produits.saveAndFlush(p));
    }

    // ---------------------------------------------------------------- Equipements

    public EquipementsVue equipements(UUID stationId) {
        Station s = stationAccessible(stationId);
        List<CuveVue> listeCuves = cuves.findByStationIdOrderByCodeAsc(s.getId()).stream().map(CuveVue::de).toList();
        Map<Long, List<PistoletVue>> parPompe = pistolets.parStation(s.getId()).stream()
                .sorted(Comparator.comparing(Pistolet::getCode))
                .collect(Collectors.groupingBy(p -> p.getPompe().getId(),
                        Collectors.mapping(PistoletVue::de, Collectors.toList())));
        List<PompeVue> listePompes = pompes.findByStationIdOrderByCodeAsc(s.getId()).stream()
                .map(p -> PompeVue.de(p, parPompe.getOrDefault(p.getId(), List.of())))
                .toList();
        return new EquipementsVue(StationVue.de(s), listeCuves, listePompes);
    }

    @Transactional
    public CuveVue creerCuve(UUID stationId, String code, String produitCode, BigDecimal capacite, BigDecimal creux) {
        Station s = stationAccessible(stationId);
        String c = code.trim().toUpperCase();
        if (cuves.existsByStationIdAndCodeIgnoreCase(s.getId(), c)) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "La cuve « " + c + " » existe déjà dans cette station.");
        }
        Produit produit = produits.findByCode(produitCode)
                .orElseThrow(() -> ErreurMetier.regle("PRODUIT_INCONNU", "Produit inconnu : " + produitCode));
        Cuve cuve = new Cuve(s, c, produit);
        cuve.modifier(capacite, creux, true);
        return CuveVue.de(cuves.saveAndFlush(cuve));
    }

    @Transactional
    public CuveVue modifierCuve(UUID cuveId, BigDecimal capacite, BigDecimal creux, boolean actif, Integer version) {
        Cuve cuve = cuves.findByUuid(cuveId).orElseThrow(() -> ErreurMetier.introuvable("Cuve"));
        perimetre.exigerStation(cuve.getStation().getId());
        cuve.verifierVersion(version);
        cuve.modifier(capacite, creux, actif);
        return CuveVue.de(cuves.saveAndFlush(cuve));
    }

    @Transactional
    public PompeVue creerPompe(UUID stationId, String code, String marque, String numeroSerie) {
        Station s = stationAccessible(stationId);
        String c = code.trim().toUpperCase();
        if (pompes.existsByStationIdAndCodeIgnoreCase(s.getId(), c)) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "La pompe « " + c + " » existe déjà dans cette station.");
        }
        Pompe p = new Pompe(s, c);
        p.modifier(vide(marque), vide(numeroSerie), true);
        return PompeVue.de(pompes.saveAndFlush(p), List.of());
    }

    @Transactional
    public PistoletVue creerPistolet(UUID pompeId, UUID cuveId, String code, String libelle, BigDecimal index) {
        Pompe pompe = pompes.findByUuid(pompeId).orElseThrow(() -> ErreurMetier.introuvable("Pompe"));
        perimetre.exigerStation(pompe.getStation().getId());
        Cuve cuve = cuves.findByUuid(cuveId).orElseThrow(() -> ErreurMetier.introuvable("Cuve"));
        String c = code.trim().toUpperCase();
        if (pistolets.existsByPompeIdAndCodeIgnoreCase(pompe.getId(), c)) {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Le pistolet « " + c + " » existe déjà sur cette pompe.");
        }
        return PistoletVue.de(pistolets.saveAndFlush(new Pistolet(pompe, cuve, c, libelle.trim(), index)));
    }

    @Transactional
    public PistoletVue modifierPistolet(UUID pistoletId, String libelle, boolean actif, Integer version) {
        Pistolet p = pistolets.findByUuid(pistoletId).orElseThrow(() -> ErreurMetier.introuvable("Pistolet"));
        perimetre.exigerStation(p.getPompe().getStation().getId());
        p.verifierVersion(version);
        p.modifier(libelle.trim(), actif);
        return PistoletVue.de(pistolets.saveAndFlush(p));
    }

    // ---------------------------------------------------------------- Outils

    private Station stationAccessible(UUID id) {
        Station s = stations.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.getId());
        return s;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
