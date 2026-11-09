import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CreditJournee, DepensesJournee, DetailJournee, LigneReleve, Permission, RecetteJournee, SaisieJournee, StockJournee } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const GERANT = utilisateurTest({
  identifiant: 'gerant.nia',
  nomComplet: 'Fatou Sall',
  role: 'GERANT',
  permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_SAISIR', 'JOURNEE_CLOTURER'] as Permission[],
  perimetre: 'STATIONS',
})

const TRANCHE = { debut: '2026-08-16T00:00:00Z', fin: '2026-08-17T00:00:00Z', prixUnitaire: 755, litres: null, montant: null }

const ligne = (pistoletId: string, code: string, pompe: string, depart: number): LigneReleve => ({
  releveId: `r-${code}`,
  pistoletId,
  pistoletCode: code,
  pistoletLibelle: code,
  pompeCode: pompe,
  cuveCode: 'C1',
  produitCode: 'GASOIL',
  produitLibelle: 'Gasoil',
  indexDepart: depart,
  indexArrivee: null,
  remiseCuve: 0,
  lectures: [],
  lecturesAttendues: [],
  ruptures: [],
  tranches: [TRANCHE],
  sortieBrute: null,
  sortieNette: null,
  montant: null,
  anomalies: [{ code: 'INDEX_ARRIVEE_MANQUANT', message: "Index d'arrivée non saisi." }],
})

function journee(version: number, lignes: LigneReleve[], statut: DetailJournee['statut'] = 'BROUILLON'): DetailJournee {
  const complet = lignes.every((l) => l.anomalies.length === 0)
  const litres = lignes.reduce((s, l) => s + (l.sortieNette ?? 0), 0)
  const montant = lignes.reduce((s, l) => s + (l.montant ?? 0), 0)
  return {
    id: 'j1',
    stationId: 's-nia',
    stationCode: 'NIA',
    stationNom: 'Station Niandane',
    date: '2026-08-16',
    debut: '2026-08-16T00:00:00Z',
    fin: '2026-08-17T00:00:00Z',
    statut,
    version,
    observation: null,
    ouvertePar: 'gerant.nia',
    ouverteLe: '2026-08-16T06:00:00Z',
    clotureePar: statut === 'BROUILLON' ? null : 'gerant.nia',
    clotureeLe: statut === 'BROUILLON' ? null : '2026-08-16T22:00:00Z',
    valideePar: null,
    valideeLe: null,
    motifRenvoi: null,
    lignes,
    totaux: [{ produitCode: 'GASOIL', produitLibelle: 'Gasoil', litres: complet ? litres : null, montant: complet ? montant : null }],
    litres: complet ? litres : null,
    montant: complet ? montant : null,
    anomalies: lignes.reduce((s, l) => s + l.anomalies.length, 0),
    actions: { saisir: statut === 'BROUILLON', cloturer: statut === 'BROUILLON', valider: false, renvoyer: false },
  }
}

describe('saisie et clôture d’une journée', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('calcule la vente en direct, enregistre, puis clôture après récapitulatif', async () => {
    let courante = journee(3, [ligne('p1', 'G1', 'P1', 88916), ligne('p2', 'G2', 'P2', 140503)])
    const saisies: SaisieJournee[] = []
    const clotures: unknown[] = []
    const jaugeages: unknown[] = []
    let jaugeFin: number | null = null
    // Cuve C1 : 10 000 L la veille, sorties = celles des relevés enregistrés (1280,5 L)
    const stock = (): StockJournee => {
      const sorties = courante.litres
      const theorique = sorties === null ? null : 10000 - sorties
      const ecart = jaugeFin === null || theorique === null ? null : jaugeFin - theorique
      const anomalies = jaugeFin === null ? [{ code: 'JAUGEAGE_CLOTURE_MANQUANT', message: 'Jaugeage de fin manquant.' }] : []
      return {
        journeeId: 'j1',
        stationId: 's-nia',
        stationCode: 'NIA',
        date: '2026-08-16',
        statut: courante.statut,
        version: courante.version,
        modifiable: courante.statut === 'BROUILLON',
        receptions: [],
        anomalies: anomalies.length,
        toleranceTransportPct: 0.3,
        cuves: [
          {
            cuveId: 'c1',
            cuveCode: 'C1',
            produitCode: 'GASOIL',
            produitLibelle: 'Gasoil',
            capaciteLitres: 30000,
            bareme: false,
            departReporte: true,
            ouvertureRequise: false,
            stockDepart: 10000,
            ouverture: null,
            receptions: 0,
            sorties,
            stockTheorique: theorique,
            cloture: jaugeFin === null ? null : { hauteurMm: null, volumeLitres: jaugeFin, observation: null, par: 'gerant.nia', le: '2026-08-16T21:00:00Z', hauteurEauMm: null, volumeEauLitres: null },
            ecartLitres: ecart,
            ecartPctJour: null,
            prixUnitaire: 755,
            ecartValeur: null,
            cumulSortiesMois: null,
            cumulEcartMois: ecart,
            ecartPctMois: null,
            tolerancePct: 0.5,
            niveau: ecart === null ? null : 'NORMAL',
            observationRequise: false,
            anomalies,
            seuilEauMm: 20,
            alertes: [],
          },
        ],
      }
    }
    // Une dépense autorisée (sous le plafond) de 5 000 F payée sur la recette
    const depenses = (): DepensesJournee => ({
      journeeId: 'j1',
      stationId: 's-nia',
      stationCode: 'NIA',
      date: '2026-08-16',
      statut: courante.statut,
      version: courante.version,
      modifiable: courante.statut === 'BROUILLON',
      justificatifObligatoire: false,
      depenses: [
        {
          id: 'd1',
          natureId: 'n1',
          natureCode: 'FOURNITURES',
          natureLibelle: 'Fournitures',
          libelle: 'Registre',
          beneficiaire: null,
          montant: 5000,
          plafond: 25000,
          statut: 'AUTORISEE',
          justificatif: null,
          decisionPar: null,
          decisionLe: null,
          motifDecision: null,
          creePar: 'gerant.nia',
          modifieLe: '2026-08-16T10:00:00Z',
          decidable: false,
        },
      ],
      totalAutorise: 5000,
      totalEnAttente: 0,
      totalRefuse: 0,
      anomalies: [],
    })
    const credit = (): CreditJournee => ({
      journeeId: 'j1',
      stationId: 's-nia',
      stationCode: 'NIA',
      date: '2026-08-16',
      statut: courante.statut,
      version: courante.version,
      modifiable: courante.statut === 'BROUILLON',
      produits: [{ id: 'p-go', code: 'GASOIL', libelle: 'Gasoil', prixUnitaire: 755 }],
      bons: [],
      recouvrements: [],
      totalBonsDirection: 0,
      totalBonsGerant: 0,
      totalRecouvrements: 0,
      anomalies: [],
    })
    const recette = (): RecetteJournee => {
      const montant = courante.montant === null ? null : courante.montant - 5000
      return {
        journeeId: 'j1',
        stationId: 's-nia',
        stationCode: 'NIA',
        date: '2026-08-16',
        statut: courante.statut,
        fige: courante.statut !== 'BROUILLON',
        ventes: courante.montant,
        bonsDirection: 0,
        bonsGerant: 0,
        depenses: 5000,
        complements: 0,
        recouvrements: 0,
        montantAVerser: montant,
        soldeOuverture: 120000,
        versements: [],
        totalVersements: 0,
        soldeFin: montant === null ? null : 120000 + montant,
        arrete: null,
        peutVerser: false,
      }
    }
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/journees/j1/depenses') return { status: 200, corps: depenses() }
      if (chemin === '/journees/j1/tresorerie') return { status: 200, corps: recette() }
      if (chemin === '/journees/j1/credit') return { status: 200, corps: credit() }
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/journees/j1' && methode === 'GET') return { status: 200, corps: courante }
      if (chemin === '/journees/j1/releves') {
        const s = corps as SaisieJournee
        saisies.push(s)
        courante = journee(
          courante.version + 1,
          courante.lignes.map((l) => {
            const r = s.releves.find((x) => x.pistoletId === l.pistoletId)!
            const nette = r.indexArrivee! - l.indexDepart - (r.remiseCuve ?? 0)
            return {
              ...l,
              indexArrivee: r.indexArrivee,
              remiseCuve: r.remiseCuve ?? 0,
              sortieBrute: nette + (r.remiseCuve ?? 0),
              sortieNette: nette,
              montant: Math.round(nette * 755),
              anomalies: [],
            }
          }),
        )
        return { status: 200, corps: courante }
      }
      if (chemin === '/journees/j1/stock') return { status: 200, corps: stock() }
      if (chemin === '/journees/j1/jaugeages') {
        const s = corps as { version: number; jaugeages: { volumeLitres: number }[] }
        jaugeages.push(s)
        jaugeFin = s.jaugeages[0]!.volumeLitres
        courante = { ...courante, version: courante.version + 1 }
        return { status: 200, corps: stock() }
      }
      if (chemin === '/journees/j1/cloture') {
        clotures.push(corps)
        courante = {
          ...courante,
          statut: 'CLOTUREE',
          version: courante.version + 1,
          clotureePar: 'gerant.nia',
          clotureeLe: '2026-08-16T22:00:00Z',
          actions: { saisir: false, cloturer: false, valider: false, renvoyer: false },
        }
        return { status: 200, corps: courante }
      }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/journees/j1'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Journée du 16/08/2026' })).toBeInTheDocument()
    expect(screen.getAllByText("Index d'arrivée non saisi.")).toHaveLength(2)

    // Aperçu immédiat : 800,5 L sortis, 20 L remis en cuve
    await u.type(screen.getByLabelText("Index d'arrivée G1"), '89716,5')
    await u.type(screen.getByLabelText('Remise en cuve G1'), '20')
    expect(screen.getByTestId('nette-G1')).toHaveTextContent('780,5 L')
    expect(screen.getByTestId('montant-G1')).toHaveTextContent('589 278 F')
    await u.type(screen.getByLabelText("Index d'arrivée G2"), '140000')
    expect(within(screen.getByTestId('ligne-G2')).getByText(/inférieur/)).toBeInTheDocument()
    await u.clear(screen.getByLabelText("Index d'arrivée G2"))
    await u.type(screen.getByLabelText("Index d'arrivée G2"), '141003')
    await u.type(screen.getByLabelText('Jaugeage de fin C1'), '8714')

    // Clôturer enregistre relevés puis jaugeages, puis montre le récapitulatif
    await u.click(screen.getByRole('button', { name: 'Clôturer la journée' }))
    const dialogue = await screen.findByRole('dialog')
    expect(saisies[0]).toMatchObject({
      version: 3,
      releves: [{ pistoletId: 'p1', indexArrivee: 89716.5, remiseCuve: 20 }, { indexArrivee: 141003 }],
    })
    expect(jaugeages).toEqual([{ version: 4, jaugeages: [{ cuveId: 'c1', moment: 'CLOTURE', hauteurMm: null, hauteurEauMm: null, volumeLitres: 8714, observation: null }] }])
    expect(within(dialogue).getByTestId('total-journee')).toHaveTextContent('966 778 F')
    expect(within(dialogue).getByTestId('recap-ecart-C1')).toHaveTextContent(/5,5 L/)
    // Montant à verser = ventes − dépenses autorisées
    expect(within(dialogue).getByTestId('montant-a-verser')).toHaveTextContent('961 778 F')
    await u.click(within(dialogue).getByRole('button', { name: 'Confirmer la clôture' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(clotures).toEqual([{ version: 5 }])
    expect(await screen.findByText(/Clôturée par gerant.nia/)).toBeInTheDocument()
    expect(screen.getByLabelText("Index d'arrivée G1")).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Enregistrer' })).not.toBeInTheDocument()
    // Recette figée et solde de caisse du gérant reporté
    const recetteFigee = screen.getByLabelText('Recette et versements')
    expect(await within(recetteFigee).findByText('Figée')).toBeInTheDocument()
    expect(within(recetteFigee).getByTestId('solde-fin')).toHaveTextContent('1 081 778 F')
    expect(screen.queryByRole('button', { name: 'Ajouter une dépense' })).not.toBeInTheDocument()
  })

  it('demande une confirmation avant de valider une journée clôturée', async () => {
    const SUPERVISEUR = utilisateurTest({
      identifiant: 'sup.reseau',
      role: 'SUPERVISEUR',
      permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_VALIDER'] as Permission[],
    })
    const terminee = { ...ligne('p1', 'G1', 'P1', 88916), indexArrivee: 89916, sortieBrute: 1000, sortieNette: 1000, montant: 755000, anomalies: [] }
    let courante: DetailJournee = {
      ...journee(7, [terminee], 'CLOTUREE'),
      actions: { saisir: false, cloturer: false, valider: true, renvoyer: true },
    }
    const validations: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', SUPERVISEUR) }
      if (chemin === '/journees/j1' && methode === 'GET') return { status: 200, corps: courante }
      if (chemin === '/journees/j1/validation') {
        validations.push(corps)
        courante = {
          ...courante,
          statut: 'VALIDEE',
          version: 8,
          valideePar: 'sup.reseau',
          valideeLe: '2026-08-17T08:00:00Z',
          actions: { saisir: false, cloturer: false, valider: false, renvoyer: false },
        }
        return { status: 200, corps: courante }
      }
      return undefined
    })
    const routeur = createMemoryRouter(routes, { initialEntries: ['/journees/j1'] })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <RestaurationSession>
          <RouterProvider router={routeur} />
        </RestaurationSession>
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Valider' }))
    let dialogue = await screen.findByRole('dialog', { name: 'Valider la journée du 16/08/2026 ?' })
    expect(within(dialogue).getByText(/clôturée par gerant.nia/)).toBeInTheDocument()
    expect(within(dialogue).getByTestId('total-journee')).toHaveTextContent('755 000 F')
    // Annuler : rien n'est envoyé
    await u.click(within(dialogue).getByRole('button', { name: 'Annuler' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(validations).toEqual([])

    await u.click(screen.getByRole('button', { name: 'Valider' }))
    dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByRole('button', { name: 'Confirmer la validation' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(validations).toEqual([{ version: 7 }])
    expect(await screen.findByText(/validée par sup.reseau/)).toBeInTheDocument()
  })
})
