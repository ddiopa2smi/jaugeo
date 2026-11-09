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

/**
 * Facteur de correction à 15 °C (ASTM D1250, table 54B), identique au calcul de l'API, pour l'aperçu de saisie.
 * Densité à 15 °C en kg/L ; null hors du domaine de la table.
 */
export function facteur15(temperatureC: number, densite15: number): number | null {
  const rho = densite15 * 1000
  if (rho < 610.5 || rho > 1075) return null
  const alpha =
    rho < 770.5
      ? 346.4228 / (rho * rho) + 0.4388 / rho
      : rho < 787.5
        ? -0.00336312 + 2680.3206 / (rho * rho)
        : rho < 839
          ? 594.5418 / (rho * rho)
          : 186.9696 / (rho * rho) + 0.4862 / rho
  const dt = temperatureC - 15
  return Math.round(Math.exp(-alpha * dt * (1 + 0.8 * alpha * dt)) * 100000) / 100000
}

export const IMPUTATIONS = { TRANSPORTEUR: 'transporteur', FOURNISSEUR: 'fournisseur' } as const

export function formaterPct(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return `${v.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} %`
}

/** Mois courant au format AAAA-MM, heure de Dakar (UTC). */
export function moisCourant(): string {
  return new Date().toISOString().slice(0, 7)
}
