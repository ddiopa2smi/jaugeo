package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Entree d'articles en stock (livraison, ou stock initial) : annoncee par la logistique avec son cout d'achat,
 * elle n'entre en stock que lorsque le gerant confirme la quantite recue a la station.
 */
@Entity
@Table(schema = "stock", name = "entree_article")
public class EntreeArticle extends EntiteAuditee {

    public enum Statut { ANNONCEE, RECUE, ANNULEE }

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false, updatable = false)
    private BigDecimal quantite;

    @Column(name = "cout_unitaire", nullable = false, updatable = false)
    private BigDecimal coutUnitaire;

    @Column(nullable = false, updatable = false)
    private String fournisseur;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ANNONCEE;

    @Column(name = "quantite_recue")
    private BigDecimal quantiteRecue;

    @Column(name = "recue_le")
    private LocalDate recueLe;

    @Column(name = "recue_par")
    private String recuePar;

    private String observation;

    protected EntreeArticle() {
    }

    public EntreeArticle(long stationId, long produitId, BigDecimal quantite, BigDecimal coutUnitaire, String fournisseur,
                         String reference) {
        this.stationId = stationId;
        this.produitId = produitId;
        this.quantite = quantite;
        this.coutUnitaire = coutUnitaire;
        this.fournisseur = fournisseur.trim();
        this.reference = reference.trim();
    }

    /** Confirmation a la station ; une quantite differente de l'annonce exige une observation. */
    public void recevoir(BigDecimal recue, LocalDate le, String par, String observation) {
        if (statut != Statut.ANNONCEE) {
            throw ErreurMetier.regle("ENTREE_TRAITEE", "Cette entrée a déjà été " + (statut == Statut.RECUE ? "reçue." : "annulée."));
        }
        String obs = observation == null || observation.isBlank() ? null : observation.trim();
        if (recue.compareTo(quantite) != 0 && obs == null) {
            throw ErreurMetier.regle("OBSERVATION_OBLIGATOIRE", "La quantité reçue diffère de la quantité annoncée : "
                    + "indiquez la raison (casse, manquant, erreur de livraison...).");
        }
        this.statut = Statut.RECUE;
        this.quantiteRecue = recue;
        this.recueLe = le;
        this.recuePar = par;
        this.observation = obs;
    }

    public void annuler(String motif) {
        if (statut != Statut.ANNONCEE) {
            throw ErreurMetier.regle("ENTREE_TRAITEE", "Seule une entrée encore annoncée peut être annulée.");
        }
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif de l'annulation.");
        }
        this.statut = Statut.ANNULEE;
        this.observation = motif.trim();
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getCoutUnitaire() {
        return coutUnitaire;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public String getReference() {
        return reference;
    }

    public Statut getStatut() {
        return statut;
    }

    public BigDecimal getQuantiteRecue() {
        return quantiteRecue;
    }

    public LocalDate getRecueLe() {
        return recueLe;
    }

    public String getRecuePar() {
        return recuePar;
    }

    public String getObservation() {
        return observation;
    }
}
