-- =====================================================================
-- Banques des versements : la banque d'un bordereau se choisit dans une liste au lieu d'etre saisie librement.
-- La table est pre-remplie avec les 29 banques agreees au Senegal (liste BCEAO des etablissements agrees de
-- l'UMOA, mise a jour au 29 juin 2026). Le champ actif designe les banques ou la compagnie depose ses recettes :
-- seules les banques actives sont proposees a la saisie d'un versement.
-- =====================================================================
SET LOCAL app.utilisateur = 'migration';

CREATE TABLE tresorerie.banque (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid          uuid          NOT NULL DEFAULT uuidv7() UNIQUE,
    code          varchar(60)   NOT NULL UNIQUE,
    nom           varchar(120)  NOT NULL,
    actif         boolean       NOT NULL DEFAULT true,
    version       integer       NOT NULL DEFAULT 0,
    cree_le       timestamptz   NOT NULL DEFAULT now(),
    cree_par      varchar(50)   NOT NULL,
    modifie_le    timestamptz   NOT NULL DEFAULT now(),
    modifie_par   varchar(50)   NOT NULL,
    CONSTRAINT ck_banque_code CHECK (code = upper(btrim(code)) AND code <> '')
);
SELECT audit.activer_audit('tresorerie.banque');

INSERT INTO tresorerie.banque (code, nom, cree_par, modifie_par) VALUES
    ('AFRIKA',   'Afrika Banque Sénégal',                                                        'migration', 'migration'),
    ('ABS',      'Algerian Bank of Senegal',                                                     'migration', 'migration'),
    ('BOA',      'Bank of Africa Sénégal',                                                       'migration', 'migration'),
    ('BASN',     'Banque Atlantique Sénégal',                                                    'migration', 'migration'),
    ('BHS',      'Banque de l''Habitat du Sénégal',                                              'migration', 'migration'),
    ('BIMAO',    'Banque des Institutions Mutualistes d''Afrique de l''Ouest',                   'migration', 'migration'),
    ('BIS',      'Banque Islamique du Sénégal',                                                  'migration', 'migration'),
    ('BNDE',     'Banque Nationale pour le Développement Économique',                            'migration', 'migration'),
    ('BRM',      'Banque Régionale de Marchés',                                                  'migration', 'migration'),
    ('BSIC',     'Banque Sahélo-Saharienne pour l''Investissement et le Commerce Sénégal',       'migration', 'migration'),
    ('BGFI',     'BGFIBank Sénégal',                                                             'migration', 'migration'),
    ('CBAO',     'CBAO Groupe Attijariwafa Bank',                                                'migration', 'migration'),
    ('CITIBANK', 'Citibank Sénégal',                                                             'migration', 'migration'),
    ('CBI',      'Coris Bank International Sénégal',                                             'migration', 'migration'),
    ('CDS',      'Crédit du Sénégal',                                                            'migration', 'migration'),
    ('CI',       'Crédit International',                                                         'migration', 'migration'),
    ('ECOBANK',  'Ecobank Sénégal',                                                              'migration', 'migration'),
    ('FBNBANK',  'FBNBank Sénégal',                                                              'migration', 'migration'),
    ('LBA',      'La Banque Agricole',                                                           'migration', 'migration'),
    ('LBO',      'La Banque Outarde',                                                            'migration', 'migration'),
    ('NSIA',     'NSIA Banque Sénégal',                                                          'migration', 'migration'),
    ('SGSN',     'Société Générale Sénégal',                                                     'migration', 'migration'),
    ('SUNU',     'Sunu Bank Sénégal',                                                            'migration', 'migration'),
    ('UBA',      'United Bank for Africa Sénégal',                                               'migration', 'migration'),
    ('BDM',      'Banque de Développement du Mali, succursale du Sénégal',                       'migration', 'migration'),
    ('BCI',      'Banque pour le Commerce et l''Industrie du Mali, succursale du Sénégal',       'migration', 'migration'),
    ('BBG',      'Bridge Bank Group Côte d''Ivoire, succursale du Sénégal',                      'migration', 'migration'),
    ('ORABANK',  'Orabank Côte d''Ivoire, succursale du Sénégal',                                'migration', 'migration'),
    ('OBA',      'Orange Bank Africa, succursale du Sénégal',                                    'migration', 'migration');

-- Banques deja saisies librement sur des versements et absentes de la liste : conservees telles quelles
-- (elles restent proposees, sauf la mention de reprise « NON PRÉCISÉE »), a rattacher ou desactiver ensuite.
INSERT INTO tresorerie.banque (code, nom, actif, cree_par, modifie_par)
SELECT DISTINCT v.banque, v.banque, v.banque <> 'NON PRÉCISÉE', 'migration', 'migration'
FROM tresorerie.versement v
WHERE NOT EXISTS (SELECT 1 FROM tresorerie.banque b WHERE b.code = v.banque);

-- La banque d'un versement est desormais le code d'une banque de la table
ALTER TABLE tresorerie.versement
    ADD CONSTRAINT fk_versement_banque FOREIGN KEY (banque) REFERENCES tresorerie.banque (code) ON UPDATE CASCADE;
