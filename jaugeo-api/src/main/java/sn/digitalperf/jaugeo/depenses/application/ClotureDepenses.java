package sn.digitalperf.jaugeo.depenses.application;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;

/**
 * Part des depenses dans la cloture : aucune depense en attente de decision, chaque depense retenue justifiee.
 * Rien a figer : une journee cloturee n'accepte plus de saisie, ses depenses restent telles quelles.
 */
@Component
@Order(20)
class ClotureDepenses implements ParticipantCloture {

    private final DepenseService service;

    ClotureDepenses(DepenseService service) {
        this.service = service;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        return service.anomalies(contexte.journee().id());
    }

    @Override
    public void figer(ContexteCloture contexte) {
        // Les depenses autorisees sont reprises par la tresorerie dans le montant a verser
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        // Renvoi : les depenses redeviennent modifiables avec la journee
    }
}
