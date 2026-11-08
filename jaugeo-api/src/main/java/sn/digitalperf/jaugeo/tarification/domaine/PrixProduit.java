package sn.digitalperf.jaugeo.tarification.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Prix d'un produit dans une station sur [debut, fin). */
@Entity
@Table(schema = "tarification", name = "prix_produit")
public class PrixProduit extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changement_id", nullable = false, updatable = false)
    private ChangementPrix changement;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(name = "prix_unitaire", nullable = false, updatable = false)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, updatable = false)
    private Instant debut;

    @Column
    private Instant fin;

    protected PrixProduit() {
    }

    public PrixProduit(ChangementPrix changement, Long stationId, Long produitId, BigDecimal prixUnitaire) {
        if (prixUnitaire == null || prixUnitaire.signum() <= 0) {
            throw ErreurMetier.regle("PRIX_INVALIDE", "Le prix doit être strictement positif.");
        }
        this.changement = changement;
        this.stationId = stationId;
        this.produitId = produitId;
        this.prixUnitaire = prixUnitaire;
        this.debut = changement.getDebut();
    }

    /** Le prix cesse de s'appliquer a l'arrivee du suivant. */
    public void cloturer(Instant finPeriode) {
        if (!finPeriode.isAfter(debut)) {
            throw ErreurMetier.regle("PRIX_POSTERIEUR_EXISTANT", "Un prix plus récent existe déjà.");
        }
        this.fin = finPeriode;
    }

    /** Annulation du prix suivant : celui-ci redevient en vigueur sans limite. */
    public void rouvrir() {
        this.fin = null;
    }

    public ChangementPrix getChangement() {
        return changement;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public Instant getDebut() {
        return debut;
    }

    public Instant getFin() {
        return fin;
    }

    public boolean enVigueur(Instant instant) {
        return !debut.isAfter(instant) && (fin == null || fin.isAfter(instant));
    }
}
