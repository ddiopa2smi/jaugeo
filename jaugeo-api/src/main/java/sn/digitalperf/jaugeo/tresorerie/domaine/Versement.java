package sn.digitalperf.jaugeo.tresorerie.domaine;

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

/**
 * Versement bancaire de la recette, justifie par un bordereau (BV). Saisi par le gerant, il est verifie
 * (ou rejete) par la comptabilite ; un versement rejete ne compte plus dans le solde du gerant.
 */
@Entity
@Table(schema = "tresorerie", name = "versement")
public class Versement extends EntiteAuditee {

    public enum Statut { SAISI, VERIFIE, REJETE }

    /** Donnees saisies du bordereau. */
    public record Donnees(LocalDate dateDepot, String banque, String referenceBv, BigDecimal montant, String deposant,
                          String observation) {
    }

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_depot", nullable = false)
    private LocalDate dateDepot;

    @Column(nullable = false)
    private String banque;

    @Column(name = "reference_bv", nullable = false)
    private String referenceBv;

    @Column(nullable = false)
    private BigDecimal montant;

    private String deposant;

    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.SAISI;

    @Column(name = "verifie_par")
    private String verifiePar;

    @Column(name = "verifie_le")
    private Instant verifieLe;

    @Column(name = "motif_rejet")
    private String motifRejet;

    protected Versement() {
    }

    public Versement(long stationId) {
        this.stationId = stationId;
    }

    public void renseigner(Donnees d, LocalDate aujourdhui) {
        exigerSaisi();
        if (d.dateDepot() == null || d.dateDepot().isAfter(aujourdhui)) {
            throw ErreurMetier.regle("DATE_DEPOT_INVALIDE", "La date de dépôt ne peut pas être dans le futur.");
        }
        if (d.montant() == null || d.montant().signum() <= 0 || d.montant().stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant est un nombre entier de francs, supérieur à zéro.");
        }
        if (d.banque() == null || d.banque().isBlank() || d.referenceBv() == null || d.referenceBv().isBlank()) {
            throw ErreurMetier.regle("BORDEREAU_INCOMPLET", "Indiquez la banque et la référence du bordereau.");
        }
        this.dateDepot = d.dateDepot();
        this.banque = d.banque().trim().replaceAll("\\s+", " ").toUpperCase();
        this.referenceBv = d.referenceBv().trim().replaceAll("\\s+", " ").toUpperCase();
        this.montant = d.montant();
        this.deposant = vide(d.deposant());
        this.observation = vide(d.observation());
    }

    public void verifier(String par, Instant le, boolean pieceJointe) {
        exigerSaisi();
        exigerAutreAuteur(par);
        if (!pieceJointe) {
            throw ErreurMetier.regle("PIECE_MANQUANTE", "Le bordereau n'est pas joint : il ne peut pas être vérifié.");
        }
        this.statut = Statut.VERIFIE;
        this.verifiePar = par;
        this.verifieLe = le;
    }

    public void rejeter(String motif, String par, Instant le) {
        exigerSaisi();
        exigerAutreAuteur(par);
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif du rejet.");
        }
        this.statut = Statut.REJETE;
        this.motifRejet = motif.trim();
        this.verifiePar = par;
        this.verifieLe = le;
    }

    public void exigerSaisi() {
        if (statut != Statut.SAISI) {
            throw ErreurMetier.regle("VERSEMENT_TRAITE", "Ce versement a déjà été " + (statut == Statut.VERIFIE ? "vérifié" : "rejeté")
                    + " par la comptabilité : il ne peut plus être modifié.");
        }
    }

    private void exigerAutreAuteur(String par) {
        if (par.equals(getCreePar())) {
            throw ErreurMetier.regle("SEPARATION_DES_TACHES", "L'auteur d'un versement ne peut pas le vérifier lui-même.");
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateDepot() {
        return dateDepot;
    }

    public String getBanque() {
        return banque;
    }

    public String getReferenceBv() {
        return referenceBv;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public String getDeposant() {
        return deposant;
    }

    public String getObservation() {
        return observation;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getVerifiePar() {
        return verifiePar;
    }

    public Instant getVerifieLe() {
        return verifieLe;
    }

    public String getMotifRejet() {
        return motifRejet;
    }
}
