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
 * Tournee citerne : chargee a un depot, elle livre une ou plusieurs stations. Suivi COMMANDEE -> CHARGEE (BL de
 * chaque livraison) -> EN_ROUTE ; la tournee est depotee quand toutes ses livraisons sont receptionnees.
 */
@Entity
@Table(schema = "approvisionnement", name = "tournee")
public class Tournee extends EntiteAuditee {

    public enum Statut { COMMANDEE, CHARGEE, EN_ROUTE, ANNULEE }

    /** Donnees de planification. */
    public record Plan(long fournisseurId, long transporteurId, Long citerneId, String immatriculation, String chauffeur,
                       LocalDate dateChargementPrevue, LocalDate dateLivraisonPrevue, String observation) {
    }

    @Column(nullable = false, updatable = false)
    private String numero;

    @Column(name = "fournisseur_id", nullable = false)
    private Long fournisseurId;

    @Column(name = "transporteur_id", nullable = false)
    private Long transporteurId;

    @Column(name = "citerne_id")
    private Long citerneId;

    private String immatriculation;
    private String chauffeur;

    @Column(name = "date_chargement_prevue", nullable = false)
    private LocalDate dateChargementPrevue;

    @Column(name = "date_livraison_prevue", nullable = false)
    private LocalDate dateLivraisonPrevue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.COMMANDEE;

    @Column(name = "charge_le")
    private LocalDate chargeLe;

    private String scelles;

    @Column(name = "parti_le")
    private Instant partiLe;

    @Column(name = "motif_annulation")
    private String motifAnnulation;

    private String observation;

    protected Tournee() {
    }

    public Tournee(String numero) {
        this.numero = numero;
    }

    public void planifier(Plan p) {
        exigerModifiable();
        if (p.dateChargementPrevue() == null || p.dateLivraisonPrevue() == null) {
            throw ErreurMetier.regle("DATES_OBLIGATOIRES", "Indiquez les dates de chargement et de livraison prévues.");
        }
        if (p.dateLivraisonPrevue().isBefore(p.dateChargementPrevue())) {
            throw ErreurMetier.regle("DATES_INVALIDES", "La livraison ne peut pas précéder le chargement.");
        }
        if (statut != Statut.COMMANDEE && (p.fournisseurId() != fournisseurId)) {
            throw ErreurMetier.regle("TOURNEE_CHARGEE", "La citerne est déjà chargée : le dépôt ne peut plus changer.");
        }
        this.fournisseurId = p.fournisseurId();
        this.transporteurId = p.transporteurId();
        this.citerneId = p.citerneId();
        this.immatriculation = Textes.majuscules(p.immatriculation());
        this.chauffeur = Textes.texte(p.chauffeur());
        this.dateChargementPrevue = p.dateChargementPrevue();
        this.dateLivraisonPrevue = p.dateLivraisonPrevue();
        this.observation = Textes.texte(p.observation());
    }

    /** Chargement au depot (ou correction du chargement tant que la citerne n'est pas partie). */
    public void charger(LocalDate le, String scelles) {
        if (statut != Statut.COMMANDEE && statut != Statut.CHARGEE) {
            throw ErreurMetier.conflit("TOURNEE_STATUT", "Seule une tournée commandée ou chargée peut enregistrer son chargement.");
        }
        if (le == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date de chargement.");
        }
        this.chargeLe = le;
        this.scelles = Textes.texte(scelles);
        this.statut = Statut.CHARGEE;
    }

    public void partir(Instant le) {
        if (statut != Statut.CHARGEE) {
            throw ErreurMetier.conflit("TOURNEE_STATUT", "La citerne doit être chargée avant de partir.");
        }
        this.partiLe = le;
        this.statut = Statut.EN_ROUTE;
    }

    public void annuler(String motif) {
        exigerModifiable();
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif de l'annulation.");
        }
        this.motifAnnulation = motif.trim();
        this.statut = Statut.ANNULEE;
    }

    public void exigerModifiable() {
        if (statut == Statut.ANNULEE) {
            throw ErreurMetier.conflit("TOURNEE_ANNULEE", "La tournée " + numero + " est annulée.");
        }
    }

    public String getNumero() {
        return numero;
    }

    public Long getFournisseurId() {
        return fournisseurId;
    }

    public Long getTransporteurId() {
        return transporteurId;
    }

    public Long getCiterneId() {
        return citerneId;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public String getChauffeur() {
        return chauffeur;
    }

    public LocalDate getDateChargementPrevue() {
        return dateChargementPrevue;
    }

    public LocalDate getDateLivraisonPrevue() {
        return dateLivraisonPrevue;
    }

    public Statut getStatut() {
        return statut;
    }

    public LocalDate getChargeLe() {
        return chargeLe;
    }

    public String getScelles() {
        return scelles;
    }

    public Instant getPartiLe() {
        return partiLe;
    }

    public String getMotifAnnulation() {
        return motifAnnulation;
    }

    public String getObservation() {
        return observation;
    }
}
