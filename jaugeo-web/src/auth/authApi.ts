import { api } from '../api/client'
import type { ReponseSession } from '../api/types'
import { useSession } from './session'

export async function connecter(identifiant: string, motDePasse: string): Promise<ReponseSession> {
  const session = await api.post<ReponseSession>('/auth/connexion', { identifiant, motDePasse })
  useSession.getState().ouvrir(session)
  return session
}

export async function deconnecter(): Promise<void> {
  try {
    await api.post<void>('/auth/deconnexion')
  } finally {
    useSession.getState().fermer()
  }
}

export async function changerMotDePasse(ancienMotDePasse: string, nouveauMotDePasse: string): Promise<void> {
  const session = await api.post<ReponseSession>('/auth/mot-de-passe', { ancienMotDePasse, nouveauMotDePasse })
  useSession.getState().ouvrir(session)
}

/** Règles identiques à PolitiqueMotDePasse côté serveur. */
export function problemesMotDePasse(mdp: string, identifiant: string): string[] {
  const problemes: string[] = []
  if (mdp.length < 10) problemes.push('au moins 10 caractères')
  if (!/\p{Ll}/u.test(mdp)) problemes.push('une minuscule')
  if (!/\p{Lu}/u.test(mdp)) problemes.push('une majuscule')
  if (!/\p{Nd}/u.test(mdp)) problemes.push('un chiffre')
  if (identifiant && mdp.toLowerCase().includes(identifiant.toLowerCase())) {
    problemes.push("ne pas contenir l'identifiant")
  }
  return problemes
}
