import { create } from 'zustand'
import type { Permission, ReponseSession, UtilisateurConnecte } from '../api/types'

export type StatutSession = 'initialisation' | 'connecte' | 'deconnecte'

interface EtatSession {
  /** Jeton d'accès : gardé en mémoire uniquement (jamais dans localStorage). */
  jeton: string | null
  utilisateur: UtilisateurConnecte | null
  statut: StatutSession
  /** Message affiché sur l'écran de connexion après une expiration de session. */
  motif: string | null
  ouvrir: (reponse: ReponseSession) => void
  fermer: (motif?: string | null) => void
  exigerChangementMotDePasse: () => void
}

export const useSession = create<EtatSession>()((set) => ({
  jeton: null,
  utilisateur: null,
  statut: 'initialisation',
  motif: null,
  ouvrir: (reponse) =>
    set({ jeton: reponse.jetonAcces, utilisateur: reponse.utilisateur, statut: 'connecte', motif: null }),
  fermer: (motif = null) => set({ jeton: null, utilisateur: null, statut: 'deconnecte', motif }),
  exigerChangementMotDePasse: () =>
    set((etat) =>
      etat.utilisateur ? { utilisateur: { ...etat.utilisateur, doitChangerMotDePasse: true } } : etat,
    ),
}))

export function aPermission(utilisateur: UtilisateurConnecte | null, permission: Permission): boolean {
  return utilisateur?.permissions.includes(permission) ?? false
}

/** Hook : vrai si l'utilisateur connecté détient la permission. */
export function usePermission(permission: Permission): boolean {
  return useSession((etat) => aPermission(etat.utilisateur, permission))
}
