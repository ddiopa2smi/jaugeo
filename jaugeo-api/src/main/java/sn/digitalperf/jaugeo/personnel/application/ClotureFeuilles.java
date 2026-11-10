package sn.digitalperf.jaugeo.personnel.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.exploitation.ParticipantCloture;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisse;
import sn.digitalperf.jaugeo.personnel.domaine.FeuilleCaisseDepot;

/**
 * Feuilles de caisse a la cloture : dans une station qui en tient, la journee ne se cloture que si les postes
 * totalisent les litres du releve ; le montant du et l'ecart de chaque pompiste sont alors figes. Hors recette :
 * le montant a verser du gerant ne change pas.
 */
@Component
@Order(40)
class ClotureFeuilles implements ParticipantCloture {

    private final FeuilleService service;
    private final FeuilleCaisseDepot feuilles;

    ClotureFeuilles(FeuilleService service, FeuilleCaisseDepot feuilles) {
        this.service = service;
        this.feuilles = feuilles;
    }

    @Override
    public List<String> controler(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        return service.actives(j.stationId()) ? service.etat(j).problemes() : List.of();
    }

    @Override
    public void figer(ContexteCloture contexte) {
        JourneeRef j = contexte.journee();
        if (feuilles.findByJourneeIdOrderByIdAsc(j.id()).isEmpty()) {
            return;
        }
        FeuilleService.Etat e = service.etat(j);
        for (FeuilleCaisse f : e.feuilles()) {
            f.figer(e.duParFeuille().getOrDefault(f.getId(), BigDecimal.ZERO));
        }
        feuilles.saveAllAndFlush(e.feuilles());
    }

    @Override
    public void rouvrir(JourneeRef journee) {
        List<FeuilleCaisse> liste = feuilles.findByJourneeIdOrderByIdAsc(journee.id());
        liste.forEach(FeuilleCaisse::defiger);
        feuilles.saveAllAndFlush(liste);
    }
}
