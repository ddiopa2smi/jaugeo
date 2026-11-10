package sn.digitalperf.jaugeo.personnel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Employe d'une station ; l'identifiant est son compte Jaugeo eventuel (pompiste qui saisit sa feuille). */
@Entity
@Table(schema = "personnel", name = "employe")
public class Employe extends EntiteAuditee {

    public enum Fonction { POMPISTE, CAISSIER, CHEF_PISTE, GERANT, GARDIEN, AUTRE }

    /** Donnees saisies de la fiche employe. */
    public record Donnees(String matricule, String nom, String prenom, String telephone, Fonction fonction,
                          LocalDate dateEntree, LocalDate dateSortie, String identifiant, Boolean actif) {
    }

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    private String matricule;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Fonction fonction = Fonction.POMPISTE;

    @Column(name = "date_entree")
    private LocalDate dateEntree;

    @Column(name = "date_sortie")
    private LocalDate dateSortie;

    private String identifiant;

    @Column(nullable = false)
    private boolean actif = true;

    protected Employe() {
    }

    public Employe(long stationId) {
        this.stationId = stationId;
    }

    public void renseigner(Donnees d) {
        if (d.nom() == null || d.nom().isBlank() || d.prenom() == null || d.prenom().isBlank()) {
            throw ErreurMetier.regle("NOM_OBLIGATOIRE", "Indiquez le nom et le prénom de l'employé.");
        }
        if (d.dateEntree() != null && d.dateSortie() != null && d.dateSortie().isBefore(d.dateEntree())) {
            throw ErreurMetier.regle("DATES_INVALIDES", "La date de sortie précède la date d'entrée.");
        }
        this.matricule = Textes.majuscules(d.matricule());
        this.nom = d.nom().trim();
        this.prenom = d.prenom().trim();
        this.telephone = Textes.texte(d.telephone());
        this.fonction = d.fonction() == null ? Fonction.POMPISTE : d.fonction();
        this.dateEntree = d.dateEntree();
        this.dateSortie = d.dateSortie();
        String compte = Textes.texte(d.identifiant());
        this.identifiant = compte == null ? null : compte.toLowerCase();
        // Un employe sorti n'est plus actif
        this.actif = d.dateSortie() == null && (d.actif() == null || d.actif());
    }

    public void affecter(long stationId) {
        this.stationId = stationId;
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }

    public Long getStationId() {
        return stationId;
    }

    public String getMatricule() {
        return matricule;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getTelephone() {
        return telephone;
    }

    public Fonction getFonction() {
        return fonction;
    }

    public LocalDate getDateEntree() {
        return dateEntree;
    }

    public LocalDate getDateSortie() {
        return dateSortie;
    }

    public String getIdentifiant() {
        return identifiant;
    }

    public boolean isActif() {
        return actif;
    }
}
