-- =====================================================================
-- Lot 2, sprint M12 : cloture mensuelle, compte d'exploitation, regularisations et budget
-- Un mois se cloture station par station ; la cloture fige le compte d'exploitation et interdit toute ecriture
-- datee dans le mois. Une erreur decouverte ensuite se corrige par une regularisation, comptee dans le mois en
-- cours. Budget mensuel par station et par produit (litres, chiffre d'affaires) et budget de depenses.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Cloture d'un mois pour une station (mois = premier jour du mois). Jamais rouverte.
-- ---------------------------------------------------------------------
CREATE TABLE reporting.cloture_mois (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint       NOT NULL REFERENCES referentiel.station (id),
    mois          date         NOT NULL,
    cloture_par   varchar(50)  NOT NULL,
    cloture_le    timestamptz  NOT NULL,
    version       integer      NOT NULL DEFAULT 0,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL,
    modifie_le    timestamptz  NOT NULL DEFAULT now(),
    modifie_par   varchar(50)  NOT NULL,
    CONSTRAINT ck_cloture_mois_premier CHECK (extract(day FROM mois) = 1),
    CONSTRAINT uq_cloture_mois UNIQUE (station_id, mois)
);
SELECT audit.activer_audit('reporting.cloture_mois');

-- Compte d'exploitation fige a la cloture : une ligne par rubrique (et par produit pour les carburants).
-- montant nul = cout inconnu (aucun prix d'achat convenu a la date des ventes).
CREATE TABLE reporting.compte_fige (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    cloture_id    bigint        NOT NULL REFERENCES reporting.cloture_mois (id) ON DELETE CASCADE,
    rubrique      varchar(20)   NOT NULL,
    produit_id    bigint        REFERENCES referentiel.produit (id),
    litres        numeric(14,2),
    montant       numeric(15,0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT ck_compte_fige_rubrique CHECK (rubrique IN ('CHIFFRE_AFFAIRES', 'COUT_ACHAT', 'ECART_STOCK',
        'VENTES_ARTICLES', 'COUT_ARTICLES', 'REMISES', 'DEPENSES'))
);
CREATE INDEX ix_compte_fige_cloture ON reporting.compte_fige (cloture_id);
SELECT audit.activer_audit('reporting.compte_fige');

-- ---------------------------------------------------------------------
-- Regularisation : correction d'un mois clos, comptee dans le resultat du mois comptable (mois en cours a la
-- saisie). En insertion seule : une regularisation erronee se corrige par une regularisation contraire.
-- montant : effet sur le resultat (positif = l'ameliore).
-- ---------------------------------------------------------------------
CREATE TABLE reporting.regularisation (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id      bigint        NOT NULL REFERENCES referentiel.station (id),
    mois_concerne   date          NOT NULL,
    mois_comptable  date          NOT NULL,
    poste           varchar(20)   NOT NULL,
    montant         numeric(15,0) NOT NULL CHECK (montant <> 0),
    motif           varchar(500)  NOT NULL,
    reference       varchar(80),
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT ck_regularisation_poste CHECK (poste IN ('CHIFFRE_AFFAIRES', 'COUT_ACHAT', 'STOCK', 'DEPENSES', 'AUTRE')),
    CONSTRAINT ck_regularisation_mois CHECK (extract(day FROM mois_concerne) = 1 AND extract(day FROM mois_comptable) = 1
        AND mois_comptable > mois_concerne)
);
CREATE INDEX ix_regularisation_comptable ON reporting.regularisation (station_id, mois_comptable);
CREATE INDEX ix_regularisation_concerne ON reporting.regularisation (station_id, mois_concerne);
SELECT audit.activer_audit('reporting.regularisation');

-- ---------------------------------------------------------------------
-- Budget mensuel : ventes par station et par produit (litres, chiffre d'affaires), depenses par station.
-- ---------------------------------------------------------------------
CREATE TABLE reporting.budget_vente (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint        NOT NULL REFERENCES referentiel.station (id),
    mois          date          NOT NULL,
    produit_id    bigint        NOT NULL REFERENCES referentiel.produit (id),
    litres        numeric(14,2) NOT NULL DEFAULT 0 CHECK (litres >= 0),
    montant       numeric(15,0) NOT NULL DEFAULT 0 CHECK (montant >= 0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT ck_budget_vente_mois CHECK (extract(day FROM mois) = 1),
    CONSTRAINT uq_budget_vente UNIQUE (station_id, mois, produit_id)
);
SELECT audit.activer_audit('reporting.budget_vente');

CREATE TABLE reporting.budget_depense (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint        NOT NULL REFERENCES referentiel.station (id),
    mois          date          NOT NULL,
    montant       numeric(15,0) NOT NULL DEFAULT 0 CHECK (montant >= 0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT ck_budget_depense_mois CHECK (extract(day FROM mois) = 1),
    CONSTRAINT uq_budget_depense UNIQUE (station_id, mois)
);
SELECT audit.activer_audit('reporting.budget_depense');

-- ---------------------------------------------------------------------
-- La Direction lit le compte d'exploitation (marge) et peut cloturer, comme le controle de gestion et la comptabilite
-- ---------------------------------------------------------------------
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, 'CLOTURE_MENSUELLE'
FROM securite.role r
WHERE r.code = 'DIRECTION'
  AND NOT EXISTS (SELECT 1 FROM securite.role_permission p WHERE p.role_id = r.id AND p.permission = 'CLOTURE_MENSUELLE');
