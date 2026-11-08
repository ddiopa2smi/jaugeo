package sn.digitalperf.jaugeo.securite.domaine;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

@Entity
@Table(schema = "securite", name = "utilisateur")
public class Utilisateur extends EntiteAuditee {

    public enum Perimetre { RESEAU, STATIONS }

    @Column(nullable = false)
    private String identifiant;

    @Column(name = "nom_complet", nullable = false)
    private String nomComplet;

    private String email;

    private String telephone;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perimetre perimetre;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "doit_changer_mot_de_passe", nullable = false)
    private boolean doitChangerMotDePasse = true;

    @Column(name = "echecs_connexion", nullable = false)
    private int echecsConnexion;

    @Column(name = "verrouille_jusqua")
    private Instant verrouilleJusqua;

    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "securite", name = "utilisateur_station", joinColumns = @JoinColumn(name = "utilisateur_id"))
    @Column(name = "station_id", nullable = false)
    private Set<Long> stationIds = new HashSet<>();

    protected Utilisateur() {
    }

    public Utilisateur(String identifiant, String nomComplet, String motDePasseHash, Role role, Perimetre perimetre) {
        this.identifiant = identifiant;
        this.nomComplet = nomComplet;
        this.motDePasseHash = motDePasseHash;
        this.role = role;
        this.perimetre = perimetre;
    }

    public boolean estVerrouille(Instant maintenant) {
        return verrouilleJusqua != null && verrouilleJusqua.isAfter(maintenant);
    }

    public void changerMotDePasse(String nouveauHash, boolean temporaire) {
        this.motDePasseHash = nouveauHash;
        this.doitChangerMotDePasse = temporaire;
    }

    public void modifier(String nomComplet, String email, String telephone, Role role,
                         Perimetre perimetre, Set<Long> stationIds, boolean actif) {
        this.nomComplet = nomComplet;
        this.email = email;
        this.telephone = telephone;
        this.role = role;
        this.perimetre = perimetre;
        this.stationIds.clear();
        if (perimetre == Perimetre.STATIONS && stationIds != null) {
            this.stationIds.addAll(stationIds);
        }
        this.actif = actif;
    }

    public String getIdentifiant() {
        return identifiant;
    }

    public String getNomComplet() {
        return nomComplet;
    }

    public String getEmail() {
        return email;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public Role getRole() {
        return role;
    }

    public Perimetre getPerimetre() {
        return perimetre;
    }

    public boolean isActif() {
        return actif;
    }

    public boolean isDoitChangerMotDePasse() {
        return doitChangerMotDePasse;
    }

    public int getEchecsConnexion() {
        return echecsConnexion;
    }

    public Instant getVerrouilleJusqua() {
        return verrouilleJusqua;
    }

    public Instant getDerniereConnexion() {
        return derniereConnexion;
    }

    public Set<Long> getStationIds() {
        return Set.copyOf(stationIds);
    }
}
