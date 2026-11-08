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
const PageJournees = differe(() => import('../pages/journees/PageJournees'), 'PageJournees')
const PageJournee = differe(() => import('../pages/journees/PageJournee'), 'PageJournee')
const PageStocks = differe(() => import('../pages/stocks/PageStocks'), 'PageStocks')
const PageTresorerie = differe(() => import('../pages/tresorerie/PageTresorerie'), 'PageTresorerie')
const PageClients = differe(() => import('../pages/clients/PageClients'), 'PageClients')
const PageSyntheses = differe(() => import('../pages/syntheses/PageSyntheses'), 'PageSyntheses')
const PagePrix = differe(() => import('../pages/prix/PagePrix'), 'PagePrix')
const PageProduits = differe(() => import('../pages/PageProduits'), 'PageProduits')
const PageUtilisateurs = differe(() => import('../pages/utilisateurs/PageUtilisateurs'), 'PageUtilisateurs')
const PageAudit = differe(() => import('../pages/PageAudit'), 'PageAudit')
const PageReseau = differe(() => import('../pages/reseau/PageReseau'), 'PageReseau')

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
          { path: 'reseau', element: protegee('TABLEAU_BORD_RESEAU', <PageReseau />) },
          { path: 'stations', element: protegee('REFERENTIEL_CONSULTER', <PageStations />) },
          { path: 'stations/:id', element: protegee('REFERENTIEL_CONSULTER', <PageStation />) },
          { path: 'journees', element: protegee('REFERENTIEL_CONSULTER', <PageJournees />) },
          { path: 'journees/:id', element: protegee('REFERENTIEL_CONSULTER', <PageJournee />) },
          { path: 'stocks', element: protegee('REFERENTIEL_CONSULTER', <PageStocks />) },
          { path: 'tresorerie', element: protegee('REFERENTIEL_CONSULTER', <PageTresorerie />) },
          { path: 'clients', element: protegee('REFERENTIEL_CONSULTER', <PageClients />) },
          { path: 'syntheses', element: protegee('REFERENTIEL_CONSULTER', <PageSyntheses />) },
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
