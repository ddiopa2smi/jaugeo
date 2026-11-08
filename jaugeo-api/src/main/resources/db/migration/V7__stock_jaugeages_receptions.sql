-- =====================================================================
-- Sprint 3 : jaugeages, receptions (BL), stock theorique, ecarts de stock, complements citerne
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Tolerance d'ecart de stock par produit, en % des sorties de la periode
-- (cahier des charges, regle 2 : 0,5 % Gasoil, 0,6 % essences).
-- ---------------------------------------------------------------------
ALTER TABLE referentiel.produit
    ADD COLUMN tolerance_ecart_pct numeric(4,2) NOT NULL DEFAULT 0.50
        CONSTRAINT ck_produit_tolerance CHECK (tolerance_ecart_pct BETWEEN 0 AND 10);
UPDATE referentiel.produit SET tolerance_ecart_pct = 0.60, modifie_par = 'migration'
WHERE code IN ('SUPER', 'ESSENCE_PIROGUE');

-- ---------------------------------------------------------------------
-- Jaugeage d'une cuve pour une journee.
-- CLOTURE : stock physique de fin de journee (obligatoire a chaque cloture).
-- OUVERTURE : stock de depart, saisi seulement quand aucun jaugeage de cloture de la veille n'existe
-- (premiere journee de la cuve). Hauteur nulle = volume lu directement (jauge electronique, barème absent).
-- ---------------------------------------------------------------------
CREATE TABLE stock.jaugeage (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id     bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id     bigint        NOT NULL REFERENCES referentiel.station (id),
    cuve_id        bigint        NOT NULL REFERENCES referentiel.cuve (id),
    date_jour      date          NOT NULL,
    moment         varchar(10)   NOT NULL,
    hauteur_mm     integer       CHECK (hauteur_mm >= 0),
    volume_litres  numeric(12,2) NOT NULL CHECK (volume_litres >= 0),
    observation    varchar(500),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT ck_jaugeage_moment CHECK (moment IN ('OUVERTURE', 'CLOTURE')),
    CONSTRAINT uq_jaugeage_journee_cuve_moment UNIQUE (journee_id, cuve_id, moment)
);
CREATE INDEX ix_jaugeage_cuve_date ON stock.jaugeage (cuve_id, date_jour);
SELECT audit.activer_audit('stock.jaugeage');

-- ---------------------------------------------------------------------
-- Reception citerne liee au bon de livraison. Un BL n'est receptionne qu'une fois par fournisseur.
-- La quantite recue (mesuree par jauge avant/apres) entre en stock ; le manquant au-dela de la
-- tolerance transport devient un complement citerne a la charge du transporteur.
-- ---------------------------------------------------------------------
CREATE TABLE stock.reception (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id       bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id       bigint        NOT NULL REFERENCES referentiel.station (id),
    cuve_id          bigint        NOT NULL REFERENCES referentiel.cuve (id),
    produit_id       bigint        NOT NULL REFERENCES referentiel.produit (id),
    date_jour        date          NOT NULL,
    numero_bl        varchar(30)   NOT NULL,
    fournisseur      varchar(80)   NOT NULL,
    transporteur     varchar(80),
    chauffeur        varchar(80),
    immatriculation  varchar(20),
    scelles          varchar(120),
    quantite_bl      numeric(12,2) NOT NULL CHECK (quantite_bl > 0),
    jauge_avant      numeric(12,2) CHECK (jauge_avant >= 0),
    jauge_apres      numeric(12,2) CHECK (jauge_apres >= 0),
    quantite_recue   numeric(12,2) NOT NULL CHECK (quantite_recue > 0),
    temperature_c    numeric(4,1),
    densite          numeric(5,4) CHECK (densite > 0 AND densite < 2),
    observation      varchar(500),
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_reception_jauges CHECK (jauge_avant IS NULL OR jauge_apres IS NULL OR jauge_apres > jauge_avant)
);
CREATE UNIQUE INDEX uq_reception_bl ON stock.reception (upper(fournisseur), upper(numero_bl));
CREATE INDEX ix_reception_journee ON stock.reception (journee_id);
CREATE INDEX ix_reception_cuve_date ON stock.reception (cuve_id, date_jour);
SELECT audit.activer_audit('stock.reception');

-- ---------------------------------------------------------------------
-- Ecart de stock fige a la cloture, par journee et par cuve.
-- Theorique = physique de la veille + receptions - sorties nettes ; ecart = physique - theorique.
-- Les cumuls portent sur le mois civil, jusqu'a ce jour inclus (pourcentage des sorties du mois).
-- ---------------------------------------------------------------------
CREATE TABLE stock.ecart_stock (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id           bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id           bigint        NOT NULL REFERENCES referentiel.station (id),
    cuve_id              bigint        NOT NULL REFERENCES referentiel.cuve (id),
    produit_id           bigint        NOT NULL REFERENCES referentiel.produit (id),
    date_jour            date          NOT NULL,
    stock_depart         numeric(12,2) NOT NULL,
    receptions           numeric(12,2) NOT NULL CHECK (receptions >= 0),
    sorties              numeric(12,2) NOT NULL CHECK (sorties >= 0),
    stock_theorique      numeric(12,2) NOT NULL CHECK (stock_theorique >= 0),
    stock_physique       numeric(12,2) NOT NULL CHECK (stock_physique >= 0),
    ecart_litres         numeric(12,2) NOT NULL,
    prix_unitaire        numeric(10,2),
    ecart_valeur         numeric(15,0),
    cumul_sorties_mois   numeric(14,2) NOT NULL,
    cumul_ecart_mois     numeric(14,2) NOT NULL,
    ecart_pct_mois       numeric(7,2),
    tolerance_pct        numeric(4,2)  NOT NULL,
    niveau               varchar(10)   NOT NULL,
    observation          varchar(500),
    version              integer       NOT NULL DEFAULT 0,
    cree_le              timestamptz   NOT NULL DEFAULT now(),
    cree_par             varchar(50)   NOT NULL,
    modifie_le           timestamptz   NOT NULL DEFAULT now(),
    modifie_par          varchar(50)   NOT NULL,
    CONSTRAINT ck_ecart_niveau CHECK (niveau IN ('NORMAL', 'ALERTE', 'ENQUETE')),
    CONSTRAINT ck_ecart_calcul CHECK (ecart_litres = stock_physique - stock_theorique),
    CONSTRAINT uq_ecart_journee_cuve UNIQUE (journee_id, cuve_id)
);
CREATE INDEX ix_ecart_cuve_date ON stock.ecart_stock (cuve_id, date_jour);
CREATE INDEX ix_ecart_station_date ON stock.ecart_stock (station_id, date_jour);
SELECT audit.activer_audit('stock.ecart_stock');

-- ---------------------------------------------------------------------
-- Complement citerne : manquant de livraison paye par le transporteur, suivi jusqu'au reglement.
-- Cree a la cloture de la journee de reception, retire au renvoi tant qu'il n'est pas regle.
-- ---------------------------------------------------------------------
CREATE TABLE stock.complement_citerne (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    reception_id          bigint        NOT NULL UNIQUE REFERENCES stock.reception (id),
    journee_id            bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id            bigint        NOT NULL REFERENCES referentiel.station (id),
    date_jour             date          NOT NULL,
    litres                numeric(12,2) NOT NULL CHECK (litres > 0),
    prix_unitaire         numeric(10,2),
    montant               numeric(15,0),
    statut                varchar(10)   NOT NULL DEFAULT 'A_REGLER',
    reference_reglement   varchar(80),
    regle_le              date,
    regle_par             varchar(50),
    version               integer       NOT NULL DEFAULT 0,
    cree_le               timestamptz   NOT NULL DEFAULT now(),
    cree_par              varchar(50)   NOT NULL,
    modifie_le            timestamptz   NOT NULL DEFAULT now(),
    modifie_par           varchar(50)   NOT NULL,
    CONSTRAINT ck_complement_statut CHECK (statut IN ('A_REGLER', 'REGLE')),
    CONSTRAINT ck_complement_reglement CHECK ((statut = 'REGLE') = (regle_le IS NOT NULL AND reference_reglement IS NOT NULL))
);
CREATE INDEX ix_complement_station_statut ON stock.complement_citerne (station_id, statut);
SELECT audit.activer_audit('stock.complement_citerne');

-- Le gerant (et le pompiste) consultent ; la logistique regle les complements (RECEPTION_VALIDER, deja attribuee).
