package sn.digitalperf.jaugeo.referentiel;

import java.time.Instant;

/**
 * Point d'extension : un module (l'exploitation) peut refuser un changement de compteur date dans une
 * periode deja cloturee. Le referentiel l'appelle avant d'enregistrer l'evenement, sans dependre de ce module.
 */
public interface GardeCompteur {

    /** Leve une ErreurMetier si un changement de compteur ne peut pas etre date a cet instant dans cette station. */
    void verifierChangementCompteur(long stationId, Instant date);
}
