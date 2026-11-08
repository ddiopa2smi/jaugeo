import Dexie, { type EntityTable } from 'dexie'
import { create } from 'zustand'
import { api, ErreurApi, messageErreur } from '../api/client'
import { useSession } from '../auth/session'

/**
 * Saisie hors ligne de la tablette : une saisie de journée (index, jaugeages, clôture) faite pendant une coupure
 * est gardée dans la tablette (IndexedDB) puis envoyée au retour du réseau, dans l'ordre de saisie.
 *
 * - Chaque saisie porte une clé d'envoi (Idempotency-Key) : rejouée après une réponse perdue, elle n'est exécutée
 *   qu'une fois par le serveur.
 * - Le serveur recalcule tout et applique ses contrôles : une saisie refusée (journée modifiée entre-temps,
 *   clôture impossible...) devient un conflit affiché à l'utilisateur, et les saisies suivantes de la même journée
 *   attendent qu'il soit réglé (abandon ou nouvel essai).
 * - Les saisies d'une même journée faites hors ligne partent toutes de la même version : à l'envoi, chacune
 *   reprend la version renvoyée par la précédente. Une modification faite par quelqu'un d'autre entre-temps
 *   reste détectée (la première saisie du lot est refusée).
 */

export type TypeEnvoi = 'releves' | 'jaugeages' | 'cloture'

export interface Envoi {
  id?: number
  cle: string
  utilisateur: string
  journeeId: string
  /** « NIA · journée du 12/03/2026 » */
  journeeLibelle: string
  type: TypeEnvoi
  methode: 'POST' | 'PUT'
  chemin: string
  corps: { version: number } & Record<string, unknown>
  creeLe: string
  etat: 'EN_ATTENTE' | 'CONFLIT'
  erreur?: { code: string; message: string }
}

export const LIBELLES_ENVOI: Record<TypeEnvoi, string> = {
  releves: 'Index et observation',
  jaugeages: 'Jaugeages',
  cloture: 'Clôture',
}

class BaseHorsLigne extends Dexie {
  envois!: EntityTable<Envoi, 'id'>

  constructor() {
    super('jaugeo-hors-ligne')
    this.version(1).stores({ envois: '++id, utilisateur, journeeId' })
  }
}

const base = new BaseHorsLigne()

interface EtatHorsLigne {
  enLigne: boolean
  /** Saisies de l'utilisateur connecté, dans l'ordre d'envoi. */
  envois: Envoi[]
  synchronisation: boolean
}

export const useHorsLigne = create<EtatHorsLigne>()(() => ({
  enLigne: typeof navigator === 'undefined' ? true : navigator.onLine,
  envois: [],
  synchronisation: false,
}))

const utilisateurCourant = () => useSession.getState().utilisateur?.identifiant ?? null

export async function chargerEnvois(): Promise<void> {
  const u = utilisateurCourant()
  const envois = u ? await base.envois.where('utilisateur').equals(u).sortBy('id') : []
  useHorsLigne.setState({ envois })
}

export const estPanneReseau = (e: unknown) => e instanceof ErreurApi && e.status === 0

function nouvelleCle(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

/** Après chaque envoi réussi (rafraîchir les écrans de la journée). */
let apresEnvoi: (journeeId: string) => void = () => undefined

export type ResultatSaisie<T> = { envoye: true; resultat: T } | { envoye: false }

/**
 * Envoie une saisie de journée, ou la garde dans la tablette si le réseau est coupé. Une saisie qui suit d'autres
 * saisies encore en attente pour la même journée attend son tour.
 */
export async function envoyerSaisie<T>(saisie: {
  journeeId: string
  journeeLibelle: string
  type: TypeEnvoi
  methode: 'POST' | 'PUT'
  chemin: string
  corps: { version: number } & Record<string, unknown>
}): Promise<ResultatSaisie<T>> {
  const utilisateur = utilisateurCourant()
  if (!utilisateur) throw new ErreurApi(401, { code: 'SESSION_EXPIREE', detail: 'Session expirée. Reconnectez-vous.' })
  const envoi: Envoi = { ...saisie, cle: nouvelleCle(), utilisateur, creeLe: new Date().toISOString(), etat: 'EN_ATTENTE' }
  const enAttente = useHorsLigne.getState().envois.some((e) => e.journeeId === saisie.journeeId)
  if (!enAttente) {
    try {
      const resultat = await api.idempotent<T>(saisie.methode, saisie.chemin, saisie.corps, envoi.cle)
      return { envoye: true, resultat }
    } catch (e) {
      if (!estPanneReseau(e)) throw e
      useHorsLigne.setState({ enLigne: false })
    }
  }
  await base.envois.add(envoi)
  await chargerEnvois()
  return { envoye: false }
}

let enCours: Promise<void> | null = null

/** Envoie les saisies en attente, dans l'ordre ; s'arrête à la première coupure. */
export function synchroniser(): Promise<void> {
  enCours ??= executer().finally(() => {
    enCours = null
  })
  return enCours
}

async function executer(): Promise<void> {
  await chargerEnvois()
  const { envois } = useHorsLigne.getState()
  if (!envois.some((e) => e.etat === 'EN_ATTENTE')) return
  useHorsLigne.setState({ synchronisation: true })
  // Une journée en conflit bloque ses saisies suivantes
  const bloquees = new Set(envois.filter((e) => e.etat === 'CONFLIT').map((e) => e.journeeId))
  const versions = new Map<string, number>()
  try {
    for (const envoi of envois) {
      if (envoi.etat !== 'EN_ATTENTE' || bloquees.has(envoi.journeeId)) continue
      const version = versions.get(envoi.journeeId)
      if (version !== undefined && version !== envoi.corps.version) {
        // Corps enregistré avant l'envoi : un nouvel essai après une réponse perdue renvoie exactement le même
        envoi.corps = { ...envoi.corps, version }
        await base.envois.update(envoi.id!, { corps: envoi.corps })
      }
      try {
        const r = await api.idempotent<{ version?: number }>(envoi.methode, envoi.chemin, envoi.corps, envoi.cle)
        await base.envois.delete(envoi.id!)
        if (typeof r?.version === 'number') versions.set(envoi.journeeId, r.version)
        useHorsLigne.setState({ enLigne: true })
        apresEnvoi(envoi.journeeId)
      } catch (e) {
        if (estPanneReseau(e)) {
          useHorsLigne.setState({ enLigne: false })
          return
        }
        if (e instanceof ErreurApi && (e.status === 401 || e.code === 'MOT_DE_PASSE_A_CHANGER')) return
        const erreur = { code: e instanceof ErreurApi ? e.code : 'ERREUR', message: messageErreur(e) }
        await base.envois.update(envoi.id!, { etat: 'CONFLIT', erreur })
        bloquees.add(envoi.journeeId)
      }
    }
  } finally {
    await chargerEnvois()
    useHorsLigne.setState({ synchronisation: false })
  }
}

/** Retire une saisie (conflit réglé autrement) ; les saisies suivantes de la journée repartent. */
export async function abandonner(id: number): Promise<void> {
  await base.envois.delete(id)
  await chargerEnvois()
  void synchroniser()
}

/** Remet un conflit en attente (après une correction faite par ailleurs, ou une erreur passagère). */
export async function reessayer(id: number): Promise<void> {
  await base.envois.update(id, { etat: 'EN_ATTENTE', erreur: undefined })
  await synchroniser()
}

/**
 * Suit l'état du réseau et envoie les saisies en attente : au démarrage, au retour du réseau et toutes les
 * 30 secondes tant qu'il en reste. Renvoie la fonction d'arrêt.
 */
export function demarrerHorsLigne(rafraichir: (journeeId: string) => void): () => void {
  apresEnvoi = rafraichir
  const enLigne = () => {
    useHorsLigne.setState({ enLigne: true })
    void synchroniser()
  }
  const horsLigne = () => useHorsLigne.setState({ enLigne: false })
  window.addEventListener('online', enLigne)
  window.addEventListener('offline', horsLigne)
  const minuterie = window.setInterval(() => {
    if (useHorsLigne.getState().envois.some((e) => e.etat === 'EN_ATTENTE')) void synchroniser()
  }, 30_000)
  void synchroniser()
  return () => {
    window.removeEventListener('online', enLigne)
    window.removeEventListener('offline', horsLigne)
    window.clearInterval(minuterie)
    apresEnvoi = () => undefined
  }
}

/** Vide la file (tests). */
export async function viderFile(): Promise<void> {
  await base.envois.clear()
  useHorsLigne.setState({ envois: [], enLigne: true, synchronisation: false })
}
