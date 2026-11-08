package sn.digitalperf.jaugeo.stock.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;
import sn.digitalperf.jaugeo.stock.application.EvaluationStock.CuveEvaluee;
import sn.digitalperf.jaugeo.stock.domaine.CalculStock;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterne;
import sn.digitalperf.jaugeo.stock.domaine.ComplementCiterneDepot;
import sn.digitalperf.jaugeo.stock.domaine.EcartStock;
import sn.digitalperf.jaugeo.stock.domaine.EcartStockDepot;
import sn.digitalperf.jaugeo.stock.domaine.Reception;
import sn.digitalperf.jaugeo.stock.domaine.ReceptionDepot;
import sn.digitalperf.jaugeo.tarification.Tarification;

/**
 * Part du stock dans la cloture d'une journee (regle 1) : jaugeage obligatoire de chaque cuve, stock theorique
 * jamais negatif, observation obligatoire hors tolerance. A la cloture, les ecarts et les complements citerne
 * sont figes ; au renvoi ils sont retires, sauf si un complement a deja ete regle.
 */
@Component
@Order(10)
class ClotureStock implements ParticipantCloture {

    private final EvaluationStock evaluation;
    private final EcartStockDepot ecarts;
    private final ReceptionDepot receptions;
    private final ComplementCiterneDepot complements;
    private final Tarification tarification;
    private final BigDecimal toleranceTransportPct;

    ClotureStock(EvaluationStock evaluation, EcartStockDepot ecarts, ReceptionDepot receptions,
                 ComplementCiterneDepot complements, Tarification tarification,
                 @Value("${jaugeo.stock.tolerance-transport-pct:0.3}") BigDecimal toleranceTransportPct) {
        this.evaluation = evaluation;
        this.ecarts = ecarts;
        this.receptions = receptions;
        this.complements = complements;
        this.tarification = tarification;
        this.toleranceTransportPct = toleranceTransportPct;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        List<String> problemes = new ArrayList<>();
        for (CuveEvaluee c : evaluation.evaluer(contexte.journee(), contexte.sortiesParCuve())) {
            c.anomalies().forEach(a -> problemes.add(libelle(c) + " : " + a.message()));
        }
        return problemes;
    }

    @Override
    public void figer(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        for (CuveEvaluee c : evaluation.evaluer(j, contexte.sortiesParCuve())) {
            CalculStock.Resultat r = c.resultat();
            if (r.ecart() == null) {
                // Controles passes : impossible, sauf cuve sans aucune donnee ; rien a figer
                continue;
            }
            ecarts.save(new EcartStock(new EcartStock.Cle(j.id(), j.stationId(), c.cuve().id(), c.cuve().produitId(), j.date()),
                    c.depart(), c.receptions(), c.sorties(), c.cloture().getVolumeLitres(), c.prixUnitaire(),
                    c.cuve().toleranceEcartPct(), r, c.cloture().getObservation()));
        }
        for (Reception r : receptions.findByJourneeIdOrderByIdAsc(j.id())) {
            BigDecimal manquant = CalculStock.complement(r.getQuantiteBl(), r.getQuantiteRecue(), toleranceTransportPct);
            if (manquant.signum() > 0) {
                BigDecimal prix = tarification.prixEnVigueur(j.stationId(), r.getProduitId(), j.fin().minusSeconds(1)).orElse(null);
                complements.save(new ComplementCiterne(r, manquant, prix));
            }
        }
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        boolean regle = complements.findByJourneeId(journee.id()).stream()
                .anyMatch(c -> c.getStatut() == ComplementCiterne.Statut.REGLE);
        if (regle) {
            throw ErreurMetier.regle("COMPLEMENT_REGLE", "Un complément citerne de cette journée a déjà été réglé par le "
                    + "transporteur : la journée ne peut plus être renvoyée.");
        }
        complements.supprimerPourJournee(journee.id());
        ecarts.supprimerPourJournee(journee.id());
    }

    BigDecimal toleranceTransportPct() {
        return toleranceTransportPct;
    }

    static String libelle(CuveEvaluee c) {
        return "Cuve " + c.cuve().code() + " (" + c.cuve().produitLibelle() + ")";
    }
}
