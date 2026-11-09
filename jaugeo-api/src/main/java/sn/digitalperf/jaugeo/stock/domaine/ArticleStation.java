package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Stock courant d'un article (lubrifiant, article boutique) dans une station, et son CMUP. */
@Entity
@Table(schema = "stock", name = "article_station")
public class ArticleStation extends EntiteAuditee {

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false)
    private BigDecimal quantite = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal cmup = BigDecimal.ZERO;

    @Column(name = "dernier_comptage")
    private LocalDate dernierComptage;

    protected ArticleStation() {
    }

    public ArticleStation(long stationId, long produitId) {
        this.stationId = stationId;
        this.produitId = produitId;
    }

    public CalculArticle.Etat etat() {
        return new CalculArticle.Etat(quantite, cmup);
    }

    public void appliquer(CalculArticle.Etat e) {
        this.quantite = e.quantite();
        this.cmup = e.cmup();
    }

    public void compte(LocalDate le) {
        this.dernierComptage = le;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getCmup() {
        return cmup;
    }

    public LocalDate getDernierComptage() {
        return dernierComptage;
    }
}
