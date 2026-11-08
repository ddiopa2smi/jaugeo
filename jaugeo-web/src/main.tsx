import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Fournisseurs } from './app/Fournisseurs'
import { RestaurationSession } from './app/Gardes'
import { routes } from './app/routes'

const routeur = createBrowserRouter(routes)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Fournisseurs>
      <RestaurationSession>
        <RouterProvider router={routeur} />
      </RestaurationSession>
    </Fournisseurs>
  </StrictMode>,
)
