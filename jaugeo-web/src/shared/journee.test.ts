import { describe, expect, it } from 'vitest'
import type { LigneReleve } from '../api/types'
import { apercu, memeSaisie, saisieInitiale } from './journee'

const ligne = (surcharge: Partial<LigneReleve> = {}): LigneReleve => ({
  releveId: 'r1',
  pistoletId: 'p1',
  pistoletCode: 'G1',
  pistoletLibelle: 'Gasoil 1',
  pompeCode: 'P1',
  cuveCode: 'C1',
  produitCode: 'GASOIL',
  produitLibelle: 'Gasoil',
  indexDepart: 88916,
  indexArrivee: null,
  remiseCuve: 0,
  lectures: [],
  lecturesAttendues: [],
  ruptures: [],
  tranches: [{ debut: '2026-08-16T00:00:00Z', fin: '2026-08-17T00:00:00Z', prixUnitaire: 755, litres: null, montant: null }],
  sortieBrute: null,
  sortieNette: null,
  montant: null,
  anomalies: [],
  ...surcharge,
})

describe('aperçu de saisie', () => {
  it('calcule sortie nette et montant arrondi au franc', () => {
    const a = apercu(ligne(), { arrivee: '89716,5', remise: '20', lectures: {} })
    expect(a).toEqual({ sortieBrute: 800.5, sortieNette: 780.5, montant: 589278, alerte: null })
  })

  it('signale un index inférieur et une remise excessive', () => {
    expect(apercu(ligne(), { arrivee: '88900', remise: '', lectures: {} })?.alerte).toMatch(/inférieur/)
    expect(apercu(ligne(), { arrivee: '88926', remise: '11', lectures: {} })?.alerte).toMatch(/remise/)
  })

  it('laisse le serveur calculer les journées à plusieurs prix ou compteurs', () => {
    const deuxPrix = ligne({
      tranches: [
        { debut: '2026-08-16T00:00:00Z', fin: '2026-08-16T12:00:00Z', prixUnitaire: 680, litres: null, montant: null },
        { debut: '2026-08-16T12:00:00Z', fin: '2026-08-17T00:00:00Z', prixUnitaire: 755, litres: null, montant: null },
      ],
    })
    expect(apercu(deuxPrix, { arrivee: '90000', remise: '', lectures: {} })).toBeNull()
  })

  it("compare la saisie à l'enregistré sans tenir compte de la forme", () => {
    const l = ligne({ indexArrivee: 89716.5, lecturesAttendues: ['2026-08-16T12:00:00Z'] })
    const initiale = saisieInitiale(l)
    expect(initiale.lectures).toEqual({ '2026-08-16T12:00:00Z': '' })
    expect(memeSaisie(initiale, { ...initiale, arrivee: '89716,50' })).toBe(true)
    expect(memeSaisie(initiale, { ...initiale, remise: '0' })).toBe(true)
    expect(memeSaisie(initiale, { ...initiale, lectures: { '2026-08-16T12:00:00Z': '89000' } })).toBe(false)
  })
})
