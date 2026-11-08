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
@Table(schema = "referentiel", name = "cuve")
public class Cuve extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false, updatable = false)
    private Station station;

    @Column(nullable = false, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(name = "capacite_litres")
    private BigDecimal capaciteLitres;

    @Column(name = "creux_securite_litres", nullable = false)
    private BigDecimal creuxSecuriteLitres = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean actif = true;

    protected Cuve() {
    }

    public Cuve(Station station, String code, Produit produit) {
        if (!produit.isSuiviJaugeage()) {
            throw ErreurMetier.regle("PRODUIT_NON_JAUGE", "Le produit « " + produit.getLibelle()
                    + " » n'est pas suivi en cuve.");
        }
        this.station = station;
        this.code = code;
        this.produit = produit;
    }

    public void modifier(BigDecimal capaciteLitres, BigDecimal creuxSecuriteLitres, boolean actif) {
        BigDecimal creux = creuxSecuriteLitres == null ? BigDecimal.ZERO : creuxSecuriteLitres;
        if (capaciteLitres != null && creux.compareTo(capaciteLitres) >= 0) {
            throw ErreurMetier.regle("CREUX_SUPERIEUR_CAPACITE", "Le creux de sécurité doit être inférieur à la capacité.");
        }
        this.capaciteLitres = capaciteLitres;
        this.creuxSecuriteLitres = creux;
        this.actif = actif;
    }

    public Station getStation() {
        return station;
    }

    public String getCode() {
        return code;
    }

    public Produit getProduit() {
        return produit;
    }

    public BigDecimal getCapaciteLitres() {
        return capaciteLitres;
    }

    public BigDecimal getCreuxSecuriteLitres() {
        return creuxSecuriteLitres;
    }

    public boolean isActif() {
        return actif;
    }
}
