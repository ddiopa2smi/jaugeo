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
 * La quantite recue (ambiante) entre en stock. La livraison se juge a 15 °C quand le BL porte la quantite a 15 °C
 * et que la temperature et la densite a 15 °C sont relevees : la quantite recue est alors corrigee (table 54B).
 * Le manquant au-dela de la tolerance transport devient un complement citerne, a la charge du transporteur
 * ou du fournisseur (imputation).
 */
@Entity
@Table(schema = "stock", name = "reception")
public class Reception extends EntiteAuditee {

    /**
     * A qui revient un manquant de livraison. Par defaut : le fournisseur si la citerne est arrivee scelles intacts
     * (le manquant vient du chargement au depot), le transporteur sinon.
     */
    public enum Imputation { TRANSPORTEUR, FOURNISSEUR }

    /** Donnees saisies d'une reception ; densite = masse volumique a 15 °C (kg/L), portee sur le BL. */
    public record Donnees(String numeroBl, String fournisseur, String transporteur, String chauffeur,
                          String immatriculation, String scelles, BigDecimal quantiteBl, BigDecimal jaugeAvant,
                          BigDecimal jaugeApres, BigDecimal quantiteRecue, BigDecimal temperatureC, BigDecimal densite,
                          String observation, BigDecimal quantiteBl15, Boolean scellesIntacts, Imputation imputation) {
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

    @Column(name = "quantite_bl_15")
    private BigDecimal quantiteBl15;

    @Column(name = "facteur_15")
    private BigDecimal facteur15;

    @Column(name = "quantite_recue_15")
    private BigDecimal quantiteRecue15;

    @Column(name = "scelles_intacts")
    private Boolean scellesIntacts;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Imputation imputation = Imputation.TRANSPORTEUR;

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
        if (d.quantiteBl15() != null && d.quantiteBl15().signum() <= 0) {
            throw ErreurMetier.regle("QUANTITE_BL_INVALIDE", "La quantité à 15 °C du BL doit être positive.");
        }
        this.quantiteBl15 = d.quantiteBl15();
        if (d.temperatureC() != null && d.densite() != null) {
            if (d.densite().compareTo(Correction15.DENSITE_MIN) < 0 || d.densite().compareTo(Correction15.DENSITE_MAX) > 0) {
                throw ErreurMetier.regle("DENSITE_INVALIDE", "La densité à 15 °C doit être comprise entre 0,6105 et 1,075 "
                        + "(par exemple 0,745 pour le super, 0,840 pour le gasoil).");
            }
            this.facteur15 = Correction15.facteur(d.temperatureC(), d.densite());
            this.quantiteRecue15 = Correction15.volume15(recue, facteur15);
        } else {
            this.facteur15 = null;
            this.quantiteRecue15 = null;
        }
        this.scellesIntacts = d.scellesIntacts();
        this.imputation = d.imputation() != null ? d.imputation()
                : Boolean.TRUE.equals(d.scellesIntacts()) ? Imputation.FOURNISSEUR : Imputation.TRANSPORTEUR;
    }

    /** La livraison se juge a 15 °C : quantite du BL a 15 °C et quantite recue corrigee toutes deux connues. */
    public boolean compareeA15() {
        return quantiteBl15 != null && quantiteRecue15 != null;
    }

    /** Quantite annoncee par le BL, dans la base de comparaison (15 °C ou ambiante). */
    public BigDecimal quantiteLivree() {
        return compareeA15() ? quantiteBl15 : quantiteBl;
    }

    /** Quantite mesuree a la station, dans la meme base que {@link #quantiteLivree()}. */
    public BigDecimal quantiteMesuree() {
        return compareeA15() ? quantiteRecue15 : quantiteRecue;
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

    public BigDecimal getQuantiteBl15() {
        return quantiteBl15;
    }

    public BigDecimal getFacteur15() {
        return facteur15;
    }

    public BigDecimal getQuantiteRecue15() {
        return quantiteRecue15;
    }

    public Boolean getScellesIntacts() {
        return scellesIntacts;
    }

    public Imputation getImputation() {
        return imputation;
    }
}
