import type { Evenement } from '../api/types'

const JOUR = 86_400_000
export const ALERTE_JOURS = 30

export interface Echeance {
  evenement: Evenement
  joursRestants: number
}

/**
 * Vérifications à venir sous 30 jours ou dépassées. Pour chaque équipement, seule l'échéance
 * de l'événement le plus récent fait foi (une nouvelle vérification remplace la précédente).
 */
export function echeances(evenements: Evenement[], maintenant: number): Echeance[] {
  const debutJour = Math.floor(maintenant / JOUR) * JOUR // Dakar = UTC
  const vus = new Set<string>()
  const resultat: Echeance[] = []
  for (const e of [...evenements].sort((a, b) => b.date.localeCompare(a.date))) {
    if (!e.prochaineVerification || vus.has(e.equipementId)) continue
    vus.add(e.equipementId)
    const jours = Math.round((Date.parse(`${e.prochaineVerification}T00:00:00Z`) - debutJour) / JOUR)
    if (jours <= ALERTE_JOURS) resultat.push({ evenement: e, joursRestants: jours })
  }
  return resultat.sort((a, b) => a.joursRestants - b.joursRestants)
}
