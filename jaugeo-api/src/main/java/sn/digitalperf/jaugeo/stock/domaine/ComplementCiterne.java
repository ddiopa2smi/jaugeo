package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Manquant de livraison a la charge du transporteur, suivi jusqu'a son reglement. */
@Entity
@Table(schema = "stock", name = "complement_citerne")
public class ComplementCiterne extends EntiteAuditee {

    public enum Statut { A_REGLER, REGLE }

    @Column(name = "reception_id", nullable = false, updatable = false)
    private Long receptionId;

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(nullable = false, updatable = false)
    private BigDecimal litres;

    @Column(name = "prix_unitaire", updatable = false)
    private BigDecimal prixUnitaire;

    @Column(updatable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.A_REGLER;

    @Column(name = "reference_reglement")
    private String referenceReglement;

    @Column(name = "regle_le")
    private LocalDate regleLe;

    @Column(name = "regle_par")
    private String reglePar;

    protected ComplementCiterne() {
    }

    public ComplementCiterne(Reception r, BigDecimal litres, BigDecimal prixUnitaire) {
        this.receptionId = r.getId();
        this.journeeId = r.getJourneeId();
        this.stationId = r.getStationId();
        this.dateJour = r.getDateJour();
        this.litres = litres;
        this.prixUnitaire = prixUnitaire;
        this.montant = prixUnitaire == null ? null : litres.multiply(prixUnitaire).setScale(0, java.math.RoundingMode.HALF_UP);
    }

    public void regler(String reference, LocalDate le, String par) {
        if (statut == Statut.REGLE) {
            throw ErreurMetier.regle("COMPLEMENT_DEJA_REGLE", "Ce complément citerne est déjà réglé.");
        }
        if (reference == null || reference.isBlank()) {
            throw ErreurMetier.regle("REFERENCE_OBLIGATOIRE", "Indiquez la référence du règlement (reçu, virement...).");
        }
        if (le == null || le.isBefore(dateJour)) {
            throw ErreurMetier.regle("DATE_REGLEMENT_INVALIDE", "Le règlement ne peut pas précéder la livraison.");
        }
        this.statut = Statut.REGLE;
        this.referenceReglement = reference.trim();
        this.regleLe = le;
        this.reglePar = par;
    }

    public Long getReceptionId() {
        return receptionId;
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

    public BigDecimal getLitres() {
        return litres;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getReferenceReglement() {
        return referenceReglement;
    }

    public LocalDate getRegleLe() {
        return regleLe;
    }

    public String getReglePar() {
        return reglePar;
    }
}
