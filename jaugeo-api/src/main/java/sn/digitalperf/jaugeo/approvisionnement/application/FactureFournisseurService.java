package sn.digitalperf.jaugeo.approvisionnement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FactureFournisseurDetail;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.FactureFournisseurVue;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.LigneControlee;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieFacture;
import sn.digitalperf.jaugeo.approvisionnement.application.VuesAppro.SaisieLigneFacture;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FactureFournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FactureFournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Fournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.FournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LigneFactureFournisseur;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LigneFactureFournisseurDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.LivraisonDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchat;
import sn.digitalperf.jaugeo.approvisionnement.domaine.PrixAchatDepot;
import sn.digitalperf.jaugeo.approvisionnement.domaine.Tournee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.ProduitRef;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.stock.StocksApprovisionnement.ReceptionRef;

/**
 * Factures fournisseur rapprochees ligne a ligne (une ligne par BL) : quantite facturee contre le BL, manquant
 * impute au fournisseur a deduire, prix contre le prix d'achat convenu. Decision : bon a payer ou litige.
 */
@Service
@Transactional(readOnly = true)
public class FactureFournisseurService {

    private final FactureFournisseurDepot factures;
    private final LigneFactureFournisseurDepot lignes;
    private final FournisseurDepot fournisseurs;
    private final PrixAchatDepot prix;
    private final LivraisonDepot livraisons;
    private final SuiviLivraisons suivi;
    private final PerimetreCourant perimetre;
    private final Clock horloge;
    private final BigDecimal toleranceFacturePct;

    FactureFournisseurService(FactureFournisseurDepot factures, LigneFactureFournisseurDepot lignes,
                              FournisseurDepot fournisseurs, PrixAchatDepot prix, LivraisonDepot livraisons,
                              SuiviLivraisons suivi, PerimetreCourant perimetre, Clock horloge,
                              @Value("${jaugeo.appro.tolerance-facture-pct:0.1}") BigDecimal toleranceFacturePct) {
        this.factures = factures;
        this.lignes = lignes;
        this.fournisseurs = fournisseurs;
        this.prix = prix;
        this.livraisons = livraisons;
        this.suivi = suivi;
        this.perimetre = perimetre;
        this.horloge = horloge;
        this.toleranceFacturePct = toleranceFacturePct;
    }

    public List<FactureFournisseurVue> liste() {
        List<FactureFournisseur> liste = factures.findAllByOrderByDateFactureDescIdDesc();
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LigneFactureFournisseur>> parFacture = lignes.findByFactureIdIn(liste.stream().map(FactureFournisseur::getId).toList())
                .stream().collect(Collectors.groupingBy(LigneFactureFournisseur::getFactureId));
        Contexte ctx = contexte();
        return liste.stream().map(f -> controler(f, parFacture.getOrDefault(f.getId(), List.of()), ctx).facture()).toList();
    }

    public FactureFournisseurDetail detail(UUID id) {
        FactureFournisseur f = facture(id);
        return controler(f, lignes.findByFactureIdOrderByOrdreAsc(f.getId()), contexte());
    }

    @Transactional
    public FactureFournisseurDetail creer(SaisieFacture s) {
        if (s.fournisseurId() == null) {
            throw ErreurMetier.regle("FOURNISSEUR_OBLIGATOIRE", "Choisissez le fournisseur.");
        }
        Fournisseur fo = fournisseurs.findByUuid(s.fournisseurId()).orElseThrow(() -> ErreurMetier.introuvable("Fournisseur"));
        FactureFournisseur f = new FactureFournisseur(fo.getId());
        return enregistrer(f, fo, s);
    }

    @Transactional
    public FactureFournisseurDetail modifier(UUID id, SaisieFacture s) {
        FactureFournisseur f = facture(id);
        f.verifierVersion(s.version());
        f.exigerModifiable();
        return enregistrer(f, fournisseurs.findById(f.getFournisseurId()).orElseThrow(), s);
    }

    private FactureFournisseurDetail enregistrer(FactureFournisseur f, Fournisseur fo, SaisieFacture s) {
        f.renseigner(s.numero(), s.dateFacture(), s.observation());
        if (s.dateFacture() != null && s.dateFacture().isAfter(java.time.LocalDate.now(horloge))) {
            throw ErreurMetier.regle("DATE_FUTURE", "La facture ne peut pas être datée dans le futur.");
        }
        factures.findByFournisseurIdAndNumeroIgnoreCase(fo.getId(), f.getNumero()).filter(x -> !x.getId().equals(f.getId()))
                .ifPresent(x -> {
                    throw ErreurMetier.conflit("FACTURE_EXISTANTE", "La facture n° " + x.getNumero() + " de " + fo.getNom()
                            + " est déjà enregistrée.");
                });
        if (s.lignes() == null || s.lignes().isEmpty()) {
            throw ErreurMetier.regle("LIGNES_OBLIGATOIRES", "Saisissez au moins une ligne (un BL facturé).");
        }
        FactureFournisseur enregistree = factures.saveAndFlush(f);
        lignes.supprimerPourFacture(enregistree.getId());
        Set<String> vus = new HashSet<>();
        int ordre = 0;
        List<LigneFactureFournisseur> nouvelles = new ArrayList<>();
        for (SaisieLigneFacture l : s.lignes()) {
            if (l.produitId() == null) {
                throw ErreurMetier.regle("PRODUIT_OBLIGATOIRE", "Chaque ligne précise le produit facturé.");
            }
            ProduitRef p = suivi.produit(l.produitId());
            String bl = l.numeroBl() == null ? "" : l.numeroBl().trim().toUpperCase();
            if (!bl.isEmpty() && !vus.add(bl)) {
                throw ErreurMetier.regle("BL_EN_DOUBLE", "Le BL n° " + bl + " figure deux fois sur la facture.");
            }
            lignes.dejaFacture(fo.getId(), enregistree.getId(), bl, FactureFournisseur.Statut.ANNULEE).stream().findFirst()
                    .ifPresent(x -> {
                        String autre = factures.findById(x.getFactureId()).map(FactureFournisseur::getNumero).orElse("?");
                        throw ErreurMetier.conflit("BL_DEJA_FACTURE", "Le BL n° " + bl + " est déjà facturé (facture " + autre + ").");
                    });
            nouvelles.add(new LigneFactureFournisseur(enregistree.getId(), ++ordre, l.numeroBl(), p.id(), l.quantite(), l.prixUnitaire()));
        }
        lignes.saveAllAndFlush(nouvelles);
        return controler(enregistree, nouvelles, contexte());
    }

    @Transactional
    public FactureFournisseurDetail decider(UUID id, Integer version, boolean bonAPayer, String motif) {
        FactureFournisseur f = facture(id);
        f.verifierVersion(version);
        FactureFournisseurDetail d = controler(f, lignes.findByFactureIdOrderByOrdreAsc(f.getId()), contexte());
        f.decider(bonAPayer, motif, d.facture().anomalies() > 0, perimetre.identifiant(), horloge.instant());
        factures.saveAndFlush(f);
        return detail(id);
    }

    @Transactional
    public FactureFournisseurDetail annuler(UUID id, Integer version, String motif) {
        FactureFournisseur f = facture(id);
        f.verifierVersion(version);
        f.annuler(motif, perimetre.identifiant(), horloge.instant());
        factures.saveAndFlush(f);
        return detail(id);
    }

    private FactureFournisseur facture(UUID id) {
        return factures.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Facture fournisseur"));
    }

    // ---------------------------------------------------------------- Controle

    private record Contexte(Map<Long, Fournisseur> fournisseurs, Map<Long, ProduitRef> produits, Map<Long, StationRef> stations) {
    }

    private Contexte contexte() {
        return new Contexte(fournisseurs.findAll().stream().collect(Collectors.toMap(Fournisseur::getId, Function.identity())),
                suivi.produits(), suivi.stations());
    }

    /** Reception du BL : par la livraison de tournee qui le porte, a defaut par le nom du fournisseur saisi. */
    private Optional<ReceptionRef> reception(Fournisseur fo, String numeroBl) {
        List<Long> ids = livraisons.parBl(fo.getId(), numeroBl, Tournee.Statut.ANNULEE).stream().map(l -> l.getId()).toList();
        if (!ids.isEmpty()) {
            Optional<ReceptionRef> r = suivi.receptions(ids).values().stream().findFirst();
            if (r.isPresent()) {
                return r;
            }
        }
        return suivi.stocks().receptionParBl(fo.getNom(), numeroBl);
    }

    private FactureFournisseurDetail controler(FactureFournisseur f, List<LigneFactureFournisseur> liste, Contexte ctx) {
        Fournisseur fo = ctx.fournisseurs().get(f.getFournisseurId());
        List<LigneControlee> controlees = new ArrayList<>();
        BigDecimal montant = BigDecimal.ZERO;
        BigDecimal attendu = BigDecimal.ZERO;
        boolean attenduComplet = true;
        int anomalies = 0;
        for (LigneFactureFournisseur l : liste) {
            ProduitRef p = ctx.produits().get(l.getProduitId());
            List<String> remarques = new ArrayList<>();
            ReceptionRef r = reception(fo, l.getNumeroBl()).orElse(null);
            String base = null;
            BigDecimal quantiteBl = null;
            BigDecimal recue = null;
            BigDecimal manquantFournisseur = null;
            String station = null;
            if (r == null) {
                remarques.add("BL non réceptionné dans les stations.");
            } else {
                station = ctx.stations().get(r.stationId()).code();
                boolean a15 = fo.isFactureA15() && r.quantiteBl15() != null;
                base = a15 ? "15 °C" : "ambiante";
                quantiteBl = a15 ? r.quantiteBl15() : r.quantiteBl();
                recue = r.compareeA15() == a15 ? r.quantiteMesuree() : a15 ? null : r.quantiteRecue();
                if (r.produitId() != l.getProduitId()) {
                    remarques.add("Produit facturé (" + p.code() + ") différent du produit réceptionné ("
                            + ctx.produits().get(r.produitId()).code() + ").");
                }
                BigDecimal ecart = l.getQuantite().subtract(quantiteBl);
                BigDecimal seuil = quantiteBl.multiply(toleranceFacturePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (ecart.abs().compareTo(seuil) > 0) {
                    remarques.add("Quantité facturée " + Libelles.litres(l.getQuantite()) + " pour " + Libelles.litres(quantiteBl)
                            + " au BL (" + base + ").");
                }
                if ("FOURNISSEUR".equals(r.imputation()) && r.complementLitres() != null) {
                    manquantFournisseur = r.complementLitres();
                    remarques.add("Manquant de " + Libelles.litres(manquantFournisseur)
                            + " imputé au fournisseur (scellés intacts) : avoir à demander.");
                }
            }
            java.time.LocalDate dateRef = r != null ? r.date() : f.getDateFacture();
            BigDecimal convenu = prix.findFirstByFournisseurIdAndProduitIdAndDebutLessThanEqualOrderByDebutDesc(fo.getId(),
                    l.getProduitId(), dateRef).map(PrixAchat::getPrix).orElse(null);
            if (convenu == null) {
                remarques.add("Aucun prix d'achat convenu du " + p.libelle() + " au " + Libelles.date(dateRef) + ".");
            } else if (l.getPrixUnitaire().subtract(convenu).abs().compareTo(new BigDecimal("0.005")) > 0) {
                remarques.add("Prix facturé " + l.getPrixUnitaire().stripTrailingZeros().toPlainString() + " F/L au lieu de "
                        + convenu.stripTrailingZeros().toPlainString() + " F/L convenus.");
            }
            BigDecimal attenduLigne = r == null || convenu == null ? null
                    : quantiteBl.subtract(manquantFournisseur == null ? BigDecimal.ZERO : manquantFournisseur)
                            .multiply(convenu).setScale(0, RoundingMode.HALF_UP);
            montant = montant.add(l.getMontant());
            if (attenduLigne == null) {
                attenduComplet = false;
            } else {
                attendu = attendu.add(attenduLigne);
            }
            anomalies += remarques.isEmpty() ? 0 : 1;
            controlees.add(new LigneControlee(l.getNumeroBl(), p.uuid(), p.code(), l.getQuantite(), l.getPrixUnitaire(),
                    l.getMontant(), r == null ? null : r.date(), station, base, quantiteBl, recue, manquantFournisseur,
                    convenu, attenduLigne, remarques));
        }
        BigDecimal montantAttendu = attenduComplet && !liste.isEmpty() ? attendu : null;
        FactureFournisseurVue vue = new FactureFournisseurVue(f.getUuid(), fo.getUuid(), fo.getNom(), f.getNumero(),
                f.getDateFacture(), f.getStatut().name(), montant, montantAttendu,
                montantAttendu == null ? null : montant.subtract(montantAttendu), liste.size(), anomalies,
                f.getMotifDecision(), f.getDecidePar(), f.getDecideLe(), f.getObservation(), f.getVersion());
        return new FactureFournisseurDetail(vue, controlees);
    }
}
