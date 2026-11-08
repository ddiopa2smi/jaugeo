import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import type {
  Bareme,
  ChangementPrix,
  Equipements,
  Evenement,
  LigneGrille,
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
}

export const useRegions = () =>
  useQuery({ queryKey: cles.regions, queryFn: () => api.get<Region[]>('/regions'), staleTime: 10 * 60_000 })

export const useStations = () => useQuery({ queryKey: cles.stations, queryFn: () => api.get<Station[]>('/stations') })

export const useEquipements = (id: string) =>
  useQuery({ queryKey: cles.equipements(id), queryFn: () => api.get<Equipements>(`/stations/${id}/equipements`) })

export const useProduits = () => useQuery({ queryKey: cles.produits, queryFn: () => api.get<Produit[]>('/produits') })

export const useUtilisateurs = () =>
  useQuery({ queryKey: cles.utilisateurs, queryFn: () => api.get<Utilisateur[]>('/utilisateurs') })

export const useRoles = () =>
  useQuery({ queryKey: cles.roles, queryFn: () => api.get<Role[]>('/roles'), staleTime: 10 * 60_000 })

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

export const useTablesAudit = () =>
  useQuery({ queryKey: cles.tablesAudit, queryFn: () => api.get<string[]>('/audit/tables') })

export const useGrillePrix = () =>
  useQuery({ queryKey: cles.grillePrix, queryFn: () => api.get<LigneGrille[]>('/prix/grille') })

export const useChangementsPrix = () =>
  useQuery({ queryKey: cles.changementsPrix, queryFn: () => api.get<ChangementPrix[]>('/prix/changements') })

export const usePrixStation = (id: string) =>
  useQuery({ queryKey: cles.prixStation(id), queryFn: () => api.get<PeriodePrix[]>(`/stations/${id}/prix`) })

export const useBareme = (cuveId: string) =>
  useQuery({ queryKey: cles.bareme(cuveId), queryFn: () => api.get<Bareme>(`/cuves/${cuveId}/bareme`) })

export const useEvenements = (stationId: string) =>
  useQuery({ queryKey: cles.evenements(stationId), queryFn: () => api.get<Evenement[]>(`/stations/${stationId}/evenements`) })
