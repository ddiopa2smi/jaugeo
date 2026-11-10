import { create } from 'zustand'

export interface DemandeConfirmation {
  titre: string
  /** Ce qui va disparaître, en clair : l'utilisateur doit reconnaître la ligne */
  message: string
  /** Libellé du bouton d'accord (« Supprimer » par défaut) */
  action?: string
}

interface EtatConfirmation {
  demande: DemandeConfirmation | null
  repondre: (accord: boolean) => void
}

let resoudre: ((accord: boolean) => void) | null = null

export const useConfirmation = create<EtatConfirmation>()((set) => ({
  demande: null,
  repondre: (accord) => {
    resoudre?.(accord)
    resoudre = null
    set({ demande: null })
  },
}))

/**
 * Ouvre la modale de confirmation et rend la réponse. Toute suppression passe par ici :
 * `confirmerPuis({ ... }, () => suppression.mutate(id))`.
 */
export function confirmerPuis(demande: DemandeConfirmation, action: () => void): void {
  void confirmer(demande).then((accord) => {
    if (accord) action()
  })
}

export function confirmer(demande: DemandeConfirmation): Promise<boolean> {
  // Une demande restée ouverte est refusée : on ne confirme que ce qu'on a sous les yeux
  resoudre?.(false)
  return new Promise((resolve) => {
    resoudre = resolve
    useConfirmation.setState({ demande })
  })
}
