-- La direction peut valider (ou renvoyer au gerant) les journees cloturees, comme le superviseur reseau.
-- La separation des taches reste : on ne valide jamais une journee que l'on a soi-meme cloturee.
INSERT INTO securite.role_permission (role_id, permission)
SELECT r.id, 'JOURNEE_VALIDER'
FROM securite.role r
WHERE r.code = 'DIRECTION'
ON CONFLICT DO NOTHING;
