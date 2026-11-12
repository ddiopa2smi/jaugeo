package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.stock.LivraisonsAttendues;
import sn.digitalperf.jaugeo.stock.LivraisonsAttendues.LivraisonAttendue;
import sn.digitalperf.jaugeo.stock.domaine.Reception;

/**
 * Tolerance de manquant d'une reception : celle du transporteur (contrat, M8) quand il en a une, sinon celle du
 * reseau ({@code jaugeo.stock.tolerance-transport-pct}). Donne aussi acces aux livraisons attendues.
 */
@Component
class ToleranceTransport {

    private final ObjectProvider<LivraisonsAttendues> livraisons;
    private final BigDecimal reseauPct;

    ToleranceTransport(ObjectProvider<LivraisonsAttendues> livraisons,
                       @Value("${jaugeo.stock.tolerance-transport-pct:0.3}") BigDecimal reseauPct) {
        this.livraisons = livraisons;
        this.reseauPct = reseauPct;
    }

    BigDecimal reseauPct() {
        return reseauPct;
    }

    BigDecimal pour(Reception r) {
        return pour(r.getLivraisonId(), r.getTransporteur());
    }

    /** Tolerance du contrat du transporteur de la livraison (ou nomme), sinon celle du reseau. */
    BigDecimal pour(Long livraisonId, String transporteur) {
        LivraisonsAttendues l = livraisons.getIfAvailable();
        return l == null ? reseauPct : l.tolerancePct(livraisonId, transporteur).orElse(reseauPct);
    }

    List<LivraisonAttendue> attendues(long stationId) {
        LivraisonsAttendues l = livraisons.getIfAvailable();
        return l == null ? List.of() : l.attendues(stationId);
    }

    /** Fournisseurs actifs proposes a la saisie d'une reception (vide sans module d'approvisionnement). */
    List<String> fournisseurs() {
        LivraisonsAttendues l = livraisons.getIfAvailable();
        return l == null ? List.of() : l.fournisseurs();
    }

    Optional<LivraisonAttendue> livraison(UUID uuid) {
        LivraisonsAttendues l = livraisons.getIfAvailable();
        return l == null ? Optional.empty() : l.livraison(uuid);
    }

    Optional<LivraisonAttendue> livraison(Long id) {
        LivraisonsAttendues l = livraisons.getIfAvailable();
        return l == null || id == null ? Optional.empty() : l.livraison(id);
    }
}
