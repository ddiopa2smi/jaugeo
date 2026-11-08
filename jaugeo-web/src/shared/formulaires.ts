import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form'
import { ErreurApi } from '../api/client'

/**
 * Reporte les erreurs de validation du serveur (propriété « champs ») sur le formulaire.
 * Renvoie vrai si au moins un champ a été marqué.
 */
export function reporterErreursServeur<T extends FieldValues>(erreur: unknown, setError: UseFormSetError<T>): boolean {
  if (!(erreur instanceof ErreurApi)) return false
  const entrees = Object.entries(erreur.champs)
  for (const [champ, message] of entrees) {
    setError(champ as FieldPath<T>, { type: 'serveur', message })
  }
  return entrees.length > 0
}
