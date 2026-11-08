package sn.digitalperf.jaugeo.referentiel.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.domaine.Bareme;
import sn.digitalperf.jaugeo.referentiel.domaine.Cuve;
import sn.digitalperf.jaugeo.referentiel.domaine.CuveDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipementDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.PistoletDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.PointBareme;
import sn.digitalperf.jaugeo.referentiel.domaine.PointBaremeDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Produit;
import sn.digitalperf.jaugeo.referentiel.domaine.ProduitDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Station;
import sn.digitalperf.jaugeo.referentiel.domaine.StationDepot;

@Service
@Transactional(readOnly = true)
class ReferentielConsultationService implements ReferentielConsultation {

    private final StationDepot stations;
    private final ProduitDepot produits;
    private final CuveDepot cuves;
    private final PointBaremeDepot baremes;
    private final PistoletDepot pistolets;
    private final EvenementEquipementDepot evenements;

    ReferentielConsultationService(StationDepot stations, ProduitDepot produits, CuveDepot cuves,
                                   PointBaremeDepot baremes, PistoletDepot pistolets,
                                   EvenementEquipementDepot evenements) {
        this.stations = stations;
        this.produits = produits;
        this.cuves = cuves;
        this.baremes = baremes;
        this.pistolets = pistolets;
        this.evenements = evenements;
    }

    @Override
    public List<StationRef> stations() {
        return stations.findAllByOrderByCodeAsc().stream().map(ReferentielConsultationService::ref).toList();
    }

    @Override
    public Optional<StationRef> station(UUID uuid) {
        return stations.findByUuid(uuid).map(ReferentielConsultationService::ref);
    }

    @Override
    public Optional<StationRef> station(long id) {
        return stations.findById(id).map(ReferentielConsultationService::ref);
    }

    @Override
    public List<ProduitRef> produits() {
        return produits.findAllByOrderByFamilleAscLibelleAsc().stream().map(ReferentielConsultationService::ref).toList();
    }

    @Override
    public Optional<ProduitRef> produit(String code) {
        return code == null ? Optional.empty() : produits.findByCode(code.trim().toUpperCase()).map(ReferentielConsultationService::ref);
    }

    @Override
    public Map<Long, Set<Long>> produitsEnCuve() {
        return cuves.findByActifTrue().stream().collect(Collectors.groupingBy(c -> c.getStation().getId(),
                Collectors.mapping(c -> c.getProduit().getId(), Collectors.toUnmodifiableSet())));
    }

    @Override
    public BigDecimal volumeJauge(UUID cuveId, int hauteurMm) {
        Cuve cuve = cuves.findByUuid(cuveId).orElseThrow(() -> ErreurMetier.introuvable("Cuve"));
        List<PointBareme> points = baremes.findByCuveIdOrderByHauteurMmAsc(cuve.getId());
        if (points.isEmpty()) {
            throw ErreurMetier.regle("BAREME_ABSENT", "La cuve " + cuve.getCode() + " n'a pas encore de barème de jaugeage.");
        }
        return Bareme.valider(points.stream().map(PointBareme::point).toList()).volumePour(hauteurMm);
    }

    @Override
    public List<ChangementCompteur> changementsCompteur(UUID pistoletId, Instant debut, Instant fin) {
        Long id = pistolets.findByUuid(pistoletId).orElseThrow(() -> ErreurMetier.introuvable("Pistolet")).getId();
        return evenements.changementsCompteur(id, debut, fin).stream()
                .map(e -> new ChangementCompteur(e.getDate(), e.getIndexAvant(), e.getIndexApres()))
                .toList();
    }

    private static StationRef ref(Station s) {
        return new StationRef(s.getId(), s.getUuid(), s.getCode(), s.getNom(), s.isActif());
    }

    private static ProduitRef ref(Produit p) {
        return new ProduitRef(p.getId(), p.getUuid(), p.getCode(), p.getLibelle(), p.getUnite().name(),
                p.isSuiviJaugeage(), p.isActif());
    }
}
