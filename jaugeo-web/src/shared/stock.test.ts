import { describe, expect, it } from 'vitest'
import { facteur15 } from './stock'

describe('correction à 15 °C (table 54B, même calcul que l’API)', () => {
  it('donne les facteurs publiés', () => {
    expect(facteur15(15, 0.84)).toBe(1)
    expect(facteur15(30, 0.84)!.toFixed(4)).toBe('0.9873') // gasoil
    expect(facteur15(30, 0.745)!.toFixed(4)).toBe('0.9817') // super
    expect(facteur15(10, 0.84)!.toFixed(4)).toBe('1.0042')
  })

  it('refuse une densité hors de la table', () => {
    expect(facteur15(30, 0.55)).toBeNull()
    expect(facteur15(30, 1.2)).toBeNull()
  })
})
