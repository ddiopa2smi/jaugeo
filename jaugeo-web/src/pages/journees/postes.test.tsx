import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Employe, PostesJournee } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { creerQueryClient } from '../../app/queryClient'
import { installerFauxServeur } from '../../test/fauxServeur'
import { SectionPostes } from './SectionPostes'

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
  dateEntree: null,
  dateSortie: null,
  identifiant: null,
  actif: true,
  version: 0,
})

const ZERO = { litres: 0, du: 0, especes: 0, electronique: 0, bons: 0, cartes: 0, remis: 0, ecart: 0 }

const VIDE: PostesJournee = {
  journeeId: 'j1',
  version: 5,
  statut: 'BROUILLON',
  actif: true,
  modifiable: true,
  pistolets: [
    { pistoletId: 'g1', code: 'G1', pompeCode: 'P1', produitCode: 'GASOIL', indexDepart: 1000, indexArrivee: 1600, litresJournee: 600, prixUnitaire: 755 },
    { pistoletId: 'g2', code: 'G2', pompeCode: 'P1', produitCode: 'GASOIL', indexDepart: 5000, indexArrivee: 5400, litresJournee: 400, prixUnitaire: 755 },
  ],
  employes: [employe('e-awa', 'Awa', 'Ndiaye'), employe('e-moussa', 'Moussa', 'Fall')],
  planning: [{ date: '2026-11-09', poste: 'MATIN', employeId: 'e-awa' }],
  postes: [],
  totaux: ZERO,
  problemes: ["Aucune feuille de caisse n'est saisie : relevez les index à chaque changement de poste."],
}

const AVEC_MATIN: PostesJournee = {
  ...VIDE,
  version: 6,
  postes: [
    {
      poste: 'MATIN',
      libelle: 'Matin',
      observation: null,
      du: 377500,
      remis: 375000,
      ecart: -2500,
      feuilles: [
        {
          id: 'f1',
          employeId: 'e-awa',
          employe: 'Awa Ndiaye',
          litres: 500,
          du: 377500,
          especes: 370000,
          electronique: 5000,
          bons: 0,
          cartes: 0,
          remis: 375000,
          ecart: -2500,
          observation: null,
          lignes: [
            { pistoletId: 'g1', pistoletCode: 'G1', pompeCode: 'P1', produitCode: 'GASOIL', indexDebut: 1000, indexFin: 1300, remiseCuve: 0, litres: 300, prixUnitaire: 755, montant: 226500, anomalie: null },
            { pistoletId: 'g2', pistoletCode: 'G2', pompeCode: 'P1', produitCode: 'GASOIL', indexDebut: 5000, indexFin: 5200, remiseCuve: 0, litres: 200, prixUnitaire: 755, montant: 151000, anomalie: null },
          ],
        },
      ],
    },
  ],
  totaux: { litres: 500, du: 377500, especes: 370000, electronique: 5000, bons: 0, cartes: 0, remis: 375000, ecart: -2500 },
  problemes: ['Pistolet G1 : 300 L sur les feuilles de caisse pour 600 L au relevé de la journée.'],
}

function afficher() {
  render(
    <Fournisseurs client={creerQueryClient()}>
      <MemoryRouter>
        <SectionPostes journeeId="j1" />
      </MemoryRouter>
    </Fournisseurs>,
  )
}

describe('feuilles de caisse par pompiste', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('saisit le poste du matin du pompiste prévu au planning et affiche son écart', async () => {
    const puts: unknown[] = []
    installerFauxServeur(({ methode, chemin, corps }) => {
      if (chemin === '/journees/j1/postes/MATIN' && methode === 'PUT') {
        puts.push(corps)
        return { status: 200, corps: AVEC_MATIN }
      }
      if (chemin === '/journees/j1/postes') return { status: 200, corps: puts.length ? AVEC_MATIN : VIDE }
      return undefined
    })
    afficher()
    const u = userEvent.setup()

    expect(await screen.findByTestId('problemes-postes')).toHaveTextContent("Aucune feuille de caisse n'est saisie")
    await u.click(screen.getByRole('button', { name: 'Poste du matin' }))
    const dialogue = await screen.findByRole('dialog')
    // Awa est prévue le matin : sa feuille est proposée, avec l'index de départ de la journée
    expect(within(dialogue).getByText('Awa Ndiaye')).toBeInTheDocument()
    expect(within(dialogue).getByText(/début 1 000/)).toBeInTheDocument()

    await u.type(within(dialogue).getByLabelText('Index de fin G1 (pompiste 1)'), '1300')
    await u.type(within(dialogue).getByLabelText('Index de fin G2 (pompiste 1)'), '5200')
    await u.type(within(dialogue).getByLabelText('Espèces remises (pompiste 1)'), '370000')
    await u.type(within(dialogue).getByLabelText('Wave, OM, carte (pompiste 1)'), '5000')
    const calcul = within(dialogue).getByTestId('calcul-1')
    expect(calcul).toHaveTextContent('500 L · dû 377 500 F')
    expect(calcul).toHaveTextContent('Écart − 2 500 F')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer le poste' }))

    await waitFor(() =>
      expect(puts).toEqual([
        {
          version: 5,
          observation: null,
          feuilles: [
            {
              employeId: 'e-awa',
              especes: 370000,
              electronique: 5000,
              bons: 0,
              cartes: 0,
              observation: null,
              lignes: [
                { pistoletId: 'g1', indexFin: 1300, remiseCuve: null },
                { pistoletId: 'g2', indexFin: 5200, remiseCuve: null },
              ],
            },
          ],
        },
      ]),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByTestId('ecart-MATIN-Awa Ndiaye')).toHaveTextContent('− 2 500 F')
    expect(screen.getByTestId('poste-MATIN')).toHaveTextContent('G1 1 000 → 1 300')
    // Le soir reste à saisir : la clôture est encore bloquée
    expect(screen.getByTestId('problemes-postes')).toHaveTextContent('Pistolet G1 : 300 L sur les feuilles de caisse pour 600 L')
    expect(screen.queryByRole('button', { name: 'Poste du matin' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Poste du soir' })).toBeInTheDocument()
  })

  it('refuse deux pompistes sur le même pistolet avant tout envoi', async () => {
    const { appels } = installerFauxServeur(({ chemin }) => (chemin === '/journees/j1/postes' ? { status: 200, corps: { ...VIDE, planning: [] } } : undefined))
    afficher()
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Poste du soir' }))
    const dialogue = await screen.findByRole('dialog')
    await u.click(within(dialogue).getByRole('button', { name: 'Ajouter un pompiste' }))
    const [premier, second] = within(dialogue).getAllByRole('combobox')
    await u.click(premier!)
    await u.click(await screen.findByRole('option', { name: 'Awa Ndiaye' }))
    await u.click(second!)
    await u.click(await screen.findByRole('option', { name: 'Moussa Fall' }))
    await u.type(within(dialogue).getByLabelText('Index de fin G1 (pompiste 1)'), '1300')
    await u.type(within(dialogue).getByLabelText('Index de fin G1 (pompiste 2)'), '1600')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer le poste' }))

    expect(await within(dialogue).findByText(/Le pistolet G1 est tenu par deux pompistes/)).toBeInTheDocument()
    expect(appels.filter((a) => a.methode === 'PUT')).toHaveLength(0)
  })

  it("ne s'affiche pas dans une station sans feuilles de caisse", async () => {
    const { appels } = installerFauxServeur(({ chemin }) =>
      chemin === '/journees/j1/postes' ? { status: 200, corps: { ...VIDE, actif: false, modifiable: false, problemes: [] } } : undefined,
    )
    afficher()
    await waitFor(() => expect(appels).toHaveLength(1))
    await waitFor(() => expect(screen.queryByLabelText('Feuilles de caisse par pompiste')).not.toBeInTheDocument())
  })
})
