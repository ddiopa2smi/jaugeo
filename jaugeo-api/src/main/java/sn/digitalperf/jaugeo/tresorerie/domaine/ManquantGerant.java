package sn.digitalperf.jaugeo.tresorerie.domaine;

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
 * Manquant de caisse constate a un arrete : dette du gerant, eventuellement reconnue par ecrit et etalee sur un
 * echeancier, reglee en une ou plusieurs fois (remboursement, retenue sur salaire) ou passee en perte.
 */
@Entity
@Table(schema = "tresorerie", name = "manquant_gerant")
public class ManquantGerant extends EntiteAuditee {

    public enum Statut { OUVERT, SOLDE }

    /** REMBOURSEMENT par le gerant, RETENUE sur salaire, PERTE passee en charge par la direction. */
    public enum Mode { REMBOURSEMENT, RETENUE, PERTE }

    @Column(name = "arrete_id", nullable = false, updatable = false)
    private Long arreteId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Column(updatable = false)
    private String responsable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.OUVERT;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_reglement")
    private Mode modeReglement;

    @Column(name = "reference_reglement")
    private String referenceReglement;

    @Column(name = "regle_le")
    private LocalDate regleLe;

    @Column(name = "regle_par")
    private String reglePar;

    @Column(name = "montant_regle", nullable = false)
    private BigDecimal montantRegle = BigDecimal.ZERO;

    @Column(name = "reconnaissance_reference")
    private String reconnaissanceReference;

    @Column(name = "reconnu_le")
    private LocalDate reconnuLe;

    protected ManquantGerant() {
    }

    public ManquantGerant(ArreteCaisse a) {
        this.arreteId = a.getId();
        this.stationId = a.getStationId();
        this.dateJour = a.getDateJour();
        this.montant = a.getEcart().negate();
        this.responsable = a.getResponsable();
    }

    /** Solde le reste du manquant en une fois ; renvoie le montant regle. */
    public BigDecimal regler(Mode mode, String reference, LocalDate le, String par) {
        BigDecimal reste = reste();
        payer(reste, mode, reference, le, par);
        return reste;
    }

    public BigDecimal reste() {
        return montant.subtract(montantRegle);
    }

    /** Reconnaissance de dette signee par le gerant (reference du document). */
    public void reconnaitre(String reference, LocalDate le) {
        exigerOuvert();
        if (reference == null || reference.isBlank()) {
            throw ErreurMetier.regle("REFERENCE_OBLIGATOIRE", "Indiquez la référence de la reconnaissance de dette signée.");
        }
        if (le == null || le.isBefore(dateJour)) {
            throw ErreurMetier.regle("DATE_INVALIDE", "La reconnaissance de dette ne peut pas précéder le constat du manquant.");
        }
        this.reconnaissanceReference = reference.trim();
        this.reconnuLe = le;
    }

    /** Reglement partiel ou total ; le manquant est solde quand le cumul atteint son montant. */
    public void payer(BigDecimal somme, Mode mode, String reference, LocalDate le, String par) {
        exigerOuvert();
        if (somme == null || somme.signum() <= 0 || somme.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant réglé est un nombre entier de francs, supérieur à zéro.");
        }
        if (somme.compareTo(reste()) > 0) {
            throw ErreurMetier.regle("MONTANT_SUPERIEUR_AU_RESTE", "Le montant réglé dépasse le reste dû par le gérant.");
        }
        if (mode == null) {
            throw ErreurMetier.regle("MODE_OBLIGATOIRE", "Indiquez le mode de règlement : remboursement, retenue ou perte.");
        }
        if (le == null || le.isBefore(dateJour)) {
            throw ErreurMetier.regle("DATE_REGLEMENT_INVALIDE", "Le règlement ne peut pas précéder le constat du manquant.");
        }
        if (mode != Mode.PERTE && (reference == null || reference.isBlank())) {
            throw ErreurMetier.regle("REFERENCE_OBLIGATOIRE", "Indiquez la référence du règlement (reçu, BV, bulletin de paie...).");
        }
        this.montantRegle = montantRegle.add(somme);
        if (reste().signum() == 0) {
            this.statut = Statut.SOLDE;
            this.modeReglement = mode;
            this.referenceReglement = reference == null || reference.isBlank() ? null : reference.trim();
            this.regleLe = le;
            this.reglePar = par;
        }
    }

    public void exigerOuvert() {
        if (statut == Statut.SOLDE) {
            throw ErreurMetier.regle("MANQUANT_DEJA_SOLDE", "Ce manquant est déjà soldé.");
        }
    }

    public BigDecimal getMontantRegle() {
        return montantRegle;
    }

    public String getReconnaissanceReference() {
        return reconnaissanceReference;
    }

    public LocalDate getReconnuLe() {
        return reconnuLe;
    }

    public Long getArreteId() {
        return arreteId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public String getResponsable() {
        return responsable;
    }

    public Statut getStatut() {
        return statut;
    }

    public Mode getModeReglement() {
        return modeReglement;
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
