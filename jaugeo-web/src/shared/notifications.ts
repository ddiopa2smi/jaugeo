import { create } from 'zustand'

type Gravite = 'success' | 'info' | 'warning' | 'error'

interface EtatNotification {
  message: string | null
  gravite: Gravite
  cle: number
  notifier: (message: string, gravite?: Gravite) => void
  fermer: () => void
}

export const useNotification = create<EtatNotification>()((set) => ({
  message: null,
  gravite: 'success',
  cle: 0,
  notifier: (message, gravite = 'success') => set((e) => ({ message, gravite, cle: e.cle + 1 })),
  fermer: () => set({ message: null }),
}))

export const notifier = (message: string, gravite?: Gravite) => useNotification.getState().notifier(message, gravite)
