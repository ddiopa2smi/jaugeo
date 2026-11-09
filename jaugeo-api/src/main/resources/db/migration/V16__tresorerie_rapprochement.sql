-- =====================================================================
-- Lot 2, sprint M5 : tresorerie
-- Encaissements electroniques du jour (Wave, Orange Money, carte) deduits des especes a verser ;
-- plafond d'especes non versees par station (alerte) ; releves bancaires et marchands importes et lettres ;
-- echeancier de remboursement des manquants des gerants.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- Totaux encaisses dans la journee par mode electronique (les especes sont le reste)
CREATE TABLE tresorerie.encaissement_journee (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id    bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id    bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour     date          NOT NULL,
    mode          varchar(15)   NOT NULL,
    montant       numeric(15,0) NOT NULL CHECK (montant > 0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT ck_encaissement_mode CHECK (mode IN ('WAVE', 'ORANGE_MONEY', 'CARTE')),
    CONSTRAINT uq_encaissement_journee UNIQUE (journee_id, mode)
);
CREATE INDEX ix_encaissement_station_date ON tresorerie.encaissement_journee (station_id, date_jour);
SELECT audit.activer_audit('tresorerie.encaissement_journee');

-- Plafond d'especes non versees d'une station : au-dela, alerte (vol, oubli de versement)
CREATE TABLE tresorerie.plafond_especes (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint        NOT NULL UNIQUE REFERENCES referentiel.station (id),
    plafond       numeric(15,0) NOT NULL CHECK (plafond > 0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL
);
SELECT audit.activer_audit('tresorerie.plafond_especes');

-- Les encaissements electroniques sortent du montant a verser en especes
ALTER TABLE tresorerie.recette_journee
    ADD COLUMN encaissements numeric(15,0) NOT NULL DEFAULT 0 CHECK (encaissements >= 0);
ALTER TABLE tresorerie.recette_journee DROP CONSTRAINT ck_recette_calcul;
ALTER TABLE tresorerie.recette_journee ADD CONSTRAINT ck_recette_calcul
    CHECK (montant_a_verser = ventes + ventes_articles - bons_direction - bons_gerant - depenses - complements
                              - encaissements + recouvrements);

-- ---------------------------------------------------------------------
-- Releves bancaires et marchands (Wave, Orange Money, carte) importes en CSV ou Excel.
-- Le format (colonnes, separateur, format de date) est parametre une fois par banque ou operateur.
-- ---------------------------------------------------------------------
CREATE TABLE tresorerie.format_releve (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    code            varchar(20)   NOT NULL UNIQUE,
    libelle         varchar(80)   NOT NULL,
    nature          varchar(15)   NOT NULL,
    separateur      varchar(1)    NOT NULL DEFAULT ';',
    format_date     varchar(20)   NOT NULL DEFAULT 'dd/MM/yyyy',
    lignes_ignorees integer       NOT NULL DEFAULT 1 CHECK (lignes_ignorees BETWEEN 0 AND 50),
    col_date        integer       NOT NULL CHECK (col_date BETWEEN 1 AND 50),
    col_libelle     integer       NOT NULL CHECK (col_libelle BETWEEN 1 AND 50),
    col_reference   integer       CHECK (col_reference BETWEEN 1 AND 50),
    col_credit      integer       NOT NULL CHECK (col_credit BETWEEN 1 AND 50),
    actif           boolean       NOT NULL DEFAULT true,
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT ck_format_nature CHECK (nature IN ('BANQUE', 'WAVE', 'ORANGE_MONEY', 'CARTE'))
);
SELECT audit.activer_audit('tresorerie.format_releve');

-- Un fichier importe ; son empreinte interdit de l'importer deux fois
CREATE TABLE tresorerie.releve (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    format_id     bigint        NOT NULL REFERENCES tresorerie.format_releve (id),
    station_id    bigint        REFERENCES referentiel.station (id),
    nom_fichier   varchar(200)  NOT NULL,
    empreinte     varchar(64)   NOT NULL UNIQUE,
    date_min      date,
    date_max      date,
    operations    integer       NOT NULL DEFAULT 0,
    doublons      integer       NOT NULL DEFAULT 0,
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL
);
SELECT audit.activer_audit('tresorerie.releve');

-- Operation au credit d'un releve ; lettree (rapprochee) avec un versement pour les releves bancaires
CREATE TABLE tresorerie.operation_releve (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    releve_id       bigint        NOT NULL REFERENCES tresorerie.releve (id),
    format_id       bigint        NOT NULL REFERENCES tresorerie.format_releve (id),
    station_id      bigint        REFERENCES referentiel.station (id),
    date_operation  date          NOT NULL,
    libelle         varchar(300)  NOT NULL,
    reference       varchar(80),
    montant         numeric(15,0) NOT NULL CHECK (montant > 0),
    cle             varchar(64)   NOT NULL,
    versement_id    bigint        UNIQUE REFERENCES tresorerie.versement (id),
    mode_lettrage   varchar(10),
    lettre_par      varchar(50),
    lettre_le       timestamptz,
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT uq_operation_cle UNIQUE (format_id, cle),
    CONSTRAINT ck_operation_lettrage CHECK ((versement_id IS NULL) = (mode_lettrage IS NULL)),
    CONSTRAINT ck_operation_mode CHECK (mode_lettrage IN ('AUTO', 'MANUEL'))
);
CREATE INDEX ix_operation_date ON tresorerie.operation_releve (date_operation);
CREATE INDEX ix_operation_station_date ON tresorerie.operation_releve (station_id, date_operation);
SELECT audit.activer_audit('tresorerie.operation_releve');

-- ---------------------------------------------------------------------
-- Manquants des gerants : reconnaissance de dette, echeancier, reglements partiels
-- (remboursement, retenue sur salaire) ; le manquant est solde quand le cumul atteint son montant.
-- ---------------------------------------------------------------------
ALTER TABLE tresorerie.manquant_gerant
    ADD COLUMN montant_regle            numeric(15,0) NOT NULL DEFAULT 0,
    ADD COLUMN reconnaissance_reference varchar(80),
    ADD COLUMN reconnu_le               date;
UPDATE tresorerie.manquant_gerant SET montant_regle = montant WHERE statut = 'SOLDE';
ALTER TABLE tresorerie.manquant_gerant
    ADD CONSTRAINT ck_manquant_regle CHECK (montant_regle >= 0 AND montant_regle <= montant),
    ADD CONSTRAINT ck_manquant_solde CHECK ((statut = 'SOLDE') = (montant_regle = montant)),
    ADD CONSTRAINT ck_manquant_reconnaissance CHECK ((reconnu_le IS NULL) = (reconnaissance_reference IS NULL));

CREATE TABLE tresorerie.echeance_manquant (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    manquant_id    bigint        NOT NULL REFERENCES tresorerie.manquant_gerant (id),
    rang           integer       NOT NULL CHECK (rang > 0),
    date_echeance  date          NOT NULL,
    montant        numeric(15,0) NOT NULL CHECK (montant > 0),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT uq_echeance_rang UNIQUE (manquant_id, rang)
);
SELECT audit.activer_audit('tresorerie.echeance_manquant');

CREATE TABLE tresorerie.paiement_manquant (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    manquant_id    bigint        NOT NULL REFERENCES tresorerie.manquant_gerant (id),
    date_paiement  date          NOT NULL,
    montant        numeric(15,0) NOT NULL CHECK (montant > 0),
    mode           varchar(20)   NOT NULL,
    reference      varchar(80),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT ck_paiement_mode CHECK (mode IN ('REMBOURSEMENT', 'RETENUE', 'PERTE'))
);
CREATE INDEX ix_paiement_manquant ON tresorerie.paiement_manquant (manquant_id);
SELECT audit.activer_audit('tresorerie.paiement_manquant');

-- Historique : les manquants deja soldes ont un reglement unique
INSERT INTO tresorerie.paiement_manquant (manquant_id, date_paiement, montant, mode, reference, cree_par, modifie_par)
SELECT id, regle_le, montant, mode_reglement, reference_reglement, coalesce(regle_par, 'migration'), 'migration'
FROM tresorerie.manquant_gerant WHERE statut = 'SOLDE';
