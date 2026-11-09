import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { EncaissementsJournee, ModeEncaissement, RecetteJournee } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { creerQueryClient } from '../../app/queryClient'
import { installerFauxServeur } from '../../test/fauxServeur'
import { SectionEncaissements } from './SectionEncaissements'
import { SectionRecette } from './SectionRecette'

const LIBELLES: Record<ModeEncaissement, string> = { WAVE: 'Wave', ORANGE_MONEY: 'Orange Money', CARTE: 'Carte bancaire' }

describe('encaissements électroniques de la journée', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('déduit Wave et carte des espèces à verser et alerte au-delà du plafond', async () => {
    let montants: Partial<Record<ModeEncaissement, number>> = {}
    let version = 3
    const puts: unknown[] = []
    const encaissements = (): EncaissementsJournee => ({
      journeeId: 'j1',
      version,
      statut: 'BROUILLON',
      modifiable: true,
      lignes: (['WAVE', 'ORANGE_MONEY', 'CARTE'] as const).map((m) => ({ mode: m, libelle: LIBELLES[m], montant: montants[m] ?? null })),
      total: Object.values(montants).reduce((t, v) => t + v, 0),
    })
    const recette = (): RecetteJournee => {
      const e = Object.values(montants).reduce((t, v) => t + v, 0)
      return {
        journeeId: 'j1',
        stationId: 's-trm',
        stationCode: 'TRM',
        date: '2026-05-01',
        statut: 'BROUILLON',
        fige: false,
        ventes: 75500,
        ventesArticles: 0,
        bonsDirection: 0,
        bonsGerant: 0,
        depenses: 0,
        complements: 0,
        encaissements: e,
        cartes: 0,
        recouvrements: 0,
        montantAVerser: 75500 - e,
        soldeOuverture: 0,
        versements: [],
        totalVersements: 0,
        soldeFin: 75500 - e,
        arrete: null,
        peutVerser: false,
        plafondEspeces: 40000,
        depassePlafond: 75500 - e > 40000,
      }
    }
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/journees/j1/encaissements' && methode === 'PUT') {
        puts.push(corps)
        for (const s of (corps as { encaissements: { mode: ModeEncaissement; montant: number | null }[] }).encaissements) {
          if (s.montant) montants = { ...montants, [s.mode]: s.montant }
        }
        version++
        return { status: 200, corps: encaissements() }
      }
      if (chemin === '/journees/j1/encaissements') return { status: 200, corps: encaissements() }
      if (chemin === '/journees/j1/tresorerie') return { status: 200, corps: recette() }
      return undefined
    })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <MemoryRouter>
          <SectionEncaissements journeeId="j1" />
          <SectionRecette journeeId="j1" />
        </MemoryRouter>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    // 75 500 F d'espèces au-delà du plafond de 40 000 F
    expect(await screen.findByTestId('alerte-plafond')).toHaveTextContent('40 000 F')
    expect(screen.queryByTestId('encaissements')).not.toBeInTheDocument()

    await u.type(screen.getByLabelText('Wave'), '20000')
    await u.type(screen.getByLabelText('Carte bancaire'), '20000')
    expect(screen.getByText('Encaissements non enregistrés.')).toBeInTheDocument()
    expect(screen.getByTestId('total-encaissements')).toHaveTextContent('40 000 F')
    await u.click(screen.getByRole('button', { name: 'Enregistrer les encaissements' }))

    await waitFor(() =>
      expect(puts).toEqual([
        {
          version: 3,
          encaissements: [
            { mode: 'WAVE', montant: 20000 },
            { mode: 'CARTE', montant: 20000 },
          ],
        },
      ]),
    )
    const r = screen.getByLabelText('Recette et versements')
    await waitFor(() => expect(within(r).getByTestId('montant-a-verser')).toHaveTextContent('35 500 F'))
    expect(within(r).getByTestId('encaissements')).toHaveTextContent('− 40 000 F')
    // 35 500 F d'espèces : sous le plafond, plus d'alerte
    expect(screen.queryByTestId('alerte-plafond')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enregistrer les encaissements' })).toBeDisabled()
  })
})
