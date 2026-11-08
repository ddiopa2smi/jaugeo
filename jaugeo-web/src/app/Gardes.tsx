import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import { useEffect, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { rafraichirSession } from '../api/client'
import type { Permission } from '../api/types'
import { aPermission, useSession } from '../auth/session'
import { Chargement } from '../shared/composants'

/** Au chargement, tente de restaurer la session à partir du cookie de rafraîchissement. */
export function RestaurationSession({ children }: { children: ReactNode }) {
  const statut = useSession((e) => e.statut)
  useEffect(() => {
    if (useSession.getState().statut === 'initialisation') {
      rafraichirSession().catch(() => useSession.getState().fermer())
    }
  }, [])
  if (statut === 'initialisation') {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <Chargement texte="Ouverture de Jaugeo…" />
      </Box>
    )
  }
  return children
}

export const CHEMIN_CHANGEMENT_MDP = '/changer-mot-de-passe'

/** Exige une session ouverte ; impose le changement de mot de passe s'il est requis. */
export function RouteProtegee() {
  const utilisateur = useSession((e) => e.utilisateur)
  const location = useLocation()
  if (!utilisateur) {
    return <Navigate to="/connexion" replace state={{ depuis: location.pathname + location.search }} />
  }
  if (utilisateur.doitChangerMotDePasse && location.pathname !== CHEMIN_CHANGEMENT_MDP) {
    return <Navigate to={CHEMIN_CHANGEMENT_MDP} replace />
  }
  return <Outlet />
}

export function RoutePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const autorise = useSession((e) => aPermission(e.utilisateur, permission))
  if (!autorise) {
    return (
      <Alert severity="warning">
        Vous n'avez pas accès à cette page. Si vous pensez que c'est une erreur, contactez l'administrateur.
      </Alert>
    )
  }
  return children
}
