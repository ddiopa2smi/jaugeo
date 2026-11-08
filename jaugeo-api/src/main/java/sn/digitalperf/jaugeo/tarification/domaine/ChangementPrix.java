package sn.digitalperf.jaugeo.tarification.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Decision de prix : un ensemble de nouveaux prix prenant effet au meme instant. */
@Entity
@Table(schema = "tarification", name = "changement_prix")
public class ChangementPrix extends EntiteAuditee {

    @Column(nullable = false, updatable = false)
    private Instant debut;

    @Column(nullable = false, updatable = false)
    private String motif;

    @Column(updatable = false)
    private String reference;

    @Column(nullable = false)
    private boolean annule;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par")
    private String annulePar;

    protected ChangementPrix() {
    }

    public ChangementPrix(Instant debut, String motif, String reference) {
        if (debut == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date et l'heure d'effet des nouveaux prix.");
        }
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif du changement de prix.");
        }
        this.debut = debut;
        this.motif = motif.trim();
        this.reference = reference == null || reference.isBlank() ? null : reference.trim();
    }

    /** Seul un changement qui n'est pas encore entre en vigueur peut etre annule. */
    public void annuler(Instant maintenant, String auteur) {
        if (annule) {
            throw ErreurMetier.conflit("DEJA_ANNULE", "Ce changement de prix est déjà annulé.");
        }
        if (!debut.isAfter(maintenant)) {
            throw ErreurMetier.regle("CHANGEMENT_EN_VIGUEUR",
                    "Ces prix sont déjà entrés en vigueur : ils ne peuvent plus être annulés. Saisissez un nouveau changement de prix.");
        }
        this.annule = true;
        this.annuleLe = maintenant;
        this.annulePar = auteur;
    }

    public Instant getDebut() {
        return debut;
    }

    public String getMotif() {
        return motif;
    }

    public String getReference() {
        return reference;
    }

    public boolean isAnnule() {
        return annule;
    }

    public Instant getAnnuleLe() {
        return annuleLe;
    }

    public String getAnnulePar() {
        return annulePar;
    }
}
