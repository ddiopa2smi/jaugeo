package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Citerne;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CiterneDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Livraison;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LivraisonDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TourneeDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TransporteurDepot;
import sn.digitalperf.jaugeo.stock.LivraisonsAttendues;

/** Livraisons attendues par les stations (reception du gerant) et tolerance de manquant des transporteurs. */
@Component
@Transactional(readOnly = true)
class LivraisonsStations implements LivraisonsAttendues {

    private final LivraisonDepot livraisons;
    private final TourneeDepot tournees;
    private final FournisseurDepot fournisseurs;
    private final TransporteurDepot transporteurs;
    private final CiterneDepot citernes;
    private final SuiviLivraisons suivi;

    LivraisonsStations(LivraisonDepot livraisons, TourneeDepot tournees, FournisseurDepot fournisseurs,
                       TransporteurDepot transporteurs, CiterneDepot citernes, SuiviLivraisons suivi) {
        this.livraisons = livraisons;
        this.tournees = tournees;
        this.fournisseurs = fournisseurs;
        this.transporteurs = transporteurs;
        this.citernes = citernes;
        this.suivi = suivi;
    }

    @Override
    public List<LivraisonAttendue> attendues(long stationId) {
        List<Livraison> liste = livraisons.enCoursVers(stationId, Tournee.Statut.ANNULEE);
        if (liste.isEmpty()) {
            return List.of();
        }
        Set<Long> recues = suivi.receptions(liste.stream().map(Livraison::getId).toList()).keySet();
        return liste.stream().filter(l -> !recues.contains(l.getId())).map(this::vue).toList();
    }

    @Override
    public List<String> fournisseurs() {
        return fournisseurs.findAllByOrderByNomAsc().stream().filter(f -> f.isActif()).map(f -> f.getNom()).toList();
    }

    @Override
    public Optional<LivraisonAttendue> livraison(UUID uuid) {
        return livraisons.findByUuid(uuid).map(this::vue).filter(l -> !"ANNULEE".equals(l.tourneeStatut()));
    }

    @Override
    public Optional<LivraisonAttendue> livraison(long id) {
        return livraisons.findById(id).map(this::vue);
    }

    @Override
    public Optional<BigDecimal> tolerancePct(Long livraisonId, String transporteur) {
        Optional<Transporteur> t = livraisonId != null
                ? livraisons.findById(livraisonId).flatMap(l -> tournees.findById(l.getTourneeId()))
                        .flatMap(x -> transporteurs.findById(x.getTransporteurId()))
                : transporteur == null || transporteur.isBlank() ? Optional.empty()
                        : transporteurs.findByNomIgnoreCase(transporteur.trim());
        return t.map(Transporteur::getTolerancePct);
    }

    private LivraisonAttendue vue(Livraison l) {
        Tournee t = tournees.findById(l.getTourneeId()).orElseThrow();
        String fournisseur = fournisseurs.findById(t.getFournisseurId()).map(f -> f.getNom()).orElse(null);
        String transporteur = transporteurs.findById(t.getTransporteurId()).map(Transporteur::getNom).orElse(null);
        String immatriculation = t.getImmatriculation() != null ? t.getImmatriculation()
                : t.getCiterneId() == null ? null : citernes.findById(t.getCiterneId()).map(Citerne::getImmatriculation).orElse(null);
        return new LivraisonAttendue(l.getId(), l.getUuid(), t.getNumero(), t.getStatut().name(), l.getStationId(),
                l.getProduitId(), l.getQuantiteCommandee(), l.getNumeroBl(), l.getQuantiteBl(), l.getQuantiteBl15(),
                fournisseur, transporteur, t.getChauffeur(), immatriculation, t.getScelles(), t.getDateLivraisonPrevue());
    }
}
