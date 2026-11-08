package sn.digitalperf.jaugeo.depenses.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Depense payee sur la recette d'une journee. Dans la limite du plafond de sa nature, elle est autorisee d'emblee ;
 * au-dela, elle attend la decision d'un superviseur, qui ne peut pas etre l'auteur de la saisie.
 */
@Entity
@Table(schema = "depenses", name = "depense")
public class Depense extends EntiteAuditee {

    public enum Statut { A_VALIDER, AUTORISEE, REFUSEE }

    @Column(name = "journee_id", nullable = false, updatable = false)
    private Long journeeId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Column(name = "nature_id", nullable = false)
    private Long natureId;

    @Column(nullable = false)
    private String libelle;

    private String beneficiaire;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(name = "plafond_applique")
    private BigDecimal plafondApplique;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "decision_par")
    private String decisionPar;

    @Column(name = "decision_le")
    private Instant decisionLe;

    @Column(name = "motif_decision")
    private String motifDecision;

    protected Depense() {
    }

    public Depense(long journeeId, long stationId, LocalDate dateJour) {
        this.journeeId = journeeId;
        this.stationId = stationId;
        this.dateJour = dateJour;
    }

    /** (Re)saisie : le statut est recalcule au regard du plafond, une decision anterieure est oubliee. */
    public void renseigner(NatureDepense nature, String libelle, String beneficiaire, BigDecimal montant) {
        if (!nature.isActif() && !nature.getId().equals(natureId)) {
            throw ErreurMetier.regle("NATURE_INACTIVE", "La nature « " + nature.getLibelle() + " » n'est plus utilisée.");
        }
        if (libelle == null || libelle.isBlank()) {
            throw ErreurMetier.regle("LIBELLE_OBLIGATOIRE", "Décrivez la dépense (objet, facture...).");
        }
        if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant est un nombre entier de francs, supérieur à zéro.");
        }
        this.natureId = nature.getId();
        this.libelle = libelle.trim();
        this.beneficiaire = beneficiaire == null || beneficiaire.isBlank() ? null : beneficiaire.trim();
        this.montant = montant;
        this.plafondApplique = nature.getPlafond();
        this.statut = horsPlafond() ? Statut.A_VALIDER : Statut.AUTORISEE;
        this.decisionPar = null;
        this.decisionLe = null;
        this.motifDecision = null;
    }

    public boolean horsPlafond() {
        return plafondApplique != null && montant.compareTo(plafondApplique) > 0;
    }

    public void decider(boolean autoriser, String motif, String par, Instant le) {
        if (statut != Statut.A_VALIDER) {
            throw ErreurMetier.regle("DEPENSE_DEJA_DECIDEE", "Cette dépense n'attend pas de validation.");
        }
        if (par.equals(getCreePar()) || par.equals(getModifiePar())) {
            throw ErreurMetier.regle("SEPARATION_DES_TACHES", "L'auteur d'une dépense ne peut pas la valider lui-même.");
        }
        if (!autoriser && (motif == null || motif.isBlank())) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif du refus.");
        }
        this.statut = autoriser ? Statut.AUTORISEE : Statut.REFUSEE;
        this.motifDecision = motif == null || motif.isBlank() ? null : motif.trim();
        this.decisionPar = par;
        this.decisionLe = le;
    }

    public String resume() {
        return "Dépense « " + libelle + " » de " + Libelles.francs(montant);
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

    public Long getNatureId() {
        return natureId;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getBeneficiaire() {
        return beneficiaire;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public BigDecimal getPlafondApplique() {
        return plafondApplique;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getDecisionPar() {
        return decisionPar;
    }

    public Instant getDecisionLe() {
        return decisionLe;
    }

    public String getMotifDecision() {
        return motifDecision;
    }
}
