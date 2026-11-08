-- =====================================================================
-- Sprint 5 : clients en compte, bons a credit, creances ; syntheses mensuelles
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Client en compte. Le recouvrement dit qui encaisse ses bons : la direction (deduits de la recette,
-- hors caisse du gerant) ou le gerant (creance que le gerant doit recouvrer). Plafond nul = sans plafond.
-- ---------------------------------------------------------------------
CREATE TABLE clients.client (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    code           varchar(20)   NOT NULL UNIQUE,
    nom            varchar(120)  NOT NULL,
    telephone      varchar(30),
    recouvrement   varchar(10)   NOT NULL,
    plafond        numeric(15,0) CHECK (plafond > 0),
    actif          boolean       NOT NULL DEFAULT true,
    observation    varchar(500),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT ck_client_recouvrement CHECK (recouvrement IN ('DIRECTION', 'GERANT'))
);
SELECT audit.activer_audit('clients.client');

-- ---------------------------------------------------------------------
-- Bon a credit (vente a credit) saisi dans la journee : litres d'un produit, valorises au prix du jour.
-- Un numero de bon n'est utilise qu'une fois par station.
-- ---------------------------------------------------------------------
CREATE TABLE clients.bon (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id      bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id      bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour       date          NOT NULL,
    client_id       bigint        NOT NULL REFERENCES clients.client (id),
    numero          varchar(30)   NOT NULL,
    produit_id      bigint        NOT NULL REFERENCES referentiel.produit (id),
    litres          numeric(12,2) NOT NULL CHECK (litres > 0),
    prix_unitaire   numeric(12,2) NOT NULL CHECK (prix_unitaire > 0),
    montant         numeric(15,0) NOT NULL CHECK (montant > 0),
    recouvrement    varchar(10)   NOT NULL,
    immatriculation varchar(30),
    porteur         varchar(80),
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT ck_bon_recouvrement CHECK (recouvrement IN ('DIRECTION', 'GERANT'))
);
CREATE UNIQUE INDEX uq_bon_numero ON clients.bon (station_id, upper(numero));
CREATE INDEX ix_bon_journee ON clients.bon (journee_id);
CREATE INDEX ix_bon_client ON clients.bon (client_id, station_id, date_jour);
SELECT audit.activer_audit('clients.bon');

-- ---------------------------------------------------------------------
-- Operation sur le compte d'un client a une station :
--   REPRISE    : creance existante au demarrage du suivi (solde d'ouverture) ;
--   REGLEMENT  : paiement du client. En especes a la station (ESPECES_STATION), il est saisi dans la journee
--                et entre dans la caisse du gerant ; les autres modes sont encaisses par le siege.
-- creance = reprises + bons - reglements
-- ---------------------------------------------------------------------
CREATE TABLE clients.operation_compte (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    client_id       bigint        NOT NULL REFERENCES clients.client (id),
    station_id      bigint        NOT NULL REFERENCES referentiel.station (id),
    date_operation  date          NOT NULL,
    type_operation  varchar(10)   NOT NULL,
    mode            varchar(20),
    journee_id      bigint        REFERENCES exploitation.journee (id),
    montant         numeric(15,0) NOT NULL CHECK (montant > 0),
    reference       varchar(80),
    observation     varchar(500),
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT ck_operation_type CHECK (type_operation IN ('REPRISE', 'REGLEMENT')),
    CONSTRAINT ck_operation_mode CHECK (
        (type_operation = 'REPRISE' AND mode IS NULL AND journee_id IS NULL)
        OR (type_operation = 'REGLEMENT' AND mode IN ('ESPECES_STATION', 'ESPECES_SIEGE', 'VIREMENT', 'CHEQUE', 'COMPENSATION')
            AND (mode = 'ESPECES_STATION') = (journee_id IS NOT NULL)))
);
CREATE INDEX ix_operation_client ON clients.operation_compte (client_id, station_id, date_operation);
CREATE INDEX ix_operation_journee ON clients.operation_compte (journee_id) WHERE journee_id IS NOT NULL;
SELECT audit.activer_audit('clients.operation_compte');

-- ---------------------------------------------------------------------
-- Recette de la journee : les recouvrements de creances encaisses en especes a la station
-- s'ajoutent au montant a verser.
-- ---------------------------------------------------------------------
ALTER TABLE tresorerie.recette_journee
    ADD COLUMN recouvrements numeric(15,0) NOT NULL DEFAULT 0 CHECK (recouvrements >= 0);
ALTER TABLE tresorerie.recette_journee DROP CONSTRAINT ck_recette_calcul;
ALTER TABLE tresorerie.recette_journee ADD CONSTRAINT ck_recette_calcul
    CHECK (montant_a_verser = ventes - bons_direction - bons_gerant - depenses - complements + recouvrements);

-- ---------------------------------------------------------------------
-- Observation du controle de gestion sur la synthese mensuelle d'une station (colonne OBSERVATIONS)
-- ---------------------------------------------------------------------
CREATE TABLE reporting.commentaire_mensuel (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid         uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id   bigint        NOT NULL REFERENCES referentiel.station (id),
    mois         date          NOT NULL CHECK (extract(day FROM mois) = 1),
    texte        varchar(1000) NOT NULL,
    version      integer       NOT NULL DEFAULT 0,
    cree_le      timestamptz   NOT NULL DEFAULT now(),
    cree_par     varchar(50)   NOT NULL,
    modifie_le   timestamptz   NOT NULL DEFAULT now(),
    modifie_par  varchar(50)   NOT NULL,
    CONSTRAINT uq_commentaire_station_mois UNIQUE (station_id, mois)
);
SELECT audit.activer_audit('reporting.commentaire_mensuel');

-- Le comptable redige aussi l'analyse mensuelle
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, 'CLOTURE_MENSUELLE' FROM securite.role r WHERE r.code = 'COMPTABLE'
ON CONFLICT DO NOTHING;
