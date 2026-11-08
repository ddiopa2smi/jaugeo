import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Client, CreditJournee, FicheClient, LigneVentes, Permission, Releve, Station, SyntheseVentes } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'
import { SectionCredit } from '../journees/SectionCredit'

const COMPTABLE = utilisateurTest({
  identifiant: 'compta',
  nomComplet: 'Ibrahima Fall',
  role: 'COMPTABLE',
  permissions: ['REFERENTIEL_CONSULTER', 'CLIENT_GERER', 'CLOTURE_MENSUELLE'] as Permission[],
})

const STATION: Station = {
  id: 's-hyd',
  numero: 4,
  code: 'HYD',
  nom: 'Hydrobase',
  type: 'PECHE',
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

const client = (surcharge: Partial<Client> = {}): Client => ({
  id: 'c1',
  code: 'OMAR',
  nom: 'Cheikh Omar',
  telephone: null,
  recouvrement: 'GERANT',
  plafond: 30000000,
  actif: true,
  observation: null,
  encours: 24212721,
  disponible: 5787279,
  version: 0,
  ...surcharge,
})

const FICHE: FicheClient = {
  client: client(),
  comptes: [{ stationId: 's-hyd', stationCode: 'HYD', stationNom: 'Hydrobase', creance: 24212721, dernierBon: null, dernierReglement: null }],
  peutGerer: true,
}

const RELEVE: Releve = {
  clientId: 'c1',
  clientCode: 'OMAR',
  clientNom: 'Cheikh Omar',
  stationId: 's-hyd',
  stationCode: 'HYD',
  du: null,
  au: null,
  soldeDebut: 0,
  totalDebit: 24212721,
  totalCredit: 0,
  soldeFin: 24212721,
  lignes: [
    {
      date: '2025-12-31',
      type: 'REPRISE',
      mode: null,
      numero: null,
      produitCode: null,
      litres: null,
      prixUnitaire: null,
      reference: 'Créances au 31/12/2025',
      observation: null,
      debit: 24212721,
      credit: null,
      solde: 24212721,
      operationId: 'o1',
      version: 0,
      supprimable: true,
      creePar: 'compta',
    },
  ],
}

const ligneVentes = (surcharge: Partial<LigneVentes> = {}): LigneVentes => ({
  stationId: 's-hyd',
  stationCode: 'HYD',
  stationNom: 'Hydrobase',
  litres: { GASOIL: 1200, ESSENCE_PIROGUE: 800 },
  recettes: 1500000,
  soldeOuverture: 40000,
  creancesDebut: 24212721,
  aVerser: 25752721,
  versements: 1200000,
  soldeCaisse: 38000,
  creancesFin: 24512721,
  depenses: 0,
  complements: 0,
  reglementsSiege: 0,
  totalVersements: 25750721,
  ecart: -2000,
  bonsDirection: 0,
  bonsGerant: 300000,
  recouvrements: 0,
  creancesGerantFin: 24512721,
  ecartsArretes: -2000,
  joursNonClotures: 0,
  observation: 'Écart sur versement de −2 000 F (manquant).',
  commentaire: null,
  commentaireVersion: null,
  ...surcharge,
})

const SYNTHESE: SyntheseVentes = {
  mois: '2026-01',
  produits: [
    { code: 'GASOIL', libelle: 'Gasoil' },
    { code: 'ESSENCE_PIROGUE', libelle: 'Essence pirogue' },
  ],
  lignes: [ligneVentes()],
  cumul: ligneVentes({ stationId: null, stationCode: 'CUMUL', observation: null }),
  peutCommenter: true,
}

function afficher(chemin: string) {
  const routeur = createMemoryRouter(routes, { initialEntries: [chemin] })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>,
  )
}

describe('clients en compte et synthèses', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('le comptable crée un client sous plafond et consulte sa créance par station', async () => {
    let clients: Client[] = [client()]
    const creations: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/clients' && methode === 'GET') return { status: 200, corps: clients }
      if (chemin === '/clients' && methode === 'POST') {
        creations.push(corps)
        clients = [...clients, client({ id: 'c2', code: 'DIALLO', nom: 'Transports Diallo', recouvrement: 'DIRECTION', plafond: null, encours: 0, disponible: null })]
        return { status: 201, corps: clients[1] }
      }
      if (chemin === '/clients/c1') return { status: 200, corps: FICHE }
      if (chemin === '/clients/c1/releve?station=s-hyd') return { status: 200, corps: RELEVE }
      return undefined
    })
    afficher('/clients')
    const u = userEvent.setup()

    expect(await screen.findByTestId('client-OMAR')).toHaveTextContent('24 212 721 F')
    await u.click(screen.getByRole('button', { name: 'Nouveau client' }))
    const dialogue = await screen.findByRole('dialog')
    await u.type(within(dialogue).getByLabelText(/Code/), 'diallo')
    await u.type(within(dialogue).getByLabelText(/Nom/), 'Transports Diallo')
    await u.type(within(dialogue).getByLabelText(/Plafond/), '12,5')
    expect(within(dialogue).getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    await u.clear(within(dialogue).getByLabelText(/Plafond/))
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(creations).toEqual([expect.objectContaining({ code: 'diallo', nom: 'Transports Diallo', recouvrement: 'DIRECTION', plafond: null })])
    expect(await screen.findByTestId('client-DIALLO')).toBeInTheDocument()

    await u.click(screen.getByTestId('client-OMAR'))
    const fiche = await screen.findByLabelText('Fiche du client OMAR')
    expect(within(fiche).getByTestId('encours-client')).toHaveTextContent('24 212 721 F')
    expect(await within(fiche).findByTestId('solde-releve')).toHaveTextContent('24 212 721 F')
    expect(within(fiche).getByText('Reprise de créance')).toBeInTheDocument()
  })

  it('affiche la synthèse des ventes et recettes, enregistre une observation et exporte le classeur', async () => {
    const commentaires: unknown[] = []
    const exports: string[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() }))
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/syntheses/ventes?mois=2026-01') return { status: 200, corps: SYNTHESE }
      if (chemin === '/syntheses/commentaires' && methode === 'PUT') {
        commentaires.push(corps)
        return { status: 200, corps: { stationId: 's-hyd', mois: '2026-01', texte: 'Retenue', version: 0 } }
      }
      if (chemin.startsWith('/syntheses/export.')) {
        exports.push(chemin)
        return { status: 200, corps: {} }
      }
      return undefined
    })
    afficher('/syntheses?mois=2026-01')
    const u = userEvent.setup()

    const ligne = await screen.findByTestId('synthese-HYD')
    expect(within(ligne).getByText('1 200')).toBeInTheDocument()
    expect(within(ligne).getByText('25 752 721 F')).toBeInTheDocument()
    expect(screen.getByTestId('ecart-HYD')).toHaveTextContent('−2 000 F')
    expect(within(ligne).getByText(/manquant/)).toBeInTheDocument()

    await u.click(screen.getByRole('button', { name: 'Observation HYD' }))
    const dialogue = await screen.findByRole('dialog')
    await u.type(within(dialogue).getByLabelText(/Observation du contrôle/), 'Retenue sur commission')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(commentaires).toEqual([{ stationId: 's-hyd', mois: '2026-01', texte: 'Retenue sur commission', version: null }])

    await u.click(screen.getByRole('button', { name: 'Excel' }))
    await waitFor(() => expect(exports).toEqual(['/syntheses/export.xlsx?mois=2026-01']))
  })

  it('le gérant saisit un bon : montant au prix du jour, plafond signalé avant l’envoi', async () => {
    const bons: unknown[] = []
    const credit: CreditJournee = {
      journeeId: 'j1',
      stationId: 's-hyd',
      stationCode: 'HYD',
      date: '2026-01-05',
      statut: 'BROUILLON',
      version: 3,
      modifiable: true,
      produits: [{ id: 'p-ep', code: 'ESSENCE_PIROGUE', libelle: 'Essence pirogue', prixUnitaire: 505 }],
      bons: [],
      recouvrements: [],
      totalBonsDirection: 0,
      totalBonsGerant: 0,
      totalRecouvrements: 0,
      anomalies: [],
    }
    useSession.setState({ jeton: 'j', utilisateur: COMPTABLE, statut: 'connecte', motif: null })
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/journees/j1/credit') return { status: 200, corps: credit }
      if (chemin === '/clients') return { status: 200, corps: [client({ disponible: 100000 })] }
      if (chemin === '/journees/j1/bons' && methode === 'POST') {
        bons.push(corps)
        return {
          status: 201,
          corps: {
            ...credit,
            version: 4,
            totalBonsGerant: 50500,
            bons: [
              {
                id: 'b1',
                numero: 'B-17',
                clientId: 'c1',
                clientCode: 'OMAR',
                clientNom: 'Cheikh Omar',
                produitId: 'p-ep',
                produitCode: 'ESSENCE_PIROGUE',
                produitLibelle: 'Essence pirogue',
                litres: 100,
                prixUnitaire: 505,
                montant: 50500,
                recouvrement: 'GERANT',
                immatriculation: 'PIROGUE 12',
                porteur: null,
                creePar: 'gerant',
                modifieLe: '2026-01-05T10:00:00Z',
              },
            ],
          },
        }
      }
      return undefined
    })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <SectionCredit journeeId="j1" />
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Ajouter un bon' }))
    const dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByLabelText(/Client/))
    await u.click(await screen.findByRole('option', { name: /Cheikh Omar/ }))
    await u.type(within(dialogue).getByLabelText(/Numéro du bon/), 'B-17')
    await u.type(within(dialogue).getByLabelText(/Litres/), '250')
    expect(within(dialogue).getByText('Montant : 126 250 F')).toBeInTheDocument()
    expect(within(dialogue).getByText(/Plafond dépassé/)).toBeInTheDocument()
    await u.clear(within(dialogue).getByLabelText(/Litres/))
    await u.type(within(dialogue).getByLabelText(/Litres/), '100')
    expect(within(dialogue).queryByText(/Plafond dépassé/)).not.toBeInTheDocument()
    await u.type(within(dialogue).getByLabelText(/Véhicule ou pirogue/), 'Pirogue 12')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(bons).toEqual([
      { version: 3, clientId: 'c1', numero: 'B-17', produitId: 'p-ep', litres: 100, immatriculation: 'Pirogue 12', porteur: null },
    ])
    expect(await screen.findByTestId('bon-B-17')).toHaveTextContent('50 500 F')
    expect(screen.getByTestId('total-credit')).toHaveTextContent('bons gérant : 50 500 F')
  })
})
