-- =====================================================================
-- Lot 2, sprint M4 (suite) : lubrifiants et articles boutique
-- Articles = produits non suivis en cuve. Stock par station valorise au cout moyen unitaire pondere (CMUP) :
-- entrees annoncees par la logistique (cout d'achat) et confirmees par le gerant, ventes saisies dans la journee
-- (sorties au CMUP a la cloture), inventaire tournant (ecart valorise au CMUP, observation obligatoire).
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- Etat courant du stock d'un article dans une station
CREATE TABLE stock.article_station (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id        bigint        NOT NULL REFERENCES referentiel.produit (id),
    quantite          numeric(12,2) NOT NULL DEFAULT 0 CHECK (quantite >= 0),
    cmup              numeric(12,2) NOT NULL DEFAULT 0 CHECK (cmup >= 0),
    dernier_comptage  date,
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT uq_article_station UNIQUE (station_id, produit_id)
);
SELECT audit.activer_audit('stock.article_station');

-- Entree en stock : annoncee par la logistique (livraison ou stock initial), confirmee a la station par le gerant
CREATE TABLE stock.entree_article (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id        bigint        NOT NULL REFERENCES referentiel.produit (id),
    quantite          numeric(12,2) NOT NULL CHECK (quantite > 0),
    cout_unitaire     numeric(12,2) NOT NULL CHECK (cout_unitaire >= 0),
    fournisseur       varchar(80)   NOT NULL,
    reference         varchar(50)   NOT NULL,
    statut            varchar(10)   NOT NULL DEFAULT 'ANNONCEE',
    quantite_recue    numeric(12,2) CHECK (quantite_recue > 0),
    recue_le          date,
    recue_par         varchar(50),
    observation       varchar(500),
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT ck_entree_statut CHECK (statut IN ('ANNONCEE', 'RECUE', 'ANNULEE')),
    CONSTRAINT ck_entree_reception CHECK ((statut = 'RECUE') = (quantite_recue IS NOT NULL AND recue_le IS NOT NULL))
);
CREATE INDEX ix_entree_article_station ON stock.entree_article (station_id, statut);
SELECT audit.activer_audit('stock.entree_article');

-- Vente d'un article dans une journee ; le CMUP de sortie est fige a la cloture
CREATE TABLE stock.vente_article (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    journee_id        bigint        NOT NULL REFERENCES exploitation.journee (id),
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id        bigint        NOT NULL REFERENCES referentiel.produit (id),
    date_jour         date          NOT NULL,
    quantite          numeric(12,2) NOT NULL CHECK (quantite > 0),
    prix_unitaire     numeric(12,2) NOT NULL CHECK (prix_unitaire >= 0),
    montant           numeric(15,0) NOT NULL CHECK (montant >= 0),
    cmup              numeric(12,2),
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT uq_vente_article UNIQUE (journee_id, produit_id)
);
CREATE INDEX ix_vente_article_station_date ON stock.vente_article (station_id, date_jour);
SELECT audit.activer_audit('stock.vente_article');

-- Comptage d'inventaire tournant : theorique, compte, ecart valorise au CMUP
CREATE TABLE stock.comptage_article (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id        bigint        NOT NULL REFERENCES referentiel.produit (id),
    date_jour         date          NOT NULL,
    theorique         numeric(12,2) NOT NULL,
    compte            numeric(12,2) NOT NULL CHECK (compte >= 0),
    ecart             numeric(12,2) NOT NULL,
    cmup              numeric(12,2) NOT NULL,
    ecart_valeur      numeric(15,0) NOT NULL,
    observation       varchar(500),
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT ck_comptage_ecart CHECK (ecart = compte - theorique),
    CONSTRAINT ck_comptage_observation CHECK (ecart = 0 OR observation IS NOT NULL)
);
CREATE INDEX ix_comptage_article_station ON stock.comptage_article (station_id, date_jour);
SELECT audit.activer_audit('stock.comptage_article');

-- Journal des mouvements : stock et CMUP apres chaque mouvement
CREATE TABLE stock.mouvement_article (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    station_id        bigint        NOT NULL REFERENCES referentiel.station (id),
    produit_id        bigint        NOT NULL REFERENCES referentiel.produit (id),
    date_jour         date          NOT NULL,
    type              varchar(12)   NOT NULL,
    quantite          numeric(12,2) NOT NULL,
    cout_unitaire     numeric(12,2) NOT NULL,
    valeur            numeric(15,0) NOT NULL,
    stock_apres       numeric(12,2) NOT NULL CHECK (stock_apres >= 0),
    cmup_apres        numeric(12,2) NOT NULL,
    journee_id        bigint        REFERENCES exploitation.journee (id),
    entree_id         bigint        REFERENCES stock.entree_article (id),
    comptage_id       bigint        REFERENCES stock.comptage_article (id),
    libelle           varchar(200),
    version           integer       NOT NULL DEFAULT 0,
    cree_le           timestamptz   NOT NULL DEFAULT now(),
    cree_par          varchar(50)   NOT NULL,
    modifie_le        timestamptz   NOT NULL DEFAULT now(),
    modifie_par       varchar(50)   NOT NULL,
    CONSTRAINT ck_mouvement_type CHECK (type IN ('ENTREE', 'VENTE', 'INVENTAIRE'))
);
CREATE INDEX ix_mouvement_article_station ON stock.mouvement_article (station_id, date_jour);
CREATE INDEX ix_mouvement_article_journee ON stock.mouvement_article (journee_id);
SELECT audit.activer_audit('stock.mouvement_article');

-- Les ventes d'articles entrent dans le montant a verser de la journee
ALTER TABLE tresorerie.recette_journee
    ADD COLUMN ventes_articles numeric(15,0) NOT NULL DEFAULT 0 CHECK (ventes_articles >= 0);
ALTER TABLE tresorerie.recette_journee DROP CONSTRAINT ck_recette_calcul;
ALTER TABLE tresorerie.recette_journee ADD CONSTRAINT ck_recette_calcul
    CHECK (montant_a_verser = ventes + ventes_articles - bons_direction - bons_gerant - depenses - complements + recouvrements);
