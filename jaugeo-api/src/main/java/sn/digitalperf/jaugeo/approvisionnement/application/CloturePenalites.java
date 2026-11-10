package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CalculAppro;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Livraison;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LivraisonDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Penalite;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PenaliteDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TourneeDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TransporteurDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.ReceptionRef;

/**
 * Penalites de transport figees a la cloture, juste apres le stock (qui fige les complements citerne) : une
 * livraison de tournee dont le manquant depasse la tolerance et revient au transporteur coute la penalite
 * forfaitaire de son contrat. Retirees au renvoi, sauf si l'une est deja reglee.
 */
@Component
@Order(11)
class CloturePenalites implements ParticipantCloture {

    private final SuiviLivraisons suivi;
    private final LivraisonDepot livraisons;
    private final TourneeDepot tournees;
    private final TransporteurDepot transporteurs;
    private final PenaliteDepot penalites;

    CloturePenalites(SuiviLivraisons suivi, LivraisonDepot livraisons, TourneeDepot tournees, TransporteurDepot transporteurs,
                     PenaliteDepot penalites) {
        this.suivi = suivi;
        this.livraisons = livraisons;
        this.tournees = tournees;
        this.transporteurs = transporteurs;
        this.penalites = penalites;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        return List.of();
    }

    @Override
    public void figer(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        for (ReceptionRef r : suivi.stocks().receptionsDeLaJournee(j.id())) {
            if (r.livraisonId() == null || r.complementLitres() == null || !"TRANSPORTEUR".equals(r.imputation())) {
                continue;
            }
            Livraison l = livraisons.findById(r.livraisonId()).orElseThrow();
            Tournee t = tournees.findById(l.getTourneeId()).orElseThrow();
            Transporteur tr = transporteurs.findById(t.getTransporteurId()).orElseThrow();
            if (tr.getPenalite() == null) {
                continue;
            }
            BigDecimal tolerance = suivi.tolerance(tr);
            penalites.save(new Penalite(tr.getId(), l.getId(), j.id(), j.date(), r.manquant(),
                    CalculAppro.pourcentage(r.manquant(), r.quantiteLivree()), tolerance, tr.getPenalite()));
        }
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        if (penalites.findByJourneeId(journee.id()).stream().anyMatch(p -> p.getStatut() == Penalite.Statut.REGLE)) {
            throw ErreurMetier.regle("PENALITE_REGLEE", "Une pénalité de transport de cette journée a déjà été réglée "
                    + ": la journée ne peut plus être renvoyée.");
        }
        penalites.supprimerPourJournee(journee.id());
    }
}
