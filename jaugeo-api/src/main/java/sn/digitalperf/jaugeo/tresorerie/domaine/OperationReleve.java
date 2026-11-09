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
 * Operation au credit d'un releve. Sa cle (empreinte des champs lus) interdit de la reprendre quand deux
 * fichiers se chevauchent. Sur un releve bancaire, elle se lettre avec le versement (BV) qu'elle constate.
 */
@Entity
@Table(schema = "tresorerie", name = "operation_releve")
public class OperationReleve extends EntiteAuditee {

    public enum Lettrage { AUTO, MANUEL }

    @Column(name = "releve_id", nullable = false, updatable = false)
    private Long releveId;

    @Column(name = "format_id", nullable = false, updatable = false)
    private Long formatId;

    @Column(name = "station_id", updatable = false)
    private Long stationId;

    @Column(name = "date_operation", nullable = false, updatable = false)
    private LocalDate dateOperation;

    @Column(nullable = false, updatable = false)
    private String libelle;

    @Column(updatable = false)
    private String reference;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Column(nullable = false, updatable = false)
    private String cle;

    @Column(name = "versement_id")
    private Long versementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_lettrage")
    private Lettrage modeLettrage;

    @Column(name = "lettre_par")
    private String lettrePar;

    @Column(name = "lettre_le")
    private Instant lettreLe;

    protected OperationReleve() {
    }

    public OperationReleve(long releveId, long formatId, Long stationId, LocalDate dateOperation, String libelle,
                           String reference, BigDecimal montant, String cle) {
        this.releveId = releveId;
        this.formatId = formatId;
        this.stationId = stationId;
        this.dateOperation = dateOperation;
        this.libelle = libelle;
        this.reference = reference;
        this.montant = montant;
        this.cle = cle;
    }

    public void lettrer(Versement v, Lettrage mode, String par, Instant le) {
        if (versementId != null) {
            throw ErreurMetier.conflit("DEJA_LETTREE", "Cette opération est déjà rapprochée d'un versement.");
        }
        if (v.getStatut() == Versement.Statut.REJETE) {
            throw ErreurMetier.regle("VERSEMENT_REJETE", "Un versement rejeté ne se rapproche pas du relevé.");
        }
        if (v.getMontant().compareTo(montant) != 0) {
            throw ErreurMetier.regle("MONTANTS_DIFFERENTS", "Le versement et l'opération du relevé n'ont pas le même montant.");
        }
        this.versementId = v.getId();
        this.modeLettrage = mode;
        this.lettrePar = par;
        this.lettreLe = le;
    }

    public void delettrer() {
        this.versementId = null;
        this.modeLettrage = null;
        this.lettrePar = null;
        this.lettreLe = null;
    }

    public Long getReleveId() {
        return releveId;
    }

    public Long getFormatId() {
        return formatId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateOperation() {
        return dateOperation;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public String getCle() {
        return cle;
    }

    public Long getVersementId() {
        return versementId;
    }

    public Lettrage getModeLettrage() {
        return modeLettrage;
    }

    public String getLettrePar() {
        return lettrePar;
    }

    public Instant getLettreLe() {
        return lettreLe;
    }
}
