import { lazy, Suspense, type ComponentType, type ReactNode } from 'react'
import type { RouteObject } from 'react-router'
import type { Permission } from '../api/types'
import { PageChangementMotDePasse } from '../pages/ChangementMotDePasse'
import { PageConnexion } from '../pages/Connexion'
import { MonCompte } from '../pages/MonCompte'
import { PageIntrouvable } from '../pages/PageIntrouvable'
import { TableauDeBord } from '../pages/TableauDeBord'
import { Chargement } from '../shared/composants'
import { Disposition } from './Disposition'
import { CHEMIN_CHANGEMENT_MDP, RoutePermission, RouteProtegee } from './Gardes'

// Pages chargées à la demande : la grille de données n'est téléchargée qu'à la première ouverture.
const differe = <K extends string>(charger: () => Promise<Record<K, ComponentType>>, nom: K) =>
  lazy(async () => ({ default: (await charger())[nom] }))

const PageStations = differe(() => import('../pages/stations/PageStations'), 'PageStations')
const PageStation = differe(() => import('../pages/stations/PageStation'), 'PageStation')
const PagePrix = differe(() => import('../pages/prix/PagePrix'), 'PagePrix')
const PageProduits = differe(() => import('../pages/PageProduits'), 'PageProduits')
const PageUtilisateurs = differe(() => import('../pages/utilisateurs/PageUtilisateurs'), 'PageUtilisateurs')
const PageAudit = differe(() => import('../pages/PageAudit'), 'PageAudit')

function protegee(permission: Permission, page: ReactNode) {
  return (
    <RoutePermission permission={permission}>
      <Suspense fallback={<Chargement />}>{page}</Suspense>
    </RoutePermission>
  )
}

export const routes: RouteObject[] = [
  { path: '/connexion', element: <PageConnexion /> },
  {
    element: <RouteProtegee />,
    children: [
      { path: CHEMIN_CHANGEMENT_MDP, element: <PageChangementMotDePasse /> },
      {
        element: <Disposition />,
        children: [
          { index: true, element: <TableauDeBord /> },
          { path: 'stations', element: protegee('REFERENTIEL_CONSULTER', <PageStations />) },
          { path: 'stations/:id', element: protegee('REFERENTIEL_CONSULTER', <PageStation />) },
          { path: 'prix', element: protegee('REFERENTIEL_CONSULTER', <PagePrix />) },
          { path: 'produits', element: protegee('REFERENTIEL_CONSULTER', <PageProduits />) },
          { path: 'utilisateurs', element: protegee('UTILISATEUR_GERER', <PageUtilisateurs />) },
          { path: 'audit', element: protegee('AUDIT_CONSULTER', <PageAudit />) },
          { path: 'mon-compte', element: <MonCompte /> },
          { path: '*', element: <PageIntrouvable /> },
        ],
      },
    ],
  },
]
