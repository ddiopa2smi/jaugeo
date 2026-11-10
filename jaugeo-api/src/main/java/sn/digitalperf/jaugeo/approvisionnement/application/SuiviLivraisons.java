package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.ReceptionLivraison;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CalculAppro;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.ReceptionRef;

/** Receptions des livraisons (module stock), tolerances de manquant et references du referentiel. */
@Component
class SuiviLivraisons {

    private final StocksApprovisionnement stocks;
    private final ReferentielConsultation referentiel;
    private final BigDecimal toleranceReseauPct;

    SuiviLivraisons(StocksApprovisionnement stocks, ReferentielConsultation referentiel,
                    @Value("${jaugeo.stock.tolerance-transport-pct:0.3}") BigDecimal toleranceReseauPct) {
        this.stocks = stocks;
        this.referentiel = referentiel;
        this.toleranceReseauPct = toleranceReseauPct;
    }

    StocksApprovisionnement stocks() {
        return stocks;
    }

    BigDecimal toleranceReseauPct() {
        return toleranceReseauPct;
    }

    BigDecimal tolerance(Transporteur t) {
        return t != null && t.getTolerancePct() != null ? t.getTolerancePct() : toleranceReseauPct;
    }

    Map<Long, ReceptionRef> receptions(Collection<Long> livraisonIds) {
        return stocks.receptionsDesLivraisons(livraisonIds).stream()
                .collect(Collectors.toMap(ReceptionRef::livraisonId, Function.identity(), (a, b) -> a));
    }

    ReceptionLivraison vue(ReceptionRef r, Tournee t, BigDecimal tolerancePct) {
        if (r == null) {
            return null;
        }
        return new ReceptionLivraison(r.date(), r.numeroBl(), r.quantiteLivree(), r.quantiteMesuree(), r.manquant(),
                CalculAppro.pourcentage(r.manquant(), r.quantiteLivree()), tolerancePct,
                CalculAppro.horsTolerance(r.quantiteLivree(), r.manquant(), tolerancePct), r.compareeA15(),
                !r.date().isAfter(t.getDateLivraisonPrevue()));
    }

    // ---------------------------------------------------------------- Referentiel

    Map<Long, StationRef> stations() {
        return referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
    }

    Map<Long, ProduitRef> produits() {
        return referentiel.produits().stream().collect(Collectors.toMap(ProduitRef::id, Function.identity()));
    }

    StationRef station(UUID uuid) {
        return referentiel.station(uuid).orElseThrow(() -> ErreurMetier.introuvable("Station"));
    }

    ProduitRef produit(UUID uuid) {
        return referentiel.produits().stream().filter(p -> p.uuid().equals(uuid)).findFirst()
                .orElseThrow(() -> ErreurMetier.introuvable("Produit"));
    }
}
