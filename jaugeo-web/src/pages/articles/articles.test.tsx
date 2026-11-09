import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ArticleStation, Permission, Station, StockArticles, VentesArticlesJournee } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'
import { SectionArticles } from '../journees/SectionArticles'

const GERANT = utilisateurTest({
  identifiant: 'gerant.nia',
  role: 'GERANT',
  permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_SAISIR'] as Permission[],
  perimetre: 'STATIONS',
  stationIds: [1],
})

const STATION = { id: 's-nia', code: 'NIA', nom: 'Station Niandane', actif: true } as Station

const article = (code: string, libelle: string, surcharge: Partial<ArticleStation> = {}): ArticleStation => ({
  produitId: `p-${code}`,
  code,
  libelle,
  unite: 'UNITE',
  quantite: 20,
  ventesEnCours: 0,
  disponible: 20,
  cmup: 3500,
  valeur: 70000,
  prixVente: 4500,
  dernierComptage: null,
  aCompter: true,
  enRetard: true,
  ...surcharge,
})

const stock = (): StockArticles => ({
  stationId: 's-nia',
  stationCode: 'NIA',
  stationNom: 'Station Niandane',
  articles: [article('HUILE5', 'Huile moteur 5 L'), article('LAVE', 'Lave-glace', { aCompter: false, enRetard: false, dernierComptage: '2026-11-05' })],
  valeurStock: 140000,
  nombreACompter: 1,
  entreesAnnoncees: [
    {
      id: 'e1',
      version: 0,
      produitId: 'p-HUILE5',
      produitCode: 'HUILE5',
      produitLibelle: 'Huile moteur 5 L',
      unite: 'UNITE',
      quantite: 24,
      coutUnitaire: 3500,
      valeur: 84000,
      fournisseur: 'Total Lubrifiants',
      reference: 'BLA-1',
      statut: 'ANNONCEE',
      quantiteRecue: null,
      recueLe: null,
      recuePar: null,
      observation: null,
      annonceeLe: '2026-11-08T09:00:00Z',
      annonceePar: 'logistique',
    },
  ],
  peutAnnoncer: false,
  peutRecevoir: true,
  peutCompter: true,
})

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

describe('Lubrifiants et boutique', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    useSession.getState().fermer()
  })

  it('le gérant confirme une entrée et compte les articles proposés, écart expliqué', async () => {
    const envois: { chemin: string; corps: unknown }[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/stations/s-nia/articles') return { status: 200, corps: stock() }
      if (methode === 'POST') {
        envois.push({ chemin, corps })
        return { status: 200, corps: chemin.endsWith('comptages') ? stock() : stock().entreesAnnoncees[0] }
      }
      return undefined
    })
    afficher('/boutique')
    const u = userEvent.setup()

    expect(await screen.findByTestId('valeur-stock')).toHaveTextContent('140 000 F')
    const entrees = screen.getByLabelText('Entrées à confirmer')
    await u.click(within(entrees).getByRole('button', { name: 'Confirmer la réception' }))
    let dialogue = await screen.findByRole('dialog')
    const quantite = within(dialogue).getByLabelText(/Quantité reçue/)
    await u.clear(quantite)
    await u.type(quantite, '23')
    expect(within(dialogue).getByRole('button', { name: 'Confirmer' })).toBeDisabled()
    await u.type(within(dialogue).getByLabelText(/Observation/), 'Un bidon percé')
    await u.click(within(dialogue).getByRole('button', { name: 'Confirmer' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(envois[0]).toEqual({
      chemin: '/articles/entrees/e1/reception',
      corps: { version: 0, quantiteRecue: 23, observation: 'Un bidon percé' },
    })

    // Inventaire : seul l'article proposé est listé ; un écart exige sa raison
    await u.click(screen.getByRole('tab', { name: 'Inventaire (1)' }))
    expect(screen.queryByText('Lave-glace')).not.toBeInTheDocument()
    await u.type(screen.getByLabelText('Quantité comptée Huile moteur 5 L'), '18')
    expect(screen.getByTestId('ecart-HUILE5')).toHaveTextContent('-2')
    expect(screen.getByTestId('ecart-HUILE5')).toHaveTextContent('-7 000 F')
    const bouton = screen.getByRole('button', { name: 'Enregistrer le comptage' })
    expect(bouton).toBeDisabled()
    await u.type(screen.getByLabelText(/Raison de l'écart/), 'Casse')
    await u.click(bouton)
    await waitFor(() => expect(envois).toHaveLength(2))
    expect(envois[1]).toEqual({
      chemin: '/stations/s-nia/articles/comptages',
      corps: { comptages: [{ produitId: 'p-HUILE5', compte: 18, observation: 'Casse' }] },
    })
  })

  it('saisit les ventes de la journée au prix du jour et n’envoie que les lignes modifiées', async () => {
    let ventes: VentesArticlesJournee = {
      journeeId: 'j1',
      version: 4,
      statut: 'BROUILLON',
      modifiable: true,
      articles: [
        { produitId: 'p-HUILE5', code: 'HUILE5', libelle: 'Huile moteur 5 L', unite: 'UNITE', disponible: 20, prixUnitaire: 4500, quantite: null, montant: null, cmup: null },
        { produitId: 'p-VIDE', code: 'VIDE', libelle: 'Article épuisé', unite: 'UNITE', disponible: 0, prixUnitaire: 1000, quantite: null, montant: null, cmup: null },
      ],
      total: 0,
    }
    const envois: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/journees/j1/articles' && methode === 'GET') return { status: 200, corps: ventes }
      if (chemin === '/journees/j1/articles' && methode === 'PUT') {
        envois.push(corps)
        ventes = { ...ventes, version: 5, total: 9000, articles: [{ ...ventes.articles[0]!, quantite: 2, montant: 9000 }, ventes.articles[1]!] }
        return { status: 200, corps: ventes }
      }
      return undefined
    })
    render(
      <Fournisseurs client={creerQueryClient()}>
        <SectionArticles journeeId="j1" />
      </Fournisseurs>,
    )
    const u = userEvent.setup()

    const champ = await screen.findByLabelText('Quantité vendue Huile moteur 5 L')
    // Article sans stock masqué par défaut
    expect(screen.queryByText('Article épuisé')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Afficher l’article sans stock' })).toBeInTheDocument()
    await u.type(champ, '2')
    expect(screen.getByTestId('total-boutique')).toHaveTextContent('9 000 F')
    expect(screen.getByText('Ventes non enregistrées.')).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Enregistrer les ventes' }))
    await waitFor(() => expect(envois).toEqual([{ version: 4, ventes: [{ produitId: 'p-HUILE5', quantite: 2 }] }]))
    await waitFor(() => expect(screen.queryByText('Ventes non enregistrées.')).not.toBeInTheDocument())
    expect(screen.getByTestId('total-boutique')).toHaveTextContent('9 000 F')
  })
})
