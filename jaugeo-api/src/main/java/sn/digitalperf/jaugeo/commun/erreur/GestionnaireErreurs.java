package sn.digitalperf.jaugeo.commun.erreur;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Traduit toutes les erreurs en application/problem+json avec un code metier stable.
 */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(ErreurMetier.class)
    ProblemDetail erreurMetier(ErreurMetier e, HttpServletRequest requete) {
        return probleme(e.getStatut(), e.getCode(), e.getMessage(), requete);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e, HttpServletRequest requete) {
        Map<String, String> champs = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> champs.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail p = probleme(HttpStatus.BAD_REQUEST, "SAISIE_INVALIDE",
                "Certains champs sont invalides.", requete);
        p.setProperty("champs", champs);
        return p;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception e, HttpServletRequest requete) {
        return probleme(HttpStatus.BAD_REQUEST, "REQUETE_ILLISIBLE",
                "La requête est mal formée.", requete);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrence(OptimisticLockingFailureException e, HttpServletRequest requete) {
        return probleme(HttpStatus.CONFLICT, "VERSION_OBSOLETE",
                "Cet élément a été modifié entre-temps par un autre utilisateur. Rechargez-le.", requete);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException e, HttpServletRequest requete) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        String message = "L'opération viole une règle d'intégrité des données.";
        if (detail.contains("duplicate key") || detail.contains("uq_")) {
            message = "Un élément avec ce code ou cet identifiant existe déjà.";
        }
        LOG.debug("Violation d'integrite : {}", detail);
        return probleme(HttpStatus.CONFLICT, "INTEGRITE_DONNEES", message, requete);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail accesRefuse(Exception e, HttpServletRequest requete) {
        return probleme(HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Vous n'avez pas le droit d'effectuer cette action.", requete);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail introuvable(NoResourceFoundException e, HttpServletRequest requete) {
        return probleme(HttpStatus.NOT_FOUND, "INTROUVABLE", "Ressource introuvable.", requete);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inattendue(Exception e, HttpServletRequest requete) {
        LOG.error("Erreur inattendue sur {} {}", requete.getMethod(), requete.getRequestURI(), e);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE",
                "Une erreur inattendue s'est produite. Elle a été journalisée.", requete);
    }

    private static ProblemDetail probleme(HttpStatus statut, String code, String message, HttpServletRequest requete) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, message);
        p.setTitle(statut.getReasonPhrase());
        p.setType(URI.create("https://jaugeo.sn/erreurs/" + code.toLowerCase().replace('_', '-')));
        p.setInstance(URI.create(requete.getRequestURI()));
        p.setProperty("code", code);
        return p;
    }
}
