import type { LigneReleve, StatutJournee } from '../api/types'
import { versNombre } from './libelles'

export const STATUTS_JOURNEE: Record<StatutJournee, { libelle: string; couleur: 'warning' | 'info' | 'success' }> = {
  BROUILLON: { libelle: 'En saisie', couleur: 'warning' },
  CLOTUREE: { libelle: 'Clôturée', couleur: 'info' },
  VALIDEE: { libelle: 'Validée', couleur: 'success' },
}

/** Saisie en cours d'une ligne, telle que tapée sur la tablette. */
export interface SaisieLigne {
  arrivee: string
  remise: string
  lectures: Record<string, string>
}

/** Nombre → texte de champ à la française (virgule décimale, sans séparateur de milliers). */
const versChamp = (v: number) => String(v).replace('.', ',')

export function saisieInitiale(l: LigneReleve): SaisieLigne {
  const lectures: Record<string, string> = {}
  for (const instant of l.lecturesAttendues) lectures[instant] = ''
  for (const lu of l.lectures) lectures[lu.instant] = versChamp(lu.index)
  return {
    arrivee: l.indexArrivee === null ? '' : versChamp(l.indexArrivee),
    remise: l.remiseCuve ? versChamp(l.remiseCuve) : '',
    lectures,
  }
}

export function memeSaisie(a: SaisieLigne, b: SaisieLigne): boolean {
  if (versNombre(a.arrivee) !== versNombre(b.arrivee) || (versNombre(a.remise) ?? 0) !== (versNombre(b.remise) ?? 0)) return false
  const instants = new Set([...Object.keys(a.lectures), ...Object.keys(b.lectures)])
  return [...instants].every((i) => versNombre(a.lectures[i]) === versNombre(b.lectures[i]))
}

export interface Apercu {
  sortieBrute: number | null
  sortieNette: number | null
  montant: number | null
  alerte: string | null
}

const arrondi2 = (v: number) => Math.round(v * 100) / 100

/**
 * Aperçu immédiat pendant la saisie, pour le cas courant (un seul compteur, un seul prix sur la journée).
 * Les journées avec changement de compteur ou de prix sont calculées par le serveur à l'enregistrement :
 * renvoie alors null. Le serveur reste la seule référence à la clôture.
 */
export function apercu(l: LigneReleve, s: SaisieLigne): Apercu | null {
  if (l.ruptures.length > 0 || l.tranches.length > 1) return null
  const arrivee = versNombre(s.arrivee)
  if (arrivee === null) return { sortieBrute: null, sortieNette: null, montant: null, alerte: null }
  const brute = arrondi2(arrivee - l.indexDepart)
  if (brute < 0) {
    return { sortieBrute: brute, sortieNette: null, montant: null, alerte: "Index d'arrivée inférieur à l'index de départ." }
  }
  const remise = versNombre(s.remise) ?? 0
  if (remise < 0 || remise > brute) {
    return { sortieBrute: brute, sortieNette: null, montant: null, alerte: 'La remise en cuve dépasse la sortie du pistolet.' }
  }
  const nette = arrondi2(brute - remise)
  const prix = l.tranches[0]?.prixUnitaire ?? null
  if (prix === null) {
    return { sortieBrute: brute, sortieNette: nette, montant: null, alerte: nette > 0 ? 'Aucun prix en vigueur pour ce produit.' : null }
  }
  // Montant arrondi au franc, demi-unité vers le haut (comme le serveur)
  return { sortieBrute: brute, sortieNette: nette, montant: Math.round(arrondi2(nette * prix) + 1e-9), alerte: null }
}
