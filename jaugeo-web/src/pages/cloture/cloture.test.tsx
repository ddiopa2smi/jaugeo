import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BudgetStation, CompteExploitation, CompteReseau, EtatClotureMois, EtatClotureStation, Station, SuiviBudget } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { creerQueryClient } from '../../app/queryClient'
import { repartir } from '../../shared/cloture'
import { installerFauxServeur } from '../../test/fauxServeur'
import { PageCloture } from './PageCloture'

const compte = (code: string, nom: string, surcharge: Partial<CompteExploitation> = {}): CompteExploitation => ({
  stationId: `s-${code}`,
  stationCode: code,
  stationNom: nom,
  produits: [
    { code: 'GASOIL', libelle: 'Gasoil', litres: 500, chiffreAffaires: 350_000, coutAchat: 300_000, marge: 50_000, ecartStockLitres: -10, ecartStockValeur: -6_000 },
  ],
  chiffreAffaires: 350_000,
  coutAchat: 300_000,
  margeCarburants: 50_000,
  ventesArticles: 0,
  coutArticles: 0,
  margeArticles: 0,
  remises: 0,
  margeBrute: 50_000,
  tauxMarge: 14.29,
  ecartsStock: -6_000,
  depenses: 20_000,
  regularisations: 0,
  resultat: 24_000,
  coutComplet: true,
  prixManquants: [],
  clos: false,
  cloturePar: null,
  clotureLe: null,
  ...surcharge,
})

const etat = (code: string, nom: string, surcharge: Partial<EtatClotureStation> = {}): EtatClotureStation => ({
  stationId: `s-${code}`,
  stationCode: code,
  stationNom: nom,
  statut: 'PRET',
  blocages: [],
  journees: 31,
  journeesValidees: 31,
  cloturePar: null,
  clotureLe: null,
  ...surcharge,
})

function afficher(onglet: string) {
  render(
    <Fournisseurs client={creerQueryClient()}>
      <MemoryRouter initialEntries={[`/cloture?mois=2026-10&onglet=${onglet}`]}>
        <PageCloture />
      </MemoryRouter>
    </Fournisseurs>,
  )
}

describe('clôture mensuelle', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('présente le compte d’exploitation du réseau puis d’une station, et signale un prix d’achat manquant', async () => {
    const nia = compte('NIA', 'Niandane', { clos: true, cloturePar: 'awa.cg', clotureLe: '2026-11-05T09:00:00Z' })
    const kay = compte('KAY', 'Kayar', {
      produits: [{ code: 'PIROGUE', libelle: 'Essence pirogue', litres: 200, chiffreAffaires: 140_000, coutAchat: null, marge: null, ecartStockLitres: 0, ecartStockValeur: 0 }],
      chiffreAffaires: 140_000,
      coutAchat: 0,
      margeCarburants: 0,
      margeBrute: 0,
      tauxMarge: 0,
      ecartsStock: 0,
      depenses: 5_000,
      regularisations: -15_000,
      resultat: -20_000,
      coutComplet: false,
      prixManquants: ['Essence pirogue'],
    })
    const reseau: CompteReseau = {
      mois: '2026-10',
      stations: [kay, nia],
      total: compte('RESEAU', 'Réseau', {
        stationId: null,
        chiffreAffaires: 490_000,
        margeBrute: 50_000,
        depenses: 25_000,
        regularisations: -15_000,
        resultat: 4_000,
        coutComplet: false,
        prixManquants: ['Essence pirogue'],
      }),
    }
    installerFauxServeur(({ chemin }) => (chemin === '/cloture/compte?mois=2026-10' ? { status: 200, corps: reseau } : undefined))
    afficher('compte')
    const u = userEvent.setup()

    // Réseau par défaut : résultat 4 000 F, avertissement sur le coût incomplet
    const vue = await screen.findByLabelText("Compte d'exploitation Réseau")
    expect(within(vue).getByTestId('compte-resultat')).toHaveTextContent('4 000 F')
    expect(within(vue).getByTestId('compte-regularisations')).toHaveTextContent('− 15 000 F')
    expect(screen.getByTestId('prix-manquants')).toHaveTextContent('Essence pirogue')

    // Un clic sur une station du comparatif ouvre son compte
    await u.click(screen.getByTestId('compte-NIA'))
    const niandane = await screen.findByLabelText("Compte d'exploitation Niandane")
    expect(within(niandane).getByTestId('compte-ca')).toHaveTextContent('350 000 F')
    expect(within(niandane).getByText('Clôturé le 05/11/2026')).toBeInTheDocument()
    expect(within(niandane).getByTestId('compte-marge')).toHaveTextContent('50 000 F')
    expect(within(niandane).getByText('14,29 % du chiffre d\'affaires')).toBeInTheDocument()
    expect(within(niandane).getByTestId('compte-resultat')).toHaveTextContent('24 000 F')
    expect(within(niandane).queryByTestId('compte-regularisations')).not.toBeInTheDocument()
    // Niandane a tous ses prix d'achat : plus d'avertissement
    expect(screen.queryByTestId('prix-manquants')).not.toBeInTheDocument()
  })

  it('ne clôture le mois d’une station prête qu’après confirmation, et liste ce qui bloque les autres', async () => {
    let nia = etat('NIA', 'Niandane')
    const kay = etat('KAY', 'Kayar', { statut: 'BLOQUE', blocages: ['2 journées à clôturer ou à valider (à partir du 30/10/2026).'], journeesValidees: 29 })
    const posts: string[] = []
    const mois = (): EtatClotureMois => ({ mois: '2026-10', stations: [kay, nia], closes: nia.statut === 'CLOS' ? 1 : 0, pretes: nia.statut === 'PRET' ? 1 : 0 })
    installerFauxServeur(({ methode, chemin }) => {
      if (chemin === '/cloture/etat?mois=2026-10') return { status: 200, corps: mois() }
      if (chemin === '/cloture/stations/s-NIA/mois/2026-10' && methode === 'POST') {
        posts.push(chemin)
        nia = etat('NIA', 'Niandane', { statut: 'CLOS', cloturePar: 'awa.cg', clotureLe: '2026-11-05T09:00:00Z' })
        return { status: 200, corps: nia }
      }
      return undefined
    })
    afficher('cloture')
    const u = userEvent.setup()

    expect(await screen.findByTestId('avancement-cloture')).toHaveTextContent('0 station clôturée sur 2')
    expect(within(screen.getByTestId('etat-KAY')).getByText(/2 journées à clôturer ou à valider/)).toBeInTheDocument()
    expect(within(screen.getByTestId('etat-KAY')).queryByRole('button')).not.toBeInTheDocument()

    // Annuler la confirmation ne clôture rien
    await u.click(within(screen.getByTestId('etat-NIA')).getByRole('button', { name: 'Clôturer le mois' }))
    let modale = await screen.findByRole('dialog', { name: 'Clôturer octobre 2026 ?' })
    expect(modale).toHaveTextContent('ne se défait pas')
    await u.click(within(modale).getByRole('button', { name: 'Annuler' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posts).toEqual([])

    await u.click(within(screen.getByTestId('etat-NIA')).getByRole('button', { name: 'Clôturer le mois' }))
    modale = await screen.findByRole('dialog', { name: 'Clôturer octobre 2026 ?' })
    await u.click(within(modale).getByRole('button', { name: 'Clôturer le mois' }))
    await waitFor(() => expect(posts).toEqual(['/cloture/stations/s-NIA/mois/2026-10']))
    expect(await screen.findByText(/Clôturé par awa.cg/)).toBeInTheDocument()
    expect(screen.getByTestId('avancement-cloture')).toHaveTextContent('1 station clôturée sur 2')
  })

  it('répartit un total annuel sur douze mois et enregistre le budget de la station', async () => {
    const station = { id: 's-NIA', code: 'NIA', nom: 'Niandane', actif: true } as Station
    const zero = Array.from({ length: 12 }, () => ({ litres: 0, montant: 0 }))
    const budget: BudgetStation = {
      stationId: 's-NIA',
      stationCode: 'NIA',
      stationNom: 'Niandane',
      annee: 2026,
      produits: [{ produitId: 'p-gasoil', code: 'GASOIL', libelle: 'Gasoil', mois: zero }],
      depenses: Array<number>(12).fill(0),
    }
    const vide = { mois: null, ecoule: true, litresBudget: 0, litresReel: 0, caBudget: 0, caReel: 0, depensesBudget: 0, depensesReel: 0 }
    const suivi: SuiviBudget = {
      annee: 2026,
      stationId: null,
      mois: Array.from({ length: 12 }, (_x, i) => ({ ...vide, mois: `2026-${String(i + 1).padStart(2, '0')}`, ecoule: i < 10 })),
      produits: [],
      cumul: vide,
    }
    const puts: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/stations') return { status: 200, corps: [station] }
      if (chemin.startsWith('/cloture/budget/suivi')) return { status: 200, corps: suivi }
      if (chemin === '/cloture/budget?station=s-NIA&annee=2026') return { status: 200, corps: budget }
      if (chemin === '/cloture/budget' && methode === 'PUT') {
        puts.push(corps)
        return { status: 200, corps: budget }
      }
      return undefined
    })
    afficher('budget')
    const u = userEvent.setup()

    expect(await screen.findByText(/Aucun budget saisi pour 2026/)).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Saisie du budget' }))
    const gasoil = await screen.findByLabelText('Budget Gasoil')
    await u.type(within(gasoil).getByLabelText('Total annuel litres Gasoil'), '100000')
    await u.click(within(gasoil).getByRole('button', { name: 'Répartir litres Gasoil sur 12 mois' }))
    // 100 000 L sur 12 mois : 8 333 L par mois, l'arrondi sur décembre
    expect(within(gasoil).getByLabelText('litres Gasoil Janvier')).toHaveValue('8333')
    expect(within(gasoil).getByLabelText('litres Gasoil Décembre')).toHaveValue('8337')
    await u.type(screen.getByLabelText('dépenses Mars'), '45000')
    await u.click(screen.getByRole('button', { name: 'Enregistrer le budget' }))

    await waitFor(() => expect(puts).toHaveLength(1))
    const envoye = puts[0] as { stationId: string; annee: number; produits: { produitId: string; mois: { litres: number; montant: number }[] }[]; depenses: number[] }
    expect(envoye.stationId).toBe('s-NIA')
    expect(envoye.annee).toBe(2026)
    expect(envoye.produits[0]!.mois.map((m) => m.litres)).toEqual(repartir(100000))
    expect(envoye.produits[0]!.mois.reduce((t, m) => t + m.litres, 0)).toBe(100000)
    expect(envoye.depenses[2]).toBe(45000)
  })
})
