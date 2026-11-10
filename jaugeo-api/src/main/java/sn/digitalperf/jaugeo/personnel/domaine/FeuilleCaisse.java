package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Feuille de caisse d'un pompiste pour un poste : ce qu'il remet au gerant (especes, paiements electroniques,
 * bons clients, pleins par carte prepayee). Le montant du (ventes de ses pistolets) et l'ecart (remis - du) sont
 * figes a la cloture de la journee.
 */
@Entity
@Table(schema = "personnel", name = "feuille_caisse")
public class FeuilleCaisse extends EntiteAuditee {

    @Column(name = "poste_id", nullable = false, updatable = false)
    private Long posteId;

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(name = "poste", nullable = false, updatable = false)
    private TypePoste type;

    @Column(name = "employe_id", nullable = false, updatable = false)
    private Long employeId;

    @Column(nullable = false)
    private BigDecimal especes = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal electronique = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal bons = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal cartes = BigDecimal.ZERO;

    @Column(name = "montant_du")
    private BigDecimal montantDu;

    private BigDecimal ecart;

    private String observation;

    protected FeuilleCaisse() {
    }

    public FeuilleCaisse(Poste poste, long employeId) {
        this.posteId = poste.getId();
        this.journeeId = poste.getJourneeId();
        this.stationId = poste.getStationId();
        this.dateJour = poste.getDateJour();
        this.type = poste.getType();
        this.employeId = employeId;
    }

    public void remettre(BigDecimal especes, BigDecimal electronique, BigDecimal bons, BigDecimal cartes, String observation) {
        this.especes = montant(especes, "espèces");
        this.electronique = montant(electronique, "paiements électroniques");
        this.bons = montant(bons, "bons clients");
        this.cartes = montant(cartes, "cartes carburant");
        this.observation = Textes.texte(observation);
    }

    private static BigDecimal montant(BigDecimal v, String quoi) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Feuille de caisse, " + quoi + " : le montant est un nombre entier de francs.");
        }
        return v;
    }

    public BigDecimal remis() {
        return especes.add(electronique).add(bons).add(cartes);
    }

    public void figer(BigDecimal du) {
        this.montantDu = du;
        this.ecart = remis().subtract(du);
    }

    public void defiger() {
        this.montantDu = null;
        this.ecart = null;
    }

    public Long getPosteId() {
        return posteId;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public TypePoste getType() {
        return type;
    }

    public Long getEmployeId() {
        return employeId;
    }

    public BigDecimal getEspeces() {
        return especes;
    }

    public BigDecimal getElectronique() {
        return electronique;
    }

    public BigDecimal getBons() {
        return bons;
    }

    public BigDecimal getCartes() {
        return cartes;
    }

    public BigDecimal getMontantDu() {
        return montantDu;
    }

    public BigDecimal getEcart() {
        return ecart;
    }

    public String getObservation() {
        return observation;
    }
}
