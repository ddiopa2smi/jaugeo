import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  TableauReseau,
  VueDirection,
  Client,
  CreditJournee,
  FicheClient,
  Releve,
  SyntheseStocks,
  SyntheseVentes,
  AnalyseMensuelle,
  ArreteCaisse,
  DepenseAValider,
  DepensesJournee,
  LivreCaisse,
  Manquant,
  NatureDepense,
  RecetteJournee,
  SituationTresorerie,
  Versement,
  ComplementCiterne,
  SituationStock,
  StockJournee,
  Bareme,
  ChangementPrix,
  DetailJournee,
  Equipements,
  Evenement,
  LigneGrille,
  ListeJournees,
  PageAudit,
  PeriodePrix,
  Produit,
  Region,
  Role,
  Station,
  Utilisateur,
} from './types'

export const cles = {
  regions: ['regions'] as const,
  stations: ['stations'] as const,
  equipements: (id: string) => ['stations', id, 'equipements'] as const,
  produits: ['produits'] as const,
  utilisateurs: ['utilisateurs'] as const,
  roles: ['roles'] as const,
  audit: (filtres: object) => ['audit', filtres] as const,
  tablesAudit: ['audit', 'tables'] as const,
  prix: ['prix'] as const,
  grillePrix: ['prix', 'grille'] as const,
  changementsPrix: ['prix', 'changements'] as const,
  prixStation: (id: string) => ['prix', 'station', id] as const,
  bareme: (cuveId: string) => ['baremes', cuveId] as const,
  evenements: (stationId: string) => ['stations', stationId, 'evenements'] as const,
  journees: ['journees'] as const,
  journeesStation: (stationId: string) => ['journees', 'station', stationId] as const,
  journee: (id: string) => ['journees', id] as const,
  stockJournee: (id: string) => ['journees', id, 'stock'] as const,
  stockMensuel: (stationId: string, mois: string) => ['stock', 'mensuel', stationId, mois] as const,
  situationStock: ['stock', 'situation'] as const,
  tableauReseau: (date: string) => ['tableau-bord', 'reseau', date] as const,
  vueDirection: (date: string) => ['tableau-bord', 'direction', date] as const,
  complements: (statut: string) => ['stock', 'complements', statut] as const,
  naturesDepense: ['depenses', 'natures'] as const,
  depensesJournee: (id: string) => ['journees', id, 'depenses'] as const,
  depensesAValider: ['depenses', 'a-valider'] as const,
  recette: (id: string) => ['journees', id, 'tresorerie'] as const,
  tresorerie: ['tresorerie'] as const,
  livreCaisse: (stationId: string, mois: string) => ['tresorerie', 'caisse', stationId, mois] as const,
  versements: (stationId: string, mois: string) => ['tresorerie', 'versements', stationId, mois] as const,
  arretes: (stationId: string) => ['tresorerie', 'arretes', stationId] as const,
  manquants: (statut: string) => ['tresorerie', 'manquants', statut] as const,
  situationTresorerie: ['tresorerie', 'situation'] as const,
  clients: ['clients'] as const,
  ficheClient: (id: string) => ['clients', id] as const,
  releve: (id: string, stationId: string, du: string, au: string) => ['clients', id, 'releve', stationId, du, au] as const,
  creditJournee: (id: string) => ['journees', id, 'credit'] as const,
  syntheses: ['syntheses'] as const,
  syntheseVentes: (mois: string) => ['syntheses', 'ventes', mois] as const,
  syntheseStocks: (mois: string) => ['syntheses', 'stocks', mois] as const,
}

export const useRegions = () => useQuery({ queryKey: cles.regions, queryFn: () => api.get<Region[]>('/regions'), staleTime: 10 * 60_000 })

export const useStations = () => useQuery({ queryKey: cles.stations, queryFn: () => api.get<Station[]>('/stations') })

export const useEquipements = (id: string) =>
  useQuery({ queryKey: cles.equipements(id), queryFn: () => api.get<Equipements>(`/stations/${id}/equipements`) })

export const useProduits = () => useQuery({ queryKey: cles.produits, queryFn: () => api.get<Produit[]>('/produits') })

export const useUtilisateurs = () => useQuery({ queryKey: cles.utilisateurs, queryFn: () => api.get<Utilisateur[]>('/utilisateurs') })

export const useRoles = () => useQuery({ queryKey: cles.roles, queryFn: () => api.get<Role[]>('/roles'), staleTime: 10 * 60_000 })

export interface FiltresAudit {
  table: string
  utilisateur: string
  page: number
  taille: number
}

export const useAudit = (f: FiltresAudit) =>
  useQuery({
    queryKey: cles.audit(f),
    queryFn: () => {
      const p = new URLSearchParams({ page: String(f.page), taille: String(f.taille) })
      if (f.table) p.set('table', f.table)
      if (f.utilisateur.trim()) p.set('utilisateur', f.utilisateur.trim())
      return api.get<PageAudit>(`/audit?${p}`)
    },
    placeholderData: (precedent) => precedent,
  })

export const useTablesAudit = () => useQuery({ queryKey: cles.tablesAudit, queryFn: () => api.get<string[]>('/audit/tables') })

export const useGrillePrix = () => useQuery({ queryKey: cles.grillePrix, queryFn: () => api.get<LigneGrille[]>('/prix/grille') })

export const useChangementsPrix = () =>
  useQuery({ queryKey: cles.changementsPrix, queryFn: () => api.get<ChangementPrix[]>('/prix/changements') })

export const usePrixStation = (id: string) =>
  useQuery({ queryKey: cles.prixStation(id), queryFn: () => api.get<PeriodePrix[]>(`/stations/${id}/prix`) })

export const useBareme = (cuveId: string) =>
  useQuery({ queryKey: cles.bareme(cuveId), queryFn: () => api.get<Bareme>(`/cuves/${cuveId}/bareme`) })

export const useEvenements = (stationId: string) =>
  useQuery({ queryKey: cles.evenements(stationId), queryFn: () => api.get<Evenement[]>(`/stations/${stationId}/evenements`) })

export const useJourneesStation = (stationId: string | null) =>
  useQuery({
    queryKey: cles.journeesStation(stationId ?? ''),
    queryFn: () => api.get<ListeJournees>(`/stations/${stationId}/journees`),
    enabled: !!stationId,
  })

export const useJournee = (id: string) => useQuery({ queryKey: cles.journee(id), queryFn: () => api.get<DetailJournee>(`/journees/${id}`) })

export const useStockJournee = (id: string) =>
  useQuery({ queryKey: cles.stockJournee(id), queryFn: () => api.get<StockJournee>(`/journees/${id}/stock`) })

export const useStockMensuel = (stationId: string | null, mois: string) =>
  useQuery({
    queryKey: cles.stockMensuel(stationId ?? '', mois),
    queryFn: () => api.get<AnalyseMensuelle>(`/stations/${stationId}/stock?mois=${mois}`),
    enabled: !!stationId && /^\d{4}-\d{2}$/.test(mois),
  })

export const useSituationStock = () =>
  useQuery({ queryKey: cles.situationStock, queryFn: () => api.get<SituationStock>('/stock/situation') })

export const useComplements = (statut: string) =>
  useQuery({
    queryKey: cles.complements(statut),
    queryFn: () => api.get<ComplementCiterne[]>(`/stock/complements${statut ? `?statut=${statut}` : ''}`),
  })

export const useNaturesDepense = () =>
  useQuery({ queryKey: cles.naturesDepense, queryFn: () => api.get<NatureDepense[]>('/depenses/natures'), staleTime: 5 * 60_000 })

export const useDepensesJournee = (id: string) =>
  useQuery({ queryKey: cles.depensesJournee(id), queryFn: () => api.get<DepensesJournee>(`/journees/${id}/depenses`) })

export const useDepensesAValider = (actif = true) =>
  useQuery({ queryKey: cles.depensesAValider, queryFn: () => api.get<DepenseAValider[]>('/depenses/a-valider'), enabled: actif })

export const useRecette = (id: string) =>
  useQuery({ queryKey: cles.recette(id), queryFn: () => api.get<RecetteJournee>(`/journees/${id}/tresorerie`) })

const moisValide = (mois: string) => /^\d{4}-\d{2}$/.test(mois)

export const useLivreCaisse = (stationId: string | null, mois: string) =>
  useQuery({
    queryKey: cles.livreCaisse(stationId ?? '', mois),
    queryFn: () => api.get<LivreCaisse>(`/stations/${stationId}/caisse?mois=${mois}`),
    enabled: !!stationId && moisValide(mois),
  })

/** Sans station : les versements à vérifier de tout le périmètre. */
export const useVersements = (stationId: string | null, mois: string) =>
  useQuery({
    queryKey: cles.versements(stationId ?? '', stationId ? mois : ''),
    queryFn: () => api.get<Versement[]>(stationId ? `/tresorerie/versements?station=${stationId}&mois=${mois}` : '/tresorerie/versements'),
    enabled: !stationId || moisValide(mois),
  })

export const useArretes = (stationId: string | null) =>
  useQuery({
    queryKey: cles.arretes(stationId ?? ''),
    queryFn: () => api.get<ArreteCaisse[]>(`/stations/${stationId}/arretes`),
    enabled: !!stationId,
  })

export const useManquants = (statut: string) =>
  useQuery({
    queryKey: cles.manquants(statut),
    queryFn: () => api.get<Manquant[]>(`/tresorerie/manquants${statut ? `?statut=${statut}` : ''}`),
  })

export const useSituationTresorerie = () =>
  useQuery({ queryKey: cles.situationTresorerie, queryFn: () => api.get<SituationTresorerie>('/tresorerie/situation') })

/**
 * Toute saisie annexe (jaugeage, réception, dépense) incrémente la version de la journée :
 * on rafraîchit la fiche et les autres sections, sauf celle dont la réponse vient d'être posée en cache.
 */
export function rafraichirJournee(queryClient: QueryClient, journeeId: string, sauf?: readonly unknown[]) {
  const garder = sauf ? JSON.stringify(sauf) : null
  return queryClient.invalidateQueries({
    queryKey: cles.journee(journeeId),
    predicate: (q) => JSON.stringify(q.queryKey) !== garder,
  })
}
/** Après toute écriture de trésorerie : recettes des journées, livres de caisse et situation changent. */
export function useRafraichirTresorerie() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: cles.tresorerie })
    queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] === 'journees' && q.queryKey[2] === 'tresorerie' })
  }
}

export const useClients = () => useQuery({ queryKey: cles.clients, queryFn: () => api.get<Client[]>('/clients') })

export const useFicheClient = (id: string | null) =>
  useQuery({ queryKey: cles.ficheClient(id ?? ''), queryFn: () => api.get<FicheClient>(`/clients/${id}`), enabled: !!id })

export const useReleve = (id: string, stationId: string | null, du: string, au: string) =>
  useQuery({
    queryKey: cles.releve(id, stationId ?? '', du, au),
    queryFn: () => {
      const p = new URLSearchParams({ station: stationId! })
      if (du) p.set('du', du)
      if (au) p.set('au', au)
      return api.get<Releve>(`/clients/${id}/releve?${p}`)
    },
    enabled: !!stationId,
  })

export const useCreditJournee = (id: string) =>
  useQuery({ queryKey: cles.creditJournee(id), queryFn: () => api.get<CreditJournee>(`/journees/${id}/credit`) })

export const useSyntheseVentes = (mois: string) =>
  useQuery({
    queryKey: cles.syntheseVentes(mois),
    queryFn: () => api.get<SyntheseVentes>(`/syntheses/ventes?mois=${mois}`),
    enabled: moisValide(mois),
  })

export const useSyntheseStocks = (mois: string) =>
  useQuery({
    queryKey: cles.syntheseStocks(mois),
    queryFn: () => api.get<SyntheseStocks>(`/syntheses/stocks?mois=${mois}`),
    enabled: moisValide(mois),
  })
/** Tableau de bord du réseau au jour donné (permission TABLEAU_BORD_RESEAU). */
export const useVueDirection = (date: string) =>
  useQuery({
    queryKey: cles.vueDirection(date),
    queryFn: () => api.get<VueDirection>(`/tableau-bord/direction?date=${date}`),
  })

export const useTableauReseau = (date: string) =>
  useQuery({
    queryKey: cles.tableauReseau(date),
    queryFn: () => api.get<TableauReseau>(`/tableau-bord/reseau?date=${date}`),
  })