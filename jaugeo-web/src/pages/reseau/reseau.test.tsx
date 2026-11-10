import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Permission, StationReseau, TableauReseau } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

// La carte (Leaflet) a besoin d'un vrai moteur de rendu : remplacée par la liste des stations placées
vi.mock('./CarteReseau', () => ({
  CarteReseau: ({ stations }: { stations: StationReseau[] }) => (
    <div data-testid="carte">{stations.filter((s) => s.latitude !== null).map((s) => s.code).join(',')}</div>
  ),
}))

const DIRECTION = utilisateurTest({
  identifiant: 'direction',
  nomComplet: 'Ibrahima Fall',
  role: 'DIRECTION',
  permissions: ['REFERENTIEL_CONSULTER', 'TABLEAU_BORD_RESEAU'] as Permission[],
})

const station = (code: string, niveau: StationReseau['niveau'], motifs: string[], latitude: number | null): StationReseau => ({
  stationId: `s-${code}`,
  code,
  nom: `Station ${code}`,
  latitude,
  longitude: latitude === null ? null : -16.5,
  niveau,
  motifs,
  derniereCloture: '2026-03-10',
  joursSansCloture: niveau === 'NORMAL' ? 0 : 3,
  journeesAValider: 0,
  litresMois: { ESSENCE_PIROGUE: 12000 },
  litresMoisTotal: 12000,
  litresVeille: 1500,
  ventesMois: 5964000,
  versementsMois: 5000000,
  soldeCaisse: 964000,
  ecartsCaisseMois: 0,
  ecartStockValeurMois: niveau === 'ENQUETE' ? -149100 : 0,
  ecartsStock: [],
})

const TABLEAU: TableauReseau = {
  date: '2026-03-12',
  mois: '2026-03',
  parNiveau: { NORMAL: 1, ALERTE: 1, ENQUETE: 1 },
  litresMois: 36000,
  litresVeille: 4500,
  ventesMois: 17892000,
  versementsMois: 15000000,
  ecartStockValeurMois: -149100,
  stations: [
    station('KAY', 'NORMAL', [], 14.9),
    station('YOF', 'ALERTE', ['3 jours sans journée clôturée'], 14.7),
    station('POT', 'ENQUETE', ['Écart de stock Essence pirogue au-delà du seuil d’enquête'], null),
  ],
}

describe('tableau de bord du réseau', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('classe les stations par niveau d’alerte et ouvre leurs journées', async () => {
    const { appels } = installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', DIRECTION) }
      if (chemin.startsWith('/tableau-bord/reseau?date=')) return { status: 200, corps: TABLEAU }
      if (chemin.startsWith('/stations')) return { status: 200, corps: [] }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/reseau'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )

    const tableau = await screen.findByRole('table', { name: 'Stations du réseau' })
    const lignes = within(tableau).getAllByRole('row').slice(1)
    // Enquête d'abord, puis alerte, puis normal
    expect(lignes.map((l) => within(l).getAllByRole('cell')[0]!.textContent)).toEqual([
      'Station POT',
      'Station YOF',
      'Station KAY',
    ])
    expect(within(lignes[0]!).getByText(/seuil d’enquête/)).toBeInTheDocument()
    expect(within(lignes[1]!).getByText('(3 j de retard)')).toBeInTheDocument()
    expect(screen.getByTestId('niveau-ENQUETE')).toHaveTextContent('1 enquête')
    expect(screen.getByText('36 000 L')).toBeInTheDocument()
    // Potou n'a pas de position : absente de la carte
    expect(await screen.findByTestId('carte')).toHaveTextContent('KAY,YOF')
    expect(appels.some((a) => a.chemin.startsWith('/tableau-bord/reseau?date='))).toBe(true)

    await userEvent.click(lignes[1]!)
    expect(routeur.state.location.pathname).toBe('/journees')
    expect(routeur.state.location.search).toBe('?station=s-YOF')
  })

  it('n’apparaît pas dans le menu sans la permission', async () => {
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir')
        return { status: 200, corps: sessionTest('j', utilisateurTest({ permissions: ['REFERENTIEL_CONSULTER'] as Permission[] })) }
      if (chemin.startsWith('/stations') || chemin.startsWith('/produits') || chemin.startsWith('/prix') || chemin.startsWith('/stock') || chemin.startsWith('/tresorerie'))
        return { status: 200, corps: chemin.startsWith('/stock') || chemin.startsWith('/tresorerie') ? undefined : [] }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    expect(await screen.findByRole('link', { name: /Tableau de bord/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Réseau/ })).not.toBeInTheDocument()
  })
})
