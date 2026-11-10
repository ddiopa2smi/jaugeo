package sn.digitalperf.jaugeo.approvisionnement.domaine;

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
 * Penalite forfaitaire du contrat de transport pour une livraison hors tolerance imputee au transporteur, en plus
 * du complement citerne. Creee a la cloture de la journee de reception, suivie jusqu'a son reglement.
 */
@Entity
@Table(schema = "approvisionnement", name = "penalite")
public class Penalite extends EntiteAuditee {

    public enum Statut { A_REGLER, REGLE }

    @Column(name = "transporteur_id", nullable = false, updatable = false)
    private Long transporteurId;

    @Column(name = "livraison_id", nullable = false, updatable = false)
    private Long livraisonId;

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(name = "manquant_litres", nullable = false, updatable = false)
    private BigDecimal manquantLitres;

    @Column(name = "manquant_pct", nullable = false, updatable = false)
    private BigDecimal manquantPct;

    @Column(name = "tolerance_pct", nullable = false, updatable = false)
    private BigDecimal tolerancePct;

    @Column(nullable = false, updatable = false)
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

    protected Penalite() {
    }

    public Penalite(long transporteurId, long livraisonId, long journeeId, LocalDate dateJour, BigDecimal manquantLitres,
                    BigDecimal manquantPct, BigDecimal tolerancePct, BigDecimal montant) {
        this.transporteurId = transporteurId;
        this.livraisonId = livraisonId;
        this.journeeId = journeeId;
        this.dateJour = dateJour;
        this.manquantLitres = manquantLitres;
        this.manquantPct = manquantPct;
        this.tolerancePct = tolerancePct;
        this.montant = montant;
    }

    public void regler(String reference, LocalDate le, String par) {
        if (statut == Statut.REGLE) {
            throw ErreurMetier.regle("PENALITE_DEJA_REGLEE", "Cette pénalité est déjà réglée.");
        }
        if (reference == null || reference.isBlank()) {
            throw ErreurMetier.regle("REFERENCE_OBLIGATOIRE", "Indiquez la référence du règlement (retenue, reçu, virement...).");
        }
        if (le == null || le.isBefore(dateJour)) {
            throw ErreurMetier.regle("DATE_REGLEMENT_INVALIDE", "Le règlement ne peut pas précéder la livraison.");
        }
        this.statut = Statut.REGLE;
        this.referenceReglement = reference.trim();
        this.regleLe = le;
        this.reglePar = par;
    }

    public Long getTransporteurId() {
        return transporteurId;
    }

    public Long getLivraisonId() {
        return livraisonId;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public BigDecimal getManquantLitres() {
        return manquantLitres;
    }

    public BigDecimal getManquantPct() {
        return manquantPct;
    }

    public BigDecimal getTolerancePct() {
        return tolerancePct;
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
