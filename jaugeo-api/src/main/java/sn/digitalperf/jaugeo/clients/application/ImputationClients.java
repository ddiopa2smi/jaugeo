package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.clients.domaine.Bon;
import sn.digitalperf.jaugeo.clients.domaine.BonDepot;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.Facture;
import sn.digitalperf.jaugeo.clients.domaine.FactureDepot;
import sn.digitalperf.jaugeo.clients.domaine.Imputation;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Dette;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Nature;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompte;
import sn.digitalperf.jaugeo.clients.domaine.OperationCompteDepot;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Dettes d'un client sur tout le reseau (reprises, factures emises, bons non factures) et imputation de ses
 * reglements de la plus ancienne a la plus recente. L'echeance d'une reprise court a partir de sa date.
 */
@Component
class ImputationClients {

    private final BonDepot bons;
    private final FactureDepot factures;
    private final OperationCompteDepot operations;

    ImputationClients(BonDepot bons, FactureDepot factures, OperationCompteDepot operations) {
        this.bons = bons;
        this.factures = factures;
        this.operations = operations;
    }

    List<Reste> restes(Client c) {
        return restes(List.of(c)).getOrDefault(c.getId(), List.of());
    }

    /** Restes dus par client (cle : identifiant technique). */
    Map<Long, List<Reste>> restes(Collection<Client> clients) {
        if (clients.isEmpty()) {
            return Map.of();
        }
        Map<Long, Client> parId = clients.stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<Long, List<Dette>> dettes = new HashMap<>();
        Map<Long, BigDecimal> reglements = new HashMap<>();
        for (OperationCompte o : operations.findByClientIdIn(parId.keySet())) {
            if (o.getType() == OperationCompte.Type.REPRISE) {
                int delai = delai(parId.get(o.getClientId()));
                dettes.computeIfAbsent(o.getClientId(), k -> new ArrayList<>()).add(new Dette(Nature.REPRISE,
                        o.getReference() == null ? "Reprise du " + Libelles.date(o.getDateOperation()) : o.getReference(),
                        o.getDateOperation(), o.getDateOperation().plusDays(delai), o.getMontant(), null));
            } else {
                reglements.merge(o.getClientId(), o.getMontant(), BigDecimal::add);
            }
        }
        for (Facture f : factures.findByClientIdInAndStatut(parId.keySet(), Facture.Statut.EMISE)) {
            dettes.computeIfAbsent(f.getClientId(), k -> new ArrayList<>()).add(new Dette(Nature.FACTURE, f.getNumero(),
                    f.getDateFacture(), f.getDateEcheance(), f.getMontant(), f.getId()));
        }
        for (Bon b : bons.findByClientIdInAndFactureIdIsNull(parId.keySet())) {
            dettes.computeIfAbsent(b.getClientId(), k -> new ArrayList<>()).add(new Dette(Nature.NON_FACTURE, b.getNumero(),
                    b.getDateJour(), null, b.getMontant(), null));
        }
        Map<Long, List<Reste>> r = new HashMap<>();
        for (Long id : parId.keySet()) {
            r.put(id, Imputation.imputer(dettes.getOrDefault(id, List.of()), reglements.getOrDefault(id, BigDecimal.ZERO)));
        }
        return r;
    }

    private static int delai(Client c) {
        return c.getDelaiPaiement() == null ? 0 : c.getDelaiPaiement();
    }
}
