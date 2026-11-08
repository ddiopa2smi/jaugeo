// Contrats de l'API jaugeo-api (/api/v1). Les identifiants publics sont des UUID v7.

export type Permission =
  | 'REFERENTIEL_CONSULTER'
  | 'REFERENTIEL_GERER'
  | 'UTILISATEUR_GERER'
  | 'AUDIT_CONSULTER'
  | 'TABLEAU_BORD_RESEAU'
  | 'PRIX_MODIFIER'
  | 'JOURNEE_SAISIR'
  | 'JOURNEE_CLOTURER'
  | 'JOURNEE_VALIDER'
  | 'RECEPTION_VALIDER'
  | 'VERSEMENT_SAISIR'
  | 'VERSEMENT_VALIDER'
  | 'CLIENT_GERER'
  | 'CREDIT_DEROGER'
  | 'CLOTURE_MENSUELLE'

export type Perimetre = 'RESEAU' | 'STATIONS'

export interface UtilisateurConnecte {
  id: string
  identifiant: string
  nomComplet: string
  role: string
  roleLibelle: string
  permissions: Permission[]
  perimetre: Perimetre
  stationIds: number[]
  doitChangerMotDePasse: boolean
}

export interface ReponseSession {
  jetonAcces: string
  expireDans: number
  utilisateur: UtilisateurConnecte
}

export interface Utilisateur {
  id: string
  identifiant: string
  nomComplet: string
  email: string | null
  telephone: string | null
  role: string
  roleLibelle: string
  perimetre: Perimetre
  stationIds: number[]
  actif: boolean
  doitChangerMotDePasse: boolean
  verrouille: boolean
  derniereConnexion: string | null
  version: number
}

export interface ReponseMotDePasseProvisoire {
  utilisateur: Utilisateur
  motDePasseProvisoire: string
}

export interface Role {
  code: string
  libelle: string
  description: string | null
  permissions: Permission[]
}

export interface Region {
  code: string
  nom: string
}

export type TypeStation = 'ROUTIERE' | 'PECHE' | 'MIXTE'
export type ModeExploitation = 'PROPRE' | 'GERANCE_LIBRE' | 'LOCATION_GERANCE'

export interface Station {
  id: string
  numero: number
  code: string
  nom: string
  type: TypeStation
  regionCode: string
  regionNom: string
  localite: string | null
  latitude: number | null
  longitude: number | null
  modeExploitation: ModeExploitation
  dateOuverture: string | null
  actif: boolean
  version: number
}

export type Famille = 'CARBURANT' | 'LUBRIFIANT' | 'GAZ' | 'BOUTIQUE'
export type Unite = 'LITRE' | 'UNITE' | 'KG'

export interface Produit {
  id: string
  code: string
  libelle: string
  famille: Famille
  unite: Unite
  suiviJaugeage: boolean
  tauxTva: number | null
  actif: boolean
  version: number
}

export interface Cuve {
  id: string
  code: string
  produitCode: string
  produitLibelle: string
  capaciteLitres: number | null
  creuxSecuriteLitres: number | null
  actif: boolean
  version: number
}

export interface Pistolet {
  id: string
  code: string
  libelle: string
  cuveId: string
  cuveCode: string
  produitLibelle: string
  indexMiseEnService: number
  actif: boolean
  version: number
}

export interface Pompe {
  id: string
  code: string
  marque: string | null
  numeroSerie: string | null
  actif: boolean
  version: number
  pistolets: Pistolet[]
}

export interface Equipements {
  station: Station
  cuves: Cuve[]
  pompes: Pompe[]
}

export interface EntreeAudit {
  id: number
  horodatage: string
  schema: string
  table: string
  operation: 'INSERT' | 'UPDATE' | 'DELETE'
  cle: string | null
  utilisateur: string | null
  ancienneValeur: string | null
  nouvelleValeur: string | null
}

export interface PageAudit {
  contenu: EntreeAudit[]
  page: number
  taille: number
  total: number
}

/** Erreur RFC 9457 renvoyée par l'API, avec le code métier. */
export interface Probleme {
  status?: number
  title?: string
  detail?: string
  code?: string
  champs?: Record<string, string>
}
