import { QueryClient } from '@tanstack/react-query'
import { ErreurApi } from '../api/client'

export function creerQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Inutile de réessayer une erreur fonctionnelle (4xx) : seules les pannes réseau/serveur le méritent.
        retry: (echecs, erreur) =>
          echecs < 2 && !(erreur instanceof ErreurApi && erreur.status >= 400 && erreur.status < 500),
      },
    },
  })
}
