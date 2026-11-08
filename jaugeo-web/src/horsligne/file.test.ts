import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSession } from '../auth/session'
import { installerFauxServeur, utilisateurTest } from '../test/fauxServeur'
import { abandonner, envoyerSaisie, synchroniser, useHorsLigne, viderFile } from './file'

const saisie = (type: 'releves' | 'jaugeages' | 'cloture', version: number) => ({
  journeeId: 'j1',
  journeeLibelle: 'NIA · journée du 16/08/2026',
  type,
  methode: type === 'cloture' ? ('POST' as const) : ('PUT' as const),
  chemin: `/journees/j1/${type}`,
  corps: { version },
})

/** Corps et clé d'envoi de chaque appel de fetch. */
function envois(faux: ReturnType<typeof installerFauxServeur>['faux']) {
  return faux.mock.calls.map(([url, init]) => ({
    chemin: String(url).replace('/api/v1', ''),
    version: JSON.parse(String(init?.body)).version as number,
    cle: ((init?.headers ?? {}) as Record<string, string>)['Idempotency-Key'],
  }))
}

describe('saisies hors ligne', () => {
  beforeEach(async () => {
    useSession.setState({ jeton: 't', utilisateur: utilisateurTest({ identifiant: 'gerant.nia' }), statut: 'connecte', motif: null })
    await viderFile()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('envoie directement en ligne, avec une clé d’envoi', async () => {
    const { faux } = installerFauxServeur(() => ({ status: 200, corps: { version: 4 } }))
    const r = await envoyerSaisie<{ version: number }>(saisie('releves', 3))
    expect(r).toEqual({ envoye: true, resultat: { version: 4 } })
    expect(envois(faux)[0]!.cle).toMatch(/.{8,}/)
    expect(useHorsLigne.getState().envois).toHaveLength(0)
  })

  it('garde les saisies pendant une coupure puis les envoie dans l’ordre, chacune sur la version de la précédente', async () => {
    let reseau = false
    let version = 3
    const { faux } = installerFauxServeur(({ corps }) => {
      if (!reseau) throw new TypeError('Failed to fetch')
      if ((corps as { version: number }).version !== version) return { status: 409, corps: { code: 'VERSION_OBSOLETE' } }
      version++
      return { status: 200, corps: { version } }
    })

    expect(await envoyerSaisie(saisie('releves', 3))).toEqual({ envoye: false })
    expect(useHorsLigne.getState().enLigne).toBe(false)
    // La suite de la même journée attend son tour, sans même tenter l'envoi
    expect(await envoyerSaisie(saisie('jaugeages', 3))).toEqual({ envoye: false })
    expect(await envoyerSaisie(saisie('cloture', 3))).toEqual({ envoye: false })
    expect(faux).toHaveBeenCalledTimes(1)
    expect(useHorsLigne.getState().envois.map((e) => e.type)).toEqual(['releves', 'jaugeages', 'cloture'])

    // Toujours coupé : rien ne part
    await synchroniser()
    expect(useHorsLigne.getState().envois).toHaveLength(3)

    reseau = true
    faux.mockClear()
    await synchroniser()
    expect(envois(faux).map((e) => [e.chemin, e.version])).toEqual([
      ['/journees/j1/releves', 3],
      ['/journees/j1/jaugeages', 4],
      ['/journees/j1/cloture', 5],
    ])
    expect(new Set(envois(faux).map((e) => e.cle)).size).toBe(3)
    expect(useHorsLigne.getState().envois).toHaveLength(0)
    expect(useHorsLigne.getState().enLigne).toBe(true)
  })

  it('rejoue une saisie avec la même clé quand la réponse a été perdue', async () => {
    let coupe = true
    const { faux } = installerFauxServeur(() => {
      if (coupe) throw new TypeError('Failed to fetch')
      return { status: 200, corps: { version: 4 } }
    })
    await envoyerSaisie(saisie('releves', 3))
    const cle = envois(faux)[0]!.cle
    coupe = false
    await synchroniser()
    expect(envois(faux)[1]!.cle).toBe(cle)
  })

  it('signale un refus du serveur et bloque la suite de la journée jusqu’à l’abandon', async () => {
    let reseau = false
    const refuser = new Set(['/journees/j1/releves'])
    const { faux } = installerFauxServeur(({ chemin }) => {
      if (!reseau) throw new TypeError('Failed to fetch')
      if (refuser.has(chemin)) return { status: 409, corps: { code: 'VERSION_OBSOLETE', detail: 'Version obsolète.' } }
      return { status: 200, corps: { version: 9 } }
    })
    await envoyerSaisie(saisie('releves', 3))
    await envoyerSaisie(saisie('cloture', 3))
    reseau = true
    faux.mockClear()
    await synchroniser()

    const [refusee, suivante] = useHorsLigne.getState().envois
    expect(refusee!.etat).toBe('CONFLIT')
    expect(refusee!.erreur?.code).toBe('VERSION_OBSOLETE')
    expect(refusee!.erreur?.message).toContain('modifiée par quelqu')
    expect(suivante!.etat).toBe('EN_ATTENTE')
    expect(faux).toHaveBeenCalledTimes(1)

    // Abandon de la saisie refusée : la clôture part
    await abandonner(refusee!.id!)
    await vi.waitFor(() => expect(useHorsLigne.getState().envois).toHaveLength(0))
    expect(envois(faux).map((e) => e.chemin)).toEqual(['/journees/j1/releves', '/journees/j1/cloture'])
  })

  it('ne montre que les saisies de l’utilisateur connecté', async () => {
    installerFauxServeur(() => {
      throw new TypeError('Failed to fetch')
    })
    await envoyerSaisie(saisie('releves', 3))
    useSession.setState({ utilisateur: utilisateurTest({ identifiant: 'gerant.yof' }) })
    await synchroniser()
    expect(useHorsLigne.getState().envois).toHaveLength(0)
  })
})
