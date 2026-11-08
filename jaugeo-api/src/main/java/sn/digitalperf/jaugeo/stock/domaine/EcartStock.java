package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Ecart de stock d'une cuve, fige a la cloture de la journee (retire au renvoi). */
@Entity
@Table(schema = "stock", name = "ecart_stock")
public class EcartStock extends EntiteAuditee {

    /** Identite de la ligne : journee, station, cuve, produit, date. */
    public record Cle(long journeeId, long stationId, long cuveId, long produitId, LocalDate dateJour) {
    }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "cuve_id", nullable = false, updatable = false)
    private Long cuveId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(name = "stock_depart", nullable = false, updatable = false)
    private BigDecimal stockDepart;

    @Column(nullable = false, updatable = false)
    private BigDecimal receptions;

    @Column(nullable = false, updatable = false)
    private BigDecimal sorties;

    @Column(name = "stock_theorique", nullable = false, updatable = false)
    private BigDecimal stockTheorique;

    @Column(name = "stock_physique", nullable = false, updatable = false)
    private BigDecimal stockPhysique;

    @Column(name = "ecart_litres", nullable = false, updatable = false)
    private BigDecimal ecartLitres;

    @Column(name = "prix_unitaire", updatable = false)
    private BigDecimal prixUnitaire;

    @Column(name = "ecart_valeur", updatable = false)
    private BigDecimal ecartValeur;

    @Column(name = "cumul_sorties_mois", nullable = false, updatable = false)
    private BigDecimal cumulSortiesMois;

    @Column(name = "cumul_ecart_mois", nullable = false, updatable = false)
    private BigDecimal cumulEcartMois;

    @Column(name = "ecart_pct_mois", updatable = false)
    private BigDecimal ecartPctMois;

    @Column(name = "tolerance_pct", nullable = false, updatable = false)
    private BigDecimal tolerancePct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CalculStock.Niveau niveau;

    @Column(updatable = false)
    private String observation;

    protected EcartStock() {
    }

    public EcartStock(Cle cle, BigDecimal depart, BigDecimal receptions, BigDecimal sorties, BigDecimal physique,
                      BigDecimal prixUnitaire, BigDecimal tolerancePct, CalculStock.Resultat r, String observation) {
        this.journeeId = cle.journeeId();
        this.stationId = cle.stationId();
        this.cuveId = cle.cuveId();
        this.produitId = cle.produitId();
        this.dateJour = cle.dateJour();
        this.stockDepart = depart;
        this.receptions = receptions;
        this.sorties = sorties;
        this.stockTheorique = r.theorique();
        this.stockPhysique = physique;
        this.ecartLitres = r.ecart();
        this.prixUnitaire = prixUnitaire;
        this.ecartValeur = r.ecartValeur();
        this.cumulSortiesMois = r.cumulSorties();
        this.cumulEcartMois = r.cumulEcart();
        this.ecartPctMois = r.ecartPctMois();
        this.tolerancePct = tolerancePct;
        this.niveau = r.niveau();
        this.observation = observation;
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

    public BigDecimal getStockDepart() {
        return stockDepart;
    }

    public BigDecimal getReceptions() {
        return receptions;
    }

    public BigDecimal getSorties() {
        return sorties;
    }

    public BigDecimal getStockTheorique() {
        return stockTheorique;
    }

    public BigDecimal getStockPhysique() {
        return stockPhysique;
    }

    public BigDecimal getEcartLitres() {
        return ecartLitres;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getEcartValeur() {
        return ecartValeur;
    }

    public BigDecimal getCumulSortiesMois() {
        return cumulSortiesMois;
    }

    public BigDecimal getCumulEcartMois() {
        return cumulEcartMois;
    }

    public BigDecimal getEcartPctMois() {
        return ecartPctMois;
    }

    public BigDecimal getTolerancePct() {
        return tolerancePct;
    }

    public CalculStock.Niveau getNiveau() {
        return niveau;
    }

    public String getObservation() {
        return observation;
    }
}
