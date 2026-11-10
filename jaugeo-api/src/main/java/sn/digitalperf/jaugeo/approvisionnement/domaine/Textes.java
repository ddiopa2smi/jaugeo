package sn.digitalperf.jaugeo.approvisionnement.domaine;

/** Normalisation des saisies libres. */
final class Textes {

    private Textes() {
    }

    static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String majuscules(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
