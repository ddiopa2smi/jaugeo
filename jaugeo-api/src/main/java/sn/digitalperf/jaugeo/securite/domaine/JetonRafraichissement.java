package sn.digitalperf.jaugeo.securite.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Jeton de rafraichissement : seule son empreinte SHA-256 est conservee en base. */
@Entity
@Table(schema = "securite", name = "jeton_rafraichissement")
public class JetonRafraichissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "revoque_le")
    private Instant revoqueLe;

    protected JetonRafraichissement() {
    }

    public JetonRafraichissement(Utilisateur utilisateur, String empreinte, Instant creeLe, Instant expireLe) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public boolean estUtilisable(Instant maintenant) {
        return revoqueLe == null && expireLe.isAfter(maintenant);
    }

    public void revoquer(Instant maintenant) {
        if (revoqueLe == null) {
            revoqueLe = maintenant;
        }
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public Instant getRevoqueLe() {
        return revoqueLe;
    }
}
