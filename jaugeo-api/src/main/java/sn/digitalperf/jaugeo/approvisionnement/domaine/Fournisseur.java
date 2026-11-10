package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Fournisseur de carburant (depot de chargement). Le delai de livraison du depot principal sert aux propositions
 * de commande ; un fournisseur qui facture a 15 °C voit ses quantites comparees a la quantite a 15 °C du BL.
 */
@Entity
@Table(schema = "approvisionnement", name = "fournisseur")
public class Fournisseur extends EntiteAuditee {

    private String nom;
    private String telephone;
    private String adresse;

    @Column(name = "delai_livraison", nullable = false)
    private int delaiLivraison = 1;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "facture_a_15", nullable = false)
    private boolean factureA15;

    @Column(nullable = false)
    private boolean actif = true;

    protected Fournisseur() {
    }

    public Fournisseur(String nom) {
        this.nom = nom;
    }

    public void renseigner(String nom, String telephone, String adresse, Integer delaiLivraison, Boolean principal,
                           Boolean factureA15, Boolean actif) {
        if (nom == null || nom.isBlank()) {
            throw ErreurMetier.regle("NOM_OBLIGATOIRE", "Indiquez le nom du fournisseur.");
        }
        if (delaiLivraison == null || delaiLivraison < 0 || delaiLivraison > 30) {
            throw ErreurMetier.regle("DELAI_INVALIDE", "Le délai de livraison est compris entre 0 et 30 jours.");
        }
        this.nom = nom.trim();
        this.telephone = Textes.texte(telephone);
        this.adresse = Textes.texte(adresse);
        this.delaiLivraison = delaiLivraison;
        this.principal = Boolean.TRUE.equals(principal);
        this.factureA15 = Boolean.TRUE.equals(factureA15);
        this.actif = actif == null || actif;
        if (this.principal && !this.actif) {
            throw ErreurMetier.regle("PRINCIPAL_INACTIF", "Le dépôt principal doit rester actif.");
        }
    }

    public void retirerPrincipal() {
        this.principal = false;
    }

    public String getNom() {
        return nom;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getAdresse() {
        return adresse;
    }

    public int getDelaiLivraison() {
        return delaiLivraison;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public boolean isFactureA15() {
        return factureA15;
    }

    public boolean isActif() {
        return actif;
    }
}
