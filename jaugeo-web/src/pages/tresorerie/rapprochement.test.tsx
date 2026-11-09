import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FormatReleve, Permission, Rapprochement, RapprochementElectronique, VersementResume } from '../../api/types'
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
  permissions: ['REFERENTIEL_CONSULTER', 'VERSEMENT_VALIDER'] as Permission[],
})

const FORMAT: FormatReleve = {
  id: 'f1',
  code: 'CBAO',
  libelle: 'CBAO compte recettes',
  nature: 'BANQUE',
  separateur: ';',
  formatDate: 'dd/MM/yyyy',
  lignesIgnorees: 1,
  colDate: 1,
  colLibelle: 2,
  colReference: 3,
  colCredit: 5,
  actif: true,
  version: 0,
}

const BV: VersementResume = {
  id: 'v1',
  stationCode: 'POT',
  dateDepot: '2026-06-02',
  banque: 'CBAO',
  referenceBv: '774401',
  montant: 123457,
  statut: 'SAISI',
}

describe('rapprochement bancaire', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('le comptable importe un relevé, rapproche un crédit et voit les écarts Wave', async () => {
    let lettre = false
    const lettrages: unknown[] = []
    const imports: string[] = []
    const rapprochement = (): Rapprochement => ({
      mois: '2026-06',
      versements: 1,
      montantVersements: 123457,
      versementsLettres: lettre ? 1 : 0,
      montantLettre: lettre ? 123457 : 0,
      taux: lettre ? 100 : 0,
      operations: [
        {
          id: 'o1',
          date: '2026-06-03',
          libelle: 'VERSEMENT ESPECES',
          reference: null,
          montant: 123457,
          format: 'CBAO compte recettes',
          versement: lettre ? BV : null,
          modeLettrage: lettre ? 'MANUEL' : null,
          lettrePar: lettre ? 'compta' : null,
          candidats: lettre ? [] : [BV],
        },
      ],
      versementsNonLettres: lettre ? [] : [BV],
      releves: [],
      peutLettrer: true,
    })
    const electronique: RapprochementElectronique = {
      mois: '2026-06',
      lignes: [
        { stationId: 's-pot', stationCode: 'POT', date: '2026-06-10', mode: 'WAVE', libelle: 'Wave', declare: 20000, releve: 18500, ecart: -1500 },
      ],
      totalDeclare: 20000,
      totalReleve: 18500,
      ecarts: 1,
      nonCouverts: 0,
    }
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/stations') return { status: 200, corps: [] }
      if (chemin === '/tresorerie/formats-releve') return { status: 200, corps: [FORMAT] }
      if (chemin === '/tresorerie/rapprochement?mois=2026-06') return { status: 200, corps: rapprochement() }
      if (chemin === '/tresorerie/rapprochement/electronique?mois=2026-06') return { status: 200, corps: electronique }
      if (chemin === '/tresorerie/rapprochement/operations/o1/lettrage' && methode === 'PUT') {
        lettrages.push(corps)
        lettre = true
        return { status: 200, corps: rapprochement().operations[0] }
      }
      if (chemin.startsWith('/tresorerie/releves?') && methode === 'POST') {
        imports.push(`${chemin} ${((corps as FormData).get('fichier') as File).name}`)
        return {
          status: 201,
          corps: { releveId: 'r1', nomFichier: 'cbao-juin.csv', format: 'CBAO', dateMin: '2026-06-01', dateMax: '2026-06-30', operations: 12, doublons: 3, ignorees: 4, lettrees: 9 },
        }
      }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/tresorerie?onglet=rapprochement&mois=2026-06'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    expect(await screen.findByTestId('taux-rapprochement')).toHaveTextContent('0 %')
    expect(screen.getByText('Versements non retrouvés au relevé')).toBeInTheDocument()
    expect(within(screen.getByTestId('electronique-POT-2026-06-10-WAVE')).getByText(/1\s500 F/)).toBeInTheDocument()

    // Lettrage manuel : seul versement de ce montant, proposé d'office
    await u.click(screen.getByRole('button', { name: 'Rapprocher' }))
    await waitFor(() => expect(lettrages).toEqual([{ versementId: 'v1' }]))
    expect(await screen.findByText('POT · BV 774401 du 02/06/2026')).toBeInTheDocument()
    expect(screen.getByTestId('taux-rapprochement')).toHaveTextContent('100 %')
    expect(screen.queryByText('Versements non retrouvés au relevé')).not.toBeInTheDocument()

    // Import d'un relevé : bilan affiché
    await u.click(screen.getByRole('button', { name: 'Importer un relevé' }))
    const dialogue = await screen.findByRole('dialog')
    await u.upload(within(dialogue).getByLabelText('Fichier du relevé'), new File(['Date;Libellé'], 'cbao-juin.csv', { type: 'text/csv' }))
    await u.click(within(dialogue).getByRole('button', { name: 'Importer' }))
    expect(await within(dialogue).findByTestId('bilan-import')).toHaveTextContent('12 opération(s)')
    expect(within(dialogue).getByText(/9 rapprochée\(s\) automatiquement/)).toBeInTheDocument()
    expect(imports).toEqual(['/tresorerie/releves?format=f1 cbao-juin.csv'])
  })
})
