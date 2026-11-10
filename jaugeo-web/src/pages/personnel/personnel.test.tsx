import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { EcartsStation, Employe, Permission, PersonnelStation, PlanningSemaine, Station } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { RestaurationSession } from '../../app/Gardes'
import { creerQueryClient } from '../../app/queryClient'
import { routes } from '../../app/routes'
import { useSession } from '../../auth/session'
import { ajouterJours, formaterEcart, lundi } from '../../shared/personnel'
import { installerFauxServeur, sessionTest, utilisateurTest } from '../../test/fauxServeur'

const GERANT = utilisateurTest({
  identifiant: 'gerant',
  nomComplet: 'Ibrahima Sarr',
  role: 'GERANT',
  permissions: ['REFERENTIEL_CONSULTER', 'JOURNEE_SAISIR', 'JOURNEE_CLOTURER', 'PERSONNEL_GERER'] as Permission[],
  perimetre: 'STATIONS',
  stationIds: [1],
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

const employe = (id: string, prenom: string, nom: string): Employe => ({
  id,
  stationId: 's-nia',
  stationCode: 'NIA',
  matricule: null,
  nom,
  prenom,
  nomComplet: `${prenom} ${nom}`,
  telephone: null,
  fonction: 'POMPISTE',
  dateEntree: '2026-01-05',
  dateSortie: null,
  identifiant: null,
  actif: true,
  version: 0,
})

const AWA = employe('e-awa', 'Awa', 'Ndiaye')
const MOUSSA = employe('e-moussa', 'Moussa', 'Fall')

const personnel = (employes: Employe[]): PersonnelStation => ({
  stationId: 's-nia',
  stationCode: 'NIA',
  stationNom: 'Station Niandane',
  feuillesCaisse: true,
  gerer: true,
  reglerFeuilles: false,
  employes,
})

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

describe('page Personnel', () => {
  beforeEach(() => useSession.setState({ jeton: null, utilisateur: null, statut: 'initialisation', motif: null }))
  afterEach(() => vi.unstubAllGlobals())

  it('crée un employé ; le réglage des feuilles de caisse reste au superviseur', async () => {
    const crees: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/personnel/stations/s-nia/employes' && methode === 'POST') {
        crees.push(corps)
        return { status: 201, corps: MOUSSA }
      }
      if (chemin === '/personnel/stations/s-nia') return { status: 200, corps: personnel(crees.length ? [MOUSSA, AWA] : [AWA]) }
      return undefined
    })
    afficher('/personnel')
    const u = userEvent.setup()

    expect(await screen.findByRole('button', { name: 'Awa Ndiaye' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Feuilles de caisse par pompiste' })).toBeChecked()
    expect(screen.getByRole('switch', { name: 'Feuilles de caisse par pompiste' })).toBeDisabled()
    expect(screen.getByText(/Ce réglage appartient au superviseur/)).toBeInTheDocument()

    await u.click(screen.getByRole('button', { name: 'Nouvel employé' }))
    const dialogue = await screen.findByRole('dialog')
    expect(within(dialogue).getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
    await u.type(within(dialogue).getByLabelText(/Prénom/), 'Moussa')
    await u.type(within(dialogue).getByLabelText(/^Nom/), 'Fall')
    await u.type(within(dialogue).getByLabelText('Matricule'), 'nia-02')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() =>
      expect(crees).toEqual([
        {
          prenom: 'Moussa',
          nom: 'Fall',
          matricule: 'nia-02',
          telephone: null,
          fonction: 'POMPISTE',
          dateEntree: null,
          dateSortie: null,
          identifiant: null,
          actif: true,
        },
      ]),
    )
    expect(await screen.findByRole('button', { name: 'Moussa Fall' })).toBeInTheDocument()
  })

  it('planifie un poste puis reconduit la semaine avec rotation', async () => {
    const semaine = lundi(new Date().toISOString().slice(0, 10))
    const enregistres: unknown[] = []
    const reconductions: unknown[] = []
    const planning = (du: string): PlanningSemaine => ({
      stationId: 's-nia',
      du,
      au: ajouterJours(du, 6),
      modifiable: true,
      employes: [AWA],
      creneaux: enregistres.length ? [{ date: du, poste: 'MATIN', employeId: 'e-awa' }] : [],
    })
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/personnel/stations/s-nia') return { status: 200, corps: personnel([AWA]) }
      if (chemin === '/personnel/stations/s-nia/planning/reconduction') {
        reconductions.push(corps)
        return { status: 200, corps: planning(ajouterJours(semaine, 7)) }
      }
      if (chemin === '/personnel/stations/s-nia/planning' && methode === 'PUT') {
        enregistres.push(corps)
        return { status: 200, corps: planning(semaine) }
      }
      if (chemin.startsWith('/personnel/stations/s-nia/planning?du=')) return { status: 200, corps: planning(chemin.split('du=')[1]!) }
      return undefined
    })
    afficher('/personnel?onglet=planning')
    const u = userEvent.setup()

    const ligne = (await screen.findByText('Awa Ndiaye')).closest('tr')!
    expect(screen.getByRole('button', { name: 'Reconduire' })).toBeDisabled()
    await u.click(within(ligne).getAllByRole('combobox')[0]!)
    await u.click(await screen.findByRole('option', { name: 'Matin' }))
    expect(screen.getByText('Planning non enregistré.')).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Enregistrer le planning' }))
    await waitFor(() => expect(enregistres).toEqual([{ du: semaine, creneaux: [{ employeId: 'e-awa', date: semaine, poste: 'MATIN' }] }]))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Reconduire' })).toBeEnabled())
    await u.click(screen.getByRole('button', { name: 'Reconduire' }))
    const dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByRole('checkbox', { name: /Rotation/ }))
    await u.click(within(dialogue).getByRole('button', { name: 'Reconduire' }))
    await waitFor(() => expect(reconductions).toEqual([{ du: semaine, semaines: 1, rotation: true }]))
  })

  it('affiche les écarts de caisse du mois par pompiste', async () => {
    const ecarts: EcartsStation = {
      stationId: 's-nia',
      mois: '2026-11',
      feuilles: 2,
      du: 755000,
      remis: 753000,
      manquants: 2500,
      excedents: 500,
      net: -2000,
      lignes: [
        { employeId: 'e-awa', employe: 'Awa Ndiaye', fonction: 'POMPISTE', actif: true, feuilles: 1, du: 377500, remis: 375000, manquants: 2500, excedents: 0, net: -2500 },
        { employeId: 'e-moussa', employe: 'Moussa Fall', fonction: 'POMPISTE', actif: true, feuilles: 1, du: 377500, remis: 378000, manquants: 0, excedents: 500, net: 500 },
      ],
    }
    installerFauxServeur(({ chemin }) => {
      if (chemin === '/auth/rafraichir') return { status: 200, corps: sessionTest('j', GERANT) }
      if (chemin === '/stations') return { status: 200, corps: [STATION] }
      if (chemin === '/personnel/stations/s-nia') return { status: 200, corps: personnel([AWA, MOUSSA]) }
      if (chemin.startsWith('/personnel/stations/s-nia/ecarts?mois=')) return { status: 200, corps: ecarts }
      return undefined
    })
    afficher('/personnel?onglet=ecarts')

    expect(await screen.findByTestId('ecart-Awa Ndiaye')).toHaveTextContent('− 2 500 F')
    expect(screen.getByTestId('ecart-Moussa Fall')).toHaveTextContent('+ 500 F')
    expect(screen.getByText('− 2 000 F')).toBeInTheDocument()
  })

  it('calcule le lundi de la semaine et écrit les écarts', () => {
    expect(lundi('2026-11-10')).toBe('2026-11-09')
    expect(lundi('2026-11-15')).toBe('2026-11-09')
    expect(lundi('2026-11-09')).toBe('2026-11-09')
    expect(ajouterJours('2026-12-29', 7)).toBe('2027-01-05')
    expect(formaterEcart(0)).toBe('0 F')
  })
})
