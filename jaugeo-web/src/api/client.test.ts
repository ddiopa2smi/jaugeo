import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSession } from '../auth/session'
import { installerFauxServeur, nonAuthentifie, sessionTest } from '../test/fauxServeur'
import { api, ErreurApi, messageErreur, rafraichirSession } from './client'

describe('client API', () => {
  beforeEach(() => {
    useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null })
  })
  afterEach(() => vi.unstubAllGlobals())

  it("envoie le jeton d'accès et renvoie le JSON", async () => {
    useSession.getState().ouvrir(sessionTest('jeton-1'))
    const { appels } = installerFauxServeur(({ chemin }) => (chemin === '/stations' ? { status: 200, corps: [{ code: 'NIA' }] } : undefined))
    await expect(api.get('/stations')).resolves.toEqual([{ code: 'NIA' }])
    expect(appels[0]).toMatchObject({ methode: 'GET', chemin: '/stations', jeton: 'jeton-1' })
  })

  it('sur 401, rafraîchit la session une fois puis rejoue la requête avec le nouveau jeton', async () => {
    useSession.getState().ouvrir(sessionTest('expire'))
    const { appels } = installerFauxServeur(({ chemin, jeton }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('neuf') }
      if (chemin === '/produits') return jeton === 'neuf' ? { status: 200, corps: [] } : nonAuthentifie
      return undefined
    })
    await expect(api.get('/produits')).resolves.toEqual([])
    expect(appels.map((a) => `${a.chemin}:${a.jeton}`)).toEqual(['/produits:expire', '/auth/rafraichir:null', '/produits:neuf'])
    expect(useSession.getState().jeton).toBe('neuf')
  })

  it('mutualise les rafraîchissements simultanés (le jeton tournant ne doit être utilisé qu’une fois)', async () => {
    useSession.getState().ouvrir(sessionTest('expire'))
    const { appels } = installerFauxServeur(({ chemin, jeton }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('neuf') }
      return jeton === 'neuf' ? { status: 200, corps: chemin } : nonAuthentifie
    })
    const resultats = await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')])
    expect(resultats).toEqual(['/a', '/b', '/c'])
    expect(appels.filter((a) => a.chemin === '/auth/rafraichir')).toHaveLength(1)
  })

  it('ferme la session si le rafraîchissement échoue', async () => {
    useSession.getState().ouvrir(sessionTest('expire'))
    installerFauxServeur(({ chemin }) =>
      chemin === '/auth/rafraichir' ? { status: 401, corps: { code: 'SESSION_EXPIREE', detail: 'Session expirée.' } } : nonAuthentifie,
    )
    await expect(api.get('/stations')).rejects.toMatchObject({ code: 'SESSION_EXPIREE', status: 401 })
    expect(useSession.getState()).toMatchObject({ statut: 'deconnecte', utilisateur: null, jeton: null })
    expect(useSession.getState().motif).toMatch(/expiré/)
  })

  it("ne tente pas de rafraîchir sur un échec de connexion et remonte le message du serveur", async () => {
    const { appels } = installerFauxServeur(() => ({
      status: 401,
      corps: { code: 'IDENTIFIANTS_INVALIDES', detail: 'Identifiant ou mot de passe incorrect.' },
    }))
    const erreur = await api.post('/auth/connexion', { identifiant: 'x', motDePasse: 'y' }).catch((e: unknown) => e)
    expect(erreur).toBeInstanceOf(ErreurApi)
    expect(messageErreur(erreur)).toBe('Identifiant ou mot de passe incorrect.')
    expect(appels).toHaveLength(1)
  })

  it('signale à la session un changement de mot de passe exigé', async () => {
    useSession.getState().ouvrir(sessionTest('j'))
    installerFauxServeur(() => ({ status: 403, corps: { code: 'MOT_DE_PASSE_A_CHANGER', detail: 'Changez votre mot de passe.' } }))
    await expect(api.get('/stations')).rejects.toMatchObject({ code: 'MOT_DE_PASSE_A_CHANGER' })
    expect(useSession.getState().utilisateur?.doitChangerMotDePasse).toBe(true)
  })

  it('expose les erreurs de champ et traduit le conflit de version', async () => {
    useSession.getState().ouvrir(sessionTest('j'))
    installerFauxServeur(({ chemin }) =>
      chemin === '/stations'
        ? { status: 400, corps: { code: 'SAISIE_INVALIDE', detail: 'Saisie invalide', champs: { nom: 'obligatoire' } } }
        : { status: 409, corps: { code: 'VERSION_OBSOLETE', detail: 'Version obsolète' } },
    )
    const invalide = (await api.post('/stations', {}).catch((e: unknown) => e)) as ErreurApi
    expect(invalide.champs).toEqual({ nom: 'obligatoire' })
    const conflit = await api.put('/stations/x', {}).catch((e: unknown) => e)
    expect(messageErreur(conflit)).toMatch(/modifiée par quelqu'un d'autre/)
  })

  it('signale un serveur injoignable', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const erreur = await api.get('/stations').catch((e: unknown) => e)
    expect(messageErreur(erreur)).toMatch(/injoignable/)
  })

  it('rafraichirSession ouvre la session', async () => {
    installerFauxServeur(() => ({ status: 200, corps: sessionTest('r') }))
    await rafraichirSession()
    expect(useSession.getState()).toMatchObject({ statut: 'connecte', jeton: 'r' })
  })
})
