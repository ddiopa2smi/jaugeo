package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.depenses.DepensesJournee;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;
import sn.digitalperf.jaugeo.stock.StocksJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisseDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJourneeDepot;

/**
 * Part de la tresorerie dans la cloture, apres le stock (complements citerne figes) et les depenses :
 * le montant a verser de la journee est fige. Un renvoi le retire, sauf si un arrete de caisse
 * a deja ete dresse a cette date ou apres (il repose sur ce montant).
 */
@Component
@Order(30)
class ClotureTresorerie implements ParticipantCloture {

    private final JourneesExploitation exploitation;
    private final DepensesJournee depenses;
    private final StocksJournee stocks;
    private final RecetteJourneeDepot recettes;
    private final ArreteCaisseDepot arretes;

    ClotureTresorerie(JourneesExploitation exploitation, DepensesJournee depenses, StocksJournee stocks,
                      RecetteJourneeDepot recettes, ArreteCaisseDepot arretes) {
        this.exploitation = exploitation;
        this.depenses = depenses;
        this.stocks = stocks;
        this.recettes = recettes;
        this.arretes = arretes;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        // Ventes incompletes et depenses en attente : deja signalees par l'exploitation et les depenses
        return List.of();
    }

    @Override
    public void figer(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        BigDecimal ventes = exploitation.montantVentes(j.id())
                .orElseThrow(() -> new IllegalStateException("Ventes inconnues a la cloture de la journee " + j.id()));
        RecetteJournee.Composantes c = new RecetteJournee.Composantes(ventes, BigDecimal.ZERO, BigDecimal.ZERO,
                depenses.totalAutorise(j.id()), stocks.montantComplements(j.id()));
        recettes.supprimerPourJournee(j.id());
        recettes.save(new RecetteJournee(j.id(), j.stationId(), j.date(), c));
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        arretes.findFirstByStationIdOrderByDateJourDesc(journee.stationId())
                .filter(a -> !a.getDateJour().isBefore(journee.date()))
                .ifPresent(a -> {
                    throw ErreurMetier.regle("ARRETE_CAISSE_POSTERIEUR", "Un arrêté de caisse a été dressé le "
                            + Libelles.date(a.getDateJour()) + " sur la base de cette journée : elle ne peut plus être renvoyée.");
                });
        recettes.supprimerPourJournee(journee.id());
    }
}
