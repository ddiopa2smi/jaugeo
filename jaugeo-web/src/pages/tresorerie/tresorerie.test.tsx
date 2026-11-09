import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LigneCaisse, LivreCaisse, Permission, Station, Versement } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const GERANT = utilisateurTest({
  identifiant: 'gerant.pot',
  nomComplet: 'Fatou Sall',
  role: 'GERANT',
  permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_SAISIR', 'VERSEMENT_SAISIR'] as Permission[],
  perimetre: 'STATIONS',
})

const COMPTABLE = utilisateurTest({
  identifiant: 'compta',
  nomComplet: 'Ibrahima Fall',
  role: 'COMPTABLE',
  permissions: ['REFERENTIEL_CONSULTER', 'VERSEMENT_VALIDER', 'CAISSE_CONTROLER'] as Permission[],
})

const STATION: Station = {
  id: 's-pot',
  numero: 2,
  code: 'POT',
  nom: 'Station Pout',
  type: 'ROUTIERE',
  regionCode: 'TH',
  regionNom: 'Thiès',
  localite: null,
  latitude: null,
  longitude: null,
  modeExploitation: 'PROPRE',
  dateOuverture: null,
  actif: true,
  version: 0,
}

const ligne = (date: string, montant: number | null, versements: number, ouverture: number): LigneCaisse => ({
  date,
  journeeId: `j-${date}`,
  statutJournee: montant === null ? 'BROUILLON' : 'CLOTUREE',
  ventes: montant === null ? null : montant + 5000,
  bons: 0,
  depenses: montant === null ? null : 5000,
  complements: 0,
  encaissements: 0,
  cartes: 0,
  recouvrements: 0,
  montantAVerser: montant,
  soldeOuverture: ouverture,
  versements,
  soldeTheorique: ouverture + (montant ?? 0) - versements,
  arrete: null,
  ecart: null,
  solde: ouverture + (montant ?? 0) - versements,
})

const LIVRE: LivreCaisse = {
  stationId: 's-pot',
  stationCode: 'POT',
  stationNom: 'Station Pout',
  mois: '2026-01',
  soldeDebut: 39760,
  totalVentes: 505000,
  totalBonsDirection: 0,
  totalBonsGerant: 0,
  totalDepenses: 5000,
  totalComplements: 0,
  totalEncaissements: 0,
  totalCartes: 0,
  totalRecouvrements: 0,
  totalMontants: 500000,
  totalVersements: 0,
  totalEcarts: 0,
  soldeFin: 539760,
  joursNonClotures: 1,
  lignes: [ligne('2026-01-01', 500000, 0, 39760), ligne('2026-01-02', null, 0, 539760)],
  peutArreter: false,
  plafondEspeces: null,
}

const versement = (surcharge: Partial<Versement> = {}): Versement => ({
  id: 'v1',
  stationId: 's-pot',
  stationCode: 'POT',
  dateDepot: '2026-01-02',
  banque: 'CBAO',
  referenceBv: '774378',
  montant: 539760,
  deposant: null,
  observation: null,
  statut: 'SAISI',
  piece: null,
  verifiePar: null,
  verifieLe: null,
  motifRejet: null,
  creePar: 'gerant.pot',
  creeLe: '2026-01-02T09:00:00Z',
  version: 0,
  modifiable: true,
  verifiable: false,
  ...surcharge,
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

describe('trésorerie', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('le gérant suit sa caisse et enregistre un versement avec la photo du bordereau', async () => {
    const versements: Versement[] = []
    const creations: unknown[] = []
    const pieces: { chemin: string; fichier: File }[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/stations/s-pot/caisse?mois=2026-01') return { status: 200, corps: LIVRE }
      if (chemin === '/tresorerie/versements?station=s-pot&mois=2026-01') return { status: 200, corps: versements }
      if (chemin === '/tresorerie/versements' && methode === 'POST') {
        creations.push(corps)
        versements.push(versement(corps as Partial<Versement>))
        return { status: 201, corps: versements[0] }
      }
      if (chemin.startsWith('/tresorerie/versements/v1/piece') && methode === 'PUT') {
        pieces.push({ chemin, fichier: (corps as FormData).get('fichier') as File })
        versements[0] = { ...versements[0]!, version: 1, piece: { nomFichier: 'bv.jpg', typeMime: 'image/jpeg', taille: 2048 } }
        return { status: 200, corps: versements[0] }
      }
      return undefined
    })
    afficher('/tresorerie?station=s-pot&mois=2026-01')
    const u = userEvent.setup()

    expect(await screen.findByTestId('solde-mois')).toHaveTextContent('539 760 F')
    expect(screen.getByText(/1 journée\(s\) du mois non clôturée/)).toBeInTheDocument()
    expect(within(screen.getByTestId('caisse-2026-01-01')).getByText('500 000 F')).toBeInTheDocument()
    expect(screen.getByText('Aucun versement enregistré sur ce mois.')).toBeInTheDocument()
    // Pas d'arrêté de caisse pour le gérant
    expect(screen.queryByRole('button', { name: 'Arrêter la caisse' })).not.toBeInTheDocument()

    await u.click(screen.getByRole('button', { name: 'Enregistrer un versement' }))
    const dialogue = await screen.findByRole('dialog')
    // Le montant proposé est le solde non versé
    expect(within(dialogue).getByLabelText(/Montant/)).toHaveValue('539760')
    await u.type(within(dialogue).getByLabelText(/Banque/), 'cbao')
    await u.type(within(dialogue).getByLabelText(/bordereau \(BV\)/), '774378')
    const photo = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'bv.jpg', { type: 'image/jpeg' })
    await u.upload(within(dialogue).getByLabelText('Photo du bordereau'), photo)
    expect(within(dialogue).getByText('bv.jpg')).toBeInTheDocument()
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(creations).toEqual([
      expect.objectContaining({ stationId: 's-pot', banque: 'cbao', referenceBv: '774378', montant: 539760, deposant: null }),
    ])
    expect(pieces).toHaveLength(1)
    expect(pieces[0]!.chemin).toBe('/tresorerie/versements/v1/piece?version=0')
    expect(pieces[0]!.fichier.name).toBe('bv.jpg')
    expect(await screen.findByTestId('versement-774378')).toHaveTextContent('À vérifier')
  })

  it('le comptable vérifie un versement ou le rejette avec un motif', async () => {
    let liste = [
      versement({ verifiable: true, modifiable: false, piece: { nomFichier: 'bv.jpg', typeMime: 'image/jpeg', taille: 2048 } }),
      versement({ id: 'v2', referenceBv: '774379', montant: 100000, verifiable: true, modifiable: false }),
    ]
    const verifications: { chemin: string; corps: unknown }[] = []
    installerFauxServeur(({ chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', COMPTABLE) }
      if (chemin === '/tresorerie/versements') return { status: 200, corps: liste }
      if (chemin.endsWith('/verification')) {
        verifications.push({ chemin, corps })
        const id = chemin.split('/')[3]
        liste = liste.filter((v) => v.id !== id)
        return { status: 200, corps: versement({ id, statut: 'VERIFIE' }) }
      }
      return undefined
    })
    afficher('/tresorerie?onglet=verification')
    const u = userEvent.setup()

    const sansPiece = await screen.findByTestId('versement-774379')
    await u.click(within(sansPiece).getByRole('button', { name: 'Vérifier' }))
    let dialogue = await screen.findByRole('dialog')
    expect(within(dialogue).getByText(/Pas de photo du bordereau/)).toBeInTheDocument()
    expect(within(dialogue).getByRole('button', { name: 'Vérifié' })).toBeDisabled()
    expect(within(dialogue).getByRole('button', { name: 'Rejeter' })).toBeDisabled()
    await u.type(within(dialogue).getByLabelText(/Motif/), 'Bordereau illisible')
    await u.click(within(dialogue).getByRole('button', { name: 'Rejeter' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await u.click(within(screen.getByTestId('versement-774378')).getByRole('button', { name: 'Vérifier' }))
    dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByRole('button', { name: 'Vérifié' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(verifications).toEqual([
      { chemin: '/tresorerie/versements/v2/verification', corps: { version: 0, valide: false, motif: 'Bordereau illisible' } },
      { chemin: '/tresorerie/versements/v1/verification', corps: { version: 0, valide: true, motif: null } },
    ])
    expect(await screen.findByText('Aucun versement en attente de vérification.')).toBeInTheDocument()
  })
})
