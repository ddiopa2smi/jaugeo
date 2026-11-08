-- =====================================================================
-- Jaugeo - V11 : position des stations du reseau (carte du tableau de bord)
-- =====================================================================
-- Coordonnees approximatives de la localite de chaque station (centre de la localite, pas l'emplacement exact
-- de la station) : a corriger dans la fiche station. Seules les stations sans position sont renseignees.

UPDATE referentiel.station s
SET latitude = v.latitude, longitude = v.longitude, modifie_par = 'migration'
FROM (VALUES
    ('NIA', 16.570000, -15.060000),  -- Niandane (Podor)
    ('HYD', 16.033000, -16.500000),  -- Hydrobase (Saint-Louis)
    ('KAY', 14.917000, -17.117000),  -- Kayar
    ('POT', 15.717000, -16.500000),  -- Potou (Louga)
    ('YOF', 14.758000, -17.475000),  -- Yoff (Dakar)
    ('MBO', 14.417000, -16.967000)   -- Mbour
) AS v(code, latitude, longitude)
WHERE s.code = v.code AND s.latitude IS NULL AND s.longitude IS NULL;
