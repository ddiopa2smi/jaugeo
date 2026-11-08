-- =====================================================================
-- Sprint 4 : depenses autorisees, montant a verser, versements (BV), solde de caisse du gerant,
-- arretes de caisse et manquants
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Nature de depense de station, avec plafond par depense (au-dela : validation du superviseur).
-- Plafond nul = pas de plafond (factures d'electricite, d'eau...).
-- ---------------------------------------------------------------------
CREATE TABLE depenses.nature_depense (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid         uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    code         varchar(30)   NOT NULL UNIQUE,
    libelle      varchar(80)   NOT NULL,
    plafond      numeric(15,0) CHECK (plafond > 0),
    actif        boolean       NOT NULL DEFAULT true,
    version      integer       NOT NULL DEFAULT 0,
    cree_le      timestamptz   NOT NULL DEFAULT now(),
    cree_par     varchar(50)   NOT NULL,
    modifie_le   timestamptz   NOT NULL DEFAULT now(),
    modifie_par  varchar(50)   NOT NULL
);
SELECT audit.activer_audit('depenses.nature_depense');

-- Natures et plafonds de depart : hypotheses a confirmer par le controle de gestion
INSERT INTO depenses.nature_depense (code, libelle, plafond, cree_par, modifie_par) VALUES
    ('ELECTRICITE',  'Électricité',                    NULL,  'migration', 'migration'),
    ('EAU',          'Eau',                            NULL,  'migration', 'migration'),
    ('GARDIENNAGE',  'Gardiennage',                    50000, 'migration', 'migration'),
    ('ENTRETIEN',    'Entretien et petites réparations', 50000, 'migration', 'migration'),
    ('FOURNITURES',  'Fournitures et consommables',    25000, 'migration', 'migration'),
    ('TRANSPORT',    'Transport et courses',           10000, 'migration', 'migration'),
    ('AUTRE',        'Autre dépense',                  10000, 'migration', 'migration');

-- ---------------------------------------------------------------------
-- Depense payee sur la recette d'une journee. AUTORISEE : deduite du montant a verser.
-- A_VALIDER (au-dela du plafond) bloque la cloture ; REFUSEE reste a la charge du gerant.
-- ---------------------------------------------------------------------
CREATE TABLE depenses.depense (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id       bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id       bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour        date          NOT NULL,
    nature_id        bigint        NOT NULL REFERENCES depenses.nature_depense (id),
    libelle          varchar(200)  NOT NULL,
    beneficiaire     varchar(120),
    montant          numeric(15,0) NOT NULL CHECK (montant > 0),
    plafond_applique numeric(15,0),
    statut           varchar(10)   NOT NULL,
    decision_par     varchar(50),
    decision_le      timestamptz,
    motif_decision   varchar(500),
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_depense_statut CHECK (statut IN ('A_VALIDER', 'AUTORISEE', 'REFUSEE')),
    CONSTRAINT ck_depense_refus CHECK (statut <> 'REFUSEE' OR motif_decision IS NOT NULL)
);
CREATE INDEX ix_depense_journee ON depenses.depense (journee_id);
CREATE INDEX ix_depense_station_date ON depenses.depense (station_id, date_jour);
SELECT audit.activer_audit('depenses.depense');

-- Justificatif (photo ou PDF) : contenu hors de la table principale, non journalise par l'audit
CREATE TABLE depenses.justificatif (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    depense_id   bigint       NOT NULL UNIQUE REFERENCES depenses.depense (id) ON DELETE CASCADE,
    nom_fichier  varchar(120) NOT NULL,
    type_mime    varchar(40)  NOT NULL,
    taille       integer      NOT NULL CHECK (taille > 0),
    empreinte    varchar(64)  NOT NULL,
    contenu      bytea        NOT NULL,
    cree_le      timestamptz  NOT NULL DEFAULT now(),
    cree_par     varchar(50)  NOT NULL
);

-- ---------------------------------------------------------------------
-- Recette d'une journee, figee a la cloture (feuille station, colonne MONTANT A VERSER) :
-- montant a verser = ventes - bons direction - bons gerant - depenses autorisees - complements citerne.
-- Les bons (clients en compte) arrivent au sprint 5 : 0 en attendant.
-- ---------------------------------------------------------------------
CREATE TABLE tresorerie.recette_journee (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id        bigint        NOT NULL UNIQUE REFERENCES exploitation.journee (id),
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour         date          NOT NULL,
    ventes            numeric(15,0) NOT NULL CHECK (ventes >= 0),
    bons_direction    numeric(15,0) NOT NULL DEFAULT 0 CHECK (bons_direction >= 0),
    bons_gerant       numeric(15,0) NOT NULL DEFAULT 0 CHECK (bons_gerant >= 0),
    depenses          numeric(15,0) NOT NULL CHECK (depenses >= 0),
    complements       numeric(15,0) NOT NULL CHECK (complements >= 0),
    montant_a_verser  numeric(15,0) NOT NULL,
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT ck_recette_calcul CHECK (montant_a_verser = ventes - bons_direction - bons_gerant - depenses - complements),
    CONSTRAINT uq_recette_station_date UNIQUE (station_id, date_jour)
);
SELECT audit.activer_audit('tresorerie.recette_journee');

-- Reprise des journees deja cloturees (sans depense ni bon a cette date)
INSERT INTO tresorerie.recette_journee (journee_id, station_id, date_jour, ventes, depenses, complements,
                                        montant_a_verser, cree_par, modifie_par)
SELECT j.id, j.station_id, j.date_jour, coalesce(v.montant, 0), 0, coalesce(c.montant, 0),
       coalesce(v.montant, 0) - coalesce(c.montant, 0), 'migration', 'migration'
FROM exploitation.journee j
LEFT JOIN (SELECT journee_id, sum(montant) AS montant FROM exploitation.vente GROUP BY journee_id) v ON v.journee_id = j.id
LEFT JOIN (SELECT journee_id, sum(montant) AS montant FROM stock.complement_citerne GROUP BY journee_id) c ON c.journee_id = j.id
WHERE j.statut <> 'BROUILLON';

-- ---------------------------------------------------------------------
-- Versement bancaire de la recette (bordereau de versement, BV). Une reference BV n'est saisie
-- qu'une fois par banque (hors versements rejetes). SAISI par le gerant, VERIFIE ou REJETE par la comptabilite.
-- ---------------------------------------------------------------------
CREATE TABLE tresorerie.versement (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id     bigint        NOT NULL REFERENCES referentiel.station (id),
    date_depot     date          NOT NULL,
    banque         varchar(60)   NOT NULL,
    reference_bv   varchar(40)   NOT NULL,
    montant        numeric(15,0) NOT NULL CHECK (montant > 0),
    deposant       varchar(80),
    observation    varchar(500),
    statut         varchar(10)   NOT NULL DEFAULT 'SAISI',
    verifie_par    varchar(50),
    verifie_le     timestamptz,
    motif_rejet    varchar(500),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT ck_versement_statut CHECK (statut IN ('SAISI', 'VERIFIE', 'REJETE')),
    CONSTRAINT ck_versement_rejet CHECK (statut <> 'REJETE' OR motif_rejet IS NOT NULL),
    CONSTRAINT ck_versement_verification CHECK ((statut = 'SAISI') = (verifie_par IS NULL))
);
CREATE UNIQUE INDEX uq_versement_bv ON tresorerie.versement (upper(banque), upper(reference_bv)) WHERE statut <> 'REJETE';
CREATE INDEX ix_versement_station_date ON tresorerie.versement (station_id, date_depot);
SELECT audit.activer_audit('tresorerie.versement');

-- Photo (ou scan PDF) du bordereau
CREATE TABLE tresorerie.piece_versement (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    versement_id  bigint       NOT NULL UNIQUE REFERENCES tresorerie.versement (id) ON DELETE CASCADE,
    nom_fichier   varchar(120) NOT NULL,
    type_mime     varchar(40)  NOT NULL,
    taille        integer      NOT NULL CHECK (taille > 0),
    empreinte     varchar(64)  NOT NULL,
    contenu       bytea        NOT NULL,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL
);

-- ---------------------------------------------------------------------
-- Arrete de caisse a la fin d'une journee : especes comptees chez le gerant.
-- REPRISE : solde de depart (aucun ecart calcule). CONTROLE : ecart = comptees - solde theorique.
-- Le solde de caisse repart des especes comptees ; un ecart negatif devient un manquant du gerant.
-- ---------------------------------------------------------------------
CREATE TABLE tresorerie.arrete_caisse (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour         date          NOT NULL,
    type_arrete       varchar(10)   NOT NULL,
    solde_theorique   numeric(15,0),
    especes_comptees  numeric(15,0) NOT NULL CHECK (especes_comptees >= 0),
    ecart             numeric(15,0),
    responsable       varchar(80),
    observation       varchar(500),
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT ck_arrete_type CHECK (type_arrete IN ('REPRISE', 'CONTROLE')),
    CONSTRAINT ck_arrete_ecart CHECK (
        (type_arrete = 'REPRISE' AND solde_theorique IS NULL AND ecart IS NULL)
        OR (type_arrete = 'CONTROLE' AND solde_theorique IS NOT NULL AND ecart = especes_comptees - solde_theorique)),
    CONSTRAINT ck_arrete_observation CHECK (ecart IS NULL OR ecart = 0 OR observation IS NOT NULL),
    CONSTRAINT uq_arrete_station_date UNIQUE (station_id, date_jour)
);
SELECT audit.activer_audit('tresorerie.arrete_caisse');

-- Manquant constate a un arrete de caisse, dette du gerant suivie jusqu'a son reglement
CREATE TABLE tresorerie.manquant_gerant (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    arrete_id           bigint        NOT NULL UNIQUE REFERENCES tresorerie.arrete_caisse (id),
    station_id          bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour           date          NOT NULL,
    montant             numeric(15,0) NOT NULL CHECK (montant > 0),
    responsable         varchar(80),
    statut              varchar(10)   NOT NULL DEFAULT 'OUVERT',
    mode_reglement      varchar(20),
    reference_reglement varchar(80),
    regle_le            date,
    regle_par           varchar(50),
    version             integer       NOT NULL DEFAULT 0,
    cree_le             timestamptz   NOT NULL DEFAULT now(),
    cree_par            varchar(50)   NOT NULL,
    modifie_le          timestamptz   NOT NULL DEFAULT now(),
    modifie_par         varchar(50)   NOT NULL,
    CONSTRAINT ck_manquant_statut CHECK (statut IN ('OUVERT', 'SOLDE')),
    CONSTRAINT ck_manquant_mode CHECK (mode_reglement IS NULL OR mode_reglement IN ('REMBOURSEMENT', 'RETENUE', 'PERTE')),
    CONSTRAINT ck_manquant_reglement CHECK ((statut = 'SOLDE') = (regle_le IS NOT NULL AND mode_reglement IS NOT NULL))
);
CREATE INDEX ix_manquant_station_statut ON tresorerie.manquant_gerant (station_id, statut);
SELECT audit.activer_audit('tresorerie.manquant_gerant');

-- ---------------------------------------------------------------------
-- Permissions : validation des depenses hors plafond, arretes de caisse
-- ---------------------------------------------------------------------
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, x.permission
FROM (VALUES
    ('SUPERVISEUR', 'DEPENSE_VALIDER'), ('DIRECTION', 'DEPENSE_VALIDER'),
    ('SUPERVISEUR', 'CAISSE_CONTROLER'), ('COMPTABLE', 'CAISSE_CONTROLER'), ('CONTROLEUR_GESTION', 'CAISSE_CONTROLER')
) AS x (role, permission)
JOIN securite.role r ON r.code = x.role;
