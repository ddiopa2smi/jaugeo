package sn.digitalperf.jaugeo.depenses.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import sn.digitalperf.jaugeo.commun.PieceJointe;

/** Justificatif d'une depense (photo de facture ou de recu, PDF). */
@Entity
@Table(schema = "depenses", name = "justificatif")
public class Justificatif {

    /** Description sans le contenu, pour les listes. */
    public interface Description {
        Long getDepenseId();

        String getNomFichier();

        String getTypeMime();

        Integer getTaille();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "depense_id", nullable = false, updatable = false)
    private Long depenseId;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(nullable = false)
    private Integer taille;

    @Column(nullable = false)
    private String empreinte;

    @Column(nullable = false)
    private byte[] contenu;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "cree_par", nullable = false)
    private String creePar;

    protected Justificatif() {
    }

    public Justificatif(long depenseId, PieceJointe piece, String par, Instant le) {
        this.depenseId = depenseId;
        this.nomFichier = piece.nomFichier();
        this.typeMime = piece.typeMime();
        this.taille = piece.taille();
        this.empreinte = piece.empreinte();
        this.contenu = piece.contenu();
        this.creePar = par;
        this.creeLe = le;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public byte[] getContenu() {
        return contenu;
    }
}
