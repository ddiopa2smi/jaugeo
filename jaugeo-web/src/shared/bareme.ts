import type { PointBareme } from '../api/types'

export type UniteHauteur = 'mm' | 'cm'

export interface AnalyseBareme {
  points: PointBareme[]
  erreurs: string[]
  /** Lignes d'en-tête ou de commentaire ignorées (texte non numérique). */
  ignorees: number
}

/** Nombre au format français ou anglais : « 1 234,56 », « 1234.56 », « 1 234 » (espace insécable compris). */
function lireNombre(texte: string): number | null {
  const t = texte.replace(/[\s  ]/g, '').replace(',', '.')
  if (!/^-?\d+(\.\d+)?$/.test(t)) return null
  return Number(t)
}

/** Découpe une ligne en deux colonnes : tabulation (copie Excel), point-virgule (CSV), sinon espaces. */
function colonnes(ligne: string): string[] {
  if (ligne.includes('\t')) return ligne.split('\t').map((c) => c.trim()).filter(Boolean)
  if (ligne.includes(';')) return ligne.split(';').map((c) => c.trim()).filter(Boolean)
  return ligne.trim().split(/\s+/)
}

/**
 * Analyse un barème collé depuis Excel ou un certificat : une ligne par point, « hauteur  volume ».
 * Les hauteurs sont converties en millimètres entiers ; les erreurs sont listées ligne par ligne.
 */
export function analyserBareme(texte: string, unite: UniteHauteur): AnalyseBareme {
  const points: PointBareme[] = []
  const erreurs: string[] = []
  let ignorees = 0
  texte.split(/\r?\n/).forEach((brute, i) => {
    const ligne = brute.trim()
    if (!ligne) return
    const cols = colonnes(ligne)
    const nombres = cols.map(lireNombre)
    if (nombres.every((n) => n === null)) {
      ignorees++
      return
    }
    if (cols.length !== 2 || nombres.some((n) => n === null)) {
      erreurs.push(`Ligne ${i + 1} : deux nombres attendus (hauteur, volume), lu « ${ligne} ».`)
      return
    }
    const [h, v] = nombres as [number, number]
    const mm = unite === 'cm' ? h * 10 : h
    const mmEntier = Math.round(mm * 1000) / 1000
    if (!Number.isInteger(mmEntier)) {
      erreurs.push(`Ligne ${i + 1} : la hauteur ${h} ${unite} ne tombe pas sur un millimètre entier.`)
      return
    }
    if (mmEntier < 0 || v < 0) {
      erreurs.push(`Ligne ${i + 1} : valeur négative.`)
      return
    }
    points.push({ hauteurMm: mmEntier, volumeLitres: Math.round(v * 100) / 100 })
  })

  points.sort((a, b) => a.hauteurMm - b.hauteurMm)
  for (let i = 1; i < points.length; i++) {
    const [a, b] = [points[i - 1]!, points[i]!]
    if (a.hauteurMm === b.hauteurMm) erreurs.push(`Hauteur ${b.hauteurMm} mm saisie deux fois.`)
    else if (b.volumeLitres <= a.volumeLitres)
      erreurs.push(`Le volume doit croître avec la hauteur : ${a.hauteurMm} mm → ${a.volumeLitres} L puis ${b.hauteurMm} mm → ${b.volumeLitres} L.`)
  }
  if (points.length > 0 && points.length < 2) erreurs.push('Il faut au moins deux points.')
  return { points, erreurs, ignorees }
}

/** Même interpolation linéaire que le serveur (aperçu instantané dans le calculateur). */
export function volumePour(points: PointBareme[], hauteurMm: number): number | null {
  if (points.length < 2) return null
  const premier = points[0]!
  const dernier = points[points.length - 1]!
  if (hauteurMm < premier.hauteurMm || hauteurMm > dernier.hauteurMm) return null
  let bas = 0
  let haut = points.length - 1
  while (haut - bas > 1) {
    const milieu = (bas + haut) >> 1
    if (points[milieu]!.hauteurMm <= hauteurMm) bas = milieu
    else haut = milieu
  }
  const a = points[bas]!
  const b = points[haut]!
  if (hauteurMm === a.hauteurMm) return a.volumeLitres
  if (hauteurMm === b.hauteurMm) return b.volumeLitres
  const v = a.volumeLitres + ((b.volumeLitres - a.volumeLitres) * (hauteurMm - a.hauteurMm)) / (b.hauteurMm - a.hauteurMm)
  return Math.round(v * 100) / 100
}
