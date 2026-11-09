package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Vehicule ou pirogue autorise a etre servi sur le compte du client (immatriculation unique par client). */
@Entity
@Table(schema = "clients", name = "engin_client")
public class EnginClient extends EntiteAuditee {

    public enum Type { VEHICULE, PIROGUE }

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_engin", nullable = false)
    private Type type;

    @Column(nullable = false)
    private String immatriculation;

    private String libelle;

    @Column(nullable = false)
    private boolean actif = true;

    protected EnginClient() {
    }

    public EnginClient(long clientId) {
        this.clientId = clientId;
    }

    public void modifier(Type type, String immatriculation, String libelle, boolean actif) {
        if (type == null) {
            throw ErreurMetier.regle("TYPE_ENGIN_OBLIGATOIRE", "Indiquez s'il s'agit d'un véhicule ou d'une pirogue.");
        }
        String immat = normaliser(immatriculation);
        if (immat == null) {
            throw ErreurMetier.regle("IMMATRICULATION_OBLIGATOIRE", "Indiquez l'immatriculation.");
        }
        this.type = type;
        this.immatriculation = immat;
        this.libelle = libelle == null || libelle.isBlank() ? null : libelle.trim();
        this.actif = actif;
    }

    /** Immatriculation comparable : majuscules, sans espaces ni tirets superflus en bordure. */
    public static String normaliser(String immatriculation) {
        return immatriculation == null || immatriculation.isBlank() ? null : immatriculation.trim().toUpperCase(Locale.ROOT);
    }

    public Long getClientId() {
        return clientId;
    }

    public Type getType() {
        return type;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isActif() {
        return actif;
    }
}
