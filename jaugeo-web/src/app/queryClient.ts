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
      // Écritures toujours tentées, même si le navigateur se croit hors ligne : une coupure est détectée à l'envoi
      // et les saisies de journée sont alors gardées dans la tablette (au lieu d'une mutation suspendue).
      mutations: { networkMode: 'always' },
    },
  })
}
