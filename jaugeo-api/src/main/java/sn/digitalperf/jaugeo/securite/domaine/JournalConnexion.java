package sn.digitalperf.jaugeo.securite.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "securite", name = "journal_connexion")
public class JournalConnexion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant horodatage;

    @Column(nullable = false)
    private String identifiant;

    @Column(nullable = false)
    private boolean succes;

    private String motif;

    @Column(name = "adresse_ip")
    private String adresseIp;

    protected JournalConnexion() {
    }

    public JournalConnexion(Instant horodatage, String identifiant, boolean succes, String motif, String adresseIp) {
        this.horodatage = horodatage;
        this.identifiant = identifiant;
        this.succes = succes;
        this.motif = motif;
        this.adresseIp = adresseIp;
    }
}
