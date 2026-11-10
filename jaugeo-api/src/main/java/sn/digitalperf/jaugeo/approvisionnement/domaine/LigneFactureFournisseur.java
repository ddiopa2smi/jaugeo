package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Ligne de facture fournisseur : un BL, son produit, la quantite et le prix factures. */
@Entity
@Table(schema = "approvisionnement", name = "ligne_facture_fournisseur")
public class LigneFactureFournisseur extends EntiteAuditee {

    @Column(name = "facture_id", nullable = false, updatable = false)
    private Long factureId;

    @Column(nullable = false)
    private int ordre;

    @Column(name = "numero_bl", nullable = false)
    private String numeroBl;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(nullable = false)
    private BigDecimal quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private BigDecimal prixUnitaire;

    @Column(nullable = false)
    private BigDecimal montant;

    protected LigneFactureFournisseur() {
    }

    public LigneFactureFournisseur(long factureId, int ordre, String numeroBl, long produitId, BigDecimal quantite,
                                   BigDecimal prixUnitaire) {
        if (numeroBl == null || numeroBl.isBlank()) {
            throw ErreurMetier.regle("BL_OBLIGATOIRE", "Chaque ligne porte le numéro du BL facturé.");
        }
        if (quantite == null || quantite.signum() <= 0 || prixUnitaire == null || prixUnitaire.signum() <= 0) {
            throw ErreurMetier.regle("LIGNE_INVALIDE", "Quantité et prix facturés doivent être positifs (BL "
                    + numeroBl.trim() + ").");
        }
        this.factureId = factureId;
        this.ordre = ordre;
        this.numeroBl = numeroBl.trim().toUpperCase();
        this.produitId = produitId;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
        this.montant = quantite.multiply(prixUnitaire).setScale(0, RoundingMode.HALF_UP);
    }

    public Long getFactureId() {
        return factureId;
    }

    public int getOrdre() {
        return ordre;
    }

    public String getNumeroBl() {
        return numeroBl;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }
}
