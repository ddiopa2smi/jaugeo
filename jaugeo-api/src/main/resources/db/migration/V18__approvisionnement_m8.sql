-- =====================================================================
-- Lot 2, sprint M8 : approvisionnement et logistique
-- Fournisseurs (depots) et prix d'achat convenus ; transporteurs (contrat, tolerance de manquant, penalite,
-- citernes) ; tournees citernes a plusieurs livraisons (commandee -> chargee -> en route -> depotee) ;
-- penalites des livraisons hors tolerance ; factures fournisseur rapprochees du BL et de la quantite recue.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Fournisseurs : depot de chargement, delai de livraison ; le depot principal sert aux propositions de commande.
-- facture_a_15 : le fournisseur facture les quantites a 15 °C (comparees a la quantite a 15 °C du BL).
-- ---------------------------------------------------------------------
CREATE TABLE approvisionnement.fournisseur (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    nom              varchar(80)   NOT NULL,
    telephone        varchar(30),
    adresse          varchar(200),
    delai_livraison  integer       NOT NULL DEFAULT 1 CHECK (delai_livraison BETWEEN 0 AND 30),
    principal        boolean       NOT NULL DEFAULT false,
    facture_a_15     boolean       NOT NULL DEFAULT false,
    actif            boolean       NOT NULL DEFAULT true,
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL
);
CREATE UNIQUE INDEX uq_fournisseur_nom ON approvisionnement.fournisseur (upper(nom));
CREATE UNIQUE INDEX uq_fournisseur_principal ON approvisionnement.fournisseur (principal) WHERE principal;
SELECT audit.activer_audit('approvisionnement.fournisseur');

-- Prix d'achat convenu par produit, date (le plus recent en vigueur a la date du BL)
CREATE TABLE approvisionnement.prix_achat (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    fournisseur_id  bigint        NOT NULL REFERENCES approvisionnement.fournisseur (id),
    produit_id      bigint        NOT NULL REFERENCES referentiel.produit (id),
    debut           date          NOT NULL,
    prix            numeric(10,2) NOT NULL CHECK (prix > 0),
    version         integer       NOT NULL DEFAULT 0,
    cree_le         timestamptz   NOT NULL DEFAULT now(),
    cree_par        varchar(50)   NOT NULL,
    modifie_le      timestamptz   NOT NULL DEFAULT now(),
    modifie_par     varchar(50)   NOT NULL,
    CONSTRAINT uq_prix_achat UNIQUE (fournisseur_id, produit_id, debut)
);
SELECT audit.activer_audit('approvisionnement.prix_achat');

-- ---------------------------------------------------------------------
-- Transporteurs : contrat, tolerance de manquant (nulle = tolerance du reseau), penalite forfaitaire par
-- livraison hors tolerance imputee au transporteur ; citernes (immatriculation, capacite).
-- ---------------------------------------------------------------------
CREATE TABLE approvisionnement.transporteur (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    nom                varchar(80)   NOT NULL,
    telephone          varchar(30),
    contrat_reference  varchar(80),
    contrat_debut      date,
    contrat_fin        date,
    tolerance_pct      numeric(4,2)  CHECK (tolerance_pct >= 0 AND tolerance_pct <= 5),
    penalite           numeric(15,0) CHECK (penalite > 0),
    actif              boolean       NOT NULL DEFAULT true,
    observation        varchar(500),
    version            integer       NOT NULL DEFAULT 0,
    cree_le            timestamptz   NOT NULL DEFAULT now(),
    cree_par           varchar(50)   NOT NULL,
    modifie_le         timestamptz   NOT NULL DEFAULT now(),
    modifie_par        varchar(50)   NOT NULL,
    CONSTRAINT ck_transporteur_contrat CHECK (contrat_fin IS NULL OR contrat_debut IS NULL OR contrat_fin >= contrat_debut)
);
CREATE UNIQUE INDEX uq_transporteur_nom ON approvisionnement.transporteur (upper(nom));
SELECT audit.activer_audit('approvisionnement.transporteur');

CREATE TABLE approvisionnement.citerne (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    transporteur_id   bigint        NOT NULL REFERENCES approvisionnement.transporteur (id),
    immatriculation   varchar(20)   NOT NULL,
    capacite_litres   numeric(12,2) NOT NULL CHECK (capacite_litres > 0),
    actif             boolean       NOT NULL DEFAULT true,
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL
);
CREATE UNIQUE INDEX uq_citerne_immatriculation ON approvisionnement.citerne (upper(immatriculation));
SELECT audit.activer_audit('approvisionnement.citerne');

-- ---------------------------------------------------------------------
-- Tournee : une citerne chargee a un depot, puis une ou plusieurs livraisons (station, produit).
-- Statut saisi : COMMANDEE -> CHARGEE (BL de chaque livraison) -> EN_ROUTE ; ANNULEE. Une tournee est
-- depotee quand chaque livraison a sa reception (stock.reception.livraison_id).
-- ---------------------------------------------------------------------
CREATE SEQUENCE approvisionnement.numero_tournee;

CREATE TABLE approvisionnement.tournee (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    numero                   varchar(20)   NOT NULL UNIQUE,
    fournisseur_id           bigint        NOT NULL REFERENCES approvisionnement.fournisseur (id),
    transporteur_id          bigint        NOT NULL REFERENCES approvisionnement.transporteur (id),
    citerne_id               bigint        REFERENCES approvisionnement.citerne (id),
    immatriculation          varchar(20),
    chauffeur                varchar(80),
    date_chargement_prevue   date          NOT NULL,
    date_livraison_prevue    date          NOT NULL,
    statut                   varchar(10)   NOT NULL DEFAULT 'COMMANDEE',
    charge_le                date,
    scelles                  varchar(120),
    parti_le                 timestamptz,
    motif_annulation         varchar(500),
    observation              varchar(500),
    version                  integer       NOT NULL DEFAULT 0,
    cree_le                  timestamptz   NOT NULL DEFAULT now(),
    cree_par                 varchar(50)   NOT NULL,
    modifie_le               timestamptz   NOT NULL DEFAULT now(),
    modifie_par              varchar(50)   NOT NULL,
    CONSTRAINT ck_tournee_statut CHECK (statut IN ('COMMANDEE', 'CHARGEE', 'EN_ROUTE', 'ANNULEE')),
    CONSTRAINT ck_tournee_dates CHECK (date_livraison_prevue >= date_chargement_prevue),
    CONSTRAINT ck_tournee_chargee CHECK (statut NOT IN ('CHARGEE', 'EN_ROUTE') OR charge_le IS NOT NULL),
    CONSTRAINT ck_tournee_annulee CHECK ((statut = 'ANNULEE') = (motif_annulation IS NOT NULL))
);
CREATE INDEX ix_tournee_statut ON approvisionnement.tournee (statut, date_livraison_prevue);
SELECT audit.activer_audit('approvisionnement.tournee');

CREATE TABLE approvisionnement.livraison (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    tournee_id           bigint        NOT NULL REFERENCES approvisionnement.tournee (id) ON DELETE CASCADE,
    ordre                integer       NOT NULL,
    station_id           bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id           bigint        NOT NULL REFERENCES referentiel.produit (id),
    quantite_commandee   numeric(12,2) NOT NULL CHECK (quantite_commandee > 0),
    numero_bl            varchar(30),
    quantite_bl          numeric(12,2) CHECK (quantite_bl > 0),
    quantite_bl_15       numeric(12,2) CHECK (quantite_bl_15 > 0),
    version              integer       NOT NULL DEFAULT 0,
    cree_le              timestamptz   NOT NULL DEFAULT now(),
    cree_par             varchar(50)   NOT NULL,
    modifie_le           timestamptz   NOT NULL DEFAULT now(),
    modifie_par          varchar(50)   NOT NULL,
    CONSTRAINT ck_livraison_bl CHECK ((numero_bl IS NULL) = (quantite_bl IS NULL))
);
CREATE INDEX ix_livraison_tournee ON approvisionnement.livraison (tournee_id, ordre);
CREATE INDEX ix_livraison_station ON approvisionnement.livraison (station_id, produit_id);
SELECT audit.activer_audit('approvisionnement.livraison');

-- La reception du gerant se rattache a la livraison attendue (une reception par livraison)
ALTER TABLE stock.reception ADD COLUMN livraison_id bigint REFERENCES approvisionnement.livraison (id);
CREATE UNIQUE INDEX uq_reception_livraison ON stock.reception (livraison_id) WHERE livraison_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Penalite forfaitaire du transporteur pour une livraison hors tolerance : creee a la cloture de la journee
-- de reception, retiree au renvoi tant qu'elle n'est pas reglee.
-- ---------------------------------------------------------------------
CREATE TABLE approvisionnement.penalite (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    transporteur_id       bigint        NOT NULL REFERENCES approvisionnement.transporteur (id),
    livraison_id          bigint        NOT NULL UNIQUE REFERENCES approvisionnement.livraison (id),
    journee_id            bigint        NOT NULL REFERENCES exploitation.journee (id),
    date_jour             date          NOT NULL,
    manquant_litres       numeric(12,2) NOT NULL CHECK (manquant_litres > 0),
    manquant_pct          numeric(7,2)  NOT NULL,
    tolerance_pct         numeric(4,2)  NOT NULL,
    montant               numeric(15,0) NOT NULL CHECK (montant > 0),
    statut                varchar(10)   NOT NULL DEFAULT 'A_REGLER',
    reference_reglement   varchar(80),
    regle_le              date,
    regle_par             varchar(50),
    version               integer       NOT NULL DEFAULT 0,
    cree_le               timestamptz   NOT NULL DEFAULT now(),
    cree_par              varchar(50)   NOT NULL,
    modifie_le            timestamptz   NOT NULL DEFAULT now(),
    modifie_par           varchar(50)   NOT NULL,
    CONSTRAINT ck_penalite_statut CHECK (statut IN ('A_REGLER', 'REGLE')),
    CONSTRAINT ck_penalite_reglement CHECK ((statut = 'REGLE') = (regle_le IS NOT NULL AND reference_reglement IS NOT NULL))
);
CREATE INDEX ix_penalite_journee ON approvisionnement.penalite (journee_id);
SELECT audit.activer_audit('approvisionnement.penalite');

-- ---------------------------------------------------------------------
-- Factures fournisseur : une ligne par BL ; controle contre le BL, la quantite recue et le prix convenu.
-- SAISIE -> BON_A_PAYER ou EN_LITIGE (motif) ; ANNULEE.
-- ---------------------------------------------------------------------
CREATE TABLE approvisionnement.facture_fournisseur (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    fournisseur_id   bigint        NOT NULL REFERENCES approvisionnement.fournisseur (id),
    numero           varchar(40)   NOT NULL,
    date_facture     date          NOT NULL,
    statut           varchar(12)   NOT NULL DEFAULT 'SAISIE',
    motif_decision   varchar(500),
    decide_par       varchar(50),
    decide_le        timestamptz,
    observation      varchar(500),
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_facture_fournisseur_statut CHECK (statut IN ('SAISIE', 'BON_A_PAYER', 'EN_LITIGE', 'ANNULEE'))
);
CREATE UNIQUE INDEX uq_facture_fournisseur ON approvisionnement.facture_fournisseur (fournisseur_id, upper(numero));
SELECT audit.activer_audit('approvisionnement.facture_fournisseur');

CREATE TABLE approvisionnement.ligne_facture_fournisseur (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    facture_id     bigint        NOT NULL REFERENCES approvisionnement.facture_fournisseur (id) ON DELETE CASCADE,
    ordre          integer       NOT NULL,
    numero_bl      varchar(30)   NOT NULL,
    produit_id     bigint        NOT NULL REFERENCES referentiel.produit (id),
    quantite       numeric(12,2) NOT NULL CHECK (quantite > 0),
    prix_unitaire  numeric(10,2) NOT NULL CHECK (prix_unitaire > 0),
    montant        numeric(15,0) NOT NULL,
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL
);
CREATE INDEX ix_ligne_facture_fournisseur ON approvisionnement.ligne_facture_fournisseur (facture_id, ordre);
CREATE INDEX ix_ligne_facture_bl ON approvisionnement.ligne_facture_fournisseur (upper(numero_bl));
SELECT audit.activer_audit('approvisionnement.ligne_facture_fournisseur');
