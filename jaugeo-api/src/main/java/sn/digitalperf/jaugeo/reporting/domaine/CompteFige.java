package sn.digitalperf.jaugeo.reporting.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Ligne du compte d'exploitation fige a la cloture d'un mois : une rubrique, par produit pour les carburants.
 * Un montant nul signifie un cout inconnu (aucun prix d'achat convenu).
 */
@Entity
@Table(schema = "reporting", name = "compte_fige")
public class CompteFige extends EntiteAuditee {

    public enum Rubrique { CHIFFRE_AFFAIRES, COUT_ACHAT, ECART_STOCK, VENTES_ARTICLES, COUT_ARTICLES, REMISES, DEPENSES }

    @Column(name = "cloture_id", nullable = false, updatable = false)
    private Long clotureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Rubrique rubrique;

    @Column(name = "produit_id", updatable = false)
    private Long produitId;

    @Column(updatable = false)
    private BigDecimal litres;

    @Column(updatable = false)
    private BigDecimal montant;

    protected CompteFige() {
    }

    public CompteFige(long clotureId, Rubrique rubrique, Long produitId, BigDecimal litres, BigDecimal montant) {
        this.clotureId = clotureId;
        this.rubrique = rubrique;
        this.produitId = produitId;
        this.litres = litres;
        this.montant = montant;
    }

    public Long getClotureId() {
        return clotureId;
    }

    public Rubrique getRubrique() {
        return rubrique;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
