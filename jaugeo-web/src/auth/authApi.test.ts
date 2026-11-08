import { describe, expect, it } from 'vitest'
import { versNombre } from '../shared/libelles'
import { problemesMotDePasse } from './authApi'

describe('politique de mot de passe (identique au serveur)', () => {
  it.each(['court1A', 'toutenminuscule1', 'TOUTENMAJUSCULE1', 'SansChiffreIci'])('refuse « %s »', (mdp) => {
    expect(problemesMotDePasse(mdp, 'kmbaye')).not.toHaveLength(0)
  })

  it("refuse un mot de passe contenant l'identifiant, quelle que soit la casse", () => {
    expect(problemesMotDePasse('Kmbaye-2026-ok', 'kmbaye')).toContain("ne pas contenir l'identifiant")
  })

  it('accepte un mot de passe robuste, y compris avec des lettres accentuées', () => {
    expect(problemesMotDePasse('Station-Yoff-2026', 'kmbaye')).toEqual([])
    expect(problemesMotDePasse('Élévation-2026', 'kmbaye')).toEqual([])
  })
})

describe('saisie des nombres', () => {
  it('accepte la virgule décimale et les espaces de milliers', () => {
    expect(versNombre('30 000,5')).toBe(30000.5)
    expect(versNombre('14.7167')).toBe(14.7167)
    expect(versNombre('')).toBeNull()
    expect(versNombre('abc')).toBeNull()
  })
})
