package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.CiterneVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FournisseurVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.LivraisonNotee;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.Notation;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.PenaliteVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.PrixAchatVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TransporteurDetail;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.TransporteurVue;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CalculAppro;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Citerne;
import sn.digitalperf.jaugeo.approvisionnement.domaine.CiterneDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Fournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Livraison;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LivraisonDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Penalite;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PenaliteDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchat;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchatDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TourneeDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Transporteur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.TransporteurDepot;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.ReceptionRef;

/** Fournisseurs (depots, prix d'achat convenus), transporteurs (contrat, citernes, notation) et penalites. */
@Service
@Transactional(readOnly = true)
public class PartenairesService {

    private final FournisseurDepot fournisseurs;
    private final PrixAchatDepot prix;
    private final TransporteurDepot transporteurs;
    private final CiterneDepot citernes;
    private final TourneeDepot tournees;
    private final LivraisonDepot livraisons;
    private final PenaliteDepot penalites;
    private final SuiviLivraisons suivi;
    private final JourneesExploitation exploitation;
    private final PerimetreCourant perimetre;
    private final Clock horloge;
    private final int joursNotation;

    PartenairesService(FournisseurDepot fournisseurs, PrixAchatDepot prix, TransporteurDepot transporteurs,
                       CiterneDepot citernes, TourneeDepot tournees, LivraisonDepot livraisons, PenaliteDepot penalites,
                       SuiviLivraisons suivi, JourneesExploitation exploitation, PerimetreCourant perimetre, Clock horloge,
                       @Value("${jaugeo.appro.notation-jours:180}") int joursNotation) {
        this.fournisseurs = fournisseurs;
        this.prix = prix;
        this.transporteurs = transporteurs;
        this.citernes = citernes;
        this.tournees = tournees;
        this.livraisons = livraisons;
        this.penalites = penalites;
        this.suivi = suivi;
        this.exploitation = exploitation;
        this.perimetre = perimetre;
        this.horloge = horloge;
        this.joursNotation = joursNotation;
    }

    // ---------------------------------------------------------------- Fournisseurs

    public List<FournisseurVue> fournisseurs() {
        Map<Long, ProduitRef> produits = suivi.produits();
        return fournisseurs.findAllByOrderByNomAsc().stream().map(f -> vue(f, produits)).toList();
    }

    @Transactional
    public FournisseurVue enregistrerFournisseur(UUID id, Integer version, String nom, String telephone, String adresse,
                                                 Integer delai, Boolean principal, Boolean factureA15, Boolean actif) {
        Fournisseur f = id == null ? new Fournisseur(nom) : fournisseur(id);
        f.verifierVersion(version);
        fournisseurs.findByNomIgnoreCase(nom == null ? "" : nom.trim()).filter(x -> !x.getId().equals(f.getId())).ifPresent(x -> {
            throw ErreurMetier.conflit("FOURNISSEUR_EXISTANT", "Un fournisseur porte déjà le nom " + x.getNom() + ".");
        });
        f.renseigner(nom, telephone, adresse, delai, principal, factureA15, actif);
        if (f.isPrincipal()) {
            // Un seul depot principal : l'ancien cesse de l'etre
            fournisseurs.findByPrincipalTrue().filter(x -> !x.getId().equals(f.getId())).ifPresent(x -> {
                x.retirerPrincipal();
                fournisseurs.saveAndFlush(x);
            });
        }
        return vue(fournisseurs.saveAndFlush(f), suivi.produits());
    }

    @Transactional
    public FournisseurVue ajouterPrix(UUID fournisseurId, UUID produitId, LocalDate debut, BigDecimal montant) {
        Fournisseur f = fournisseur(fournisseurId);
        ProduitRef p = suivi.produit(produitId);
        if (prix.existsByFournisseurIdAndProduitIdAndDebut(f.getId(), p.id(), debut)) {
            throw ErreurMetier.conflit("PRIX_EXISTANT", "Un prix du " + p.libelle() + " existe déjà à cette date.");
        }
        prix.saveAndFlush(new PrixAchat(f.getId(), p.id(), debut, montant));
        return vue(f, suivi.produits());
    }

    @Transactional
    public FournisseurVue supprimerPrix(UUID prixId) {
        PrixAchat x = prix.findByUuid(prixId).orElseThrow(() -> ErreurMetier.introuvable("Prix d'achat"));
        Fournisseur f = fournisseurs.findById(x.getFournisseurId()).orElseThrow();
        prix.delete(x);
        prix.flush();
        return vue(f, suivi.produits());
    }

    Fournisseur fournisseur(UUID id) {
        return fournisseurs.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Fournisseur"));
    }

    private FournisseurVue vue(Fournisseur f, Map<Long, ProduitRef> produits) {
        LocalDate jour = LocalDate.now(horloge);
        Set<Long> vus = new HashSet<>();
        List<PrixAchatVue> lignes = new ArrayList<>();
        // Tries par produit puis date decroissante : le premier prix non futur de chaque produit est en vigueur
        for (PrixAchat x : prix.findByFournisseurIdOrderByProduitIdAscDebutDesc(f.getId())) {
            ProduitRef p = produits.get(x.getProduitId());
            boolean enVigueur = !x.getDebut().isAfter(jour) && vus.add(x.getProduitId());
            lignes.add(new PrixAchatVue(x.getUuid(), p.uuid(), p.code(), p.libelle(), x.getDebut(), x.getPrix(), enVigueur));
        }
        return new FournisseurVue(f.getUuid(), f.getNom(), f.getTelephone(), f.getAdresse(), f.getDelaiLivraison(),
                f.isPrincipal(), f.isFactureA15(), f.isActif(), f.getVersion(), lignes);
    }

    // ---------------------------------------------------------------- Transporteurs

    public List<TransporteurVue> transporteurs() {
        List<Penalite> toutes = penalites.findAllByOrderByDateJourDescIdDesc();
        return transporteurs.findAllByOrderByNomAsc().stream().map(t -> vue(t, notation(t).note(), toutes)).toList();
    }

    public TransporteurDetail transporteur(UUID id) {
        Transporteur t = transporteurParUuid(id);
        Notes n = notation(t);
        List<Penalite> siennes = penalites.findByTransporteurIdOrderByDateJourDesc(t.getId());
        return new TransporteurDetail(vue(t, n.note(), siennes), vues(siennes), n.livraisons());
    }

    @Transactional
    public TransporteurDetail enregistrerTransporteur(UUID id, Integer version, Transporteur.Donnees d) {
        Transporteur t = id == null ? new Transporteur(d.nom()) : transporteurParUuid(id);
        t.verifierVersion(version);
        transporteurs.findByNomIgnoreCase(d.nom() == null ? "" : d.nom().trim()).filter(x -> !x.getId().equals(t.getId()))
                .ifPresent(x -> {
                    throw ErreurMetier.conflit("TRANSPORTEUR_EXISTANT", "Un transporteur porte déjà le nom " + x.getNom() + ".");
                });
        t.renseigner(d);
        Transporteur enregistre = transporteurs.saveAndFlush(t);
        return transporteur(enregistre.getUuid());
    }

    @Transactional
    public TransporteurDetail enregistrerCiterne(UUID transporteurId, UUID citerneId, Integer version, String immatriculation,
                                                 BigDecimal capacite, Boolean actif) {
        Transporteur t = transporteurParUuid(transporteurId);
        Citerne c = citerneId == null ? new Citerne(t.getId())
                : citernes.findByUuid(citerneId).filter(x -> x.getTransporteurId().equals(t.getId()))
                        .orElseThrow(() -> ErreurMetier.introuvable("Citerne"));
        c.verifierVersion(version);
        citernes.findByImmatriculationIgnoreCase(immatriculation == null ? "" : immatriculation.trim())
                .filter(x -> !x.getId().equals(c.getId())).ifPresent(x -> {
                    throw ErreurMetier.conflit("CITERNE_EXISTANTE", "La citerne " + x.getImmatriculation() + " est déjà enregistrée.");
                });
        c.renseigner(immatriculation, capacite, actif);
        citernes.saveAndFlush(c);
        return transporteur(t.getUuid());
    }

    Transporteur transporteurParUuid(UUID id) {
        return transporteurs.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Transporteur"));
    }

    private TransporteurVue vue(Transporteur t, Notation n, List<Penalite> liste) {
        BigDecimal aRegler = liste.stream().filter(p -> p.getTransporteurId().equals(t.getId()) && p.getStatut() == Penalite.Statut.A_REGLER)
                .map(Penalite::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CiterneVue> siennes = citernes.findByTransporteurIdOrderByImmatriculationAsc(t.getId()).stream()
                .map(c -> new CiterneVue(c.getUuid(), c.getImmatriculation(), c.getCapaciteLitres(), c.isActif(), c.getVersion()))
                .toList();
        return new TransporteurVue(t.getUuid(), t.getNom(), t.getTelephone(), t.getContratReference(), t.getContratDebut(),
                t.getContratFin(), t.getTolerancePct(), suivi.tolerance(t), t.getPenalite(), t.isActif(), t.getObservation(),
                t.getVersion(), siennes, n, aRegler);
    }

    private record Notes(Notation note, List<LivraisonNotee> livraisons) {
    }

    /** Livraisons du transporteur receptionnees sur la periode glissante, et leur note. */
    private Notes notation(Transporteur t) {
        LocalDate jour = LocalDate.now(horloge);
        LocalDate depuis = jour.minusDays(joursNotation);
        Map<Long, Tournee> siennes = tournees.findByTransporteurId(t.getId()).stream()
                .filter(x -> x.getStatut() != Tournee.Statut.ANNULEE)
                .collect(Collectors.toMap(Tournee::getId, Function.identity()));
        List<Livraison> liste = siennes.isEmpty() ? List.of() : livraisons.findByTourneeIdIn(siennes.keySet());
        Map<Long, ReceptionRef> recues = liste.isEmpty() ? Map.of() : suivi.receptions(liste.stream().map(Livraison::getId).toList());
        BigDecimal tolerance = suivi.tolerance(t);
        Map<Long, StationRef> stations = suivi.stations();
        Map<Long, ProduitRef> produits = suivi.produits();
        List<CalculAppro.Livree> livrees = new ArrayList<>();
        List<LivraisonNotee> notees = new ArrayList<>();
        for (Livraison l : liste) {
            ReceptionRef r = recues.get(l.getId());
            if (r == null || r.date().isBefore(depuis)) {
                continue;
            }
            Tournee x = siennes.get(l.getTourneeId());
            boolean aLHeure = !r.date().isAfter(x.getDateLivraisonPrevue());
            livrees.add(new CalculAppro.Livree(r.quantiteLivree(), r.manquant(), aLHeure, tolerance));
            notees.add(new LivraisonNotee(x.getNumero(), stations.get(l.getStationId()).code(), produits.get(l.getProduitId()).code(),
                    x.getDateLivraisonPrevue(), r.date(), r.quantiteLivree(), r.manquant(),
                    CalculAppro.pourcentage(r.manquant(), r.quantiteLivree()), tolerance, aLHeure,
                    CalculAppro.horsTolerance(r.quantiteLivree(), r.manquant(), tolerance)));
        }
        notees.sort((a, b) -> b.recue().compareTo(a.recue()));
        CalculAppro.Note n = CalculAppro.noter(livrees);
        return new Notes(new Notation(n.note(), n.livraisons(), n.ponctualitePct(), n.conformitePct(), n.tauxManquantPct(),
                n.horsTolerance(), joursNotation), notees);
    }

    // ---------------------------------------------------------------- Penalites

    public List<PenaliteVue> penalites(String statut) {
        return vues(penalites.findAllByOrderByDateJourDescIdDesc().stream()
                .filter(p -> statut == null || statut.isBlank() || p.getStatut().name().equalsIgnoreCase(statut)).toList());
    }

    @Transactional
    public PenaliteVue reglerPenalite(UUID id, Integer version, String reference, LocalDate le) {
        Penalite p = penalites.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Pénalité"));
        p.verifierVersion(version);
        p.regler(reference, le, perimetre.identifiant());
        return vues(List.of(penalites.saveAndFlush(p))).get(0);
    }

    private List<PenaliteVue> vues(List<Penalite> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, Transporteur> parId = transporteurs.findAllById(liste.stream().map(Penalite::getTransporteurId).toList())
                .stream().collect(Collectors.toMap(Transporteur::getId, Function.identity()));
        Map<Long, Livraison> parLivraison = livraisons.findAllById(liste.stream().map(Penalite::getLivraisonId).toList())
                .stream().collect(Collectors.toMap(Livraison::getId, Function.identity()));
        Map<Long, StationRef> stations = suivi.stations();
        return liste.stream().map(p -> {
            Transporteur t = parId.get(p.getTransporteurId());
            Livraison l = parLivraison.get(p.getLivraisonId());
            String tournee = tournees.findById(l.getTourneeId()).map(Tournee::getNumero).orElse(null);
            UUID journee = exploitation.journee(p.getJourneeId()).map(JourneeRef::uuid).orElse(null);
            String bl = suivi.receptions(List.of(l.getId())).values().stream().map(ReceptionRef::numeroBl).findFirst()
                    .orElse(l.getNumeroBl());
            return new PenaliteVue(p.getUuid(), t.getUuid(), t.getNom(), tournee, stations.get(l.getStationId()).code(),
                    p.getDateJour(), journee, bl, p.getManquantLitres(), p.getManquantPct(), p.getTolerancePct(),
                    p.getMontant(), p.getStatut().name(), p.getReferenceReglement(), p.getRegleLe(), p.getReglePar(),
                    p.getVersion());
        }).toList();
    }
}
