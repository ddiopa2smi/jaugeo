package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Facture d'un fournisseur, une ligne par BL. Controlee contre le BL, la quantite recue et le prix convenu,
 * elle passe BON_A_PAYER ou EN_LITIGE (motif) ; une facture en litige peut etre corrigee puis acceptee.
 */
@Entity
@Table(schema = "approvisionnement", name = "facture_fournisseur")
public class FactureFournisseur extends EntiteAuditee {

    public enum Statut { SAISIE, BON_A_PAYER, EN_LITIGE, ANNULEE }

    @Column(name = "fournisseur_id", nullable = false)
    private Long fournisseurId;

    @Column(nullable = false)
    private String numero;

    @Column(name = "date_facture", nullable = false)
    private LocalDate dateFacture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.SAISIE;

    @Column(name = "motif_decision")
    private String motifDecision;

    @Column(name = "decide_par")
    private String decidePar;

    @Column(name = "decide_le")
    private Instant decideLe;

    private String observation;

    protected FactureFournisseur() {
    }

    public FactureFournisseur(long fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public void renseigner(String numero, LocalDate dateFacture, String observation) {
        exigerModifiable();
        if (numero == null || numero.isBlank()) {
            throw ErreurMetier.regle("NUMERO_OBLIGATOIRE", "Indiquez le numéro de la facture.");
        }
        if (dateFacture == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date de la facture.");
        }
        this.numero = numero.trim().toUpperCase();
        this.dateFacture = dateFacture;
        this.observation = Textes.texte(observation);
    }

    /** Bon a payer (motif exige si le controle releve des anomalies) ou litige (motif toujours exige). */
    public void decider(boolean bonAPayer, String motif, boolean anomalies, String par, Instant le) {
        exigerModifiable();
        String m = Textes.texte(motif);
        if (!bonAPayer && m == null) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif du litige.");
        }
        if (bonAPayer && anomalies && m == null) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Le contrôle relève des anomalies : justifiez le bon à payer.");
        }
        this.statut = bonAPayer ? Statut.BON_A_PAYER : Statut.EN_LITIGE;
        this.motifDecision = m;
        this.decidePar = par;
        this.decideLe = le;
    }

    public void annuler(String motif, String par, Instant le) {
        exigerModifiable();
        String m = Textes.texte(motif);
        if (m == null) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif de l'annulation.");
        }
        this.statut = Statut.ANNULEE;
        this.motifDecision = m;
        this.decidePar = par;
        this.decideLe = le;
    }

    /** Une facture bon a payer ou annulee est definitive ; saisie ou en litige, elle se corrige. */
    public void exigerModifiable() {
        if (statut == Statut.BON_A_PAYER || statut == Statut.ANNULEE) {
            throw ErreurMetier.conflit("FACTURE_FIGEE", "La facture " + numero + " est "
                    + (statut == Statut.ANNULEE ? "annulée" : "déjà bonne à payer") + " : elle ne peut plus changer.");
        }
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public String getNumero() {
        return numero;
    }

    public LocalDate getDateFacture() {
        return dateFacture;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getMotifDecision() {
        return motifDecision;
    }

    public String getDecidePar() {
        return decidePar;
    }

    public Instant getDecideLe() {
        return decideLe;
    }

    public String getObservation() {
        return observation;
    }
}
