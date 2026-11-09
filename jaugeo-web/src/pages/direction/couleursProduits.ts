import type { ProduitDirection } from '../../api/types'

// Couleur attachée au produit, jamais à son rang : trois premiers emplacements de la palette de référence,
// validés ensemble (séparation daltonisme et vision normale) sur fond blanc. L'aqua est sous 3:1 de contraste :
// le tableau par produit sous le graphique en donne les valeurs.
const COULEURS: Record<string, string> = { GASOIL: '#2a78d6', SUPER: '#eb6834', ESSENCE_PIROGUE: '#1baf7a' }
const SUPPLEMENTAIRES = ['#eda100', '#e87ba4', '#6250d6']

export function couleurProduit(code: string, produits: ProduitDirection[]): string {
  if (COULEURS[code]) return COULEURS[code]
  const autres = produits.filter((p) => !COULEURS[p.code]).map((p) => p.code)
  return SUPPLEMENTAIRES[autres.indexOf(code) % SUPPLEMENTAIRES.length] ?? '#8a8a8a'
}
