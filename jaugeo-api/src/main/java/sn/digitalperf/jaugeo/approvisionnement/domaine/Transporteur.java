package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Transporteur sous contrat : tolerance de manquant propre (sinon celle du reseau) et penalite forfaitaire par
 * livraison hors tolerance qui lui est imputee.
 */
@Entity
@Table(schema = "approvisionnement", name = "transporteur")
public class Transporteur extends EntiteAuditee {

    /** Donnees saisies du transporteur. */
    public record Donnees(String nom, String telephone, String contratReference, LocalDate contratDebut,
                          LocalDate contratFin, BigDecimal tolerancePct, BigDecimal penalite, Boolean actif,
                          String observation) {
    }

    private String nom;
    private String telephone;

    @Column(name = "contrat_reference")
    private String contratReference;

    @Column(name = "contrat_debut")
    private LocalDate contratDebut;

    @Column(name = "contrat_fin")
    private LocalDate contratFin;

    @Column(name = "tolerance_pct")
    private BigDecimal tolerancePct;

    private BigDecimal penalite;

    @Column(nullable = false)
    private boolean actif = true;

    private String observation;

    protected Transporteur() {
    }

    public Transporteur(String nom) {
        this.nom = nom;
    }

    public void renseigner(Donnees d) {
        if (d.nom() == null || d.nom().isBlank()) {
            throw ErreurMetier.regle("NOM_OBLIGATOIRE", "Indiquez le nom du transporteur.");
        }
        if (d.contratDebut() != null && d.contratFin() != null && d.contratFin().isBefore(d.contratDebut())) {
            throw ErreurMetier.regle("CONTRAT_INVALIDE", "La fin du contrat précède son début.");
        }
        if (d.tolerancePct() != null && (d.tolerancePct().signum() < 0 || d.tolerancePct().compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw ErreurMetier.regle("TOLERANCE_INVALIDE", "La tolérance de manquant est comprise entre 0 et 5 %.");
        }
        if (d.penalite() != null && d.penalite().signum() <= 0) {
            throw ErreurMetier.regle("PENALITE_INVALIDE", "La pénalité doit être positive (laissez vide : pas de pénalité).");
        }
        this.nom = d.nom().trim();
        this.telephone = Textes.texte(d.telephone());
        this.contratReference = Textes.texte(d.contratReference());
        this.contratDebut = d.contratDebut();
        this.contratFin = d.contratFin();
        this.tolerancePct = d.tolerancePct();
        this.penalite = d.penalite();
        this.actif = d.actif() == null || d.actif();
        this.observation = Textes.texte(d.observation());
    }

    public String getNom() {
        return nom;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getContratReference() {
        return contratReference;
    }

    public LocalDate getContratDebut() {
        return contratDebut;
    }

    public LocalDate getContratFin() {
        return contratFin;
    }

    public BigDecimal getTolerancePct() {
        return tolerancePct;
    }

    public BigDecimal getPenalite() {
        return penalite;
    }

    public boolean isActif() {
        return actif;
    }

    public String getObservation() {
        return observation;
    }
}
