import type { CuveStock, NiveauEcart } from '../api/types'
import { versNombre } from './libelles'

export const NIVEAUX_ECART: Record<NiveauEcart, { libelle: string; couleur: 'success' | 'warning' | 'error' }> = {
  NORMAL: { libelle: 'Dans la tolérance', couleur: 'success' },
  ALERTE: { libelle: 'Alerte', couleur: 'warning' },
  ENQUETE: { libelle: 'Enquête', couleur: 'error' },
}

/** Saisie d'un jaugeage : hauteur (mm, convertie par le barème) ou volume lu directement. */
export interface SaisieJauge {
  mode: 'hauteur' | 'volume'
  valeur: string
}

export function jaugeInitiale(cuve: CuveStock, j: CuveStock['cloture']): SaisieJauge {
  if (j?.hauteurMm != null) return { mode: 'hauteur', valeur: String(j.hauteurMm) }
  if (j) return { mode: 'volume', valeur: String(j.volumeLitres) }
  return { mode: cuve.bareme ? 'hauteur' : 'volume', valeur: '' }
}

export function memeJauge(a: SaisieJauge, b: SaisieJauge): boolean {
  return a.mode === b.mode && versNombre(a.valeur) === versNombre(b.valeur)
}

/** Corps d'API d'une saisie de jauge ; les deux valeurs nulles retirent le jaugeage. */
export function corpsJauge(s: SaisieJauge): { hauteurMm: number | null; volumeLitres: number | null } {
  const n = versNombre(s.valeur)
  if (n === null) return { hauteurMm: null, volumeLitres: null }
  return s.mode === 'hauteur' ? { hauteurMm: Math.round(n), volumeLitres: null } : { hauteurMm: null, volumeLitres: n }
}

/**
 * Écart prévisible avant enregistrement, quand le volume est saisi directement et le théorique connu ;
 * une hauteur n'est convertie qu'à l'enregistrement (barème côté serveur).
 */
export function apercuEcart(theorique: number | null, s: SaisieJauge): number | null {
  const v = versNombre(s.valeur)
  if (theorique === null || v === null || s.mode !== 'volume') return null
  return Math.round((v - theorique) * 100) / 100
}

export function formaterPct(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return `${v.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
}

/** Mois courant au format AAAA-MM, heure de Dakar (UTC). */
export function moisCourant(): string {
  return new Date().toISOString().slice(0, 7)
}
