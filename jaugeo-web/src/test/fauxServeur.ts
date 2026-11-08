import { vi } from 'vitest'
import type { Permission, ReponseSession, UtilisateurConnecte } from '../api/types'

type Gestionnaire = (requete: { methode: string; chemin: string; corps: unknown; jeton: string | null }) =>
  | { status: number; corps?: unknown }
  | undefined

/** Remplace fetch par un faux serveur : chaque appel est journalisé et routé vers le gestionnaire. */
export function installerFauxServeur(gestionnaire: Gestionnaire) {
  const appels: { methode: string; chemin: string; jeton: string | null }[] = []
  const faux = vi.fn(async (url: string, init?: RequestInit) => {
    const methode = init?.method ?? 'GET'
    const chemin = url.replace(/^\/api\/v1/, '')
    const entetes = (init?.headers ?? {}) as Record<string, string>
    const jeton = entetes.Authorization?.replace('Bearer ', '') ?? null
    const corps = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    appels.push({ methode, chemin, jeton })
    const r = gestionnaire({ methode, chemin, corps, jeton }) ?? { status: 404, corps: { code: 'INTROUVABLE', detail: 'Introuvable' } }
    return new Response(r.corps === undefined ? null : JSON.stringify(r.corps), {
      status: r.status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', faux)
  return { appels, faux }
}

export function utilisateurTest(surcharge: Partial<UtilisateurConnecte> = {}): UtilisateurConnecte {
  return {
    id: '0190a000-0000-7000-8000-000000000001',
    identifiant: 'admin',
    nomComplet: 'Awa Ndiaye',
    role: 'ADMINISTRATEUR',
    roleLibelle: 'Administrateur',
    permissions: ['REFERENTIEL_CONSULTER', 'REFERENTIEL_GERER', 'UTILISATEUR_GERER', 'AUDIT_CONSULTER'] as Permission[],
    perimetre: 'RESEAU',
    stationIds: [],
    doitChangerMotDePasse: false,
    ...surcharge,
  }
}

export function sessionTest(jeton: string, utilisateur = utilisateurTest()): ReponseSession {
  return { jetonAcces: jeton, expireDans: 900, utilisateur }
}

export const nonAuthentifie = { status: 401, corps: { code: 'NON_AUTHENTIFIE', detail: 'Session absente ou expirée.' } }
