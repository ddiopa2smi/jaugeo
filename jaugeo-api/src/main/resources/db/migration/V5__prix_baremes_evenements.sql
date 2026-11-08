-- =====================================================================
-- Jaugeo - V5 (sprint 1) : prix dates, certificats de bareme,
-- historique des equipements
-- =====================================================================

SET LOCAL app.utilisateur = 'migration';

-- btree_gist : contrainte d'exclusion combinant egalite (station, produit) et chevauchement de periodes
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Tarification
-- ---------------------------------------------------------------------

-- Un changement de prix regroupe les nouveaux prix d'une decision (ex. arrete de prix
-- structure applique a tout le reseau a une heure donnee).
CREATE TABLE tarification.changement_prix (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid         uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    debut        timestamptz NOT NULL,
    motif        varchar(200) NOT NULL,
    reference    varchar(100),
    annule       boolean NOT NULL DEFAULT false,
    annule_le    timestamptz,
    annule_par   varchar(100),
    version      integer NOT NULL DEFAULT 0,
    cree_le      timestamptz NOT NULL DEFAULT now(),
    cree_par     varchar(100) NOT NULL,
    modifie_le   timestamptz NOT NULL DEFAULT now(),
    modifie_par  varchar(100) NOT NULL,
    CONSTRAINT ck_changement_annulation CHECK (annule = (annule_le IS NOT NULL))
);

-- Prix de vente d'un produit dans une station sur la periode [debut, fin).
-- fin NULL = prix en vigueur jusqu'a nouvel ordre.
CREATE TABLE tarification.prix_produit (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    changement_id  bigint NOT NULL REFERENCES tarification.changement_prix (id),
    station_id     bigint NOT NULL REFERENCES referentiel.station (id),
    produit_id     bigint NOT NULL REFERENCES referentiel.produit (id),
    prix_unitaire  numeric(10, 2) NOT NULL CHECK (prix_unitaire > 0),
    debut          timestamptz NOT NULL,
    fin            timestamptz,
    version        integer NOT NULL DEFAULT 0,
    cree_le        timestamptz NOT NULL DEFAULT now(),
    cree_par       varchar(100) NOT NULL,
    modifie_le     timestamptz NOT NULL DEFAULT now(),
    modifie_par    varchar(100) NOT NULL,
    CONSTRAINT ck_prix_periode CHECK (fin IS NULL OR fin > debut),
    -- Jamais deux prix pour le meme produit et la meme station au meme instant
    CONSTRAINT ex_prix_chevauchement EXCLUDE USING gist (
        station_id WITH =, produit_id WITH =, tstzrange(debut, fin, '[)') WITH &&)
);

CREATE INDEX ix_prix_recherche ON tarification.prix_produit (station_id, produit_id, debut DESC);
CREATE INDEX ix_prix_changement ON tarification.prix_produit (changement_id);

SELECT audit.activer_audit('tarification.changement_prix');
SELECT audit.activer_audit('tarification.prix_produit');

-- Reprise du classeur 2026 ----------------------------------------------
-- Prix au 1er janvier 2026 (Mbour : a son ouverture le 1er fevrier), puis hausse
-- Niandane du 16 aout 2026 (Gasoil 680 -> 755, Super 920 -> 990).
INSERT INTO tarification.changement_prix (debut, motif, reference, cree_par, modifie_par) VALUES
    (TIMESTAMPTZ '2026-01-01 00:00:00+00', 'Prix en vigueur au 1er janvier 2026', 'Reprise du classeur 2026', 'migration', 'migration'),
    (TIMESTAMPTZ '2026-08-16 00:00:00+00', 'Hausse des prix Gasoil et Super', 'Reprise du classeur 2026', 'migration', 'migration');

INSERT INTO tarification.prix_produit (changement_id, station_id, produit_id, prix_unitaire, debut, fin, cree_par, modifie_par)
SELECT c.id, st.id, p.id, x.prix, x.debut, x.fin, 'migration', 'migration'
FROM (VALUES
    ('NIA', 'GASOIL',          680, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-08-16 00:00:00+00'),
    ('NIA', 'SUPER',           920, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-08-16 00:00:00+00'),
    ('NIA', 'GASOIL',          755, TIMESTAMPTZ '2026-08-16 00:00:00+00', NULL),
    ('NIA', 'SUPER',           990, TIMESTAMPTZ '2026-08-16 00:00:00+00', NULL),
    ('HYD', 'ESSENCE_PIROGUE', 497, TIMESTAMPTZ '2026-01-01 00:00:00+00', NULL),
    ('KAY', 'ESSENCE_PIROGUE', 497, TIMESTAMPTZ '2026-01-01 00:00:00+00', NULL),
    ('POT', 'ESSENCE_PIROGUE', 497, TIMESTAMPTZ '2026-01-01 00:00:00+00', NULL),
    ('YOF', 'ESSENCE_PIROGUE', 497, TIMESTAMPTZ '2026-01-01 00:00:00+00', NULL),
    ('MBO', 'ESSENCE_PIROGUE', 497, TIMESTAMPTZ '2026-02-01 00:00:00+00', NULL)
) AS x (station, produit, prix, debut, fin)
JOIN referentiel.station st ON st.code = x.station
JOIN referentiel.produit p ON p.code = x.produit
JOIN tarification.changement_prix c
  ON c.debut = CASE WHEN x.debut = TIMESTAMPTZ '2026-08-16 00:00:00+00'
                    THEN TIMESTAMPTZ '2026-08-16 00:00:00+00' ELSE TIMESTAMPTZ '2026-01-01 00:00:00+00' END;

-- La direction fixe les prix ; l'administrateur les consulte seulement (separation des taches).

-- ---------------------------------------------------------------------
-- Certificat de bareme de la cuve
-- ---------------------------------------------------------------------
ALTER TABLE referentiel.cuve
    ADD COLUMN bareme_certificat varchar(100),
    ADD COLUMN bareme_date       date;

-- ---------------------------------------------------------------------
-- Historique des equipements (M1) : totalisateurs, etalonnages, scelles
-- ---------------------------------------------------------------------
CREATE TABLE referentiel.evenement_equipement (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id              bigint NOT NULL REFERENCES referentiel.station (id),
    pistolet_id             bigint REFERENCES referentiel.pistolet (id),
    pompe_id                bigint REFERENCES referentiel.pompe (id),
    cuve_id                 bigint REFERENCES referentiel.cuve (id),
    type_evenement          varchar(30) NOT NULL CHECK (type_evenement IN (
                                'REMPLACEMENT_TOTALISATEUR', 'REMISE_A_ZERO', 'ETALONNAGE',
                                'POSE_SCELLES', 'NETTOYAGE', 'MAINTENANCE', 'AUTRE')),
    date_evenement          timestamptz NOT NULL,
    index_avant             numeric(14, 2) CHECK (index_avant >= 0),
    index_apres             numeric(14, 2) CHECK (index_apres >= 0),
    reference_scelles       varchar(100),
    intervenant             varchar(100),
    prochaine_verification  date,
    commentaire             varchar(500),
    version                 integer NOT NULL DEFAULT 0,
    cree_le                 timestamptz NOT NULL DEFAULT now(),
    cree_par                varchar(100) NOT NULL,
    modifie_le              timestamptz NOT NULL DEFAULT now(),
    modifie_par             varchar(100) NOT NULL,
    -- Un evenement vise exactement un equipement
    CONSTRAINT ck_evenement_cible CHECK (num_nonnulls(pistolet_id, pompe_id, cuve_id) = 1),
    -- Un changement de compteur exige l'index de l'ancien et du nouveau totalisateur
    CONSTRAINT ck_evenement_compteur CHECK (
        type_evenement NOT IN ('REMPLACEMENT_TOTALISATEUR', 'REMISE_A_ZERO')
        OR (pistolet_id IS NOT NULL AND index_avant IS NOT NULL AND index_apres IS NOT NULL))
);

CREATE INDEX ix_evenement_station ON referentiel.evenement_equipement (station_id, date_evenement DESC);
CREATE INDEX ix_evenement_pistolet ON referentiel.evenement_equipement (pistolet_id, date_evenement)
    WHERE pistolet_id IS NOT NULL;

-- Historique en insertion seule : une erreur se corrige par un nouvel evenement
CREATE OR REPLACE FUNCTION referentiel.interdire_modification_evenement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'L''historique des equipements est en insertion seule (operation % refusee)', TG_OP
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER tg_evenement_insertion_seule
    BEFORE UPDATE OR DELETE ON referentiel.evenement_equipement
    FOR EACH ROW EXECUTE FUNCTION referentiel.interdire_modification_evenement();

SELECT audit.activer_audit('referentiel.evenement_equipement');
