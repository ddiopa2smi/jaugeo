package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Mouvement du stock d'un article (entree, vente, inventaire), avec le stock et le CMUP qui en resultent. */
@Entity
@Table(schema = "stock", name = "mouvement_article")
public class MouvementArticle extends EntiteAuditee {

    public enum Type { ENTREE, VENTE, INVENTAIRE }

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    /** Signee : positive en entree, negative en sortie. */
    @Column(nullable = false, updatable = false)
    private BigDecimal quantite;

    @Column(name = "cout_unitaire", nullable = false, updatable = false)
    private BigDecimal coutUnitaire;

    @Column(nullable = false, updatable = false)
    private BigDecimal valeur;

    @Column(name = "stock_apres", nullable = false, updatable = false)
    private BigDecimal stockApres;

    @Column(name = "cmup_apres", nullable = false, updatable = false)
    private BigDecimal cmupApres;

    @Column(name = "journee_id", updatable = false)
    private Long journeeId;

    @Column(name = "entree_id", updatable = false)
    private Long entreeId;

    @Column(name = "comptage_id", updatable = false)
    private Long comptageId;

    @Column(updatable = false)
    private String libelle;

    protected MouvementArticle() {
    }

    public MouvementArticle(ArticleStation a, LocalDate date, Type type, BigDecimal quantite, BigDecimal coutUnitaire,
                            String libelle) {
        this.stationId = a.getStationId();
        this.produitId = a.getProduitId();
        this.dateJour = date;
        this.type = type;
        this.quantite = quantite;
        this.coutUnitaire = coutUnitaire;
        this.valeur = CalculArticle.valeur(quantite, coutUnitaire);
        this.stockApres = a.getQuantite();
        this.cmupApres = a.getCmup();
        this.libelle = libelle;
    }

    public MouvementArticle journee(Long id) {
        this.journeeId = id;
        return this;
    }

    public MouvementArticle entree(Long id) {
        this.entreeId = id;
        return this;
    }

    public MouvementArticle comptage(Long id) {
        this.comptageId = id;
        return this;
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

    public Type getType() {
        return type;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public BigDecimal getCoutUnitaire() {
        return coutUnitaire;
    }

    public BigDecimal getValeur() {
        return valeur;
    }

    public BigDecimal getStockApres() {
        return stockApres;
    }

    public BigDecimal getCmupApres() {
        return cmupApres;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public String getLibelle() {
        return libelle;
    }
}
