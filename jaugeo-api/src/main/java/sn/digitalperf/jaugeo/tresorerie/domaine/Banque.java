package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Banque ou la recette peut etre deposee. La table est pre-remplie avec les banques agreees au Senegal ;
 * seules les banques actives (celles de la compagnie) sont proposees a la saisie d'un versement.
 */
@Entity
@Table(schema = "tresorerie", name = "banque")
public class Banque extends EntiteAuditee {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private boolean actif = true;

    protected Banque() {
    }

    public Banque(String code, String nom) {
        this.code = normaliser(code);
        this.nom = nom.trim().replaceAll("\\s+", " ");
    }

    /** Meme ecriture que la banque portee par un versement : espaces reduits, majuscules. */
    public static String normaliser(String code) {
        return code.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    public void activer(boolean actif) {
        this.actif = actif;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public boolean isActif() {
        return actif;
    }
}
