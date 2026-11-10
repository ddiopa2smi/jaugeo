import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  FactureFournisseurDetail,
  FournisseurAppro,
  Permission,
  Produit,
  Propositions,
  Station,
  Tournee,
  Transporteur,
} from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const LOGISTIQUE = utilisateurTest({
  identifiant: 'logistique',
  nomComplet: 'Moussa Diop',
  role: 'LOGISTIQUE',
  permissions: ['REFERENTIEL_CONSULTER', 'RECEPTION_VALIDER', 'TABLEAU_BORD_RESEAU'] as Permission[],
})

const STATION: Station = {
  id: 's-nia',
  numero: 1,
  code: 'NIA',
  nom: 'Station Niandane',
  type: 'ROUTIERE',
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

const GASOIL: Produit = {
  id: 'p-go',
  code: 'GASOIL',
  libelle: 'Gasoil',
  famille: 'CARBURANT',
  unite: 'LITRE',
  suiviJaugeage: true,
  tauxTva: null,
  actif: true,
  toleranceEcartPct: 0.5,
  version: 0,
}

const DEPOT: FournisseurAppro = {
  id: 'f-sar',
  nom: 'Dépôt SAR',
  telephone: null,
  adresse: null,
  delaiLivraison: 2,
  principal: true,
  factureA15: true,
  actif: true,
  version: 0,
  prix: [],
}

const TRANSPORTEUR: Transporteur = {
  id: 't-ndar',
  nom: 'Trans Ndar',
  telephone: null,
  contratReference: 'CT-1',
  contratDebut: '2026-01-01',
  contratFin: null,
  tolerancePct: 0.2,
  toleranceAppliquee: 0.2,
  penalite: 50000,
  actif: true,
  observation: null,
  version: 0,
  citernes: [{ id: 'ci-1', immatriculation: 'SL-1234-A', capaciteLitres: 30000, actif: true, version: 0 }],
  notation: { note: 3.7, livraisons: 4, ponctualitePct: 75, conformitePct: 75, tauxManquantPct: 0.33, horsTolerance: 1, jours: 180 },
  penalitesARegler: 50000,
}

const PROPOSITIONS: Propositions = {
  fournisseurId: 'f-sar',
  fournisseurNom: 'Dépôt SAR',
  delaiLivraison: 2,
  margeJours: 2,
  tauxRemplissagePct: 95,
  date: '2026-11-10',
  lignes: [
    {
      stationId: 's-nia',
      stationCode: 'NIA',
      stationNom: 'Station Niandane',
      produitId: 'p-go',
      produitCode: 'GASOIL',
      produitLibelle: 'Gasoil',
      dateStock: '2026-11-09',
      stockAujourdhui: 6000,
      capaciteUtile: 28500,
      ventesMoyennes: 3000,
      autonomieJours: 1.8,
      enCours: 0,
      stockArrivee: 0,
      quantiteProposee: 28000,
      ruptureLe: '2026-11-11',
      aCommander: true,
      motif: 'Autonomie de 1.8 j pour un délai de livraison de 2 j et 2 j de marge.',
    },
  ],
}

const TOURNEE: Tournee = {
  id: 'to-1',
  numero: 'TO2026-00001',
  statut: 'COMMANDEE',
  fournisseurId: 'f-sar',
  fournisseurNom: 'Dépôt SAR',
  transporteurId: 't-ndar',
  transporteurNom: 'Trans Ndar',
  citerneId: 'ci-1',
  immatriculation: 'SL-1234-A',
  capaciteCiterne: 30000,
  chauffeur: null,
  dateChargementPrevue: '2026-11-10',
  dateLivraisonPrevue: '2026-11-11',
  chargeLe: null,
  scelles: null,
  partiLe: null,
  motifAnnulation: null,
  observation: null,
  quantiteCommandee: 28000,
  livraisons: 1,
  receptionnees: 0,
  version: 0,
  lignes: [
    {
      id: 'l-1',
      ordre: 1,
      stationId: 's-nia',
      stationCode: 'NIA',
      stationNom: 'Station Niandane',
      produitId: 'p-go',
      produitCode: 'GASOIL',
      quantiteCommandee: 28000,
      numeroBl: null,
      quantiteBl: null,
      quantiteBl15: null,
      reception: null,
      version: 0,
    },
  ],
}

function afficher(url: string) {
  const routeur = createMemoryRouter(routes, { initialEntries: [url] })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>,
  )
}

describe('page Approvisionnement', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('commande une tournée à partir de la proposition', async () => {
    const commandes: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', LOGISTIQUE) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/produits') return { status: 200, corps: [GASOIL] }
      if (chemin === '/appro/fournisseurs') return { status: 200, corps: [DEPOT] }
      if (chemin === '/appro/transporteurs') return { status: 200, corps: [TRANSPORTEUR] }
      if (chemin === '/appro/propositions') return { status: 200, corps: PROPOSITIONS }
      if (chemin === '/appro/tournees' && methode === 'POST') {
        commandes.push(corps)
        return { status: 201, corps: TOURNEE }
      }
      if (chemin.startsWith('/appro/tournees?')) return { status: 200, corps: commandes.length ? [TOURNEE] : [] }
      return undefined
    })
    afficher('/approvisionnement')
    const u = userEvent.setup()

    const ligne = await screen.findByTestId('proposition-NIA-GASOIL')
    expect(ligne).toHaveTextContent('28 000 L')
    expect(ligne).toHaveTextContent('1,8 j')
    expect(screen.getByText(/Dépôt principal Dépôt SAR : délai de 2 j/)).toBeInTheDocument()

    await u.click(within(ligne).getByRole('checkbox', { name: 'Sélectionner NIA GASOIL' }))
    await u.click(screen.getByRole('button', { name: 'Commander la sélection (1)' }))
    const dialogue = await screen.findByRole('dialog')
    expect(within(dialogue).getByDisplayValue('28000')).toBeInTheDocument()
    await u.click(within(dialogue).getByLabelText(/Transporteur/))
    await u.click(await screen.findByRole('option', { name: /Trans Ndar/ }))
    await u.click(within(dialogue).getByLabelText(/Citerne/))
    await u.click(await screen.findByRole('option', { name: /SL-1234-A/ }))
    await u.click(within(dialogue).getByRole('button', { name: 'Commander' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(commandes).toMatchObject([
      {
        fournisseurId: 'f-sar',
        transporteurId: 't-ndar',
        citerneId: 'ci-1',
        livraisons: [{ stationId: 's-nia', produitId: 'p-go', quantite: 28000 }],
      },
    ])
    // Bascule sur les tournées
    expect(await screen.findByText('TO2026-00001')).toBeInTheDocument()
  })

  it('contrôle une facture fournisseur et exige un motif pour un bon à payer avec anomalies', async () => {
    const detail = (statut: FactureFournisseurDetail['facture']['statut']): FactureFournisseurDetail => ({
      facture: {
        id: 'fa-1',
        fournisseurId: 'f-sar',
        fournisseurNom: 'Dépôt SAR',
        numero: 'FA-77',
        dateFacture: '2026-11-09',
        statut,
        montant: 12200000,
        montantAttendu: 12000000,
        ecart: 200000,
        lignes: 1,
        anomalies: 1,
        motifDecision: statut === 'EN_LITIGE' ? 'Prix contesté' : null,
        decidePar: statut === 'EN_LITIGE' ? 'logistique' : null,
        decideLe: statut === 'EN_LITIGE' ? '2026-11-10T09:00:00Z' : null,
        observation: null,
        version: statut === 'SAISIE' ? 0 : 1,
      },
      lignes: [
        {
          numeroBl: 'BL-77',
          produitId: 'p-go',
          produitCode: 'GASOIL',
          quantite: 20000,
          prixUnitaire: 610,
          montant: 12200000,
          dateReception: '2026-11-08',
          stationCode: 'NIA',
          base: 'ambiante',
          quantiteBl: 20000,
          quantiteRecue: 19900,
          manquantFournisseur: 0,
          prixConvenu: 600,
          montantAttendu: 12000000,
          anomalies: ['Prix facturé 610 F pour un prix convenu de 600 F.'],
        },
      ],
    })
    let statut: FactureFournisseurDetail['facture']['statut'] = 'SAISIE'
    const decisions: unknown[] = []
    installerFauxServeur(({ chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', LOGISTIQUE) }
      if (chemin === '/appro/factures') return { status: 200, corps: [detail(statut).facture] }
      if (chemin === '/appro/factures/fa-1') return { status: 200, corps: detail(statut) }
      if (chemin === '/appro/factures/fa-1/decision') {
        decisions.push(corps)
        statut = 'EN_LITIGE'
        return { status: 200, corps: detail(statut) }
      }
      return undefined
    })
    afficher('/approvisionnement?onglet=factures')
    const u = userEvent.setup()

    await u.click(await screen.findByText('FA-77'))
    const fiche = await screen.findByRole('dialog')
    expect(await within(fiche).findByText(/prix convenu de 600 F/)).toBeInTheDocument()

    // Bon à payer malgré l'anomalie : motif exigé
    await u.click(within(fiche).getByRole('button', { name: 'Bon à payer' }))
    let decision = (await screen.findAllByRole('dialog')).at(-1)!
    expect(within(decision).getByText(/justifiez le bon à payer/)).toBeInTheDocument()
    expect(within(decision).getByRole('button', { name: 'Bon à payer' })).toBeDisabled()
    await u.click(within(decision).getByRole('button', { name: 'Retour' }))

    await u.click(within(fiche).getByRole('button', { name: 'Litige' }))
    decision = (await screen.findAllByRole('dialog')).at(-1)!
    await u.type(within(decision).getByLabelText(/Motif/), 'Prix contesté')
    await u.click(within(decision).getByRole('button', { name: 'Mettre en litige' }))

    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1))
    expect(decisions).toMatchObject([{ version: 0, bonAPayer: false, motif: 'Prix contesté' }])
    expect(await within(screen.getByRole('dialog')).findByText(/Prix contesté — logistique/)).toBeInTheDocument()
  })
})
