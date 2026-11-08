import { useSession } from '../auth/session'
import type { Probleme, ReponseSession } from './types'

const BASE = '/api/v1'

/** Erreur d'appel API, portant le code métier renvoyé par le serveur (RFC 9457). */
export class ErreurApi extends Error {
  readonly status: number
  readonly code: string
  readonly champs: Record<string, string>

  constructor(status: number, probleme: Probleme) {
    super(probleme.detail ?? probleme.title ?? messageParDefaut(status))
    this.name = 'ErreurApi'
    this.status = status
    this.code = probleme.code ?? `HTTP_${status}`
    this.champs = probleme.champs ?? {}
  }
}

function messageParDefaut(status: number): string {
  if (status === 0) return 'Serveur injoignable. Vérifiez votre connexion.'
  if (status >= 500) return 'Erreur technique du serveur. Réessayez dans un instant.'
  return `Requête refusée (${status}).`
}

async function lireProbleme(reponse: Response): Promise<Probleme> {
  try {
    return (await reponse.json()) as Probleme
  } catch {
    return {}
  }
}

async function envoyer(methode: string, chemin: string, corps: unknown, jeton: string | null): Promise<Response> {
  const entetes: Record<string, string> = { Accept: 'application/json' }
  if (corps !== undefined) entetes['Content-Type'] = 'application/json'
  if (jeton) entetes.Authorization = `Bearer ${jeton}`
  try {
    return await fetch(BASE + chemin, {
      method: methode,
      headers: entetes,
      body: corps === undefined ? undefined : JSON.stringify(corps),
      credentials: 'same-origin',
    })
  } catch {
    throw new ErreurApi(0, {})
  }
}

let rafraichissementEnCours: Promise<ReponseSession> | null = null

async function appelerRafraichir(): Promise<ReponseSession> {
  const reponse = await envoyer('POST', '/auth/rafraichir', undefined, null)
  if (!reponse.ok) throw new ErreurApi(reponse.status, await lireProbleme(reponse))
  return (await reponse.json()) as ReponseSession
}

/**
 * Obtient un nouveau jeton d'accès à partir du cookie HttpOnly.
 * Un seul rafraîchissement à la fois : dans l'onglet (promesse partagée) et entre onglets
 * (Web Locks), car le serveur fait tourner le jeton et traite la réutilisation d'un ancien
 * jeton comme un vol de session.
 */
export function rafraichirSession(): Promise<ReponseSession> {
  if (!rafraichissementEnCours) {
    const verrous = typeof navigator !== 'undefined' ? navigator.locks : undefined
    const execution = verrous
      ? verrous.request('jaugeo-rafraichissement', () => appelerRafraichir())
      : appelerRafraichir()
    rafraichissementEnCours = execution
      .then((session) => {
        useSession.getState().ouvrir(session)
        return session
      })
      .finally(() => {
        rafraichissementEnCours = null
      })
  }
  return rafraichissementEnCours
}

async function requete<T>(methode: string, chemin: string, corps?: unknown): Promise<T> {
  const estAuth = chemin.startsWith('/auth/connexion') || chemin.startsWith('/auth/rafraichir')
  let reponse = await envoyer(methode, chemin, corps, useSession.getState().jeton)

  if (reponse.status === 401 && !estAuth) {
    try {
      const session = await rafraichirSession()
      reponse = await envoyer(methode, chemin, corps, session.jetonAcces)
    } catch {
      useSession.getState().fermer('Votre session a expiré. Reconnectez-vous.')
      throw new ErreurApi(401, { code: 'SESSION_EXPIREE', detail: 'Session expirée. Reconnectez-vous.' })
    }
  }

  if (!reponse.ok) {
    const probleme = await lireProbleme(reponse)
    if (reponse.status === 403 && probleme.code === 'MOT_DE_PASSE_A_CHANGER') {
      useSession.getState().exigerChangementMotDePasse()
    }
    throw new ErreurApi(reponse.status, probleme)
  }
  if (reponse.status === 204) return undefined as T
  const texte = await reponse.text()
  return (texte ? JSON.parse(texte) : undefined) as T
}

export const api = {
  get: <T>(chemin: string) => requete<T>('GET', chemin),
  post: <T>(chemin: string, corps?: unknown) => requete<T>('POST', chemin, corps ?? {}),
  put: <T>(chemin: string, corps: unknown) => requete<T>('PUT', chemin, corps),
  delete: <T>(chemin: string) => requete<T>('DELETE', chemin),
}

/** Message lisible pour n'importe quelle erreur. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof ErreurApi) {
    if (erreur.code === 'VERSION_OBSOLETE') {
      return "Cette fiche a été modifiée par quelqu'un d'autre entre-temps. Rechargez-la puis refaites votre modification."
    }
    return erreur.message
  }
  if (erreur instanceof Error) return erreur.message
  return 'Erreur inattendue.'
}
