package sn.digitalperf.jaugeo.securite.application;

import java.security.SecureRandom;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Regles de robustesse des mots de passe et generation de mots de passe provisoires. */
public final class PolitiqueMotDePasse {

    public static final int LONGUEUR_MIN = 10;
    private static final String MAJ = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MIN = "abcdefghijkmnpqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SPECIAUX = "@#%+=!?";
    private static final SecureRandom ALEA = new SecureRandom();

    private PolitiqueMotDePasse() {
    }

    public static void verifier(String motDePasse, String identifiant) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN
                || !motDePasse.chars().anyMatch(Character::isUpperCase)
                || !motDePasse.chars().anyMatch(Character::isLowerCase)
                || !motDePasse.chars().anyMatch(Character::isDigit)) {
            throw ErreurMetier.regle("MOT_DE_PASSE_FAIBLE",
                    "Le mot de passe doit contenir au moins " + LONGUEUR_MIN
                            + " caractères, dont une majuscule, une minuscule et un chiffre.");
        }
        if (identifiant != null && motDePasse.toLowerCase().contains(identifiant.toLowerCase())) {
            throw ErreurMetier.regle("MOT_DE_PASSE_FAIBLE", "Le mot de passe ne doit pas contenir l'identifiant.");
        }
    }

    /** Mot de passe provisoire de 12 caracteres respectant la politique. */
    public static String genererProvisoire() {
        StringBuilder sb = new StringBuilder();
        sb.append(MAJ.charAt(ALEA.nextInt(MAJ.length())));
        sb.append(MIN.charAt(ALEA.nextInt(MIN.length())));
        sb.append(CHIFFRES.charAt(ALEA.nextInt(CHIFFRES.length())));
        sb.append(SPECIAUX.charAt(ALEA.nextInt(SPECIAUX.length())));
        String tous = MAJ + MIN + CHIFFRES;
        while (sb.length() < 12) {
            sb.append(tous.charAt(ALEA.nextInt(tous.length())));
        }
        // Melange
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = ALEA.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }
}
