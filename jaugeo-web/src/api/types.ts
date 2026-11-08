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
  baremePoints: number
  baremeCertificat: string | null
  baremeDate: string | null
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

// ---------------------------------------------------------------- Prix

export interface LigneGrille {
  stationId: string
  stationCode: string
  stationNom: string
  produitCode: string
  produitLibelle: string
  prix: number | null
  depuis: string | null
  prochainPrix: number | null
  prochainDebut: string | null
}

export interface LigneChangement {
  stationId: string
  stationCode: string
  stationNom: string
  produitCode: string
  produitLibelle: string
  ancienPrix: number | null
  nouveauPrix: number
}

export interface ChangementPrix {
  id: string
  debut: string
  motif: string
  reference: string | null
  annule: boolean
  annulable: boolean
  creePar: string
  creeLe: string
  annulePar: string | null
  annuleLe: string | null
  lignes: LigneChangement[]
  ignores: string[]
}

export interface DemandeChangementPrix {
  debut: string
  motif: string
  reference: string | null
  stations: string[]
  prix: { produitCode: string; prixUnitaire: number }[]
  confirmerVariation: boolean
}

export interface PeriodePrix {
  id: string
  produitCode: string
  produitLibelle: string
  prixUnitaire: number
  debut: string
  fin: string | null
  changementId: string
  motif: string
  reference: string | null
  creePar: string
}

// ---------------------------------------------------------------- Barèmes et événements

export interface PointBareme {
  hauteurMm: number
  volumeLitres: number
}

export interface Bareme {
  cuveId: string
  cuveCode: string
  produitLibelle: string
  capaciteLitres: number | null
  certificat: string | null
  dateCertificat: string | null
  cuveVersion: number
  points: PointBareme[]
}

export type TypeEvenement =
  | 'REMPLACEMENT_TOTALISATEUR'
  | 'REMISE_A_ZERO'
  | 'ETALONNAGE'
  | 'POSE_SCELLES'
  | 'NETTOYAGE'
  | 'MAINTENANCE'
  | 'AUTRE'

export type CibleEvenement = 'PISTOLET' | 'POMPE' | 'CUVE'

export interface Evenement {
  id: string
  cible: CibleEvenement
  equipementId: string
  equipement: string
  type: TypeEvenement
  date: string
  indexAvant: number | null
  indexApres: number | null
  referenceScelles: string | null
  intervenant: string | null
  prochaineVerification: string | null
  commentaire: string | null
  creePar: string
}

/** Erreur RFC 9457 renvoyée par l'API, avec le code métier. */
export interface Probleme {
  status?: number
  title?: string
  detail?: string
  code?: string
  champs?: Record<string, string>
}
