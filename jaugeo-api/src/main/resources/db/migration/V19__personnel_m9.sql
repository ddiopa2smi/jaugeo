-- =====================================================================
-- Lot 2, sprint M9 : personnel de station
-- Employes et historique de leurs affectations ; planning par poste (matin, soir, nuit) ; feuilles de caisse
-- par pompiste dans la journee (releve d'index a chaque changement de poste, un pompiste par pistolet),
-- ecart individuel fige a la cloture. Les feuilles s'activent station par station.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Employe : rattache a une station ; identifiant = compte Jaugeo eventuel (pompiste qui saisit lui-meme).
-- ---------------------------------------------------------------------
CREATE TABLE personnel.employe (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint       NOT NULL REFERENCES referentiel.station (id),
    matricule     varchar(20),
    nom           varchar(60)  NOT NULL,
    prenom        varchar(60)  NOT NULL,
    telephone     varchar(30),
    fonction      varchar(12)  NOT NULL DEFAULT 'POMPISTE',
    date_entree   date,
    date_sortie   date,
    identifiant   varchar(50),
    actif         boolean      NOT NULL DEFAULT true,
    version       integer      NOT NULL DEFAULT 0,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL,
    modifie_le    timestamptz  NOT NULL DEFAULT now(),
    modifie_par   varchar(50)  NOT NULL,
    CONSTRAINT ck_employe_fonction CHECK (fonction IN ('POMPISTE', 'CAISSIER', 'CHEF_PISTE', 'GERANT', 'GARDIEN', 'AUTRE')),
    CONSTRAINT ck_employe_dates CHECK (date_sortie IS NULL OR date_entree IS NULL OR date_sortie >= date_entree)
);
CREATE UNIQUE INDEX uq_employe_matricule ON personnel.employe (upper(matricule)) WHERE matricule IS NOT NULL;
CREATE UNIQUE INDEX uq_employe_identifiant ON personnel.employe (lower(identifiant)) WHERE identifiant IS NOT NULL;
CREATE INDEX ix_employe_station ON personnel.employe (station_id, actif);
SELECT audit.activer_audit('personnel.employe');

-- Historique des affectations : une ligne par passage dans une station (fin nulle = affectation en cours)
CREATE TABLE personnel.affectation (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    employe_id    bigint       NOT NULL REFERENCES personnel.employe (id),
    station_id    bigint       NOT NULL REFERENCES referentiel.station (id),
    debut         date         NOT NULL,
    fin           date,
    version       integer      NOT NULL DEFAULT 0,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL,
    modifie_le    timestamptz  NOT NULL DEFAULT now(),
    modifie_par   varchar(50)  NOT NULL,
    CONSTRAINT ck_affectation_dates CHECK (fin IS NULL OR fin >= debut)
);
CREATE INDEX ix_affectation_employe ON personnel.affectation (employe_id, debut);
SELECT audit.activer_audit('personnel.affectation');

-- ---------------------------------------------------------------------
-- Planning : qui tient quel poste, quel jour.
-- ---------------------------------------------------------------------
CREATE TABLE personnel.planning (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint       NOT NULL REFERENCES referentiel.station (id),
    date_jour     date         NOT NULL,
    poste         varchar(6)   NOT NULL,
    employe_id    bigint       NOT NULL REFERENCES personnel.employe (id),
    version       integer      NOT NULL DEFAULT 0,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL,
    modifie_le    timestamptz  NOT NULL DEFAULT now(),
    modifie_par   varchar(50)  NOT NULL,
    CONSTRAINT ck_planning_poste CHECK (poste IN ('MATIN', 'SOIR', 'NUIT')),
    CONSTRAINT uq_planning UNIQUE (station_id, date_jour, poste, employe_id)
);
SELECT audit.activer_audit('personnel.planning');

-- Feuilles de caisse par pompiste : activees station par station (absence de ligne = inactives)
CREATE TABLE personnel.parametre_station (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id       bigint       NOT NULL UNIQUE REFERENCES referentiel.station (id),
    feuilles_caisse  boolean      NOT NULL DEFAULT false,
    version          integer      NOT NULL DEFAULT 0,
    cree_le          timestamptz  NOT NULL DEFAULT now(),
    cree_par         varchar(50)  NOT NULL,
    modifie_le       timestamptz  NOT NULL DEFAULT now(),
    modifie_par      varchar(50)  NOT NULL
);
SELECT audit.activer_audit('personnel.parametre_station');

-- ---------------------------------------------------------------------
-- Poste d'une journee, feuille de caisse d'un pompiste pour ce poste, et releve de fin de poste de chacun
-- de ses pistolets. L'index de debut est l'index de fin du poste precedent (ou l'index de depart de la journee).
-- montant_du et ecart (remis - du) sont figes a la cloture de la journee, effaces a son renvoi.
-- ---------------------------------------------------------------------
CREATE TABLE personnel.poste (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid         NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id    bigint       NOT NULL REFERENCES exploitation.journee (id),
    station_id    bigint       NOT NULL REFERENCES referentiel.station (id),
    date_jour     date         NOT NULL,
    poste         varchar(6)   NOT NULL,
    observation   varchar(500),
    version       integer      NOT NULL DEFAULT 0,
    cree_le       timestamptz  NOT NULL DEFAULT now(),
    cree_par      varchar(50)  NOT NULL,
    modifie_le    timestamptz  NOT NULL DEFAULT now(),
    modifie_par   varchar(50)  NOT NULL,
    CONSTRAINT ck_poste_poste CHECK (poste IN ('MATIN', 'SOIR', 'NUIT')),
    CONSTRAINT uq_poste_journee UNIQUE (journee_id, poste)
);
SELECT audit.activer_audit('personnel.poste');

CREATE TABLE personnel.feuille_caisse (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    poste_id      bigint        NOT NULL REFERENCES personnel.poste (id) ON DELETE CASCADE,
    journee_id    bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id    bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour     date          NOT NULL,
    poste         varchar(6)    NOT NULL,
    employe_id    bigint        NOT NULL REFERENCES personnel.employe (id),
    especes       numeric(15,0) NOT NULL DEFAULT 0 CHECK (especes >= 0),
    electronique  numeric(15,0) NOT NULL DEFAULT 0 CHECK (electronique >= 0),
    bons          numeric(15,0) NOT NULL DEFAULT 0 CHECK (bons >= 0),
    cartes        numeric(15,0) NOT NULL DEFAULT 0 CHECK (cartes >= 0),
    montant_du    numeric(15,0),
    ecart         numeric(15,0),
    observation   varchar(500),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT uq_feuille_poste_employe UNIQUE (poste_id, employe_id),
    CONSTRAINT ck_feuille_figee CHECK ((montant_du IS NULL) = (ecart IS NULL))
);
CREATE INDEX ix_feuille_employe ON personnel.feuille_caisse (employe_id, date_jour);
CREATE INDEX ix_feuille_station ON personnel.feuille_caisse (station_id, date_jour);
CREATE INDEX ix_feuille_journee ON personnel.feuille_caisse (journee_id);
SELECT audit.activer_audit('personnel.feuille_caisse');

CREATE TABLE personnel.ligne_feuille (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    feuille_id    bigint        NOT NULL REFERENCES personnel.feuille_caisse (id) ON DELETE CASCADE,
    poste_id      bigint        NOT NULL REFERENCES personnel.poste (id) ON DELETE CASCADE,
    pistolet_id   bigint        NOT NULL REFERENCES referentiel.pistolet (id),
    index_fin     numeric(14,2) NOT NULL CHECK (index_fin >= 0),
    remise_cuve   numeric(12,2) NOT NULL DEFAULT 0 CHECK (remise_cuve >= 0),
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    -- Un seul pompiste par pistolet pendant un poste
    CONSTRAINT uq_ligne_poste_pistolet UNIQUE (poste_id, pistolet_id)
);
CREATE INDEX ix_ligne_feuille ON personnel.ligne_feuille (feuille_id);
SELECT audit.activer_audit('personnel.ligne_feuille');

-- ---------------------------------------------------------------------
-- Gestion du personnel et du planning : gerant, superviseur, administrateur
-- ---------------------------------------------------------------------
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, 'PERSONNEL_GERER'
FROM securite.role r
WHERE r.code IN ('ADMINISTRATEUR', 'SUPERVISEUR', 'GERANT');
