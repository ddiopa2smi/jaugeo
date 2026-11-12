import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Reception, StockJournee } from '../../api/types'
import { Fournisseurs } from '../../app/Fournisseurs'
import { creerQueryClient } from '../../app/queryClient'
import { installerFauxServeur } from '../../test/fauxServeur'
import { SectionStock } from './SectionStock'

const reception = (surcharge: Partial<Reception> = {}): Reception => ({
  id: 'r1',
  cuveId: 'c1',
  cuveCode: 'C1',
  produitCode: 'GASOIL',
  numeroBl: 'BL-77',
  fournisseur: 'Dépôt SAR',
  transporteur: 'Trans Sahel',
  chauffeur: null,
  immatriculation: null,
  scelles: null,
  quantiteBl: 10000,
  jaugeAvant: null,
  jaugeApres: null,
  quantiteRecue: 10000,
  temperatureC: null,
  densite: null,
  observation: null,
  ecartLivraison: 0,
  ecartLivraisonPct: 0,
  complementLitres: null,
  complementStatut: null,
  par: 'Gérant',
  le: '2026-08-16T10:00:00Z',
  quantiteBl15: null,
  facteur15: null,
  quantiteRecue15: null,
  compareeA15: false,
  scellesIntacts: true,
  imputation: 'FOURNISSEUR',
  livraisonId: null,
  tourneeNumero: null,
  tolerancePct: 0.3,
  ...surcharge,
})

function monter(fournisseurs?: string[]) {
  let version = 3
  let receptions: Reception[] = [reception()]
  const ecritures: { methode: string; chemin: string; corps: unknown }[] = []
  const stock = (): StockJournee => ({
    journeeId: 'j1',
    stationId: 's-nia',
    stationCode: 'NIA',
    date: '2026-08-16',
    statut: 'BROUILLON',
    version,
    modifiable: true,
    receptions,
    anomalies: 0,
    toleranceTransportPct: 0.3,
    livraisonsAttendues: [],
    fournisseurs,
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
        receptions: 10000,
        sorties: 0,
        stockTheorique: 20000,
        cloture: null,
        ecartLitres: null,
        ecartPctJour: null,
        prixUnitaire: 755,
        ecartValeur: null,
        cumulSortiesMois: null,
        cumulEcartMois: null,
        ecartPctMois: null,
        tolerancePct: 0.5,
        niveau: null,
        observationRequise: false,
        anomalies: [],
        seuilEauMm: 20,
        alertes: [],
      },
    ],
  })
  installerFauxServeur(({ methode, chemin, corps }) => {
    if (chemin === '/journees/j1/stock') return { status: 200, corps: stock() }
    if (chemin.startsWith('/journees/j1/receptions/r1') && methode === 'DELETE') {
      ecritures.push({ methode, chemin, corps })
      receptions = []
      version++
      return { status: 200, corps: stock() }
    }
    if (chemin === '/journees/j1/receptions/r1' && methode === 'PUT') {
      ecritures.push({ methode, chemin, corps })
      const c = corps as { quantiteRecue: number }
      receptions = [reception({ quantiteRecue: c.quantiteRecue, ecartLivraison: c.quantiteRecue - 10000 })]
      version++
      return { status: 200, corps: stock() }
    }
    return undefined
  })
  render(
    <Fournisseurs client={creerQueryClient()}>
      <MemoryRouter>
        <SectionStock journeeId="j1" />
      </MemoryRouter>
    </Fournisseurs>,
  )
  return { ecritures }
}

describe('réceptions de la journée', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('ne supprime une réception qu’après confirmation', async () => {
    const { ecritures } = monter()
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Retirer le BL BL-77' }))
    let modale = await screen.findByRole('dialog', { name: 'Supprimer la réception ?' })
    expect(modale).toHaveTextContent('BL-77')
    expect(modale).toHaveTextContent('C1')
    await u.click(within(modale).getByRole('button', { name: 'Annuler' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(ecritures).toEqual([])
    expect(screen.getByRole('button', { name: 'Retirer le BL BL-77' })).toBeInTheDocument()

    await u.click(screen.getByRole('button', { name: 'Retirer le BL BL-77' }))
    modale = await screen.findByRole('dialog', { name: 'Supprimer la réception ?' })
    await u.click(within(modale).getByRole('button', { name: 'Supprimer' }))
    await waitFor(() => expect(ecritures).toEqual([{ methode: 'DELETE', chemin: '/journees/j1/receptions/r1?version=3', corps: undefined }]))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Retirer le BL BL-77' })).not.toBeInTheDocument())
  })

  it('corrige une réception enregistrée sans la ressaisir', async () => {
    const { ecritures } = monter()
    const u = userEvent.setup()

    await u.click(await screen.findByRole('button', { name: 'Corriger le BL BL-77' }))
    const dialogue = await screen.findByRole('dialog', { name: 'Corriger la réception du BL BL-77' })
    // Le formulaire reprend la saisie d'origine
    expect(within(dialogue).getByLabelText(/N° de BL/)).toHaveValue('BL-77')
    expect(within(dialogue).getByLabelText(/Fournisseur/)).toHaveValue('Dépôt SAR')
    expect(within(dialogue).getByLabelText('Transporteur')).toHaveValue('Trans Sahel')
    const recue = within(dialogue).getByLabelText(/Quantité reçue/)
    expect(recue).toHaveValue('10000')

    await u.clear(recue)
    await u.type(recue, '9990')
    await u.click(within(dialogue).getByRole('button', { name: 'Enregistrer la correction' }))

    await waitFor(() => expect(ecritures).toHaveLength(1))
    expect(ecritures[0]).toMatchObject({
      methode: 'PUT',
      chemin: '/journees/j1/receptions/r1',
      corps: {
        version: 3,
        cuveId: 'c1',
        numeroBl: 'BL-77',
        fournisseur: 'Dépôt SAR',
        quantiteBl: 10000,
        quantiteRecue: 9990,
        scellesIntacts: true,
        imputation: 'FOURNISSEUR',
      },
    })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('Réception corrigée.')).toBeInTheDocument()
  })

  it('choisit le fournisseur dans la liste déclarée par la logistique', async () => {
    monter(['Dépôt Senstock', 'SAR Mbao'])
    const u = userEvent.setup()

    // Une réception ancienne garde son fournisseur, même absent de la liste
    await u.click(await screen.findByRole('button', { name: 'Corriger le BL BL-77' }))
    let dialogue = await screen.findByRole('dialog', { name: 'Corriger la réception du BL BL-77' })
    const champ = within(dialogue).getByRole('combobox', { name: /Fournisseur/ })
    expect(champ).toHaveTextContent('Dépôt SAR')
    await u.click(champ)
    const options = (await screen.findAllByRole('option')).map((o) => o.textContent)
    expect(options).toEqual(['Dépôt Senstock', 'SAR Mbao', 'Dépôt SAR (hors liste)'])
    await u.keyboard('{Escape}')
    await u.click(within(dialogue).getByRole('button', { name: 'Annuler' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    // Nouvelle réception : pas de saisie libre, seulement les fournisseurs déclarés
    await u.click(screen.getByRole('button', { name: /Ajouter une réception/ }))
    dialogue = await screen.findByRole('dialog')
    const nouveau = within(dialogue).getByRole('combobox', { name: /Fournisseur/ })
    await u.click(nouveau)
    expect((await screen.findAllByRole('option')).map((o) => o.textContent)).toEqual(['Dépôt Senstock', 'SAR Mbao'])
    await u.click(screen.getByRole('option', { name: 'SAR Mbao' }))
    expect(nouveau).toHaveTextContent('SAR Mbao')
  })
})
