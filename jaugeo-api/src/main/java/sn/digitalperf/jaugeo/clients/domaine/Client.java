package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Client en compte du reseau. Le mode de recouvrement dit qui encaisse ses bons : la direction (bons deduits de la
 * recette, recouvres par le siege) ou le gerant (creance que le gerant recouvre a la station). Sans plafond, l'encours
 * n'est pas limite.
 */
@Entity
@Table(schema = "clients", name = "client")
public class Client extends EntiteAuditee {

    public enum Recouvrement { DIRECTION, GERANT }

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String nom;

    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recouvrement recouvrement;

    private BigDecimal plafond;

    @Column(nullable = false)
    private boolean actif = true;

    private String observation;

    protected Client() {
    }

    public Client(String code) {
        if (code == null || code.isBlank()) {
            throw ErreurMetier.regle("CODE_OBLIGATOIRE", "Donnez un code au client.");
        }
        this.code = code.trim().toUpperCase();
    }

    public void modifier(String nom, String telephone, Recouvrement recouvrement, BigDecimal plafond, boolean actif,
                         String observation) {
        if (nom == null || nom.isBlank()) {
            throw ErreurMetier.regle("NOM_OBLIGATOIRE", "Indiquez le nom du client.");
        }
        if (recouvrement == null) {
            throw ErreurMetier.regle("RECOUVREMENT_OBLIGATOIRE", "Indiquez qui recouvre les bons du client : la direction ou le gérant.");
        }
        if (plafond != null && (plafond.signum() <= 0 || plafond.stripTrailingZeros().scale() > 0)) {
            throw ErreurMetier.regle("PLAFOND_INVALIDE", "Le plafond est un nombre entier de francs, supérieur à zéro.");
        }
        this.nom = nom.trim();
        this.telephone = vide(telephone);
        this.recouvrement = recouvrement;
        this.plafond = plafond;
        this.actif = actif;
        this.observation = vide(observation);
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public String getTelephone() {
        return telephone;
    }

    public Recouvrement getRecouvrement() {
        return recouvrement;
    }

    public BigDecimal getPlafond() {
        return plafond;
    }

    public boolean isActif() {
        return actif;
    }

    public String getObservation() {
        return observation;
    }
}
