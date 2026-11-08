import type { CibleEvenement, Famille, ModeExploitation, Perimetre, TypeEvenement, TypeStation, Unite } from '../api/types'

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

export const TYPES_EVENEMENT: Record<TypeEvenement, string> = {
  REMPLACEMENT_TOTALISATEUR: 'Remplacement du totalisateur',
  REMISE_A_ZERO: 'Remise à zéro du totalisateur',
  ETALONNAGE: 'Étalonnage / vérification',
  POSE_SCELLES: 'Pose de scellés',
  NETTOYAGE: 'Nettoyage',
  MAINTENANCE: 'Maintenance',
  AUTRE: 'Autre',
}

/** Types proposés selon l'équipement (mêmes règles que le serveur). */
export const EVENEMENTS_PAR_CIBLE: Record<CibleEvenement, TypeEvenement[]> = {
  PISTOLET: ['REMPLACEMENT_TOTALISATEUR', 'REMISE_A_ZERO', 'ETALONNAGE', 'POSE_SCELLES', 'MAINTENANCE', 'AUTRE'],
  POMPE: ['ETALONNAGE', 'POSE_SCELLES', 'MAINTENANCE', 'AUTRE'],
  CUVE: ['ETALONNAGE', 'NETTOYAGE', 'MAINTENANCE', 'AUTRE'],
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

const prix = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 })

/** Prix en francs CFA : « 755 F ». */
export function formaterPrix(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${prix.format(v)} F`
}

const dateHeureCourte = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Africa/Dakar' })

export function formaterDateHeureCourte(iso: string | null | undefined): string {
  return iso ? dateHeureCourte.format(new Date(iso)) : '—'
}

// Dakar vit à UTC+0 toute l'année (pas d'heure d'été) : un champ « datetime-local » saisi à Dakar
// se convertit sans ambiguïté, quel que soit le fuseau du poste.

/** « 2026-08-16T00:00 » (heure de Dakar) → instant ISO. */
export function dakarVersIso(local: string): string {
  return new Date(`${local}:00Z`).toISOString()
}

/** Instant → valeur de champ « datetime-local » à l'heure de Dakar. */
export function isoVersDakar(iso: string | Date): string {
  return new Date(iso).toISOString().slice(0, 16)
}
