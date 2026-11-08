package sn.digitalperf.jaugeo.clients.application;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;

/**
 * Part des clients dans la cloture : les litres servis sur bons ne depassent pas les sorties des pompes.
 * Rien a figer : bons et recouvrements restent tels quels, la tresorerie reprend leurs totaux dans le montant a verser.
 */
@Component
@Order(15)
class ClotureClients implements ParticipantCloture {

    private final CreditJourneeService service;

    ClotureClients(CreditJourneeService service) {
        this.service = service;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        return service.anomalies(contexte.journee(), contexte.sortiesParCuve());
    }

    @Override
    public void figer(ContexteCloture contexte) {
        // Les bons sont valorises a la saisie
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        // Renvoi : bons et recouvrements redeviennent modifiables avec la journee
    }
}
