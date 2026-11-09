package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Format d'un releve bancaire ou marchand, parametre une fois par banque ou operateur : numeros de colonnes
 * (a partir de 1), separateur du CSV, format des dates et nombre de lignes d'en-tete a ignorer.
 */
@Entity
@Table(schema = "tresorerie", name = "format_releve")
public class FormatReleve extends EntiteAuditee {

    /** Banque : credits a lettrer avec les versements ; Wave, Orange Money, carte : a comparer aux encaissements declares. */
    public enum Nature { BANQUE, WAVE, ORANGE_MONEY, CARTE }

    public record Parametres(String code, String libelle, Nature nature, String separateur, String formatDate,
                             int lignesIgnorees, int colDate, int colLibelle, Integer colReference, int colCredit,
                             boolean actif) {
    }

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Nature nature;

    @Column(nullable = false)
    private String separateur;

    @Column(name = "format_date", nullable = false)
    private String formatDate;

    @Column(name = "lignes_ignorees", nullable = false)
    private int lignesIgnorees;

    @Column(name = "col_date", nullable = false)
    private int colDate;

    @Column(name = "col_libelle", nullable = false)
    private int colLibelle;

    @Column(name = "col_reference")
    private Integer colReference;

    @Column(name = "col_credit", nullable = false)
    private int colCredit;

    @Column(nullable = false)
    private boolean actif = true;

    protected FormatReleve() {
    }

    public FormatReleve(Parametres p) {
        parametrer(p);
    }

    public void parametrer(Parametres p) {
        this.code = p.code().trim().toUpperCase();
        this.libelle = p.libelle().trim();
        this.nature = p.nature();
        this.separateur = p.separateur();
        this.formatDate = p.formatDate().trim();
        this.lignesIgnorees = p.lignesIgnorees();
        this.colDate = p.colDate();
        this.colLibelle = p.colLibelle();
        this.colReference = p.colReference();
        this.colCredit = p.colCredit();
        this.actif = p.actif();
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public Nature getNature() {
        return nature;
    }

    public String getSeparateur() {
        return separateur;
    }

    public String getFormatDate() {
        return formatDate;
    }

    public int getLignesIgnorees() {
        return lignesIgnorees;
    }

    public int getColDate() {
        return colDate;
    }

    public int getColLibelle() {
        return colLibelle;
    }

    public Integer getColReference() {
        return colReference;
    }

    public int getColCredit() {
        return colCredit;
    }

    public boolean isActif() {
        return actif;
    }
}
