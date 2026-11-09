package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Comptage d'inventaire tournant : l'ecart (compte - theorique) est valorise au CMUP ; observation si ecart. */
@Entity
@Table(schema = "stock", name = "comptage_article")
public class ComptageArticle extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(nullable = false, updatable = false)
    private BigDecimal theorique;

    @Column(nullable = false, updatable = false)
    private BigDecimal compte;

    @Column(nullable = false, updatable = false)
    private BigDecimal ecart;

    @Column(nullable = false, updatable = false)
    private BigDecimal cmup;

    @Column(name = "ecart_valeur", nullable = false, updatable = false)
    private BigDecimal ecartValeur;

    @Column(updatable = false)
    private String observation;

    protected ComptageArticle() {
    }

    public ComptageArticle(long stationId, long produitId, LocalDate dateJour, CalculArticle.Etat avant, BigDecimal compte,
                           String observation, String libelleArticle) {
        String obs = observation == null || observation.isBlank() ? null : observation.trim();
        BigDecimal ecart = compte.subtract(avant.quantite());
        if (ecart.signum() != 0 && obs == null) {
            throw ErreurMetier.regle("OBSERVATION_OBLIGATOIRE", libelleArticle + " : " + compte.stripTrailingZeros().toPlainString()
                    + " compté(s) pour " + avant.quantite().stripTrailingZeros().toPlainString()
                    + " attendu(s). Expliquez l'écart (casse, vol, vente non saisie...).");
        }
        this.stationId = stationId;
        this.produitId = produitId;
        this.dateJour = dateJour;
        this.theorique = avant.quantite();
        this.compte = compte;
        this.ecart = ecart;
        this.cmup = avant.cmup();
        this.ecartValeur = CalculArticle.valeur(ecart, avant.cmup());
        this.observation = obs;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public BigDecimal getTheorique() {
        return theorique;
    }

    public BigDecimal getCompte() {
        return compte;
    }

    public BigDecimal getEcart() {
        return ecart;
    }

    public BigDecimal getCmup() {
        return cmup;
    }

    public BigDecimal getEcartValeur() {
        return ecartValeur;
    }

    public String getObservation() {
        return observation;
    }
}
