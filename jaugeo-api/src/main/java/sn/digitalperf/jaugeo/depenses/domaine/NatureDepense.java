package sn.digitalperf.jaugeo.depenses.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Nature de depense de station ; au-dela du plafond, une depense attend la validation du superviseur. */
@Entity
@Table(schema = "depenses", name = "nature_depense")
public class NatureDepense extends EntiteAuditee {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    private BigDecimal plafond;

    @Column(nullable = false)
    private boolean actif = true;

    protected NatureDepense() {
    }

    public NatureDepense(String code) {
        if (code == null || !code.trim().matches("[A-Za-z0-9_]{2,30}")) {
            throw ErreurMetier.regle("CODE_INVALIDE", "Code de 2 à 30 lettres, chiffres ou « _ ».");
        }
        this.code = code.trim().toUpperCase();
    }

    public void modifier(String libelle, BigDecimal plafond, boolean actif) {
        if (libelle == null || libelle.isBlank()) {
            throw ErreurMetier.regle("LIBELLE_OBLIGATOIRE", "Indiquez le libellé de la nature de dépense.");
        }
        if (plafond != null && plafond.signum() <= 0) {
            throw ErreurMetier.regle("PLAFOND_INVALIDE", "Le plafond doit être positif (laissez-le vide pour aucun plafond).");
        }
        this.libelle = libelle.trim();
        this.plafond = plafond;
        this.actif = actif;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public BigDecimal getPlafond() {
        return plafond;
    }

    public boolean isActif() {
        return actif;
    }
}
