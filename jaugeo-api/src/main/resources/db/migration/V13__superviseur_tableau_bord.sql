-- Le superviseur accede au tableau de bord du reseau (page Reseau).
-- Les stations affichees restent celles de son perimetre : tout le reseau ou ses stations.
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, 'TABLEAU_BORD_RESEAU'
FROM securite.role r
WHERE r.code = 'SUPERVISEUR'
ON CONFLICT DO NOTHING;
