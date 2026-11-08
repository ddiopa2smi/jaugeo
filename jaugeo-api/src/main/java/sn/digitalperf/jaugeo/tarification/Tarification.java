package sn.digitalperf.jaugeo.tarification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface publique du module tarification, utilisee par l'exploitation pour valoriser les ventes.
 * Identifiants techniques (station, produit) tels que fournis par ReferentielConsultation.
 */
public interface Tarification {

    /** Periode de validite d'un prix, [debut, fin) ; fin nulle = en vigueur jusqu'a nouvel ordre. */
    record PeriodePrix(UUID prixId, BigDecimal prixUnitaire, Instant debut, Instant fin) {
    }

    Optional<BigDecimal> prixEnVigueur(long stationId, long produitId, Instant instant);

    /**
     * Prix successifs couvrant [debut, fin) : une journee traversee par un changement de prix
     * se decoupe en tranches, chacune valorisee a son prix.
     */
    List<PeriodePrix> periodes(long stationId, long produitId, Instant debut, Instant fin);
}
