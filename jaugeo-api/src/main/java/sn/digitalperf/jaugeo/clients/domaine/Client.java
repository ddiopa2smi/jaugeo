package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/**
 * Client en compte du reseau. Le mode de recouvrement dit qui encaisse ses bons : la direction (bons deduits de la
 * recette, recouvres par le siege) ou le gerant (creance que le gerant recouvre a la station). Sans plafond, l'encours
 * n'est pas limite ; sans delai de paiement, aucune echeance ne bloque ses bons.
 */
@Entity
@Table(schema = "clients", name = "client")
public class Client extends EntiteAuditee {

    public enum Recouvrement { DIRECTION, GERANT }

    /** Contrat, coordonnees et garanties du client (tous facultatifs). */
    public record Contrat(String adresse, String email, String ninea, String reference, LocalDate debut, LocalDate fin,
                          Integer delaiPaiement, BigDecimal cautionMontant, String cautionBanque, LocalDate cautionEcheance) {

        public static final Contrat AUCUN = new Contrat(null, null, null, null, null, null, null, null, null, null);
    }

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

    private String adresse;

    private String email;

    private String ninea;

    @Column(name = "contrat_reference")
    private String contratReference;

    @Column(name = "contrat_debut")
    private LocalDate contratDebut;

    @Column(name = "contrat_fin")
    private LocalDate contratFin;

    @Column(name = "delai_paiement")
    private Integer delaiPaiement;

    @Column(name = "caution_montant")
    private BigDecimal cautionMontant;

    @Column(name = "caution_banque")
    private String cautionBanque;

    @Column(name = "caution_echeance")
    private LocalDate cautionEcheance;

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

    public void renseignerContrat(Contrat c) {
        if (c.debut() != null && c.fin() != null && c.fin().isBefore(c.debut())) {
            throw ErreurMetier.regle("CONTRAT_INVALIDE", "La fin du contrat précède son début.");
        }
        if (c.delaiPaiement() != null && (c.delaiPaiement() < 0 || c.delaiPaiement() > 365)) {
            throw ErreurMetier.regle("DELAI_INVALIDE", "Le délai de paiement est compris entre 0 et 365 jours.");
        }
        if (c.cautionMontant() != null && (c.cautionMontant().signum() <= 0 || c.cautionMontant().stripTrailingZeros().scale() > 0)) {
            throw ErreurMetier.regle("CAUTION_INVALIDE", "La caution est un nombre entier de francs, supérieur à zéro.");
        }
        String mail = vide(c.email());
        if (mail != null && !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw ErreurMetier.regle("EMAIL_INVALIDE", "L'adresse e-mail n'est pas valide.");
        }
        this.adresse = vide(c.adresse());
        this.email = mail;
        this.ninea = vide(c.ninea());
        this.contratReference = vide(c.reference());
        this.contratDebut = c.debut();
        this.contratFin = c.fin();
        this.delaiPaiement = c.delaiPaiement();
        this.cautionMontant = c.cautionMontant();
        this.cautionBanque = vide(c.cautionBanque());
        this.cautionEcheance = c.cautionEcheance();
    }

    public Contrat contrat() {
        return new Contrat(adresse, email, ninea, contratReference, contratDebut, contratFin, delaiPaiement, cautionMontant,
                cautionBanque, cautionEcheance);
    }

    /** Le contrat couvre-t-il cette date ? (sans dates de contrat : oui) */
    public boolean sousContrat(LocalDate date) {
        return (contratDebut == null || !date.isBefore(contratDebut)) && (contratFin == null || !date.isAfter(contratFin));
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

    public Integer getDelaiPaiement() {
        return delaiPaiement;
    }

    public String getAdresse() {
        return adresse;
    }

    public String getEmail() {
        return email;
    }

    public String getNinea() {
        return ninea;
    }
}
