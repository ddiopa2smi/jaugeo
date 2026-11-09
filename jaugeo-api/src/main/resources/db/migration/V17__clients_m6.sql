-- =====================================================================
-- Lot 2, sprint M6 : clients en compte
-- Fiche client complete (contrat, delai de paiement, caution, engins autorises, remise au litre) ;
-- bons en attente de derogation (plafond, echeance, engin, contrat) bloquant la cloture ;
-- facturation periodique, balance agee, relances ; cartes carburant prepayees a QR code.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Fiche client : coordonnees, contrat, delai de paiement (jours apres la facture), caution bancaire
-- ---------------------------------------------------------------------
ALTER TABLE clients.client
    ADD COLUMN adresse            varchar(200),
    ADD COLUMN email              varchar(120),
    ADD COLUMN ninea              varchar(30),
    ADD COLUMN contrat_reference  varchar(80),
    ADD COLUMN contrat_debut      date,
    ADD COLUMN contrat_fin        date,
    ADD COLUMN delai_paiement     integer CHECK (delai_paiement BETWEEN 0 AND 365),
    ADD COLUMN caution_montant    numeric(15,0) CHECK (caution_montant > 0),
    ADD COLUMN caution_banque     varchar(80),
    ADD COLUMN caution_echeance   date,
    ADD CONSTRAINT ck_client_contrat CHECK (contrat_fin IS NULL OR contrat_debut IS NULL OR contrat_fin >= contrat_debut);

-- Vehicules et pirogues autorises : si un client en a, ses bons doivent porter l'une de ces immatriculations
CREATE TABLE clients.engin_client (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    client_id        bigint        NOT NULL REFERENCES clients.client (id),
    type_engin       varchar(10)   NOT NULL,
    immatriculation  varchar(30)   NOT NULL,
    libelle          varchar(80),
    actif            boolean       NOT NULL DEFAULT true,
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_engin_type CHECK (type_engin IN ('VEHICULE', 'PIROGUE'))
);
CREATE UNIQUE INDEX uq_engin_client ON clients.engin_client (client_id, upper(immatriculation));
SELECT audit.activer_audit('clients.engin_client');

-- Prix contractuel : remise au litre par produit, datee (la plus recente en vigueur a la date du bon)
CREATE TABLE clients.remise_client (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid         uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    client_id    bigint        NOT NULL REFERENCES clients.client (id),
    produit_id   bigint        NOT NULL REFERENCES referentiel.produit (id),
    debut        date          NOT NULL,
    remise       numeric(12,2) NOT NULL CHECK (remise >= 0),
    version      integer       NOT NULL DEFAULT 0,
    cree_le      timestamptz   NOT NULL DEFAULT now(),
    cree_par     varchar(50)   NOT NULL,
    modifie_le   timestamptz   NOT NULL DEFAULT now(),
    modifie_par  varchar(50)   NOT NULL,
    CONSTRAINT uq_remise_client UNIQUE (client_id, produit_id, debut)
);
SELECT audit.activer_audit('clients.remise_client');

-- ---------------------------------------------------------------------
-- Factures periodiques : regroupent les bons d'une periode ; echeance = date de facture + delai du client.
-- ---------------------------------------------------------------------
CREATE SEQUENCE clients.numero_facture;

CREATE TABLE clients.facture (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    numero           varchar(20)   NOT NULL UNIQUE,
    client_id        bigint        NOT NULL REFERENCES clients.client (id),
    periode_debut    date          NOT NULL,
    periode_fin      date          NOT NULL,
    date_facture     date          NOT NULL,
    date_echeance    date          NOT NULL,
    montant          numeric(15,0) NOT NULL CHECK (montant > 0),
    statut           varchar(10)   NOT NULL DEFAULT 'EMISE',
    motif_annulation varchar(500),
    annulee_par      varchar(50),
    annulee_le       timestamptz,
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_facture_statut CHECK (statut IN ('EMISE', 'ANNULEE')),
    CONSTRAINT ck_facture_periode CHECK (periode_fin >= periode_debut AND date_facture >= periode_fin
                                         AND date_echeance >= date_facture),
    CONSTRAINT ck_facture_annulation CHECK ((statut = 'ANNULEE') = (motif_annulation IS NOT NULL))
);
CREATE INDEX ix_facture_client ON clients.facture (client_id, date_facture);
SELECT audit.activer_audit('clients.facture');

-- ---------------------------------------------------------------------
-- Bons : remise au litre (prix_unitaire = prix pompe - remise ; le montant du a la pompe, montant_pompe,
-- sort de la recette de la station) ; statut de derogation ; facture.
--   VALIDE      : accorde sans depassement ;
--   EN_ATTENTE  : plafond, echeance, engin ou contrat hors regle : la journee ne peut pas etre cloturee ;
--   ACCORDE     : derogation de la direction (motif trace) ;
--   REFUSE      : refus de la direction : le bon est a recouvrer par le gerant.
-- ---------------------------------------------------------------------
ALTER TABLE clients.bon
    ADD COLUMN remise_unitaire  numeric(12,2) NOT NULL DEFAULT 0 CHECK (remise_unitaire >= 0),
    ADD COLUMN montant_pompe    numeric(15,0),
    ADD COLUMN statut           varchar(12)   NOT NULL DEFAULT 'VALIDE',
    ADD COLUMN motif_blocage    varchar(500),
    ADD COLUMN derogation_par   varchar(50),
    ADD COLUMN derogation_le    timestamptz,
    ADD COLUMN derogation_motif varchar(500),
    ADD COLUMN facture_id       bigint REFERENCES clients.facture (id);
UPDATE clients.bon SET montant_pompe = montant;
ALTER TABLE clients.bon
    ALTER COLUMN montant_pompe SET NOT NULL,
    ADD CONSTRAINT ck_bon_montant_pompe CHECK (montant_pompe >= montant),
    ADD CONSTRAINT ck_bon_statut CHECK (statut IN ('VALIDE', 'EN_ATTENTE', 'ACCORDE', 'REFUSE')),
    ADD CONSTRAINT ck_bon_blocage CHECK ((statut = 'VALIDE') = (motif_blocage IS NULL)),
    ADD CONSTRAINT ck_bon_derogation CHECK ((statut IN ('ACCORDE', 'REFUSE')) = (derogation_par IS NOT NULL)),
    ADD CONSTRAINT ck_bon_facture CHECK (facture_id IS NULL OR statut <> 'EN_ATTENTE');
CREATE INDEX ix_bon_en_attente ON clients.bon (station_id) WHERE statut = 'EN_ATTENTE';
CREATE INDEX ix_bon_facture ON clients.bon (facture_id) WHERE facture_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Relances des clients en retard de paiement (niveau 1 a 3), avec le montant echu a la date
-- ---------------------------------------------------------------------
CREATE TABLE clients.relance (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    client_id      bigint        NOT NULL REFERENCES clients.client (id),
    date_relance   date          NOT NULL,
    niveau         integer       NOT NULL CHECK (niveau BETWEEN 1 AND 3),
    mode           varchar(10)   NOT NULL,
    montant_echu   numeric(15,0) NOT NULL CHECK (montant_echu > 0),
    commentaire    varchar(500),
    version        integer       NOT NULL DEFAULT 0,
    cree_le        timestamptz   NOT NULL DEFAULT now(),
    cree_par       varchar(50)   NOT NULL,
    modifie_le     timestamptz   NOT NULL DEFAULT now(),
    modifie_par    varchar(50)   NOT NULL,
    CONSTRAINT ck_relance_mode CHECK (mode IN ('COURRIER', 'EMAIL', 'TELEPHONE', 'VISITE'))
);
CREATE INDEX ix_relance_client ON clients.relance (client_id, date_relance);
SELECT audit.activer_audit('clients.relance');

-- ---------------------------------------------------------------------
-- Cartes carburant prepayees : nominatives (client, porteur, immatriculation), numero unique porte par
-- un QR code, rechargees au siege, debitees dans toute station du reseau.
-- ---------------------------------------------------------------------
CREATE TABLE clients.carte (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    numero           varchar(20)   NOT NULL UNIQUE,
    client_id        bigint        NOT NULL REFERENCES clients.client (id),
    porteur          varchar(80)   NOT NULL,
    immatriculation  varchar(30),
    statut           varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    solde            numeric(15,0) NOT NULL DEFAULT 0 CHECK (solde >= 0),
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_carte_statut CHECK (statut IN ('ACTIVE', 'BLOQUEE', 'ANNULEE'))
);
CREATE INDEX ix_carte_client ON clients.carte (client_id);
SELECT audit.activer_audit('clients.carte');

-- RECHARGE (siege) ou DEBIT (plein servi a une station, saisi dans la journee)
CREATE TABLE clients.mouvement_carte (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    carte_id         bigint        NOT NULL REFERENCES clients.carte (id),
    type_mouvement   varchar(10)   NOT NULL,
    date_mouvement   date          NOT NULL,
    montant          numeric(15,0) NOT NULL CHECK (montant > 0),
    mode             varchar(20),
    reference        varchar(80),
    journee_id       bigint        REFERENCES exploitation.journee (id),
    station_id       bigint        REFERENCES referentiel.station (id),
    produit_id       bigint        REFERENCES referentiel.produit (id),
    litres           numeric(12,2) CHECK (litres > 0),
    prix_unitaire    numeric(12,2) CHECK (prix_unitaire > 0),
    remise_unitaire  numeric(12,2) CHECK (remise_unitaire >= 0),
    montant_pompe    numeric(15,0),
    version          integer       NOT NULL DEFAULT 0,
    cree_le          timestamptz   NOT NULL DEFAULT now(),
    cree_par         varchar(50)   NOT NULL,
    modifie_le       timestamptz   NOT NULL DEFAULT now(),
    modifie_par      varchar(50)   NOT NULL,
    CONSTRAINT ck_mouvement_carte CHECK (
        (type_mouvement = 'RECHARGE' AND mode IN ('ESPECES_SIEGE', 'VIREMENT', 'CHEQUE', 'WAVE', 'ORANGE_MONEY')
            AND journee_id IS NULL AND station_id IS NULL AND produit_id IS NULL)
        OR (type_mouvement = 'DEBIT' AND mode IS NULL AND journee_id IS NOT NULL AND station_id IS NOT NULL
            AND produit_id IS NOT NULL AND litres IS NOT NULL AND prix_unitaire IS NOT NULL
            AND remise_unitaire IS NOT NULL AND montant_pompe >= montant))
);
CREATE INDEX ix_mouvement_carte ON clients.mouvement_carte (carte_id, date_mouvement);
CREATE INDEX ix_mouvement_carte_journee ON clients.mouvement_carte (journee_id) WHERE journee_id IS NOT NULL;
SELECT audit.activer_audit('clients.mouvement_carte');

-- ---------------------------------------------------------------------
-- Recette : les pleins payes par carte prepayee (valeur a la pompe) ne passent pas par la caisse du gerant
-- ---------------------------------------------------------------------
ALTER TABLE tresorerie.recette_journee
    ADD COLUMN cartes numeric(15,0) NOT NULL DEFAULT 0 CHECK (cartes >= 0);
ALTER TABLE tresorerie.recette_journee DROP CONSTRAINT ck_recette_calcul;
ALTER TABLE tresorerie.recette_journee ADD CONSTRAINT ck_recette_calcul
    CHECK (montant_a_verser = ventes + ventes_articles - bons_direction - bons_gerant - cartes - depenses - complements
                              - encaissements + recouvrements);
