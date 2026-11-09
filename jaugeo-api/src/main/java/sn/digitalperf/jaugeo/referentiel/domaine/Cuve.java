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

    /** Hauteur d'eau en fond de cuve (mm) au-dela de laquelle le jaugeage leve une alerte ; nul = seuil du reseau. */
    @Column(name = "seuil_eau_mm")
    private Integer seuilEauMm;

    /** Stock (litres) sous lequel la cuve est a reapprovisionner ; nul = jugement sur l'autonomie en jours. */
    @Column(name = "seuil_reappro_litres")
    private BigDecimal seuilReapproLitres;

    @Column(name = "bareme_certificat")
    private String baremeCertificat;

    @Column(name = "bareme_date")
    private java.time.LocalDate baremeDate;

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

    public void definirSeuils(Integer seuilEauMm, BigDecimal seuilReapproLitres) {
        if (seuilReapproLitres != null && capaciteLitres != null && seuilReapproLitres.compareTo(capaciteLitres) >= 0) {
            throw ErreurMetier.regle("SEUIL_SUPERIEUR_CAPACITE", "Le seuil de réapprovisionnement doit être inférieur à la capacité.");
        }
        this.seuilEauMm = seuilEauMm;
        this.seuilReapproLitres = seuilReapproLitres == null || seuilReapproLitres.signum() == 0 ? null : seuilReapproLitres;
    }

    public Integer getSeuilEauMm() {
        return seuilEauMm;
    }

    public BigDecimal getSeuilReapproLitres() {
        return seuilReapproLitres;
    }

    /**
     * Rattache un nouveau bareme : renseigne la capacite si elle etait inconnue, refuse un bareme
     * dont le volume maximal depasse nettement la capacite nominale (mauvaise cuve ou mauvaise unite).
     */
    public void appliquerBareme(Bareme bareme, String certificat, java.time.LocalDate dateCertificat) {
        BigDecimal volumeMax = bareme.volumeMax();
        if (capaciteLitres == null) {
            capaciteLitres = volumeMax.setScale(0, java.math.RoundingMode.DOWN);
        } else if (volumeMax.compareTo(capaciteLitres.multiply(new BigDecimal("1.10"))) > 0) {
            throw ErreurMetier.regle("BAREME_INCOHERENT", "Le barème atteint " + volumeMax.stripTrailingZeros().toPlainString()
                    + " L alors que la capacité de la cuve est de " + capaciteLitres.stripTrailingZeros().toPlainString()
                    + " L. Vérifiez la cuve, l'unité des hauteurs (cm ou mm) ou la capacité.");
        }
        if (creuxSecuriteLitres.compareTo(capaciteLitres) >= 0) {
            throw ErreurMetier.regle("CREUX_SUPERIEUR_CAPACITE", "Le creux de sécurité doit être inférieur à la capacité.");
        }
        this.baremeCertificat = certificat;
        this.baremeDate = dateCertificat;
    }

    public String getBaremeCertificat() {
        return baremeCertificat;
    }

    public java.time.LocalDate getBaremeDate() {
        return baremeDate;
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
