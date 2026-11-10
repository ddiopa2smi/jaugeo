package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Prix d'achat convenu avec un fournisseur pour un produit, a partir d'une date (le plus recent en vigueur). */
@Entity
@Table(schema = "approvisionnement", name = "prix_achat")
public class PrixAchat extends EntiteAuditee {

    @Column(name = "fournisseur_id", nullable = false, updatable = false)
    private Long fournisseurId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false, updatable = false)
    private LocalDate debut;

    @Column(nullable = false, updatable = false)
    private BigDecimal prix;

    protected PrixAchat() {
    }

    public PrixAchat(long fournisseurId, long produitId, LocalDate debut, BigDecimal prix) {
        if (debut == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date d'effet du prix.");
        }
        if (prix == null || prix.signum() <= 0) {
            throw ErreurMetier.regle("PRIX_INVALIDE", "Le prix d'achat doit être positif.");
        }
        this.fournisseurId = fournisseurId;
        this.produitId = produitId;
        this.debut = debut;
        this.prix = prix;
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public LocalDate getDebut() {
        return debut;
    }

    public BigDecimal getPrix() {
        return prix;
    }
}
