import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalyseMensuelle, ComplementCiterne, Permission, Station } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const LOGISTIQUE = utilisateurTest({
  identifiant: 'logistique',
  nomComplet: 'Moussa Diop',
  role: 'LOGISTIQUE',
  permissions: ['REFERENTIEL_CONSULTER', 'RECEPTION_VALIDER'] as Permission[],
})

const STATION: Station = {
  id: 's-nia',
  numero: 1,
  code: 'NIA',
  nom: 'Station Niandane',
  type: 'ROUTIERE',
  regionCode: 'SL',
  regionNom: 'Saint-Louis',
  localite: null,
  latitude: null,
  longitude: null,
  modeExploitation: 'PROPRE',
  dateOuverture: null,
  actif: true,
  version: 0,
}

const ANALYSE: AnalyseMensuelle = {
  stationId: 's-nia',
  stationCode: 'NIA',
  stationNom: 'Station Niandane',
  mois: '2026-08',
  totaux: [
    {
      cuveId: 'c1',
      cuveCode: 'C1',
      produitCode: 'GASOIL',
      produitLibelle: 'Gasoil',
      stockDepart: 10000,
      receptions: 0,
      sorties: 1000,
      stockTheorique: 9000,
      stockPhysique: 8980,
      ecartLitres: -20,
      ecartPct: -2,
      ecartValeur: -15100,
      tolerancePct: 0.5,
      niveau: 'ALERTE',
      jours: 1,
    },
  ],
  lignes: [
    {
      date: '2026-08-16',
      journeeId: 'j1',
      cuveId: 'c1',
      cuveCode: 'C1',
      produitCode: 'GASOIL',
      stockDepart: 10000,
      receptions: 0,
      sorties: 1000,
      stockTheorique: 9000,
      stockPhysique: 8980,
      ecartLitres: -20,
      ecartValeur: -15100,
      cumulEcartMois: -20,
      ecartPctMois: -2,
      niveau: 'ALERTE',
      observation: 'Fuite vanne',
    },
  ],
}

const complement = (statut: ComplementCiterne['statut']): ComplementCiterne => ({
  id: 'k1',
  stationId: 's-nia',
  stationCode: 'NIA',
  date: '2026-08-16',
  journeeId: 'j1',
  numeroBl: 'BL-77',
  fournisseur: 'Dépôt SAR',
  transporteur: 'Trans Ndar',
  immatriculation: 'SL-1234-A',
  produitCode: 'GASOIL',
  quantiteBl: 20000,
  quantiteRecue: 19850,
  litres: 150,
  prixUnitaire: 755,
  montant: 113250,
  statut,
  referenceReglement: statut === 'REGLE' ? 'AV-12' : null,
  regleLe: statut === 'REGLE' ? '2026-08-20' : null,
  reglePar: statut === 'REGLE' ? 'logistique' : null,
  version: statut === 'REGLE' ? 1 : 0,
  reglable: statut === 'A_REGLER',
})

describe('page Stocks', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it("affiche l'analyse du mois et règle un complément citerne", async () => {
    let regle = false
    const reglements: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', LOGISTIQUE) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/stations/s-nia/stock?mois=2026-08') return { status: 200, corps: ANALYSE }
      if (chemin.startsWith('/stock/complements') && methode === 'GET') {
        const voulu = chemin.includes('A_REGLER') ? 'A_REGLER' : chemin.includes('REGLE') ? 'REGLE' : null
        const actuel = regle ? 'REGLE' : 'A_REGLER'
        return { status: 200, corps: voulu === null || voulu === actuel ? [complement(actuel)] : [] }
      }
      if (chemin === '/stock/complements/k1/reglement') {
        reglements.push(corps)
        regle = true
        return { status: 200, corps: complement('REGLE') }
      }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/stocks?mois=2026-08'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    // Station unique : sélectionnée d'office
    const total = await screen.findByTestId('total-C1')
    expect(within(total).getByText(/Alerte/i)).toBeInTheDocument()
    expect(screen.getByText('Fuite vanne')).toBeInTheDocument()

    await u.click(screen.getByRole('tab', { name: 'Compléments citerne' }))
    expect(await screen.findByText('BL-77')).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Régler' }))
    const dialogue = await screen.findByRole('dialog')
    await u.type(within(dialogue).getByLabelText(/Référence du règlement/), 'AV-12')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(reglements).toMatchObject([{ version: 0, reference: 'AV-12' }])
    expect(await screen.findByText(/Aucun complément citerne en attente/)).toBeInTheDocument()
  })
})
