import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DemandeChangementPrix, LigneGrille, Permission } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const station = (id: string, code: string, nom: string) => ({
  id, numero: 1, code, nom, type: 'ROUTIERE', regionCode: 'SL', regionNom: 'Saint-Louis', localite: null,
  latitude: null, longitude: null, modeExploitation: 'PROPRE', dateOuverture: null, actif: true, version: 0,
})

const ligne = (stationId: string, stationCode: string, produitCode: string, produitLibelle: string, prix: number | null): LigneGrille => ({
  stationId, stationCode, stationNom: stationCode, produitCode, produitLibelle, prix, depuis: '2026-08-16T00:00:00Z',
  prochainPrix: null, prochainDebut: null,
})

const GRILLE = [
  ligne('s-nia', 'NIA', 'GASOIL', 'Gasoil', 755),
  ligne('s-nia', 'NIA', 'SUPER', 'Super', 990),
  ligne('s-kay', 'KAY', 'ESSENCE_PIROGUE', 'Essence pirogue', null),
]

const DIRECTION = utilisateurTest({
  identifiant: 'dg',
  nomComplet: 'Moussa Diop',
  role: 'DIRECTION',
  permissions: ['REFERENTIEL_CONSULTER', 'PRIX_MODIFIER', 'TABLEAU_BORD_RESEAU'] as Permission[],
})

function demarrer() {
  const routeur = createMemoryRouter(routes, { initialEntries: ['/prix'] })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>,
  )
}

describe('page Prix', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it("signale les prix manquants et fait confirmer une variation de plus de 20 % avant d'enregistrer", async () => {
    const envois: DemandeChangementPrix[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', DIRECTION) }
      if (chemin === '/prix/grille') return { status: 200, corps: GRILLE }
      if (chemin === '/prix/changements' && methode === 'GET') return { status: 200, corps: [] }
      if (chemin === '/stations') return { status: 200, corps: [station('s-nia', 'NIA', 'Niandane'), station('s-kay', 'KAY', 'Kayar')] }
      if (chemin === '/prix/changements' && methode === 'POST') {
        const d = corps as DemandeChangementPrix
        envois.push(d)
        if (!d.confirmerVariation) {
          return { status: 422, corps: { code: 'VARIATION_A_CONFIRMER', detail: 'Variation de plus de 20 % : Gasoil NIA 755 F → 7550 F.' } }
        }
        return {
          status: 201,
          corps: {
            id: 'c1', debut: d.debut, motif: d.motif, reference: null, annule: false, annulable: true, creePar: 'dg',
            creeLe: d.debut, annulePar: null, annuleLe: null, ignores: ['KAY : pas de cuve de Gasoil'],
            lignes: [{ stationId: 's-nia', stationCode: 'NIA', stationNom: 'Niandane', produitCode: 'GASOIL', produitLibelle: 'Gasoil', ancienPrix: 755, nouveauPrix: 7550 }],
          },
        }
      }
      return undefined
    })
    demarrer()
    const u = userEvent.setup()

    expect(await screen.findByText(/1 prix manquant/)).toBeInTheDocument()
    expect(screen.getByText('755 F')).toBeInTheDocument()

    await u.click(screen.getByRole('button', { name: 'Changement de prix' }))
    const dialogue = await screen.findByRole('dialog')
    // Sans motif ni prix : rien n'est envoyé
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    expect(within(dialogue).getByText(/Motif obligatoire/)).toBeInTheDocument()
    expect(envois).toHaveLength(0)

    expect(within(dialogue).getByText('Actuel : 755 F')).toBeInTheDocument()
    await u.type(within(dialogue).getByLabelText(/Motif/), 'Arrêté de novembre')
    await u.type(within(dialogue).getByLabelText('Gasoil'), '7550')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))

    expect(await within(dialogue).findByTestId('variation-a-confirmer')).toHaveTextContent('7550 F')
    expect(envois[0]).toMatchObject({ motif: 'Arrêté de novembre', stations: [], prix: [{ produitCode: 'GASOIL', prixUnitaire: 7550 }] })

    await u.click(within(dialogue).getByRole('button', { name: 'Confirmer ces montants' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(envois[1]!.confirmerVariation).toBe(true)
    // Tout le réseau : les stations sans cuve de Gasoil sont écartées sans alarme
    expect(await screen.findByText('Changement enregistré pour 1 station(s).')).toBeInTheDocument()
    expect(screen.queryByText(/Non appliqué/)).not.toBeInTheDocument()
  })

  it('la confirmation est perdue dès qu’un montant change', async () => {
    const envois: DemandeChangementPrix[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', DIRECTION) }
      if (chemin === '/prix/grille') return { status: 200, corps: GRILLE }
      if (chemin === '/prix/changements' && methode === 'GET') return { status: 200, corps: [] }
      if (chemin === '/stations') return { status: 200, corps: [] }
      if (chemin === '/prix/changements') {
        envois.push(corps as DemandeChangementPrix)
        return { status: 422, corps: { code: 'VARIATION_A_CONFIRMER', detail: 'Variation de plus de 20 %.' } }
      }
      return undefined
    })
    demarrer()
    const u = userEvent.setup()
    await u.click(await screen.findByRole('button', { name: 'Changement de prix' }))
    const dialogue = await screen.findByRole('dialog')
    await u.type(within(dialogue).getByLabelText(/Motif/), 'Test')
    await u.type(within(dialogue).getByLabelText('Super'), '9900')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))
    await within(dialogue).findByRole('button', { name: 'Confirmer ces montants' })

    await u.clear(within(dialogue).getByLabelText('Super'))
    await u.type(within(dialogue).getByLabelText('Super'), '1000')
    expect(within(dialogue).getByRole('button', { name: 'Enregistrer' })).toBeInTheDocument()
    expect(within(dialogue).queryByTestId('variation-a-confirmer')).not.toBeInTheDocument()
  })
})
