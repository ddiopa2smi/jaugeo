package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Reception citerne liee a un bon de livraison (BL), saisie dans la journee du depotage.
 * La quantite recue entre en stock ; le manquant au-dela de la tolerance transport devient un complement citerne.
 */
@Entity
@Table(schema = "stock", name = "reception")
public class Reception extends EntiteAuditee {

    /** Donnees saisies d'une reception. */
    public record Donnees(String numeroBl, String fournisseur, String transporteur, String chauffeur,
                          String immatriculation, String scelles, BigDecimal quantiteBl, BigDecimal jaugeAvant,
                          BigDecimal jaugeApres, BigDecimal quantiteRecue, BigDecimal temperatureC, BigDecimal densite,
                          String observation) {
    }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "cuve_id", nullable = false)
    private Long cuveId;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(name = "numero_bl", nullable = false)
    private String numeroBl;

    @Column(nullable = false)
    private String fournisseur;

    private String transporteur;
    private String chauffeur;
    private String immatriculation;
    private String scelles;

    @Column(name = "quantite_bl", nullable = false)
    private BigDecimal quantiteBl;

    @Column(name = "jauge_avant")
    private BigDecimal jaugeAvant;

    @Column(name = "jauge_apres")
    private BigDecimal jaugeApres;

    @Column(name = "quantite_recue", nullable = false)
    private BigDecimal quantiteRecue;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    private BigDecimal densite;

    private String observation;

    protected Reception() {
    }

    public Reception(long journeeId, long stationId, LocalDate dateJour) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
    }

    public void renseigner(long cuveId, long produitId, Donnees d) {
        if (d.quantiteBl() == null || d.quantiteBl().signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_BL_INVALIDE", "Indiquez la quantité portée sur le BL.");
        }
        if (d.jaugeAvant() != null && d.jaugeApres() != null && d.jaugeApres().compareTo(d.jaugeAvant()) <= 0) {
            throw ErreurMetier.regle("JAUGES_INCOHERENTES", "La jauge après dépotage doit être supérieure à la jauge avant.");
        }
        BigDecimal recue = CalculStock.quantiteRecue(d.quantiteRecue(), d.jaugeAvant(), d.jaugeApres(), d.quantiteBl());
        if (recue.signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_RECUE_INVALIDE", "La quantité reçue doit être positive.");
        }
        this.cuveId = cuveId;
        this.produitId = produitId;
        this.numeroBl = d.numeroBl().trim().toUpperCase();
        this.fournisseur = d.fournisseur().trim();
        this.transporteur = texte(d.transporteur());
        this.chauffeur = texte(d.chauffeur());
        this.immatriculation = d.immatriculation() == null || d.immatriculation().isBlank() ? null
                : d.immatriculation().trim().toUpperCase();
        this.scelles = texte(d.scelles());
        this.quantiteBl = d.quantiteBl();
        this.jaugeAvant = d.jaugeAvant();
        this.jaugeApres = d.jaugeApres();
        this.quantiteRecue = recue;
        this.temperatureC = d.temperatureC();
        this.densite = d.densite();
        this.observation = texte(d.observation());
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getCuveId() {
        return cuveId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public String getNumeroBl() {
        return numeroBl;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public String getTransporteur() {
        return transporteur;
    }

    public String getChauffeur() {
        return chauffeur;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public String getScelles() {
        return scelles;
    }

    public BigDecimal getQuantiteBl() {
        return quantiteBl;
    }

    public BigDecimal getJaugeAvant() {
        return jaugeAvant;
    }

    public BigDecimal getJaugeApres() {
        return jaugeApres;
    }

    public BigDecimal getQuantiteRecue() {
        return quantiteRecue;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public BigDecimal getDensite() {
        return densite;
    }

    public String getObservation() {
        return observation;
    }
}
