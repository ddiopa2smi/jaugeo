import { useState } from 'react'
import { useSearchParams } from 'react-router'

/**
 * Ligne choisie d'un tableau, gardée dans l'adresse (paramètre `nom`) : elle reste marquée
 * au retour d'une page ouverte depuis la ligne et après un rechargement.
 */
export function useLigneChoisie(nom = 'ligne'): [string | null, (cle: string) => void] {
  const [params, setParams] = useSearchParams()
  const choisir = (cle: string) =>
    setParams(
      (p) => {
        const n = new URLSearchParams(p)
        n.set(nom, cle)
        return n
      },
      { replace: true },
    )
  return [params.get(nom), choisir]
}

// Tri et recherche des tableaux (les composants d'affichage sont dans tableaux.tsx).

type Valeur = string | number | null | undefined
export type Sens = 'asc' | 'desc'

export interface Tri<T, C extends string> {
  cle: C
  sens: Sens
  basculer: (cle: C) => void
  trier: (liste: T[]) => T[]
}

/** Valeurs vides toujours en fin de liste ; textes comparés à la française, nombres compris (« BV 9 » avant « BV 10 »). */
function comparer(a: Valeur, b: Valeur, sens: Sens): number {
  const videA = a === null || a === undefined || a === ''
  const videB = b === null || b === undefined || b === ''
  if (videA || videB) return videA === videB ? 0 : videA ? 1 : -1
  const c = typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'fr', { numeric: true, sensitivity: 'base' })
  return sens === 'asc' ? c : -c
}

/** Tri d'un tableau par colonne : un clic sur l'en-tête trie, un second inverse le sens. */
export function useTri<T, C extends string>(acces: Record<C, (x: T) => Valeur>, cleInitiale: C, sensInitial: Sens = 'asc'): Tri<T, C> {
  const [etat, setEtat] = useState<{ cle: C; sens: Sens }>({ cle: cleInitiale, sens: sensInitial })
  return {
    ...etat,
    basculer: (cle) => setEtat((e) => (e.cle === cle ? { cle, sens: e.sens === 'asc' ? 'desc' : 'asc' } : { cle, sens: 'asc' })),
    trier: (liste) => [...liste].sort((a, b) => comparer(acces[etat.cle](a), acces[etat.cle](b), etat.sens)),
  }
}

/** Recherche sans tenir compte de la casse ni des accents. */
export const normaliser = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Vrai si tous les mots recherchés figurent dans l'un des textes. */
export function correspond(recherche: string, ...textes: (string | null | undefined)[]): boolean {
  const mots = normaliser(recherche).split(/\s+/).filter(Boolean)
  if (mots.length === 0) return true
  const cible = normaliser(textes.filter(Boolean).join(' '))
  return mots.every((m) => cible.includes(m))
}
