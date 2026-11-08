package sn.digitalperf.jaugeo.commun;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * Piece jointe controlee (photo de bordereau, justificatif de depense) : JPEG, PNG, WebP ou PDF de 5 Mo au plus.
 * Le type est deduit du contenu (signature du fichier), jamais du nom ni de l'en-tete envoye par le client.
 */
public record PieceJointe(String nomFichier, String typeMime, byte[] contenu, String empreinte) {

    public static final int TAILLE_MAX = 5 * 1024 * 1024;

    public static PieceJointe lire(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw ErreurMetier.regle("FICHIER_VIDE", "Joignez une photo ou un fichier PDF.");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw ErreurMetier.regle("FICHIER_TROP_LOURD", "Le fichier dépasse 5 Mo : réduisez la photo.");
        }
        byte[] octets;
        try {
            octets = fichier.getBytes();
        } catch (IOException e) {
            throw ErreurMetier.regle("FICHIER_ILLISIBLE", "Le fichier n'a pas pu être lu.");
        }
        String type = type(octets);
        if (type == null) {
            throw ErreurMetier.regle("TYPE_FICHIER_REFUSE", "Formats acceptés : photo JPEG, PNG ou WebP, ou document PDF.");
        }
        return new PieceJointe(nom(fichier.getOriginalFilename(), type), type, octets, empreinte(octets));
    }

    public int taille() {
        return contenu.length;
    }

    /** Reponse de telechargement : affichee dans le navigateur, jamais interpretee comme une page. */
    public static org.springframework.http.ResponseEntity<byte[]> reponse(String nomFichier, String typeMime, byte[] contenu) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(typeMime))
                .header("Content-Disposition", org.springframework.http.ContentDisposition.inline()
                        .filename(nomFichier, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .header("Cache-Control", "private, max-age=300")
                .body(contenu);
    }

    static String type(byte[] o) {
        if (commence(o, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (commence(o, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (commence(o, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (o.length >= 12 && commence(o, 'R', 'I', 'F', 'F') && o[8] == 'W' && o[9] == 'E' && o[10] == 'B' && o[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean commence(byte[] o, int... signature) {
        if (o.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((o[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Nom de fichier assaini (pas de chemin, caracteres sans risque), extension conforme au type reel. */
    static String nom(String original, String type) {
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int point = base.lastIndexOf('.');
        if (point > 0) {
            base = base.substring(0, point);
        }
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty()) {
            base = "piece";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        String extension = switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
        return base + extension;
    }

    private static String empreinte(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
