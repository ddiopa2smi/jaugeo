import '@testing-library/jest-dom/vitest'
// IndexedDB (file des saisies hors ligne) : absente de jsdom
import 'fake-indexeddb/auto'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// Les pages chargées à la demande (grille de données) mettent plus d'une seconde à s'importer sous jsdom.
configure({ asyncUtilTimeout: 10_000 })

// jsdom n'implémente pas matchMedia : on simule un écran de bureau (≥ 1280 px).
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (requete: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(requete)
    const max = /max-width:\s*([\d.]+)px/.exec(requete)
    const largeur = 1280
    const correspond = (!min || largeur >= Number(min[1])) && (!max || largeur <= Number(max[1]))
    return {
      matches: correspond,
      media: requete,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }
  },
})

afterEach(() => cleanup())
