package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Reglement (partiel ou total) d'un manquant : remboursement, retenue sur salaire ou perte. */
@Entity
@Table(schema = "tresorerie", name = "paiement_manquant")
public class PaiementManquant extends EntiteAuditee {

    @Column(name = "manquant_id", nullable = false, updatable = false)
    private Long manquantId;

    @Column(name = "date_paiement", nullable = false, updatable = false)
    private LocalDate datePaiement;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ManquantGerant.Mode mode;

    @Column(updatable = false)
    private String reference;

    protected PaiementManquant() {
    }

    public PaiementManquant(long manquantId, LocalDate datePaiement, BigDecimal montant, ManquantGerant.Mode mode,
                            String reference) {
        this.manquantId = manquantId;
        this.datePaiement = datePaiement;
        this.montant = montant;
        this.mode = mode;
        this.reference = reference == null || reference.isBlank() ? null : reference.trim();
    }

    public Long getManquantId() {
        return manquantId;
    }

    public LocalDate getDatePaiement() {
        return datePaiement;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public ManquantGerant.Mode getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }
}
