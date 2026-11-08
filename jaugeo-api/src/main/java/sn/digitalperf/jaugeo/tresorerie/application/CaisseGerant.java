package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.depenses.DepensesJournee;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.stock.StocksJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisse;
import sn.digitalperf.jaugeo.tresorerie.domaine.ArreteCaisseDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.RecetteJourneeDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.VersementDepot;

/**
 * Solde de caisse du gerant (especes encaissees non encore versees) : il part du dernier arrete de caisse,
 * augmente des montants a verser des journees cloturees et diminue des versements non rejetes.
 */
@Component
class CaisseGerant {

    /** Origine du suivi quand aucun arrete n'existe : le solde part de zero. */
    static final LocalDate ORIGINE = LocalDate.of(1900, 1, 1);

    /** Point de depart du calcul : date (exclue des mouvements) et solde a cette date. */
    record Base(LocalDate date, BigDecimal solde, ArreteCaisse arrete) {
    }

    private final RecetteJourneeDepot recettes;
    private final VersementDepot versements;
    private final ArreteCaisseDepot arretes;
    private final JourneesExploitation exploitation;
    private final DepensesJournee depenses;
    private final StocksJournee stocks;

    CaisseGerant(RecetteJourneeDepot recettes, VersementDepot versements, ArreteCaisseDepot arretes,
                 JourneesExploitation exploitation, DepensesJournee depenses, StocksJournee stocks) {
        this.recettes = recettes;
        this.versements = versements;
        this.arretes = arretes;
        this.exploitation = exploitation;
        this.depenses = depenses;
        this.stocks = stocks;
    }

    /** Dernier arrete a cette date ou avant ; a defaut, l'origine a zero. */
    Base base(long stationId, LocalDate auPlusTard) {
        return arretes.findFirstByStationIdAndDateJourLessThanEqualOrderByDateJourDesc(stationId, auPlusTard)
                .map(a -> new Base(a.getDateJour(), a.getEspecesComptees(), a))
                .orElse(new Base(ORIGINE, BigDecimal.ZERO, null));
    }

    /** Solde en fin de journee (apres l'arrete du jour s'il y en a un). */
    BigDecimal soldeFin(long stationId, LocalDate date) {
        return solde(base(stationId, date), stationId, date);
    }

    /** Solde theorique en fin de journee, sans tenir compte d'un arrete ce jour-la. */
    BigDecimal soldeTheorique(long stationId, LocalDate date) {
        return solde(base(stationId, date.minusDays(1)), stationId, date);
    }

    BigDecimal solde(Base b, long stationId, LocalDate date) {
        if (date.isBefore(b.date()) || date.equals(b.date())) {
            return b.solde();
        }
        return b.solde().add(recettes.cumul(stationId, b.date(), date)).subtract(versements.cumul(stationId, b.date(), date));
    }

    /**
     * Composantes du montant a verser d'une journee : figees a la cloture, sinon apercu.
     * Ventes inconnues (releve incomplet) : vide.
     */
    Optional<RecetteJournee.Composantes> composantes(JourneeRef j) {
        Optional<RecetteJournee> figee = recettes.findByJourneeId(j.id());
        if (figee.isPresent()) {
            return Optional.of(figee.get().composantes());
        }
        return exploitation.montantVentes(j.id()).map(ventes -> new RecetteJournee.Composantes(ventes, BigDecimal.ZERO,
                BigDecimal.ZERO, depenses.totalAutorise(j.id()), stocks.montantComplements(j.id())));
    }
}
