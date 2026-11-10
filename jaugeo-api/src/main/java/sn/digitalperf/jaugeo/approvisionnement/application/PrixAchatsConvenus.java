package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.PrixAchats;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Fournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchat;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchatDepot;

@Component
@Transactional(readOnly = true)
class PrixAchatsConvenus implements PrixAchats {

    private final PrixAchatDepot prix;
    private final FournisseurDepot fournisseurs;

    PrixAchatsConvenus(PrixAchatDepot prix, FournisseurDepot fournisseurs) {
        this.prix = prix;
        this.fournisseurs = fournisseurs;
    }

    @Override
    public NavigableMap<LocalDate, BigDecimal> prixConvenus(long produitId) {
        List<PrixAchat> tous = prix.findByProduitIdOrderByDebutAscIdAsc(produitId);
        Long principal = fournisseurs.findByPrincipalTrue().map(Fournisseur::getId).orElse(null);
        List<PrixAchat> duPrincipal = tous.stream().filter(p -> p.getFournisseurId().equals(principal)).toList();
        NavigableMap<LocalDate, BigDecimal> r = new TreeMap<>();
        (duPrincipal.isEmpty() ? tous : duPrincipal).forEach(p -> r.put(p.getDebut(), p.getPrix()));
        return r;
    }
}
