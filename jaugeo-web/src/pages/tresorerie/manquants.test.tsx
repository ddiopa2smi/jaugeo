import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Manquant, Permission } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const COMPTABLE = utilisateurTest({
  identifiant: 'compta',
  role: 'COMPTABLE',
  permissions: ['REFERENTIEL_CONSULTER', 'VERSEMENT_VALIDER'] as Permission[],
})

const manquant = (surcharge: Partial<Manquant> = {}): Manquant => ({
  id: 'm1',
  stationId: 's-pot',
  stationCode: 'POT',
  date: '2026-07-01',
  montant: 30000,
  responsable: 'Keba Mbaye',
  observation: 'Billets manquants',
  statut: 'OUVERT',
  modeReglement: null,
  referenceReglement: null,
  regleLe: null,
  reglePar: null,
  version: 0,
  reglable: true,
  montantRegle: 0,
  reste: 30000,
  reconnaissanceReference: null,
  reconnuLe: null,
  echeances: [],
  paiements: [],
  prochaineEcheance: null,
  retard: 0,
  ...surcharge,
})

describe('manquants des gérants', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('la comptabilité établit un échéancier puis enregistre une retenue partielle', async () => {
    let courant = manquant()
    const appels: { chemin: string; corps: unknown }[] = []
    const echeances = (regle: number): Manquant['echeances'] =>
      [1, 2, 3].map((rang) => {
        const r = Math.max(0, Math.min(10000, regle - (rang - 1) * 10000))
        return { rang, date: `2026-0${6 + rang}-28`, montant: 10000, regle: r, statut: r === 10000 ? 'PAYEE' : 'A_VENIR' }
      })
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/stations') return { status: 200, corps: [] }
      if (chemin.startsWith('/tresorerie/manquants?')) return { status: 200, corps: [courant] }
      if (chemin === '/tresorerie/manquants/m1/echeancier' && methode === 'PUT') {
        appels.push({ chemin, corps })
        courant = { ...courant, version: 1, reconnaissanceReference: 'RD-14', reconnuLe: '2026-07-02', echeances: echeances(0), prochaineEcheance: '2026-07-28' }
        return { status: 200, corps: courant }
      }
      if (chemin === '/tresorerie/manquants/m1/paiements' && methode === 'POST') {
        appels.push({ chemin, corps })
        courant = {
          ...courant,
          version: 2,
          montantRegle: 12000,
          reste: 18000,
          echeances: echeances(12000),
          paiements: [{ date: '2026-07-28', montant: 12000, mode: 'RETENUE', reference: 'Paie juillet', par: 'compta' }],
          prochaineEcheance: '2026-08-28',
        }
        return { status: 200, corps: courant }
      }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/tresorerie?onglet=manquants'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    expect(await screen.findByTestId('reste-m1')).toHaveTextContent('30 000 F')
    await u.click(screen.getByRole('button', { name: 'Gérer' }))
    const dialogue = await screen.findByRole('dialog')
    expect(within(dialogue).getByText('Pas d’échéancier : le manquant est dû en une fois.')).toBeInTheDocument()

    await u.type(within(dialogue).getByLabelText('Reconnaissance de dette n°'), 'RD-14')
    expect(within(dialogue).getByText(/10\s000 F par mois/)).toBeInTheDocument()
    await u.click(within(dialogue).getByRole('button', { name: 'Établir l’échéancier' }))
    expect(await within(dialogue).findByText(/Reconnaissance de dette RD-14/)).toBeInTheDocument()
    expect(appels[0]).toMatchObject({ corps: { version: 0, reference: 'RD-14', nombre: 3 } })
    // Statut en puce (grand écran) et en mention sous la date (téléphone)
    expect(within(dialogue).getAllByText('À venir')).toHaveLength(6)

    // Le montant proposé est celui de la prochaine échéance ; retenue partielle de 12 000 F
    const montant = within(dialogue).getByLabelText('Montant (F CFA)')
    expect(montant).toHaveValue('10000')
    await u.clear(montant)
    await u.type(montant, '12000')
    await u.type(within(dialogue).getByLabelText('Référence (reçu, bulletin…)'), 'Paie juillet')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer le règlement' }))
    await waitFor(() => expect(within(dialogue).getByTestId('manquant-Reste dû')).toHaveTextContent('18 000 F'))
    expect(appels[1]).toMatchObject({ corps: { version: 1, montant: 12000, mode: 'RETENUE', reference: 'Paie juillet' } })
    expect(within(dialogue).getAllByText('Payée')).toHaveLength(2)
    expect(within(dialogue).getByLabelText('Montant (F CFA)')).toHaveValue('8000')
  })
})
