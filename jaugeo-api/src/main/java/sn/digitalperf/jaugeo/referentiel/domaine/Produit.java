package sn.digitalperf.jaugeo.referentiel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

@Entity
@Table(schema = "referentiel", name = "produit")
public class Produit extends EntiteAuditee {

    public enum Famille { CARBURANT, LUBRIFIANT, GAZ, BOUTIQUE }

    public enum Unite { LITRE, UNITE, KG }

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Famille famille;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unite unite;

    @Column(name = "suivi_jaugeage", nullable = false)
    private boolean suiviJaugeage;

    @Column(name = "taux_tva", nullable = false)
    private BigDecimal tauxTva = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean actif = true;

    /** Tolerance d'ecart de stock, en % des sorties de la periode (cahier des charges : 0,5 % Gasoil, 0,6 % essences). */
    @Column(name = "tolerance_ecart_pct", nullable = false)
    private BigDecimal toleranceEcartPct = TOLERANCE_PAR_DEFAUT;

    public static final BigDecimal TOLERANCE_PAR_DEFAUT = new BigDecimal("0.50");

    protected Produit() {
    }

    public Produit(String code) {
        this.code = code;
    }

    public void modifier(String libelle, Famille famille, Unite unite, boolean suiviJaugeage, BigDecimal tauxTva,
                         boolean actif) {
        modifier(libelle, famille, unite, suiviJaugeage, tauxTva, actif, null);
    }

    /** Tolerance nulle = inchangee. */
    public void modifier(String libelle, Famille famille, Unite unite, boolean suiviJaugeage, BigDecimal tauxTva,
                         boolean actif, BigDecimal toleranceEcartPct) {
        if (suiviJaugeage && unite != Unite.LITRE) {
            throw ErreurMetier.regle("JAUGEAGE_HORS_LITRE", "Seul un produit vendu au litre peut être suivi en cuve.");
        }
        if (toleranceEcartPct != null && (toleranceEcartPct.signum() < 0 || toleranceEcartPct.compareTo(BigDecimal.TEN) > 0)) {
            throw ErreurMetier.regle("TOLERANCE_INVALIDE", "La tolérance d'écart de stock doit être comprise entre 0 et 10 %.");
        }
        this.libelle = libelle;
        this.famille = famille;
        this.unite = unite;
        this.suiviJaugeage = suiviJaugeage;
        this.tauxTva = tauxTva == null ? BigDecimal.ZERO : tauxTva;
        this.actif = actif;
        if (toleranceEcartPct != null) {
            this.toleranceEcartPct = toleranceEcartPct;
        }
    }

    public BigDecimal getToleranceEcartPct() {
        return toleranceEcartPct;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public Famille getFamille() {
        return famille;
    }

    public Unite getUnite() {
        return unite;
    }

    public boolean isSuiviJaugeage() {
        return suiviJaugeage;
    }

    public BigDecimal getTauxTva() {
        return tauxTva;
    }

    public boolean isActif() {
        return actif;
    }
}
