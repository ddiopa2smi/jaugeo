package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Arret d'une tournee : station, produit et quantite commandee ; BL et quantites renseignes au chargement. */
@Entity
@Table(schema = "approvisionnement", name = "livraison")
public class Livraison extends EntiteAuditee {

    @Column(name = "tournee_id", nullable = false, updatable = false)
    private Long tourneeId;

    @Column(nullable = false)
    private int ordre;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(name = "quantite_commandee", nullable = false)
    private BigDecimal quantiteCommandee;

    @Column(name = "numero_bl")
    private String numeroBl;

    @Column(name = "quantite_bl")
    private BigDecimal quantiteBl;

    @Column(name = "quantite_bl_15")
    private BigDecimal quantiteBl15;

    protected Livraison() {
    }

    public Livraison(long tourneeId) {
        this.tourneeId = tourneeId;
    }

    public void commander(int ordre, long stationId, long produitId, BigDecimal quantite) {
        if (quantite == null || quantite.signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_INVALIDE", "La quantité commandée doit être positive.");
        }
        this.ordre = ordre;
        this.stationId = stationId;
        this.produitId = produitId;
        this.quantiteCommandee = quantite;
    }

    public void renseignerBl(String numeroBl, BigDecimal quantiteBl, BigDecimal quantiteBl15) {
        if (numeroBl == null || numeroBl.isBlank()) {
            throw ErreurMetier.regle("BL_OBLIGATOIRE", "Indiquez le numéro du BL de chaque livraison.");
        }
        if (quantiteBl == null || quantiteBl.signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_BL_INVALIDE", "Indiquez la quantité chargée portée sur le BL.");
        }
        if (quantiteBl15 != null && quantiteBl15.signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_BL_INVALIDE", "La quantité à 15 °C du BL doit être positive.");
        }
        this.numeroBl = Textes.majuscules(numeroBl);
        this.quantiteBl = quantiteBl;
        this.quantiteBl15 = quantiteBl15;
    }

    /** Quantite en route : celle du BL des le chargement, la quantite commandee avant. */
    public BigDecimal quantitePrevue() {
        return quantiteBl != null ? quantiteBl : quantiteCommandee;
    }

    public Long getTourneeId() {
        return tourneeId;
    }

    public int getOrdre() {
        return ordre;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getQuantiteCommandee() {
        return quantiteCommandee;
    }

    public String getNumeroBl() {
        return numeroBl;
    }

    public BigDecimal getQuantiteBl() {
        return quantiteBl;
    }

    public BigDecimal getQuantiteBl15() {
        return quantiteBl15;
    }
}
