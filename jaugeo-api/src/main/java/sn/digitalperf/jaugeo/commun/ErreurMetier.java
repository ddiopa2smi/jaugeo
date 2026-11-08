package sn.digitalperf.jaugeo.commun;

import org.springframework.http.HttpStatus;

/**
 * Erreur metier portant un code stable (lu par le frontend) et un message lisible.
 * Traduite en reponse application/problem+json (RFC 9457).
 */
public class ErreurMetier extends RuntimeException {

    private final String code;
    private final HttpStatus statut;

    public ErreurMetier(HttpStatus statut, String code, String message) {
        super(message);
        this.statut = statut;
        this.code = code;
    }

    public static ErreurMetier introuvable(String quoi) {
        return new ErreurMetier(HttpStatus.NOT_FOUND, "INTROUVABLE", quoi + " introuvable.");
    }

    public static ErreurMetier conflit(String code, String message) {
        return new ErreurMetier(HttpStatus.CONFLICT, code, message);
    }

    public static ErreurMetier regle(String code, String message) {
        return new ErreurMetier(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public static ErreurMetier interdit(String message) {
        return new ErreurMetier(HttpStatus.FORBIDDEN, "ACCES_REFUSE", message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatut() {
        return statut;
    }
}
