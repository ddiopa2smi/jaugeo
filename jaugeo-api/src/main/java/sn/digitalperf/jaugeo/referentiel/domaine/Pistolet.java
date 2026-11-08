package sn.digitalperf.jaugeo.referentiel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

@Entity
@Table(schema = "referentiel", name = "pistolet")
public class Pistolet extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pompe_id", nullable = false, updatable = false)
    private Pompe pompe;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cuve_id", nullable = false)
    private Cuve cuve;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "index_mise_en_service", nullable = false, updatable = false)
    private BigDecimal indexMiseEnService;

    @Column(nullable = false)
    private boolean actif = true;

    protected Pistolet() {
    }

    public Pistolet(Pompe pompe, Cuve cuve, String code, String libelle, BigDecimal indexMiseEnService) {
        if (!pompe.getStation().getId().equals(cuve.getStation().getId())) {
            throw ErreurMetier.regle("CUVE_AUTRE_STATION",
                    "Le pistolet doit puiser dans une cuve de la même station que sa pompe.");
        }
        if (indexMiseEnService == null || indexMiseEnService.signum() < 0) {
            throw ErreurMetier.regle("INDEX_INVALIDE", "L'index de mise en service doit être positif ou nul.");
        }
        this.pompe = pompe;
        this.cuve = cuve;
        this.code = code;
        this.libelle = libelle;
        this.indexMiseEnService = indexMiseEnService;
    }

    public void modifier(String libelle, boolean actif) {
        this.libelle = libelle;
        this.actif = actif;
    }

    public Pompe getPompe() {
        return pompe;
    }

    public Cuve getCuve() {
        return cuve;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public BigDecimal getIndexMiseEnService() {
        return indexMiseEnService;
    }

    public boolean isActif() {
        return actif;
    }
}
