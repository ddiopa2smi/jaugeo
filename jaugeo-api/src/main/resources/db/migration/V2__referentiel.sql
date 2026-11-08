-- =====================================================================
-- Jaugeo - V2 : referentiel reseau (regions, stations, produits, cuves,
-- baremes de jaugeage, pompes, pistolets)
-- =====================================================================

CREATE TABLE referentiel.region (
    id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code  varchar(20)  NOT NULL UNIQUE,
    nom   varchar(100) NOT NULL
);

CREATE TABLE referentiel.station (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    code               varchar(10)  NOT NULL UNIQUE,
    nom                varchar(100) NOT NULL,
    type_station       varchar(10)  NOT NULL CHECK (type_station IN ('ROUTIERE', 'PECHE', 'MIXTE')),
    region_id          bigint NOT NULL REFERENCES referentiel.region (id),
    localite           varchar(100),
    latitude           numeric(9, 6) CHECK (latitude BETWEEN -90 AND 90),
    longitude          numeric(9, 6) CHECK (longitude BETWEEN -180 AND 180),
    mode_exploitation  varchar(15)  NOT NULL DEFAULT 'PROPRE'
                       CHECK (mode_exploitation IN ('PROPRE', 'GERANCE_LIBRE', 'LOCATION_GERANCE')),
    date_ouverture     date,
    actif              boolean NOT NULL DEFAULT true,
    version            integer NOT NULL DEFAULT 0,
    cree_le            timestamptz NOT NULL DEFAULT now(),
    cree_par           varchar(100) NOT NULL,
    modifie_le         timestamptz NOT NULL DEFAULT now(),
    modifie_par        varchar(100) NOT NULL
);

CREATE TABLE referentiel.produit (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    code             varchar(20)  NOT NULL UNIQUE,
    libelle          varchar(100) NOT NULL,
    famille          varchar(10)  NOT NULL CHECK (famille IN ('CARBURANT', 'LUBRIFIANT', 'GAZ', 'BOUTIQUE')),
    unite            varchar(10)  NOT NULL CHECK (unite IN ('LITRE', 'UNITE', 'KG')),
    suivi_jaugeage   boolean NOT NULL DEFAULT false,
    taux_tva         numeric(5, 2) NOT NULL DEFAULT 0 CHECK (taux_tva BETWEEN 0 AND 100),
    actif            boolean NOT NULL DEFAULT true,
    version          integer NOT NULL DEFAULT 0,
    cree_le          timestamptz NOT NULL DEFAULT now(),
    cree_par         varchar(100) NOT NULL,
    modifie_le       timestamptz NOT NULL DEFAULT now(),
    modifie_par      varchar(100) NOT NULL,
    -- Seul un produit au litre peut etre jauge en cuve
    CONSTRAINT ck_produit_jaugeage CHECK (NOT suivi_jaugeage OR unite = 'LITRE')
);

CREATE TABLE referentiel.cuve (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id             bigint NOT NULL REFERENCES referentiel.station (id),
    code                   varchar(10) NOT NULL,
    produit_id             bigint NOT NULL REFERENCES referentiel.produit (id),
    capacite_litres        numeric(12, 2) CHECK (capacite_litres > 0),
    creux_securite_litres  numeric(12, 2) NOT NULL DEFAULT 0 CHECK (creux_securite_litres >= 0),
    actif                  boolean NOT NULL DEFAULT true,
    version                integer NOT NULL DEFAULT 0,
    cree_le                timestamptz NOT NULL DEFAULT now(),
    cree_par               varchar(100) NOT NULL,
    modifie_le             timestamptz NOT NULL DEFAULT now(),
    modifie_par            varchar(100) NOT NULL,
    CONSTRAINT uq_cuve_station_code UNIQUE (station_id, code),
    CONSTRAINT ck_cuve_creux CHECK (capacite_litres IS NULL OR creux_securite_litres < capacite_litres)
);

-- Bareme de jaugeage : hauteur mesuree (mm) -> volume (litres), issu du certificat de la cuve
CREATE TABLE referentiel.bareme_cuve (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuve_id        bigint NOT NULL REFERENCES referentiel.cuve (id) ON DELETE CASCADE,
    hauteur_mm     integer NOT NULL CHECK (hauteur_mm >= 0),
    volume_litres  numeric(12, 2) NOT NULL CHECK (volume_litres >= 0),
    CONSTRAINT uq_bareme_cuve_hauteur UNIQUE (cuve_id, hauteur_mm)
);

CREATE TABLE referentiel.pompe (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id    bigint NOT NULL REFERENCES referentiel.station (id),
    code          varchar(10) NOT NULL,
    marque        varchar(50),
    numero_serie  varchar(50),
    actif         boolean NOT NULL DEFAULT true,
    version       integer NOT NULL DEFAULT 0,
    cree_le       timestamptz NOT NULL DEFAULT now(),
    cree_par      varchar(100) NOT NULL,
    modifie_le    timestamptz NOT NULL DEFAULT now(),
    modifie_par   varchar(100) NOT NULL,
    CONSTRAINT uq_pompe_station_code UNIQUE (station_id, code)
);

CREATE TABLE referentiel.pistolet (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     uuid NOT NULL DEFAULT uuidv7() UNIQUE,
    pompe_id                 bigint NOT NULL REFERENCES referentiel.pompe (id),
    cuve_id                  bigint NOT NULL REFERENCES referentiel.cuve (id),
    code                     varchar(10)  NOT NULL,
    libelle                  varchar(50)  NOT NULL,
    index_mise_en_service    numeric(14, 2) NOT NULL DEFAULT 0 CHECK (index_mise_en_service >= 0),
    actif                    boolean NOT NULL DEFAULT true,
    version                  integer NOT NULL DEFAULT 0,
    cree_le                  timestamptz NOT NULL DEFAULT now(),
    cree_par                 varchar(100) NOT NULL,
    modifie_le               timestamptz NOT NULL DEFAULT now(),
    modifie_par              varchar(100) NOT NULL,
    CONSTRAINT uq_pistolet_pompe_code UNIQUE (pompe_id, code)
);

-- Un pistolet doit puiser dans une cuve de la meme station que sa pompe
CREATE OR REPLACE FUNCTION referentiel.verifier_pistolet_station()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT station_id FROM referentiel.pompe WHERE id = NEW.pompe_id)
       IS DISTINCT FROM (SELECT station_id FROM referentiel.cuve WHERE id = NEW.cuve_id) THEN
        RAISE EXCEPTION 'Le pistolet % doit etre relie a une cuve de la station de sa pompe', NEW.code
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_pistolet_station
    BEFORE INSERT OR UPDATE OF pompe_id, cuve_id ON referentiel.pistolet
    FOR EACH ROW EXECUTE FUNCTION referentiel.verifier_pistolet_station();

CREATE INDEX ix_station_region ON referentiel.station (region_id);
CREATE INDEX ix_cuve_station ON referentiel.cuve (station_id);
CREATE INDEX ix_pompe_station ON referentiel.pompe (station_id);
CREATE INDEX ix_pistolet_pompe ON referentiel.pistolet (pompe_id);
CREATE INDEX ix_pistolet_cuve ON referentiel.pistolet (cuve_id);

SELECT audit.activer_audit('referentiel.region');
SELECT audit.activer_audit('referentiel.station');
SELECT audit.activer_audit('referentiel.produit');
SELECT audit.activer_audit('referentiel.cuve');
SELECT audit.activer_audit('referentiel.bareme_cuve');
SELECT audit.activer_audit('referentiel.pompe');
SELECT audit.activer_audit('referentiel.pistolet');
