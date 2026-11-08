import 'leaflet/dist/leaflet.css'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useTheme } from '@mui/material/styles'
import L from 'leaflet'
import { useEffect, useRef } from 'react'
import type { StationReseau } from '../../api/types'
import { NIVEAUX_ECART } from '../../shared/stock'

type StationPlacee = Pick<StationReseau, 'code' | 'nom' | 'niveau' | 'motifs'> & { latitude: number; longitude: number }

/** Contenu de la bulle d'une station (construit en DOM : aucun texte interprété comme HTML). */
function bulle(s: StationPlacee): HTMLElement {
  const racine = document.createElement('div')
  const titre = document.createElement('strong')
  titre.textContent = `${s.code} · ${s.nom}`
  racine.append(titre)
  const niveau = document.createElement('div')
  niveau.textContent = s.niveau === 'NORMAL' ? 'Situation normale' : NIVEAUX_ECART[s.niveau].libelle
  racine.append(niveau)
  for (const m of s.motifs) {
    const ligne = document.createElement('div')
    ligne.textContent = `• ${m}`
    racine.append(ligne)
  }
  return racine
}

/** Carte des stations, colorées par niveau d'alerte (fond OpenStreetMap). */
export function CarteReseau({ stations }: { stations: StationReseau[] }) {
  const theme = useTheme()
  const conteneur = useRef<HTMLDivElement>(null)
  const placees: StationPlacee[] = stations
    .filter((s) => s.latitude !== null && s.longitude !== null)
    .map((s) => ({ code: s.code, nom: s.nom, niveau: s.niveau, motifs: s.motifs, latitude: s.latitude!, longitude: s.longitude! }))
  // Carte refaite seulement quand les stations, leurs niveaux ou leurs motifs changent
  const signature = JSON.stringify(placees)

  useEffect(() => {
    const aPlacer = JSON.parse(signature) as StationPlacee[]
    if (!conteneur.current || aPlacer.length === 0) return
    const carte = L.map(conteneur.current, { scrollWheelZoom: false, attributionControl: true })
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 18,
      attribution: '&copy; contributeurs OpenStreetMap',
    }).addTo(carte)
    const couleurs = {
      NORMAL: theme.palette.success.main,
      ALERTE: theme.palette.warning.main,
      ENQUETE: theme.palette.error.main,
    }
    const points: L.LatLngExpression[] = []
    for (const s of aPlacer) {
      const position: L.LatLngExpression = [s.latitude, s.longitude]
      points.push(position)
      L.circleMarker(position, {
        radius: 11,
        color: '#ffffff',
        weight: 2,
        fillColor: couleurs[s.niveau],
        fillOpacity: 0.95,
      })
        .bindTooltip(s.code, { permanent: true, direction: 'right', offset: [10, 0] })
        .bindPopup(bulle(s))
        .addTo(carte)
    }
    carte.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 9 })
    return () => {
      carte.remove()
    }
  }, [signature, theme])

  if (placees.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
        Aucune station n’a de position : renseignez latitude et longitude dans la fiche station pour l’afficher sur la carte.
      </Typography>
    )
  }
  return (
    <Box
      ref={conteneur}
      role="region"
      aria-label="Carte des stations"
      sx={{ height: { xs: 320, md: 420 }, borderRadius: 1, overflow: 'hidden', zIndex: 0, position: 'relative' }}
    />
  )
}
