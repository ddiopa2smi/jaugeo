package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Mouvement d'une carte prepayee : recharge encaissee par le siege, ou plein servi a une station (saisi dans la
 * journee, valorise au prix pompe diminue de la remise du client ; la valeur a la pompe sort de la recette).
 */
@Entity
@Table(schema = "clients", name = "mouvement_carte")
public class MouvementCarte extends EntiteAuditee {

    public enum Type { RECHARGE, DEBIT }

    public enum Mode { ESPECES_SIEGE, VIREMENT, CHEQUE, WAVE, ORANGE_MONEY }

    @Column(name = "carte_id", nullable = false, updatable = false)
    private Long carteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_mouvement", nullable = false, updatable = false)
    private Type type;

    @Column(name = "date_mouvement", nullable = false, updatable = false)
    private LocalDate dateMouvement;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Mode mode;

    @Column(updatable = false)
    private String reference;

    @Column(name = "journee_id", updatable = false)
    private Long journeeId;

    @Column(name = "station_id", updatable = false)
    private Long stationId;

    @Column(name = "produit_id", updatable = false)
    private Long produitId;

    @Column(updatable = false)
    private BigDecimal litres;

    @Column(name = "prix_unitaire", updatable = false)
    private BigDecimal prixUnitaire;

    @Column(name = "remise_unitaire", updatable = false)
    private BigDecimal remiseUnitaire;

    @Column(name = "montant_pompe", updatable = false)
    private BigDecimal montantPompe;

    protected MouvementCarte() {
    }

    public static MouvementCarte recharge(long carteId, LocalDate date, Mode mode, BigDecimal montant, String reference) {
        if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant est un nombre entier de francs, supérieur à zéro.");
        }
        if (mode == null) {
            throw ErreurMetier.regle("MODE_OBLIGATOIRE", "Indiquez le mode de paiement de la recharge.");
        }
        if (date == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date de la recharge.");
        }
        MouvementCarte m = new MouvementCarte();
        m.carteId = carteId;
        m.type = Type.RECHARGE;
        m.dateMouvement = date;
        m.mode = mode;
        m.montant = montant;
        m.reference = reference == null || reference.isBlank() ? null : reference.trim();
        return m;
    }

    public static MouvementCarte debit(long carteId, long journeeId, long stationId, LocalDate date, long produitId,
                                       BigDecimal litres, BigDecimal prixPompe, BigDecimal remise, String reference) {
        if (litres == null || litres.signum() <= 0 || litres.stripTrailingZeros().scale() > 2) {
            throw ErreurMetier.regle("LITRES_INVALIDES", "Les litres servis sont supérieurs à zéro, avec deux décimales au plus.");
        }
        BigDecimal r = remise == null ? BigDecimal.ZERO : remise;
        if (r.compareTo(prixPompe) >= 0) {
            throw ErreurMetier.regle("REMISE_INVALIDE", "La remise du client atteint le prix à la pompe.");
        }
        MouvementCarte m = new MouvementCarte();
        m.carteId = carteId;
        m.type = Type.DEBIT;
        m.dateMouvement = date;
        m.journeeId = journeeId;
        m.stationId = stationId;
        m.produitId = produitId;
        m.litres = litres;
        m.remiseUnitaire = r;
        m.prixUnitaire = prixPompe.subtract(r);
        m.montant = litres.multiply(m.prixUnitaire).setScale(0, RoundingMode.HALF_UP);
        m.montantPompe = litres.multiply(prixPompe).setScale(0, RoundingMode.HALF_UP);
        m.reference = reference == null || reference.isBlank() ? null : reference.trim();
        return m;
    }

    public Long getCarteId() {
        return carteId;
    }

    public Type getType() {
        return type;
    }

    public LocalDate getDateMouvement() {
        return dateMouvement;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public Mode getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getRemiseUnitaire() {
        return remiseUnitaire;
    }

    public BigDecimal getMontantPompe() {
        return montantPompe;
    }
}
