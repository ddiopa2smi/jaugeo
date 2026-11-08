import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Permission } from '../api/types'
import { useSession } from '../auth/session'
import { installerFauxServeur, nonAuthentifie, sessionTest, utilisateurTest } from '../test/fauxServeur'
import { Fournisseurs } from './Fournisseurs'
import { RestaurationSession } from './Gardes'
import { creerQueryClient } from './queryClient'
import { routes } from './routes'

const STATIONS = [
  {
    id: 's-nia', numero: 1, code: 'NIA', nom: 'Niandane', type: 'ROUTIERE', regionCode: 'SL', regionNom: 'Saint-Louis',
    localite: 'Niandane', latitude: null, longitude: null, modeExploitation: 'PROPRE', dateOuverture: null, actif: true, version: 0,
  },
]

function demarrer(chemin = '/') {
  const routeur = createMemoryRouter(routes, { initialEntries: [chemin] })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>,
  )
  return routeur
}

describe("parcours de l'application", () => {
  beforeEach(() => {
    useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null })
  })
  afterEach(() => vi.unstubAllGlobals())

  it("sans session, redirige vers la connexion puis revient à la page demandée après connexion", async () => {
    const { appels } = installerFauxServeur(({ chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return nonAuthentifie
      if (chemin === '/auth/connexion') {
        const { motDePasse } = corps as { motDePasse: string }
        return motDePasse === 'Bon-Mot-2026'
          ? { status: 200, corps: sessionTest('j1') }
          : { status: 401, corps: { code: 'IDENTIFIANTS_INVALIDES', detail: 'Identifiant ou mot de passe incorrect.' } }
      }
      if (chemin === '/stations') return { status: 200, corps: STATIONS }
      return undefined
    })
    const routeur = demarrer('/stations')
    const utilisateur = userEvent.setup()

    await screen.findByRole('button', { name: 'Se connecter' })
    expect(routeur.state.location.pathname).toBe('/connexion')

    await utilisateur.click(screen.getByRole('button', { name: 'Se connecter' }))
    expect(await screen.findByText('Saisissez votre identifiant')).toBeInTheDocument()

    await utilisateur.type(screen.getByLabelText('Identifiant'), 'admin')
    await utilisateur.type(screen.getByLabelText('Mot de passe'), 'mauvais')
    await utilisateur.click(screen.getByRole('button', { name: 'Se connecter' }))
    expect(await screen.findByText('Identifiant ou mot de passe incorrect.')).toBeInTheDocument()

    await utilisateur.clear(screen.getByLabelText('Mot de passe'))
    await utilisateur.type(screen.getByLabelText('Mot de passe'), 'Bon-Mot-2026')
    await utilisateur.click(screen.getByRole('button', { name: 'Se connecter' }))

    expect(await screen.findByRole('heading', { name: 'Stations' })).toBeInTheDocument()
    expect(routeur.state.location.pathname).toBe('/stations')
    // La grille ne dessine pas ses cellules sous jsdom (largeur nulle) : on vérifie l'appel authentifié.
    await waitFor(() => expect(appels).toContainEqual({ methode: 'GET', chemin: '/stations', jeton: 'j1' }))
  })

  it('restaure la session depuis le cookie au chargement', async () => {
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j2') }
      if (chemin === '/stations') return { status: 200, corps: STATIONS }
      if (chemin === '/produits') return { status: 200, corps: [] }
      return undefined
    })
    demarrer('/')
    expect(await screen.findByRole('heading', { name: 'Bonjour Awa' })).toBeInTheDocument()
  })

  it('impose le changement du mot de passe provisoire avant tout accès', async () => {
    const provisoire = utilisateurTest({ doitChangerMotDePasse: true })
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j3', provisoire) }
      if (chemin === '/auth/mot-de-passe') return { status: 200, corps: sessionTest('j4') }
      if (chemin === '/stations' || chemin === '/produits') return { status: 200, corps: [] }
      return undefined
    })
    const routeur = demarrer('/utilisateurs')
    const utilisateur = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Choisissez votre mot de passe' })).toBeInTheDocument()
    expect(routeur.state.location.pathname).toBe('/changer-mot-de-passe')

    await utilisateur.type(screen.getByLabelText('Mot de passe actuel'), 'Provisoire1')
    await utilisateur.type(screen.getByLabelText('Nouveau mot de passe'), 'faible')
    await utilisateur.type(screen.getByLabelText('Confirmer le nouveau mot de passe'), 'faible')
    await utilisateur.click(screen.getByRole('button', { name: 'Enregistrer le nouveau mot de passe' }))
    expect(await screen.findByText(/Il faut : au moins 10 caractères/)).toBeInTheDocument()

    await utilisateur.clear(screen.getByLabelText('Nouveau mot de passe'))
    await utilisateur.clear(screen.getByLabelText('Confirmer le nouveau mot de passe'))
    await utilisateur.type(screen.getByLabelText('Nouveau mot de passe'), 'Siege-Dakar-2026')
    await utilisateur.type(screen.getByLabelText('Confirmer le nouveau mot de passe'), 'Siege-Dakar-2026')
    await utilisateur.click(screen.getByRole('button', { name: 'Enregistrer le nouveau mot de passe' }))

    await waitFor(() => expect(routeur.state.location.pathname).toBe('/'))
    expect(useSession.getState().jeton).toBe('j4')
  })

  it("n'affiche dans le menu que les rubriques autorisées et bloque l'accès direct aux autres", async () => {
    const gerant = utilisateurTest({
      identifiant: 'gerant.nia',
      nomComplet: 'Moussa Fall',
      role: 'GERANT',
      roleLibelle: 'Gérant de station',
      permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_SAISIR'] as Permission[],
      perimetre: 'STATIONS',
      stationIds: [1],
    })
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j5', gerant) }
      if (chemin === '/stations') return { status: 200, corps: STATIONS }
      if (chemin === '/produits') return { status: 200, corps: [] }
      return undefined
    })
    const routeur = demarrer('/')
    await screen.findByRole('heading', { name: 'Bonjour Moussa' })

    const menu = screen.getByRole('navigation')
    expect(within(menu).getByText('Stations')).toBeInTheDocument()
    expect(within(menu).queryByText('Utilisateurs')).not.toBeInTheDocument()
    expect(within(menu).queryByText("Journal d'audit")).not.toBeInTheDocument()

    await routeur.navigate('/utilisateurs')
    expect(await screen.findByText(/Vous n'avez pas accès à cette page/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nouvelle station' })).not.toBeInTheDocument()
  })
})
