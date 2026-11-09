import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BalanceAgee, Client, FicheClient, LigneBalance, Permission, Relance } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const COMPTABLE = utilisateurTest({
  identifiant: 'compta',
  nomComplet: 'Ibrahima Fall',
  role: 'COMPTABLE',
  permissions: ['REFERENTIEL_CONSULTER', 'CLIENT_GERER'] as Permission[],
})

const LIGNE: LigneBalance = {
  clientId: 'c1',
  code: 'SOPESEN',
  nom: 'Sopesen',
  recouvrement: 'DIRECTION',
  delaiPaiement: 30,
  plafond: 2000000,
  nonFacture: 150000,
  jours0a30: 0,
  jours31a60: 420000,
  jours61a90: 0,
  plusDe90: 80000,
  total: 650000,
  echu: 500000,
  plusAncienneEcheance: '2026-07-31',
  derniereRelance: null,
  dernierNiveau: null,
  niveauSuggere: 1,
}

const BALANCE: BalanceAgee = {
  au: '2026-11-09',
  lignes: [LIGNE],
  nonFacture: 150000,
  jours0a30: 0,
  jours31a60: 420000,
  jours61a90: 0,
  plusDe90: 80000,
  total: 650000,
  echu: 500000,
}

const CLIENT: Client = {
  id: 'c1',
  code: 'SOPESEN',
  nom: 'Sopesen',
  telephone: null,
  recouvrement: 'DIRECTION',
  plafond: 2000000,
  actif: true,
  observation: null,
  encours: 650000,
  disponible: 1350000,
  echu: 500000,
  contrat: {
    adresse: 'Môle 10, port de Dakar',
    email: null,
    ninea: null,
    reference: 'CT-2026-04',
    debut: '2026-01-01',
    fin: '2026-12-31',
    delaiPaiement: 30,
    cautionMontant: 1000000,
    cautionBanque: 'CBAO',
    cautionEcheance: '2026-12-31',
  },
  version: 2,
}

const FICHE: FicheClient = {
  client: CLIENT,
  comptes: [],
  engins: [],
  remises: [],
  factures: [],
  cartes: [],
  relances: [],
  peutGerer: true,
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

describe('créances, relances et fiche client (M6)', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('la balance âgée propose le rappel, enregistre la relance et télécharge la lettre', async () => {
    const relances: unknown[] = []
    const lettres: string[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() }))
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin.startsWith('/clients/balance-agee')) return { status: 200, corps: BALANCE }
      if (chemin === '/clients/bons-en-attente') return { status: 200, corps: [] }
      if (chemin === '/clients/c1/relances' && methode === 'POST') {
        relances.push(corps)
        const r: Relance = { id: 'r1', date: '2026-11-09', niveau: 1, mode: 'COURRIER', montantEchu: 500000, commentaire: null, creePar: 'compta' }
        return { status: 201, corps: r }
      }
      if (chemin.startsWith('/clients/relances/')) {
        lettres.push(chemin)
        return { status: 200, corps: {} }
      }
      return undefined
    })
    afficher('/creances')
    const u = userEvent.setup()

    const ligne = await screen.findByTestId('balance-SOPESEN')
    expect(within(ligne).getByText('420 000 F')).toBeInTheDocument()
    expect(within(ligne).getByText('500 000 F')).toBeInTheDocument()
    await u.click(within(ligne).getByRole('button', { name: 'Rappel' }))
    const dialogue = await screen.findByRole('dialog')
    expect(within(dialogue).getByText(/depuis le 31\/07\/2026/)).toBeInTheDocument()
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer la relance' }))
    await waitFor(() => expect(lettres).toEqual(['/clients/relances/r1/lettre']))
    expect(relances).toEqual([{ mode: 'COURRIER', commentaire: null }])
  })

  it('la fiche client montre le contrat et ajoute un engin autorisé', async () => {
    const engins: unknown[] = []
    let fiche: FicheClient = FICHE
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/clients' && methode === 'GET') return { status: 200, corps: [CLIENT] }
      if (chemin === '/clients/c1') return { status: 200, corps: fiche }
      if (chemin === '/clients/c1/engins' && methode === 'POST') {
        engins.push(corps)
        fiche = { ...FICHE, engins: [{ id: 'e1', type: 'PIROGUE', immatriculation: 'DK 1234 P', libelle: 'Pirogue Ndiaga', actif: true, version: 0 }] }
        return { status: 201, corps: fiche }
      }
      return undefined
    })
    afficher('/clients?client=c1')
    const u = userEvent.setup()

    const panneau = await screen.findByLabelText('Fiche du client SOPESEN')
    expect(within(panneau).getByTestId('echu-client')).toHaveTextContent('échu 500 000 F')
    await u.click(within(panneau).getByRole('tab', { name: 'Contrat' }))
    expect(within(panneau).getByText('30 jours après facture')).toBeInTheDocument()
    expect(within(panneau).getByText(/1 000 000 F · CBAO/)).toBeInTheDocument()

    await u.click(within(panneau).getByRole('tab', { name: /Engins et prix/ }))
    await u.click(within(panneau).getAllByRole('button', { name: 'Ajouter' })[0]!)
    const dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByLabelText(/Type/))
    await u.click(await screen.findByRole('option', { name: 'Pirogue' }))
    await u.type(within(dialogue).getByLabelText(/Immatriculation/), 'dk 1234 p')
    await u.type(within(dialogue).getByLabelText(/Libellé/), 'Pirogue Ndiaga')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(engins).toEqual([{ type: 'PIROGUE', immatriculation: 'DK 1234 P', libelle: 'Pirogue Ndiaga', actif: true }])
    expect(await within(panneau).findByText('DK 1234 P')).toBeInTheDocument()
  })
})
