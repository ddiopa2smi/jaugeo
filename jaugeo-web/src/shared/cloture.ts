import type { PosteRegularisation } from '../api/types'

export const POSTES_REGULARISATION: Record<PosteRegularisation, string> = {
  CHIFFRE_AFFAIRES: "Chiffre d'affaires",
  COUT_ACHAT: "Coût d'achat",
  STOCK: 'Stock',
  DEPENSES: 'Dépenses',
  AUTRE: 'Autre',
}

export const MOIS_ANNEE = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre']

const formatMois = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric', timeZone: 'UTC' })

/** « 2026-01 » → « janvier 2026 » */
export function libelleMois(mois: string): string {
  return /^\d{4}-\d{2}$/.test(mois) ? formatMois.format(new Date(`${mois}-01T00:00:00Z`)) : mois
}

/** Mois précédent : « 2026-01 » → « 2025-12 » */
export function moisPrecedent(mois: string): string {
  const [a, m] = mois.split('-').map(Number)
  return m === 1 ? `${a - 1}-12` : `${a}-${String(m - 1).padStart(2, '0')}`
}

/** Répartit un total annuel sur douze mois égaux ; l'arrondi est porté par décembre, le total est conservé. */
export function repartir(total: number, decimales = 0): number[] {
  const pas = 10 ** decimales
  const part = Math.floor((total / 12) * pas) / pas
  const mois = Array<number>(12).fill(part)
  mois[11] = Math.round((total - part * 11) * pas) / pas
  return mois
}

/** Écart du réel au budget en % ; null sans budget. */
export function ecartBudgetPct(reel: number, budget: number): number | null {
  return budget > 0 ? ((reel - budget) / budget) * 100 : null
}

export function formaterEcartPct(v: number | null): string {
  if (v === null) return '—'
  const arrondi = Math.round(v * 10) / 10
  return `${arrondi > 0 ? '+' : ''}${arrondi.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`
}
