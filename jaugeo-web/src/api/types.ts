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
  /** Tolérance d'écart de stock, en % des sorties du mois */
  toleranceEcartPct: number
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
  'REMPLACEMENT_TOTALISATEUR' | 'REMISE_A_ZERO' | 'ETALONNAGE' | 'POSE_SCELLES' | 'NETTOYAGE' | 'MAINTENANCE' | 'AUTRE'

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

// ------------------------------------------------------------------ Journées (Sprint 2)

export type StatutJournee = 'BROUILLON' | 'CLOTUREE' | 'VALIDEE'

export interface ResumeJournee {
  id: string
  date: string
  statut: StatutJournee
  litres: number | null
  montant: number | null
  clotureePar: string | null
  clotureeLe: string | null
  valideePar: string | null
  motifRenvoi: string | null
}

/** date nulle = première journée de la station : date libre jusqu'à dateMax. */
export interface ProchaineOuverture {
  date: string | null
  possible: boolean
  motif: string | null
  dateMax: string
}

export interface ListeJournees {
  stationId: string
  stationCode: string
  stationNom: string
  prochaine: ProchaineOuverture
  journees: ResumeJournee[]
}

export interface LectureIndex {
  instant: string
  index: number
}

export interface RuptureCompteur {
  date: string
  indexAvant: number
  indexApres: number
}

export interface TrancheVente {
  debut: string
  fin: string
  prixUnitaire: number | null
  litres: number | null
  montant: number | null
}

export interface AnomalieReleve {
  code: string
  message: string
}

export interface LigneReleve {
  releveId: string
  pistoletId: string
  pistoletCode: string
  pistoletLibelle: string
  pompeCode: string
  cuveCode: string
  produitCode: string
  produitLibelle: string
  indexDepart: number
  indexArrivee: number | null
  remiseCuve: number
  lectures: LectureIndex[]
  lecturesAttendues: string[]
  ruptures: RuptureCompteur[]
  tranches: TrancheVente[]
  sortieBrute: number | null
  sortieNette: number | null
  montant: number | null
  anomalies: AnomalieReleve[]
}

export interface TotalProduit {
  produitCode: string
  produitLibelle: string
  litres: number | null
  montant: number | null
}

export interface DetailJournee {
  id: string
  stationId: string
  stationCode: string
  stationNom: string
  date: string
  debut: string
  fin: string
  statut: StatutJournee
  version: number
  observation: string | null
  ouvertePar: string
  ouverteLe: string
  clotureePar: string | null
  clotureeLe: string | null
  valideePar: string | null
  valideeLe: string | null
  motifRenvoi: string | null
  lignes: LigneReleve[]
  totaux: TotalProduit[]
  litres: number | null
  montant: number | null
  anomalies: number
  actions: { saisir: boolean; cloturer: boolean; valider: boolean; renvoyer: boolean }
}

export interface SaisieJournee {
  version: number
  observation: string | null
  releves: { pistoletId: string; indexArrivee: number | null; remiseCuve: number | null; lectures: LectureIndex[] }[]
}

// ---------------------------------------------------------------- Stocks (Sprint 3)

export type NiveauEcart = 'NORMAL' | 'ALERTE' | 'ENQUETE'

export interface Anomalie {
  code: string
  message: string
}

export interface Jaugeage {
  hauteurMm: number | null
  volumeLitres: number
  observation: string | null
  par: string
  le: string
}

export interface CuveStock {
  cuveId: string
  cuveCode: string
  produitCode: string
  produitLibelle: string
  capaciteLitres: number | null
  bareme: boolean
  departReporte: boolean
  ouvertureRequise: boolean
  stockDepart: number | null
  ouverture: Jaugeage | null
  receptions: number
  sorties: number | null
  stockTheorique: number | null
  cloture: Jaugeage | null
  ecartLitres: number | null
  ecartPctJour: number | null
  prixUnitaire: number | null
  ecartValeur: number | null
  cumulSortiesMois: number | null
  cumulEcartMois: number | null
  ecartPctMois: number | null
  tolerancePct: number
  niveau: NiveauEcart | null
  observationRequise: boolean
  anomalies: Anomalie[]
}

export interface Reception {
  id: string
  cuveId: string
  cuveCode: string
  produitCode: string
  numeroBl: string
  fournisseur: string
  transporteur: string | null
  chauffeur: string | null
  immatriculation: string | null
  scelles: string | null
  quantiteBl: number
  jaugeAvant: number | null
  jaugeApres: number | null
  quantiteRecue: number
  temperatureC: number | null
  densite: number | null
  observation: string | null
  ecartLivraison: number
  ecartLivraisonPct: number | null
  complementLitres: number | null
  complementStatut: 'A_REGLER' | 'REGLE' | null
  par: string
  le: string
}

export interface StockJournee {
  journeeId: string
  stationId: string
  stationCode: string
  date: string
  statut: StatutJournee
  version: number
  modifiable: boolean
  cuves: CuveStock[]
  receptions: Reception[]
  anomalies: number
  toleranceTransportPct: number
}

export interface SaisieReception {
  version: number
  cuveId: string
  numeroBl: string
  fournisseur: string
  transporteur: string | null
  chauffeur: string | null
  immatriculation: string | null
  scelles: string | null
  quantiteBl: number
  jaugeAvant: number | null
  jaugeApres: number | null
  quantiteRecue: number | null
  temperatureC: number | null
  densite: number | null
  observation: string | null
}

export interface SaisieJaugeage {
  cuveId: string
  moment: 'OUVERTURE' | 'CLOTURE'
  hauteurMm: number | null
  volumeLitres: number | null
  observation: string | null
}

export interface LigneStock {
  date: string
  journeeId: string | null
  cuveId: string
  cuveCode: string
  produitCode: string
  stockDepart: number
  receptions: number
  sorties: number
  stockTheorique: number
  stockPhysique: number
  ecartLitres: number
  ecartValeur: number | null
  cumulEcartMois: number
  ecartPctMois: number | null
  niveau: NiveauEcart
  observation: string | null
}

export interface TotalCuve {
  cuveId: string
  cuveCode: string
  produitCode: string
  produitLibelle: string
  stockDepart: number
  receptions: number
  sorties: number
  stockTheorique: number
  stockPhysique: number
  ecartLitres: number
  ecartPct: number | null
  ecartValeur: number
  tolerancePct: number
  niveau: NiveauEcart
  jours: number
}

export interface AnalyseMensuelle {
  stationId: string
  stationCode: string
  stationNom: string
  mois: string
  totaux: TotalCuve[]
  lignes: LigneStock[]
}

export interface SituationCuve {
  stationId: string
  stationCode: string
  stationNom: string
  cuveId: string
  cuveCode: string
  produitCode: string
  produitLibelle: string
  date: string
  stockPhysique: number
  capaciteLitres: number | null
  cumulEcartMois: number
  ecartPctMois: number | null
  tolerancePct: number
  niveau: NiveauEcart
  observation: string | null
}

export interface SituationStock {
  cuves: SituationCuve[]
  alertes: number
  complementsARegler: number
  montantComplementsARegler: number
}

export interface ComplementCiterne {
  id: string
  stationId: string
  stationCode: string
  date: string
  journeeId: string | null
  numeroBl: string
  fournisseur: string
  transporteur: string | null
  immatriculation: string | null
  produitCode: string | null
  quantiteBl: number
  quantiteRecue: number
  litres: number
  prixUnitaire: number | null
  montant: number | null
  statut: 'A_REGLER' | 'REGLE'
  referenceReglement: string | null
  regleLe: string | null
  reglePar: string | null
  version: number
  reglable: boolean
}