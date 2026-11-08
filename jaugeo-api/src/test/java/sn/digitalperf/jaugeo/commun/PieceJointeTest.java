package sn.digitalperf.jaugeo.commun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PieceJointeTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    static final byte[] PDF = "%PDF-1.7\n%".getBytes();

    @Test
    void leTypeEstDeduitDuContenuEtLeNomAssaini() {
        PieceJointe p = PieceJointe.lire(new MockMultipartFile("fichier", "..\\..\\BV 774378 (1).png.exe", "application/x-msdownload", JPEG));
        assertThat(p.typeMime()).isEqualTo("image/jpeg");
        assertThat(p.nomFichier()).isEqualTo("BV 774378 _1_.png.jpg");
        assertThat(p.empreinte()).hasSize(64);
        assertThat(PieceJointe.lire(new MockMultipartFile("fichier", "facture.pdf", "image/png", PDF)).typeMime())
                .isEqualTo("application/pdf");
    }

    @Test
    void contenuInconnuVideOuTropLourdRefuse() {
        assertThatThrownBy(() -> PieceJointe.lire(new MockMultipartFile("fichier", "page.html", "image/png", "<html>".getBytes())))
                .hasFieldOrPropertyWithValue("code", "TYPE_FICHIER_REFUSE");
        assertThatThrownBy(() -> PieceJointe.lire(new MockMultipartFile("fichier", "vide.jpg", "image/jpeg", new byte[0])))
                .hasFieldOrPropertyWithValue("code", "FICHIER_VIDE");
        byte[] lourd = new byte[PieceJointe.TAILLE_MAX + 1];
        System.arraycopy(JPEG, 0, lourd, 0, JPEG.length);
        assertThatThrownBy(() -> PieceJointe.lire(new MockMultipartFile("fichier", "photo.jpg", "image/jpeg", lourd)))
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_LOURD");
    }
}
