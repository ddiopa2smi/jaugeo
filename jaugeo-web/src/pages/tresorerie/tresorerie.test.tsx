import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Banque, LigneCaisse,LivreCaisse, Permission, Station, Versement } from '../../api/types'
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

const BANQUES: Banque[] = [
  { id: 'b-bis', code: 'BIS', nom: 'Banque Islamique du Sénégal', actif: true, version: 0 },
  { id: 'b-cbao', code: 'CBAO', nom: 'CBAO Groupe Attijariwafa Bank', actif: true, version: 0 },
  { id: 'b-lbo', code: 'LBO', nom: 'La Banque Outarde', actif: false, version: 0 },
]

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
    const creations: FormData[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/stations/s-pot/caisse?mois=2026-01') return { status: 200, corps: LIVRE }
      if (chemin === '/tresorerie/versements?station=s-pot&mois=2026-01') return { status: 200, corps: versements }
      if (chemin === '/tresorerie/banques') return { status: 200, corps: BANQUES.filter((b) => b.actif) }
      if (chemin === '/tresorerie/versements' && methode === 'POST') {
        // Un seul envoi : les données du versement et la photo du bordereau
        creations.push(corps as FormData)
        versements.push(versement({ referenceBv: '774378', piece: { nomFichier: 'bv.jpg', typeMime: 'image/jpeg', taille: 2048 } }))
        return { status: 201, corps: versements[0] }
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
    // La banque se choisit parmi les banques actives de la compagnie
    await u.click(within(dialogue).getByLabelText(/Banque/))
    const choix = within(await screen.findByRole('listbox'))
    expect(choix.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Banque Islamique du Sénégal (BIS)',
      'CBAO Groupe Attijariwafa Bank (CBAO)',
    ])
    await u.click(choix.getByRole('option', { name: /CBAO/ }))
    await u.type(within(dialogue).getByLabelText(/bordereau \(BV\)/), '774378')
    // Sans la photo du bordereau, la création est impossible
    expect(within(dialogue).getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    const photo = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'bv.jpg', { type: 'image/jpeg' })
    await u.upload(within(dialogue).getByLabelText('Photo du bordereau'), photo)
    expect(within(dialogue).getByText('bv.jpg')).toBeInTheDocument()
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(creations).toHaveLength(1)
    expect(JSON.parse(await (creations[0]!.get('versement') as Blob).text())).toEqual(
      expect.objectContaining({ stationId: 's-pot', banque: 'CBAO', referenceBv: '774378', montant: 539760, deposant: null }),
    )
    expect((creations[0]!.get('fichier') as File).name).toBe('bv.jpg')
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

  it('les versements se filtrent par recherche et par statut, et se trient par colonne', async () => {
    const liste = [
      versement({ id: 'v1', referenceBv: '774378', montant: 539760, dateDepot: '2026-01-02', statut: 'VERIFIE', verifiePar: 'compta' }),
      versement({ id: 'v2', referenceBv: '774379', montant: 100000, dateDepot: '2026-01-05', deposant: 'Keba Mbaye' }),
      versement({ id: 'v3', referenceBv: '774380', montant: 250000, dateDepot: '2026-01-09', banque: 'BIS' }),
    ]
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/stations/s-pot/caisse?mois=2026-01') return { status: 200, corps: LIVRE }
      if (chemin === '/tresorerie/versements?station=s-pot&mois=2026-01') return { status: 200, corps: liste }
      return undefined
    })
    afficher('/tresorerie?station=s-pot&mois=2026-01')
    const u = userEvent.setup()
    const ordre = () => screen.getAllByTestId(/^versement-/).map((l) => l.getAttribute('data-testid'))

    expect(await screen.findByTestId('resume-versements')).toHaveTextContent('3 versement(s) · 889 760 F')
    expect(ordre()).toEqual(['versement-774378', 'versement-774379', 'versement-774380'])

    // Tri par montant, puis sens inverse au second clic
    await u.click(screen.getByRole('button', { name: 'Montant' }))
    expect(ordre()).toEqual(['versement-774379', 'versement-774380', 'versement-774378'])
    await u.click(screen.getByRole('button', { name: 'Montant' }))
    expect(ordre()).toEqual(['versement-774378', 'versement-774380', 'versement-774379'])

    // Filtre par statut, avec le nombre de versements de chaque statut
    await u.click(within(screen.getByRole('group', { name: 'Filtrer par statut' })).getByText('À vérifier · 2'))
    expect(ordre()).toEqual(['versement-774380', 'versement-774379'])
    expect(screen.getByTestId('resume-versements')).toHaveTextContent('2 versement(s) · 350 000 F')

    // Recherche sans accents ni majuscules, sur le déposant
    await u.type(screen.getByLabelText('Rechercher un bordereau, un déposant'), 'KEBA')
    expect(ordre()).toEqual(['versement-774379'])
    await u.type(screen.getByLabelText('Rechercher un bordereau, un déposant'), ' zzz')
    expect(screen.getByText('Aucun versement ne correspond à ces filtres.')).toBeInTheDocument()

    // Le livre de caisse se filtre aussi : un seul jour encore en saisie
    await u.click(within(screen.getByRole('group', { name: 'Filtrer les jours' })).getByText('En saisie · 1'))
    expect(screen.queryByTestId('caisse-2026-01-01')).not.toBeInTheDocument()
    expect(screen.getByTestId('caisse-2026-01-02')).toBeInTheDocument()
  })

  it('l’administrateur choisit les banques de la compagnie et en ajoute une', async () => {
    let banques = [...BANQUES]
    const envois: { methode: string; chemin: string; corps: unknown }[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', utilisateurTest()) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/tresorerie/banques?toutes=true') return { status: 200, corps: banques }
      if (chemin === '/tresorerie/banques/b-lbo' && methode === 'PUT') {
        envois.push({ methode, chemin, corps })
        banques = banques.map((b) => (b.id === 'b-lbo' ? { ...b, actif: true, version: 1 } : b))
        return { status: 200, corps: banques[2] }
      }
      if (chemin === '/tresorerie/banques' && methode === 'POST') {
        envois.push({ methode, chemin, corps })
        const b = { id: 'b-x', code: 'BQX', nom: 'Banque X', actif: true, version: 0 }
        banques = [...banques, b]
        return { status: 201, corps: b }
      }
      return undefined
    })
    afficher('/tresorerie')
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Banques' }))
    const dialogue = await screen.findByRole('dialog')
    expect(await within(dialogue).findByTestId('banques-actives')).toHaveTextContent('2 banque(s) active(s) sur 3')
    await u.click(within(within(dialogue).getByTestId('banque-LBO')).getByRole('switch'))
    await waitFor(() => expect(within(dialogue).getByTestId('banques-actives')).toHaveTextContent('3 banque(s) active(s) sur 3'))

    await u.type(within(dialogue).getByLabelText('Sigle'), 'bqx')
    await u.type(within(dialogue).getByLabelText('Nom de la banque'), 'Banque X')
    await u.click(within(dialogue).getByRole('button', { name: 'Ajouter' }))
    expect(await within(dialogue).findByTestId('banque-BQX')).toBeInTheDocument()
    expect(envois).toEqual([
      { methode: 'PUT', chemin: '/tresorerie/banques/b-lbo', corps: { version: 0, actif: true } },
      { methode: 'POST', chemin: '/tresorerie/banques', corps: { code: 'bqx', nom: 'Banque X' } },
    ])
  })
})
