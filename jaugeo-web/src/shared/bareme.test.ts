import { describe, expect, it } from 'vitest'
import { analyserBareme, volumePour } from './bareme'

describe('analyserBareme', () => {
  it('lit une copie Excel française avec en-tête, tabulations et séparateurs de milliers', () => {
    const r = analyserBareme('Hauteur (cm)\tVolume (L)\n0\t0\n10\t310,5\n50\t3 420\n100,5\t9 812,25\n', 'cm')
    expect(r.erreurs).toEqual([])
    expect(r.ignorees).toBe(1)
    expect(r.points).toEqual([
      { hauteurMm: 0, volumeLitres: 0 },
      { hauteurMm: 100, volumeLitres: 310.5 },
      { hauteurMm: 500, volumeLitres: 3420 },
      { hauteurMm: 1005, volumeLitres: 9812.25 },
    ])
  })

  it('accepte le CSV à point-virgule et trie les lignes', () => {
    const r = analyserBareme('200;2000\n0;0\n100;900', 'mm')
    expect(r.points.map((p) => p.hauteurMm)).toEqual([0, 100, 200])
  })

  it('signale les lignes illisibles, les doublons et les volumes non croissants', () => {
    const r = analyserBareme('0\t0\n10\tabc\n20\t50\n20\t60\n30\t40', 'mm')
    expect(r.erreurs).toEqual([
      'Ligne 2 : deux nombres attendus (hauteur, volume), lu « 10\tabc ».',
      'Hauteur 20 mm saisie deux fois.',
      'Le volume doit croître avec la hauteur : 20 mm → 60 L puis 30 mm → 40 L.',
    ])
  })

  it('refuse une hauteur en cm qui ne tombe pas sur un millimètre', () => {
    expect(analyserBareme('0\t0\n1,25\t10', 'cm').erreurs[0]).toContain('millimètre entier')
  })
})

describe('volumePour', () => {
  const points = [
    { hauteurMm: 0, volumeLitres: 0 },
    { hauteurMm: 1000, volumeLitres: 9750 },
    { hauteurMm: 1500, volumeLitres: 16080 },
  ]
  it('interpole comme le serveur', () => {
    expect(volumePour(points, 1234)).toBe(12712.44)
    expect(volumePour(points, 1000)).toBe(9750)
    expect(volumePour(points, 0)).toBe(0)
  })
  it('renvoie null hors barème', () => {
    expect(volumePour(points, 1501)).toBeNull()
  })
})
