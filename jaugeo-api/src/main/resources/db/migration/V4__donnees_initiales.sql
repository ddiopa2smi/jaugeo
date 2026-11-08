-- =====================================================================
-- Jaugeo - V4 : donnees de depart
-- Reseau issu du classeur "SUIVI DES STATIONS 2026" ; index de mise en
-- service = index de depart du 01/01/2026. Capacites de cuves a renseigner
-- depuis les certificats de jaugeage.
-- =====================================================================

SET LOCAL app.utilisateur = 'migration';

-- Regions -------------------------------------------------------------
INSERT INTO referentiel.region (code, nom) VALUES
    ('DK', 'Dakar'),
    ('TH', 'Thiès'),
    ('SL', 'Saint-Louis'),
    ('LG', 'Louga');

-- Stations ------------------------------------------------------------
INSERT INTO referentiel.station (code, nom, type_station, region_id, localite, date_ouverture, cree_par, modifie_par)
SELECT s.code, s.nom, s.type_station, r.id, s.localite, s.date_ouverture, 'migration', 'migration'
FROM (VALUES
    ('NIA', 'Station Niandane',  'ROUTIERE', 'SL', 'Niandane',          NULL::date),
    ('HYD', 'Station Hydrobase', 'PECHE',    'SL', 'Hydrobase',         NULL::date),
    ('KAY', 'Station Kayar',     'PECHE',    'TH', 'Kayar',             NULL::date),
    ('POT', 'Station Potou',     'PECHE',    'LG', 'Potou',             NULL::date),
    ('YOF', 'Station Yoff',      'PECHE',    'DK', 'Yoff',              NULL::date),
    ('MBO', 'Station Mbour',     'PECHE',    'TH', 'Mbour (quai de pêche)', DATE '2026-02-01')
) AS s (code, nom, type_station, region, localite, date_ouverture)
JOIN referentiel.region r ON r.code = s.region;

-- Produits ------------------------------------------------------------
INSERT INTO referentiel.produit (code, libelle, famille, unite, suivi_jaugeage, cree_par, modifie_par) VALUES
    ('GASOIL',          'Gasoil',          'CARBURANT', 'LITRE', true, 'migration', 'migration'),
    ('SUPER',           'Super',           'CARBURANT', 'LITRE', true, 'migration', 'migration'),
    ('ESSENCE_PIROGUE', 'Essence pirogue', 'CARBURANT', 'LITRE', true, 'migration', 'migration');

-- Cuves ---------------------------------------------------------------
INSERT INTO referentiel.cuve (station_id, code, produit_id, cree_par, modifie_par)
SELECT st.id, c.code, p.id, 'migration', 'migration'
FROM (VALUES
    ('NIA', 'C1', 'GASOIL'),
    ('NIA', 'C2', 'SUPER'),
    ('HYD', 'C1', 'ESSENCE_PIROGUE'),
    ('KAY', 'C1', 'ESSENCE_PIROGUE'),
    ('POT', 'C1', 'ESSENCE_PIROGUE'),
    ('YOF', 'C1', 'ESSENCE_PIROGUE'),
    ('MBO', 'C1', 'ESSENCE_PIROGUE')
) AS c (station, code, produit)
JOIN referentiel.station st ON st.code = c.station
JOIN referentiel.produit p ON p.code = c.produit;

-- Pompes --------------------------------------------------------------
INSERT INTO referentiel.pompe (station_id, code, cree_par, modifie_par)
SELECT st.id, p.code, 'migration', 'migration'
FROM referentiel.station st
CROSS JOIN (VALUES ('P1'), ('P2')) AS p (code);

-- Pistolets -----------------------------------------------------------
-- Niandane : 2 pompes double produit (Gasoil 1 / Super 1, Gasoil 2 / Super 2)
-- Stations peche : 1 pistolet Essence pirogue par pompe
INSERT INTO referentiel.pistolet (pompe_id, cuve_id, code, libelle, index_mise_en_service, cree_par, modifie_par)
SELECT po.id, cu.id, x.code, x.libelle, x.index_depart, 'migration', 'migration'
FROM (VALUES
    ('NIA', 'P1', 'C1', 'G1', 'Gasoil 1',  88916),
    ('NIA', 'P1', 'C2', 'S1', 'Super 1',   80521),
    ('NIA', 'P2', 'C1', 'G2', 'Gasoil 2', 140503),
    ('NIA', 'P2', 'C2', 'S2', 'Super 2',   77781),
    ('HYD', 'P1', 'C1', 'E1', 'Pompe 1', 1747066),
    ('HYD', 'P2', 'C1', 'E2', 'Pompe 2', 1396495),
    ('KAY', 'P1', 'C1', 'E1', 'Pompe 1',  605965),
    ('KAY', 'P2', 'C1', 'E2', 'Pompe 2',  503244),
    ('POT', 'P1', 'C1', 'E1', 'Pompe 1',   57500),
    ('POT', 'P2', 'C1', 'E2', 'Pompe 2',   70305),
    ('YOF', 'P1', 'C1', 'E1', 'Pompe 1',  106875),
    ('YOF', 'P2', 'C1', 'E2', 'Pompe 2',  125647),
    ('MBO', 'P1', 'C1', 'E1', 'Pompe 1',     864),
    ('MBO', 'P2', 'C1', 'E2', 'Pompe 2',     902)
) AS x (station, pompe, cuve, code, libelle, index_depart)
JOIN referentiel.station st ON st.code = x.station
JOIN referentiel.pompe po ON po.station_id = st.id AND po.code = x.pompe
JOIN referentiel.cuve cu ON cu.station_id = st.id AND cu.code = x.cuve;

-- Roles et permissions --------------------------------------------------
INSERT INTO securite.role (code, libelle, description) VALUES
    ('ADMINISTRATEUR',     'Administrateur',               'Utilisateurs, rôles, paramètres et référentiels'),
    ('DIRECTION',          'Direction générale',           'Tableaux de bord, prix, plafonds de crédit, arbitrages'),
    ('CONTROLEUR_GESTION', 'Contrôleur de gestion',        'Analyse des écarts, clôture mensuelle, budget'),
    ('COMPTABLE',          'Comptable / trésorier',        'Versements, rapprochements, créances clients'),
    ('LOGISTIQUE',         'Responsable approvisionnement','Commandes, citernes, réceptions'),
    ('SUPERVISEUR',        'Superviseur réseau',           'Contrôles et validation des journées de ses stations'),
    ('GERANT',             'Gérant de station',            'Clôture de journée, jaugeages, réceptions, dépôts'),
    ('POMPISTE',           'Pompiste / caissier',          'Saisie des index et encaissements de son poste');

INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, x.permission
FROM (VALUES
    ('ADMINISTRATEUR', 'REFERENTIEL_CONSULTER'), ('ADMINISTRATEUR', 'REFERENTIEL_GERER'),
    ('ADMINISTRATEUR', 'UTILISATEUR_GERER'),     ('ADMINISTRATEUR', 'AUDIT_CONSULTER'),
    ('ADMINISTRATEUR', 'TABLEAU_BORD_RESEAU'),
    ('DIRECTION', 'REFERENTIEL_CONSULTER'), ('DIRECTION', 'AUDIT_CONSULTER'), ('DIRECTION', 'TABLEAU_BORD_RESEAU'),
    ('DIRECTION', 'PRIX_MODIFIER'),         ('DIRECTION', 'CREDIT_DEROGER'),
    ('CONTROLEUR_GESTION', 'REFERENTIEL_CONSULTER'), ('CONTROLEUR_GESTION', 'AUDIT_CONSULTER'),
    ('CONTROLEUR_GESTION', 'TABLEAU_BORD_RESEAU'),   ('CONTROLEUR_GESTION', 'CLOTURE_MENSUELLE'),
    ('COMPTABLE', 'REFERENTIEL_CONSULTER'), ('COMPTABLE', 'VERSEMENT_VALIDER'), ('COMPTABLE', 'CLIENT_GERER'),
    ('COMPTABLE', 'TABLEAU_BORD_RESEAU'),
    ('LOGISTIQUE', 'REFERENTIEL_CONSULTER'), ('LOGISTIQUE', 'RECEPTION_VALIDER'), ('LOGISTIQUE', 'TABLEAU_BORD_RESEAU'),
    ('SUPERVISEUR', 'REFERENTIEL_CONSULTER'), ('SUPERVISEUR', 'JOURNEE_VALIDER'),
    ('GERANT', 'REFERENTIEL_CONSULTER'), ('GERANT', 'JOURNEE_SAISIR'), ('GERANT', 'JOURNEE_CLOTURER'),
    ('GERANT', 'VERSEMENT_SAISIR'),
    ('POMPISTE', 'REFERENTIEL_CONSULTER'), ('POMPISTE', 'JOURNEE_SAISIR')
) AS x (role, permission)
JOIN securite.role r ON r.code = x.role;
