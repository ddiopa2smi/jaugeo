import type { ModeReglementManquant, StatutDepense, StatutManquant, StatutVersement, TypeArrete } from '../api/types'

type Couleur = 'default' | 'success' | 'warning' | 'error' | 'info'

export const STATUTS_DEPENSE: Record<StatutDepense, { libelle: string; couleur: Couleur }> = {
  A_VALIDER: { libelle: 'À valider', couleur: 'warning' },
  AUTORISEE: { libelle: 'Autorisée', couleur: 'success' },
  REFUSEE: { libelle: 'Refusée', couleur: 'error' },
}

export const STATUTS_VERSEMENT: Record<StatutVersement, { libelle: string; couleur: Couleur }> = {
  SAISI: { libelle: 'À vérifier', couleur: 'warning' },
  VERIFIE: { libelle: 'Vérifié', couleur: 'success' },
  REJETE: { libelle: 'Rejeté', couleur: 'error' },
}

export const STATUTS_MANQUANT: Record<StatutManquant, { libelle: string; couleur: Couleur }> = {
  OUVERT: { libelle: 'Ouvert', couleur: 'error' },
  SOLDE: { libelle: 'Soldé', couleur: 'success' },
}

export const TYPES_ARRETE: Record<TypeArrete, string> = {
  REPRISE: 'Reprise de solde',
  CONTROLE: 'Contrôle de caisse',
}

export const MODES_REGLEMENT: Record<ModeReglementManquant, string> = {
  REMBOURSEMENT: 'Remboursement par le gérant',
  RETENUE: 'Retenue sur commission',
  PERTE: 'Passé en perte',
}

/** Types acceptés par l'API pour une photo de bordereau ou un justificatif. */
export const TYPES_PIECE = 'image/jpeg,image/png,image/webp,application/pdf'
export const TAILLE_MAX_PIECE = 5 * 1024 * 1024

/** Contrôle côté navigateur avant l'envoi (l'API refait le contrôle sur le contenu). */
export function erreurPiece(fichier: File): string | null {
  if (fichier.size === 0) return 'Le fichier est vide.'
  if (fichier.size > TAILLE_MAX_PIECE) return 'Fichier trop lourd : 5 Mo au plus. Reprenez la photo en qualité réduite.'
  if (fichier.type && !TYPES_PIECE.split(',').includes(fichier.type)) return 'Joignez une photo (JPEG, PNG, WebP) ou un PDF.'
  return null
}

/** Signe explicite pour un écart : +1 500 F / −1 500 F. */
export function formaterEcart(v: number | null | undefined, format: (n: number) => string): string {
  if (v === null || v === undefined) return '—'
  if (v === 0) return format(0)
  return `${v > 0 ? '+' : '−'}${format(Math.abs(v))}`
}

/** Date du jour à Dakar (UTC toute l'année), au format AAAA-MM-JJ. */
export function aujourdhui(): string {
  return new Date().toISOString().slice(0, 10)
}
