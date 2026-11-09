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
