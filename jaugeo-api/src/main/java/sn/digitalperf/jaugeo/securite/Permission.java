package sn.digitalperf.jaugeo.securite;

/**
 * Permissions fonctionnelles. Un role regroupe des permissions ; chaque appel d'API
 * verifie la permission requise cote serveur.
 */
public enum Permission {
    REFERENTIEL_CONSULTER,
    REFERENTIEL_GERER,
    UTILISATEUR_GERER,
    AUDIT_CONSULTER,
    TABLEAU_BORD_RESEAU,
    PRIX_MODIFIER,
    JOURNEE_SAISIR,
    JOURNEE_CLOTURER,
    JOURNEE_VALIDER,
    RECEPTION_VALIDER,
    VERSEMENT_SAISIR,
    VERSEMENT_VALIDER,
    CLIENT_GERER,
    CREDIT_DEROGER,
    CLOTURE_MENSUELLE
}
