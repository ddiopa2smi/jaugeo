import { describe, expect, it } from 'vitest'
import type { Evenement } from '../api/types'
import { echeances } from './echeances'

const ev = (id: string, equipementId: string, date: string, prochaine: string | null): Evenement => ({
  id,
  cible: 'CUVE',
  equipementId,
  equipement: `Cuve ${equipementId}`,
  type: 'ETALONNAGE',
  date,
  indexAvant: null,
  indexApres: null,
  referenceScelles: null,
  intervenant: 'Métrologie',
  prochaineVerification: prochaine,
  commentaire: null,
  creePar: 'admin',
})

describe('echeances', () => {
  const maintenant = Date.parse('2026-11-08T15:00:00Z')

  it('signale les échéances dépassées et proches, triées, et ignore les lointaines', () => {
    const r = echeances(
      [
        ev('1', 'C1', '2025-11-01T10:00:00Z', '2026-11-01'),
        ev('2', 'C2', '2026-05-01T10:00:00Z', '2026-11-20'),
        ev('3', 'C3', '2026-05-01T10:00:00Z', '2027-06-30'),
      ],
      maintenant,
    )
    expect(r.map((e) => [e.evenement.equipementId, e.joursRestants])).toEqual([
      ['C1', -7],
      ['C2', 12],
    ])
  })

  it("ne retient que l'événement le plus récent de chaque équipement", () => {
    const r = echeances(
      [ev('1', 'C1', '2025-11-01T10:00:00Z', '2026-11-01'), ev('2', 'C1', '2026-11-05T10:00:00Z', '2027-11-05')],
      maintenant,
    )
    expect(r).toEqual([])
  })

  it("compte aujourd'hui comme 0 jour", () => {
    expect(echeances([ev('1', 'C1', '2026-01-01T00:00:00Z', '2026-11-08')], maintenant)[0]!.joursRestants).toBe(0)
  })
})
