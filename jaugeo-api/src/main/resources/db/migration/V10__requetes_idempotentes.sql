-- =====================================================================
-- Jaugeo - V10 : requetes idempotentes (saisie hors ligne de la tablette)
-- =====================================================================
-- La tablette rejoue les saisies faites hors connexion avec un en-tete Idempotency-Key : une requete deja traitee
-- n'est pas executee une seconde fois, sa reponse d'origine est renvoyee. Lignes purgees apres 7 jours.
-- Table technique : pas d'audit (les ecritures metier qu'elle protege sont deja auditees).

CREATE TABLE securite.requete_idempotente (
    utilisateur   varchar(64)  NOT NULL,
    cle           varchar(100) NOT NULL,
    methode       varchar(10)  NOT NULL,
    chemin        varchar(500) NOT NULL,
    empreinte     char(64)     NOT NULL,
    statut        integer,
    type_contenu  varchar(100),
    corps         bytea,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (utilisateur, cle)
);

CREATE INDEX requete_idempotente_cree_le ON securite.requete_idempotente (cree_le);

COMMENT ON TABLE securite.requete_idempotente IS
    'Reponses des requetes envoyees avec Idempotency-Key ; statut nul = requete en cours de traitement.';
