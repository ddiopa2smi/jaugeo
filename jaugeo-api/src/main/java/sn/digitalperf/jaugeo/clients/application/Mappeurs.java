package sn.digitalperf.jaugeo.clients.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import sn.digitalperf.jaugeo.clients.application.VuesClients.CarteVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.ClientVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.EnginVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.RelanceVue;
import sn.digitalperf.jaugeo.clients.domaine.Carte;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.EnginClient;
import sn.digitalperf.jaugeo.clients.domaine.Facture;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.clients.domaine.Relance;

/** Passage des entites du module aux vues de l'API. */
final class Mappeurs {

    private Mappeurs() {
    }

    static ClientVue client(Client c, BigDecimal encours, BigDecimal echu) {
        BigDecimal disponible = c.getPlafond() == null ? null : c.getPlafond().subtract(encours).max(BigDecimal.ZERO);
        return new ClientVue(c.getUuid(), c.getCode(), c.getNom(), c.getTelephone(), c.getRecouvrement().name(), c.getPlafond(),
                c.isActif(), c.getObservation(), encours, disponible, echu, c.contrat(), c.getVersion());
    }

    static EnginVue engin(EnginClient e) {
        return new EnginVue(e.getUuid(), e.getType().name(), e.getImmatriculation(), e.getLibelle(), e.isActif(), e.getVersion());
    }

    /** Reste du d'une facture d'apres l'imputation des reglements du client. */
    static BigDecimal reste(Facture f, List<Reste> restes) {
        if (f.getStatut() == Facture.Statut.ANNULEE) {
            return BigDecimal.ZERO;
        }
        return restes.stream().filter(r -> f.getId().equals(r.dette().factureId())).map(Reste::reste).findFirst()
                .orElse(BigDecimal.ZERO);
    }

    static FactureVue facture(Facture f, Client c, List<Reste> restes, int bons) {
        return new FactureVue(f.getUuid(), f.getNumero(), c.getUuid(), c.getCode(), c.getNom(), f.getPeriodeDebut(),
                f.getPeriodeFin(), f.getDateFacture(), f.getDateEcheance(), f.getMontant(), reste(f, restes), f.getStatut().name(),
                f.getMotifAnnulation(), bons, f.getCreePar(), f.getVersion());
    }

    static RelanceVue relance(Relance r) {
        return new RelanceVue(r.getUuid(), r.getDateRelance(), r.getNiveau(), r.getMode().name(), r.getMontantEchu(),
                r.getCommentaire(), r.getCreePar());
    }

    static CarteVue carte(Carte k, Map<Long, Client> clients) {
        Client c = clients.get(k.getClientId());
        return new CarteVue(k.getUuid(), k.getNumero(), c.getUuid(), c.getCode(), c.getNom(), k.getPorteur(), k.getImmatriculation(),
                k.getStatut().name(), k.getSolde(), k.getVersion());
    }
}
