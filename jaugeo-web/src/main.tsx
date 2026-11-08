import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { registerSW } from 'virtual:pwa-register'
import { Fournisseurs } from './app/Fournisseurs'
import { RestaurationSession } from './app/Gardes'
import { routes } from './app/routes'

const routeur = createBrowserRouter(routes)

// Service worker (build de production) : coquille de l'application disponible hors ligne, mise à jour automatique
if (import.meta.env.PROD) registerSW({ immediate: true })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Fournisseurs>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>
  </StrictMode>,
)
