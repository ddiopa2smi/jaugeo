package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Releve de fin de poste d'un pistolet tenu par un pompiste ; un seul pompiste par pistolet et par poste. */
@Entity
@Table(schema = "personnel", name = "ligne_feuille")
public class LigneFeuille extends EntiteAuditee {

    @Column(name = "feuille_id", nullable = false, updatable = false)
    private Long feuilleId;

    @Column(name = "poste_id", nullable = false, updatable = false)
    private Long posteId;

    @Column(name = "pistolet_id", nullable = false, updatable = false)
    private Long pistoletId;

    @Column(name = "index_fin", nullable = false)
    private BigDecimal indexFin;

    @Column(name = "remise_cuve", nullable = false)
    private BigDecimal remiseCuve = BigDecimal.ZERO;

    protected LigneFeuille() {
    }

    public LigneFeuille(FeuilleCaisse feuille, long pistoletId, BigDecimal indexFin, BigDecimal remiseCuve) {
        if (indexFin == null || indexFin.signum() < 0) {
            throw ErreurMetier.regle("INDEX_OBLIGATOIRE", "Indiquez l'index de fin de poste de chaque pistolet.");
        }
        if (remiseCuve != null && remiseCuve.signum() < 0) {
            throw ErreurMetier.regle("REMISE_INVALIDE", "La remise en cuve ne peut pas être négative.");
        }
        this.feuilleId = feuille.getId();
        this.posteId = feuille.getPosteId();
        this.pistoletId = pistoletId;
        this.indexFin = indexFin;
        this.remiseCuve = remiseCuve == null ? BigDecimal.ZERO : remiseCuve;
    }

    public Long getFeuilleId() {
        return feuilleId;
    }

    public Long getPosteId() {
        return posteId;
    }

    public Long getPistoletId() {
        return pistoletId;
    }

    public BigDecimal getIndexFin() {
        return indexFin;
    }

    public BigDecimal getRemiseCuve() {
        return remiseCuve;
    }
}
