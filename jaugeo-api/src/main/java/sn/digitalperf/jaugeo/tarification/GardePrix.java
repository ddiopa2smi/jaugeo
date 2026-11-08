package sn.digitalperf.jaugeo.tarification;

import java.time.Instant;
import java.util.Collection;

/**
 * Point d'extension : l'exploitation refuse un changement (ou une annulation) de prix qui modifierait
 * la valorisation d'une journee deja cloturee.
 */
public interface GardePrix {

    /** Leve une ErreurMetier si un prix ne peut pas changer a cet instant dans ces stations (identifiants techniques). */
    void verifierChangementPrix(Collection<Long> stationIds, Instant debut);
}
