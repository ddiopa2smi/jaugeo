import { useState } from 'react'

/** Instant figé à l'ouverture du composant : un rendu reste pur et stable. */
export function useMaintenant(): number {
  const [maintenant] = useState(() => Date.now())
  return maintenant
}
