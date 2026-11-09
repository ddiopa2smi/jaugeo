#!/bin/sh
# Sauvegarde quotidienne de la base Jaugeo : pg_dump au format custom (restauration par pg_restore),
# une fois par jour a HEURE_SAUVEGARDE (heure de Dakar), fichiers plus vieux que JOURS_CONSERVES supprimes.
# Une sauvegarde est aussi faite au demarrage du conteneur.
set -eu

sauvegarder() {
    fichier="/sauvegardes/jaugeo-$(date +%Y%m%d-%H%M%S).dump"
    if pg_dump --format=custom --file="$fichier.partiel"; then
        mv "$fichier.partiel" "$fichier"
        echo "$(date -Iseconds) sauvegarde $fichier ($(du -h "$fichier" | cut -f1))"
    else
        rm -f "$fichier.partiel"
        echo "$(date -Iseconds) ECHEC de la sauvegarde" >&2
    fi
    find /sauvegardes -name 'jaugeo-*.dump' -mtime +"$JOURS_CONSERVES" -delete
}

sauvegarder
derniere=$(date +%Y%m%d)
while true; do
    sleep 300
    if [ "$(date +%H)" = "$HEURE_SAUVEGARDE" ] && [ "$(date +%Y%m%d)" != "$derniere" ]; then
        sauvegarder
        derniere=$(date +%Y%m%d)
    fi
done
