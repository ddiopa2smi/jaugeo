import type { FonctionEmploye, TypePoste } from '../api/types'
import { formaterPrix } from './libelles'

/** Postes d'une journée, dans l'ordre où ils se succèdent. */
export const POSTES: TypePoste[] = ['MATIN', 'SOIR', 'NUIT']

export const LIBELLES_POSTE: Record<TypePoste, string> = { MATIN: 'Matin', SOIR: 'Soir', NUIT: 'Nuit' }

export const FONCTIONS: Record<FonctionEmploye, string> = {
  POMPISTE: 'Pompiste',
  CAISSIER: 'Caissier',
  CHEF_PISTE: 'Chef de piste',
  GERANT: 'Gérant',
  GARDIEN: 'Gardien',
  AUTRE: 'Autre',
}

/** Écart de caisse (remis − dû) : « − 2 500 F » pour un manquant, « + 500 F » pour un excédent. */
export function formaterEcart(v: number): string {
  if (v === 0) return formaterPrix(0)
  return `${v < 0 ? '−' : '+'} ${formaterPrix(Math.abs(v))}`
}

export const COULEUR_ECART = (v: number) => (v < 0 ? 'error.main' : v > 0 ? 'success.main' : undefined)

/** Lundi de la semaine d'une date « AAAA-MM-JJ ». */
export function lundi(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7))
  return d.toISOString().slice(0, 10)
}

export function ajouterJours(iso: string, jours: number): string {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + jours)
  return d.toISOString().slice(0, 10)
}
