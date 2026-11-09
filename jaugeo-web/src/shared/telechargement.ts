import { api } from '../api/client'

/** Télécharge un fichier de l'API (PDF, Excel) sous le nom donné. */
export async function telecharger(chemin: string, nom: string) {
  const url = URL.createObjectURL(await api.blob(chemin))
  const lien = document.createElement('a')
  lien.href = url
  lien.download = nom
  lien.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
