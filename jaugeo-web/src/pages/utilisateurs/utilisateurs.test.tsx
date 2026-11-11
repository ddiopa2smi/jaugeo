import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Station, Utilisateur } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { creerQueryClient } from '../../app/queryClient'
import { installerFauxServeur } from '../../test/fauxServeur'
import { PageUtilisateurs } from './PageUtilisateurs'

const utilisateur = (identifiant: string, nomComplet: string, surcharge: Partial<Utilisateur> = {}): Utilisateur => ({
  id: `u-${identifiant}`,
  identifiant,
  nomComplet,
  email: null,
  telephone: null,
  role: 'GERANT',
  roleLibelle: 'Gérant de station',
  perimetre: 'STATIONS',
  stationIds: [1],
  actif: true,
  doitChangerMotDePasse: false,
  verrouille: false,
  derniereConnexion: '2026-11-09T08:30:00Z',
  version: 0,
  ...surcharge,
})

const STATIONS = [
  { id: 's1', numero: 1, code: 'NIA', nom: 'Niandane', actif: true },
  { id: 's2', numero: 2, code: 'KAY', nom: 'Kayar', actif: true },
  { id: 's3', numero: 3, code: 'POT', nom: 'Potou', actif: true },
] as Station[]

const UTILISATEURS = [
  utilisateur('fsarr', 'Fatou Sarr', { stationIds: [1, 2, 3], email: 'fatou@jaugeo.sn' }),
  utilisateur('admin', 'Awa Ndiaye', { role: 'ADMINISTRATEUR', roleLibelle: 'Administrateur', perimetre: 'RESEAU', stationIds: [] }),
  utilisateur('mdiop', 'Moussa Diop', { verrouille: true }),
  utilisateur('ibah', 'Ibrahima Bâ', { doitChangerMotDePasse: true, derniereConnexion: null }),
  utilisateur('ofall', 'Omar Fall', { actif: false }),
]

function afficher() {
  const posts: string[] = []
  installerFauxServeur(({ methode, chemin }) => {
    if (chemin === '/utilisateurs') return { status: 200, corps: UTILISATEURS }
    if (chemin === '/stations') return { status: 200, corps: STATIONS }
    if (chemin === '/utilisateurs/u-mdiop/reinitialiser-mot-de-passe' && methode === 'POST') {
      posts.push(chemin)
      return { status: 200, corps: { utilisateur: UTILISATEURS[2], motDePasseProvisoire: 'Xk7-pQ2m-Tz9' } }
    }
    return undefined
  })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <PageUtilisateurs />
    </Fournisseurs>,
  )
  return { posts }
}

const identifiants = () => screen.getAllByRole('listitem').map((c) => within(c).getByTestId(/^utilisateur-/).dataset.testid)

describe('utilisateurs', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('présente chaque compte avec son rôle, son périmètre en clair et son état', async () => {
    afficher()
    const fatou = await screen.findByTestId('utilisateur-fsarr')
    // Classés par nom ; les stations par leur nom, pas par leur code
    expect(identifiants()).toEqual(['utilisateur-admin', 'utilisateur-fsarr', 'utilisateur-ibah', 'utilisateur-mdiop', 'utilisateur-ofall'])
    expect(within(fatou).getByText('fsarr · Gérant de station')).toBeInTheDocument()
    await waitFor(() => expect(within(fatou).getByText('Niandane, Kayar +1')).toBeInTheDocument())
    expect(within(fatou).getByText('fatou@jaugeo.sn')).toBeInTheDocument()
    expect(within(fatou).getByText('Actif')).toBeInTheDocument()
    expect(within(screen.getByTestId('utilisateur-admin')).getByText('Tout le réseau')).toBeInTheDocument()
    expect(within(screen.getByTestId('utilisateur-mdiop')).getByText('Verrouillé')).toBeInTheDocument()
    const ibrahima = screen.getByTestId('utilisateur-ibah')
    expect(within(ibrahima).getByTitle('Mot de passe provisoire')).toBeInTheDocument()
    expect(within(ibrahima).getByText('Jamais connecté')).toBeInTheDocument()
    expect(within(screen.getByTestId('utilisateur-ofall')).getByText('Désactivé')).toBeInTheDocument()
  })

  it('filtre par état, par rôle et par recherche sans tenir compte des accents', async () => {
    afficher()
    const u = userEvent.setup()
    await screen.findByTestId('utilisateur-admin')
    expect(screen.getByText('Tous · 5')).toBeInTheDocument()

    await u.click(screen.getByTestId('filtre-VERROUILLE'))
    expect(identifiants()).toEqual(['utilisateur-mdiop'])
    // Un second clic retire le filtre
    await u.click(screen.getByTestId('filtre-VERROUILLE'))
    expect(identifiants()).toHaveLength(5)

    await u.type(screen.getByLabelText('Rechercher un utilisateur'), 'ba')
    expect(identifiants()).toEqual(['utilisateur-ibah'])
    await u.clear(screen.getByLabelText('Rechercher un utilisateur'))

    await u.click(screen.getByLabelText('Rôle'))
    await u.click(await screen.findByRole('option', { name: 'Administrateur' }))
    expect(identifiants()).toEqual(['utilisateur-admin'])

    await u.type(screen.getByLabelText('Rechercher un utilisateur'), 'zzz')
    expect(screen.getByText('Aucun utilisateur ne correspond à cette recherche.')).toBeInTheDocument()
  })

  it('réinitialise un mot de passe après confirmation et l’affiche une seule fois', async () => {
    const { posts } = afficher()
    const u = userEvent.setup()
    const moussa = await screen.findByTestId('utilisateur-mdiop')
    await u.click(within(moussa).getByRole('button', { name: 'Réinitialiser le mot de passe de mdiop' }))
    const confirmation = await screen.findByRole('dialog', { name: 'Réinitialiser le mot de passe ?' })
    expect(confirmation).toHaveTextContent('Moussa Diop')
    expect(posts).toEqual([])
    await u.click(within(confirmation).getByRole('button', { name: 'Réinitialiser' }))
    expect(await screen.findByTestId('mdp-provisoire')).toHaveTextContent('Xk7-pQ2m-Tz9')
    expect(posts).toEqual(['/utilisateurs/u-mdiop/reinitialiser-mot-de-passe'])
  })
})
