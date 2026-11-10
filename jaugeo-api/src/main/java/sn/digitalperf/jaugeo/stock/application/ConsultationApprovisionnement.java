package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.CuveRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterne;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterneDepot;
import sn.digitalperf.jaugeo.stock.domaine.EcartStock;
import sn.digitalperf.jaugeo.stock.domaine.EcartStockDepot;
import sn.digitalperf.jaugeo.stock.domaine.Reception;
import sn.digitalperf.jaugeo.stock.domaine.ReceptionDepot;

@Component
@Transactional(readOnly = true)
class ConsultationApprovisionnement implements StocksApprovisionnement {

    private final ReferentielConsultation referentiel;
    private final EcartStockDepot ecarts;
    private final ReceptionDepot receptions;
    private final ComplementCiterneDepot complements;

    ConsultationApprovisionnement(ReferentielConsultation referentiel, EcartStockDepot ecarts, ReceptionDepot receptions,
                                  ComplementCiterneDepot complements) {
        this.referentiel = referentiel;
        this.ecarts = ecarts;
        this.receptions = receptions;
        this.complements = complements;
    }

    @Override
    public List<EtatCuve> etatCuves() {
        List<Long> stations = referentiel.stations().stream().filter(StationRef::actif).map(StationRef::id).toList();
        if (stations.isEmpty()) {
            return List.of();
        }
        Map<Long, CuveRef> cuves = new HashMap<>();
        stations.forEach(id -> referentiel.cuves(id).stream().filter(CuveRef::actif).forEach(c -> cuves.put(c.id(), c)));
        List<EtatCuve> etats = new ArrayList<>();
        for (EcartStock e : ecarts.dernieres(stations)) {
            CuveRef c = cuves.get(e.getCuveId());
            if (c == null) {
                continue;
            }
            EcartStockDepot.Moyenne m = ecarts.sorties(c.id(), e.getDateJour().minusDays(7), e.getDateJour());
            int jours = m.getJours().intValue();
            BigDecimal moyenne = jours == 0 ? null : m.getSorties().divide(BigDecimal.valueOf(jours), 2, RoundingMode.HALF_UP);
            etats.add(new EtatCuve(c.stationId(), c.id(), c.code(), c.produitId(), c.capaciteLitres(), c.creuxSecuriteLitres(),
                    c.seuilReapproLitres(), e.getDateJour(), e.getStockPhysique(), moyenne));
        }
        return etats;
    }

    @Override
    public List<ReceptionRef> receptionsDesLivraisons(Collection<Long> livraisonIds) {
        return livraisonIds.isEmpty() ? List.of() : vues(receptions.findByLivraisonIdIn(livraisonIds));
    }

    @Override
    public List<ReceptionRef> receptionsDeLaJournee(long journeeId) {
        return vues(receptions.findByJourneeIdOrderByIdAsc(journeeId));
    }

    @Override
    public Optional<ReceptionRef> receptionParBl(String fournisseur, String numeroBl) {
        return receptions.parBl(fournisseur.trim(), numeroBl.trim()).map(r -> vues(List.of(r)).get(0));
    }

    private List<ReceptionRef> vues(List<Reception> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, ComplementCiterne> figes = complements.findByReceptionIdIn(liste.stream().map(Reception::getId).toList())
                .stream().collect(Collectors.toMap(ComplementCiterne::getReceptionId, Function.identity()));
        return liste.stream().map(r -> {
            ComplementCiterne c = figes.get(r.getId());
            BigDecimal manquant = r.quantiteLivree().subtract(r.quantiteMesuree()).max(BigDecimal.ZERO);
            return new ReceptionRef(r.getId(), r.getLivraisonId(), r.getJourneeId(), r.getStationId(), r.getProduitId(),
                    r.getDateJour(), r.getNumeroBl(), r.getFournisseur(), r.getTransporteur(), r.getQuantiteBl(),
                    r.getQuantiteBl15(), r.getQuantiteRecue(), r.compareeA15(), r.quantiteLivree(), r.quantiteMesuree(),
                    manquant, r.getImputation().name(), c == null ? null : c.getLitres(),
                    c == null ? null : c.getStatut().name());
        }).toList();
    }
}
