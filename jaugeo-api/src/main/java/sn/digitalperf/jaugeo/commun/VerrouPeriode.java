package sn.digitalperf.jaugeo.commun;

import java.time.LocalDate;

/**
 * Verrou des periodes cloturees : un module qui enregistre une ecriture datee (renvoi de journee, versement,
 * arrete de caisse, operation de compte client...) l'interroge d'abord. Mis en oeuvre par le reporting
 * (cloture mensuelle), sans que les modules d'ecriture en dependent.
 */
public interface VerrouPeriode {

    /** Refuse (MOIS_CLOS) une ecriture datee dans un mois cloture pour cette station. */
    void exigerOuvert(long stationId, LocalDate date);
}
