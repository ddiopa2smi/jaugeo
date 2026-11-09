import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Client, Permission, TableauReseau, VueDirection } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const DIRECTION = utilisateurTest({
  identifiant: 'direction',
  nomComplet: 'Ibrahima Fall',
  role: 'DIRECTION',
  roleLibelle: 'Direction générale',
  permissions: ['REFERENTIEL_CONSULTER', 'TABLEAU_BORD_RESEAU', 'PRIX_MODIFIER', 'DEPENSE_VALIDER'] as Permission[],
})
const CONTROLEUR = utilisateurTest({
  identifiant: 'controle',
  nomComplet: 'Awa Ndiaye',
  role: 'CONTROLEUR_GESTION',
  permissions: ['REFERENTIEL_CONSULTER', 'TABLEAU_BORD_RESEAU'] as Permission[],
})

const VUE: VueDirection = {
  date: '2026-11-09',
  mois: '2026-11',
  periode: { du: '2026-11-01', au: '2026-11-08', litres: 40000, ventes: 30_000_000 },
  precedente: { du: '2026-10-01', au: '2026-10-08', litres: 40000, ventes: 25_000_000 },
  ventesMoisPrecedent: 98_000_000,
  montantDu: 28_000_000,
  versements: 21_000_000,
  soldeCaisse: 7_000_000,
  produits: [
    { code: 'GASOIL', libelle: 'Gasoil', litres: 25000, ventes: 18_000_000, litresPrecedent: 24000, ventesPrecedent: 15_000_000 },
    { code: 'SUPER', libelle: 'Super', litres: 15000, ventes: 12_000_000, litresPrecedent: 16000, ventesPrecedent: 10_000_000 },
  ],
  jours: [
    { date: '2026-11-01', litres: { GASOIL: 3000, SUPER: 2000 }, ventes: { GASOIL: 2_200_000, SUPER: 1_500_000 }, ventesTotal: 3_700_000 },
    { date: '2026-11-02', litres: { GASOIL: 0, SUPER: 0 }, ventes: { GASOIL: 0, SUPER: 0 }, ventesTotal: 0 },
  ],
  stations: [
    {
      stationId: 's-NIA',
      code: 'NIA',
      nom: 'Niandane',
      litres: 40000,
      ventes: 30_000_000,
      ventesPrecedent: 25_000_000,
      montantDu: 28_000_000,
      versements: 21_000_000,
      soldeCaisse: 7_000_000,
      joursNonClotures: 2,
    },
  ],
}

const RESEAU: TableauReseau = {
  date: '2026-11-09',
  mois: '2026-11',
  parNiveau: { NORMAL: 0, ALERTE: 1, ENQUETE: 0 },
  litresMois: 40000,
  litresVeille: 5000,
  ventesMois: 30_000_000,
  versementsMois: 21_000_000,
  ecartStockValeurMois: -50_000,
  stations: [
    {
      stationId: 's-NIA',
      code: 'NIA',
      nom: 'Niandane',
      latitude: null,
      longitude: null,
      niveau: 'ALERTE',
      motifs: ['3 journées clôturées à valider'],
      derniereCloture: '2026-11-08',
      joursSansCloture: 0,
      journeesAValider: 3,
      litresMois: {},
      litresMoisTotal: 40000,
      litresVeille: 5000,
      ventesMois: 30_000_000,
      versementsMois: 21_000_000,
      soldeCaisse: 7_000_000,
      ecartsCaisseMois: 0,
      ecartStockValeurMois: -50_000,
      ecartsStock: [],
    },
  ],
}

const client = (nom: string, encours: number, plafond: number | null): Client => ({
  id: `c-${nom}`,
  code: nom.slice(0, 3).toUpperCase(),
  nom,
  telephone: null,
  recouvrement: 'DIRECTION',
  plafond,
  actif: true,
  observation: null,
  encours,
  disponible: plafond === null ? null : Math.max(0, plafond - encours),
  version: 0,
})

function serveur(utilisateur: ReturnType<typeof utilisateurTest>) {
  return installerFauxServeur(({ chemin }) => {
    if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', utilisateur) }
    if (chemin.startsWith('/tableau-bord/direction')) return { status: 200, corps: VUE }
    if (chemin.startsWith('/tableau-bord/reseau')) return { status: 200, corps: RESEAU }
    if (chemin === '/clients') return { status: 200, corps: [client('Cheikh Omar', 24_000_000, 20_000_000), client('Pêcheurs de Kayar', 1_000_000, null)] }
    if (chemin === '/tresorerie/situation')
      return { status: 200, corps: { stations: [], retards: 0, versementsAVerifier: 2, manquantsOuverts: 1, montantManquants: 150_000, delaiVersementJours: 3, depassementsPlafond: 0 } }
    if (chemin === '/stock/situation') return { status: 200, corps: { cuves: [], alertes: 0, complementsARegler: 1, montantComplementsARegler: 80_000 } }
    if (chemin === '/depenses/a-valider') return { status: 200, corps: [] }
    if (chemin === '/prix/grille') return { status: 200, corps: [] }
    if (chemin.startsWith('/stations') || chemin.startsWith('/produits')) return { status: 200, corps: [] }
    return undefined
  })
}

function afficher() {
  const routeur = createMemoryRouter(routes, { initialEntries: ['/'] })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>,
  )
}

describe('vue de la Direction', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('est la page d’accueil de la Direction : tendances, argent dû, décisions et risques', async () => {
    serveur(DIRECTION)
    afficher()

    expect(await screen.findByText(/activité du 1er au 8 novembre 2026/)).toBeInTheDocument()
    // Ventes : +20 % par rapport à la même période d'octobre (tuile, Gasoil, Super, station NIA) ; litres stables
    expect(screen.getAllByText('+20 %')).toHaveLength(4)
    expect(screen.getAllByText('vs 1er au 8 octobre')).toHaveLength(2)
    expect(screen.getByText('75 % des 28 000 000 F dus par les stations')).toBeInTheDocument()

    // Argent dû = caisse 7 M + créances 25 M + manquants 150 000 + compléments 80 000
    const argent = await screen.findByLabelText('Argent dû au réseau')
    expect(await within(argent).findByText('32 230 000 F')).toBeInTheDocument()
    expect(within(argent).getByText(/1 au-delà de leur plafond : Cheikh Omar/)).toBeInTheDocument()

    const decisions = screen.getByLabelText('Décisions en attente')
    expect(await within(decisions).findByText('3')).toBeInTheDocument()

    const risques = screen.getByLabelText('Risques')
    expect(await within(risques).findByText('3 journées clôturées à valider')).toBeInTheDocument()

    expect(screen.getByRole('img', { name: /Ventes par jour et par produit/ })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Ventes par produit' })).toHaveTextContent('Gasoil')
  })

  it('les autres rôles gardent le tableau de bord habituel', async () => {
    const { appels } = serveur(CONTROLEUR)
    afficher()
    expect(await screen.findByText('Bonjour Awa')).toBeInTheDocument()
    expect(screen.queryByText(/Argent dû au réseau/)).not.toBeInTheDocument()
    expect(appels.some((a) => a.chemin.startsWith('/tableau-bord/direction'))).toBe(false)
  })
})
