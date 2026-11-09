package sn.digitalperf.jaugeo.clients;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Interface publique des comptes clients pour la tresorerie et les syntheses mensuelles.
 * Identifiants techniques (journee, station) ; aucun filtrage par perimetre.
 */
public interface ComptesClients {

    /**
     * Bons d'une journee par mode de recouvrement et pleins payes par carte prepayee (valeur a la pompe, hors remise
     * des clients), recouvrements de creances encaisses en especes a la station.
     */
    record TotauxJournee(BigDecimal bonsDirection, BigDecimal bonsGerant, BigDecimal cartes, BigDecimal recouvrements) {

        public static final TotauxJournee AUCUN = new TotauxJournee(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Creance d'un client a une station sur une periode : debut (reprises comprises), bons de la periode,
     * reglements encaisses a la station (especes de la journee) ou par le siege, fin.
     */
    record CreanceClient(UUID clientId, String code, String nom, String recouvrement, BigDecimal debut, BigDecimal bons,
                         BigDecimal reglementsStation, BigDecimal reglementsSiege, BigDecimal fin) {
    }

    TotauxJournee totauxJournee(long journeeId);

    /**
     * Remises accordees sur les bons d'une station entre deux dates incluses (valeur a la pompe - montant du par les
     * clients) : elles sortent de la recette sans etre dues par les clients.
     */
    BigDecimal remises(long stationId, LocalDate du, LocalDate au);

    /**
     * Creances des clients d'une station entre deux dates incluses, par code client ; une reprise datee dans la
     * periode compte dans le debut. Seuls les clients ayant un mouvement ou un solde figurent.
     */
    List<CreanceClient> creances(long stationId, LocalDate du, LocalDate au);
}
