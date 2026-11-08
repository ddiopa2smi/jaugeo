import type { Famille, ModeExploitation, Perimetre, TypeStation, Unite } from '../api/types'

export const TYPES_STATION: Record<TypeStation, string> = {
  ROUTIERE: 'Routière',
  PECHE: 'Pêche (quai)',
  MIXTE: 'Mixte',
}

export const MODES_EXPLOITATION: Record<ModeExploitation, string> = {
  PROPRE: 'Exploitation propre',
  GERANCE_LIBRE: 'Gérance libre',
  LOCATION_GERANCE: 'Location-gérance',
}

export const FAMILLES: Record<Famille, string> = {
  CARBURANT: 'Carburant',
  LUBRIFIANT: 'Lubrifiant',
  GAZ: 'Gaz',
  BOUTIQUE: 'Boutique',
}

export const UNITES: Record<Unite, string> = {
  LITRE: 'Litre',
  UNITE: 'Unité',
  KG: 'Kilogramme',
}

export const PERIMETRES: Record<Perimetre, string> = {
  RESEAU: 'Tout le réseau',
  STATIONS: 'Stations désignées',
}

export const OPERATIONS_AUDIT: Record<string, string> = {
  INSERT: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
}

const nombre = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 })
const dateHeure = new Intl.DateTimeFormat('fr-FR', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'Africa/Dakar',
})
const date = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeZone: 'UTC' })

export function formaterNombre(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : nombre.format(v)
}

export function formaterDateHeure(iso: string | null | undefined): string {
  return iso ? dateHeure.format(new Date(iso)) : '—'
}

/** Date locale « AAAA-MM-JJ » (LocalDate côté serveur). */
export function formaterDate(iso: string | null | undefined): string {
  return iso ? date.format(new Date(`${iso}T00:00:00Z`)) : '—'
}

/** Champ texte de formulaire → nombre ou null (accepte la virgule décimale). */
export function versNombre(texte: string | null | undefined): number | null {
  if (texte === null || texte === undefined || texte.trim() === '') return null
  const n = Number(texte.replace(/\s/g, '').replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

export function videVersNull(texte: string | null | undefined): string | null {
  const t = texte?.trim()
  return t ? t : null
}
