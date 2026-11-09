-- =====================================================================
-- Lot 2, sprint M4 : depotage a 15 °C et imputation des manquants, eau en fond de cuve,
-- seuils de reapprovisionnement
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

-- ---------------------------------------------------------------------
-- Seuils par cuve (nuls = valeurs du reseau, jaugeo.stock.*)
-- ---------------------------------------------------------------------
ALTER TABLE referentiel.cuve
    ADD COLUMN seuil_eau_mm         integer       CHECK (seuil_eau_mm >= 0),
    ADD COLUMN seuil_reappro_litres numeric(12,2) CHECK (seuil_reappro_litres > 0),
    ADD CONSTRAINT ck_cuve_seuil_reappro CHECK (capacite_litres IS NULL OR seuil_reappro_litres IS NULL
                                                OR seuil_reappro_litres < capacite_litres);

-- ---------------------------------------------------------------------
-- Eau en fond de cuve (pate a eau) relevee au jaugeage. Le volume d'eau, lu sur le bareme, est deduit :
-- volume_litres reste le volume de produit.
-- ---------------------------------------------------------------------
ALTER TABLE stock.jaugeage
    ADD COLUMN hauteur_eau_mm    integer       CHECK (hauteur_eau_mm >= 0),
    ADD COLUMN volume_eau_litres numeric(12,2) CHECK (volume_eau_litres >= 0),
    ADD CONSTRAINT ck_jaugeage_eau CHECK (volume_eau_litres IS NULL OR hauteur_eau_mm IS NOT NULL);

-- ---------------------------------------------------------------------
-- Reception a 15 °C : quantite du BL a 15 °C, facteur de correction (table 54B) et quantite recue corrigee.
-- La densite deja saisie est la masse volumique a 15 °C portee sur le BL.
-- Imputation du manquant : fournisseur si la citerne est arrivee scelles intacts, transporteur sinon.
-- ---------------------------------------------------------------------
ALTER TABLE stock.reception
    ADD COLUMN quantite_bl_15    numeric(12,2) CHECK (quantite_bl_15 > 0),
    ADD COLUMN facteur_15        numeric(6,5)  CHECK (facteur_15 > 0.9 AND facteur_15 < 1.1),
    ADD COLUMN quantite_recue_15 numeric(12,2) CHECK (quantite_recue_15 > 0),
    ADD COLUMN scelles_intacts   boolean,
    ADD COLUMN imputation        varchar(12)   NOT NULL DEFAULT 'TRANSPORTEUR'
        CONSTRAINT ck_reception_imputation CHECK (imputation IN ('TRANSPORTEUR', 'FOURNISSEUR')),
    ADD CONSTRAINT ck_reception_15 CHECK ((facteur_15 IS NULL) = (quantite_recue_15 IS NULL));

ALTER TABLE stock.complement_citerne
    ADD COLUMN impute_a varchar(12) NOT NULL DEFAULT 'TRANSPORTEUR'
        CONSTRAINT ck_complement_impute CHECK (impute_a IN ('TRANSPORTEUR', 'FOURNISSEUR')),
    ADD COLUMN a_15     boolean     NOT NULL DEFAULT false;
