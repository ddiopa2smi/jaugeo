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
  | 'DEPENSE_VALIDER'
  | 'CAISSE_CONTROLER'
  | 'PERSONNEL_GERER'

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
  /** Eau en fond de cuve (mm) au-delà de laquelle le jaugeage alerte ; null = seuil du réseau */
  seuilEauMm: number | null
  /** Stock (L) sous lequel la cuve est à réapprovisionner ; null = jugement sur l'autonomie */
  seuilReapproLitres: number | null
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
  /** Volume de produit, eau déduite */
  volumeLitres: number
  observation: string | null
  par: string
  le: string
  hauteurEauMm: number | null
  volumeEauLitres: number | null
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
  seuilEauMm: number
  /** Alertes non bloquantes (eau en fond de cuve) */
  alertes: Anomalie[]
}

export type Imputation = 'TRANSPORTEUR' | 'FOURNISSEUR'

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
  quantiteBl15: number | null
  facteur15: number | null
  quantiteRecue15: number | null
  /** Écart de livraison et complément calculés à 15 °C */
  compareeA15: boolean
  scellesIntacts: boolean | null
  imputation: Imputation
  /** Livraison de tournée rattachée (M8) */
  livraisonId: string | null
  tourneeNumero: string | null
  /** Tolérance appliquée : contrat du transporteur, sinon tolérance réseau */
  tolerancePct: number
}

/** Livraison d'une tournée chargée ou en route vers la station, pas encore réceptionnée. */
export interface LivraisonAttendue {
  id: string
  tourneeNumero: string
  tourneeStatut: string
  produitCode: string
  quantiteCommandee: number
  numeroBl: string | null
  quantiteBl: number | null
  quantiteBl15: number | null
  fournisseur: string
  transporteur: string
  chauffeur: string | null
  immatriculation: string | null
  scelles: string | null
  dateLivraisonPrevue: string
  /** Tolérance de manquant du contrat du transporteur, sinon du réseau */
  tolerancePct: number
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
  livraisonsAttendues: LivraisonAttendue[]
  /** Fournisseurs (dépôts) actifs déclarés par la logistique, proposés à la saisie d'une réception */
  fournisseurs?: string[]
}

export interface SaisieReception {
  version: number
  cuveId: string
  livraisonId: string | null
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
  quantiteBl15: number | null
  scellesIntacts: boolean | null
  /** null = automatique (fournisseur si scellés intacts, transporteur sinon) */
  imputation: Imputation | null
}

export interface SaisieJaugeage {
  cuveId: string
  moment: 'OUVERTURE' | 'CLOTURE'
  hauteurMm: number | null
  volumeLitres: number | null
  observation: string | null
  hauteurEauMm: number | null
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
  creuxSecuriteLitres: number | null
  hauteurEauMm: number | null
  seuilEauMm: number
  eauAlerte: boolean
  /** Ventes moyennes par jour sur les 7 derniers jours figés */
  ventesMoyennes: number | null
  joursMoyenne: number
  autonomieJours: number | null
  seuilReapproLitres: number | null
  aReapprovisionner: boolean
}

export interface SituationStock {
  cuves: SituationCuve[]
  alertes: number
  complementsARegler: number
  montantComplementsARegler: number
  cuvesEau: number
  cuvesAReapprovisionner: number
  autonomieAlerteJours: number
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
  imputeA: Imputation
  a15: boolean
}
// ---------------------------------------------------------------- Dépenses (M7)

export interface NatureDepense {
  id: string
  code: string
  libelle: string
  plafond: number | null
  actif: boolean
  version: number
}

export type StatutDepense = 'A_VALIDER' | 'AUTORISEE' | 'REFUSEE'

export interface PieceJointe {
  nomFichier: string
  typeMime: string
  taille: number
}

export interface Depense {
  id: string
  natureId: string
  natureCode: string
  natureLibelle: string
  libelle: string
  beneficiaire: string | null
  montant: number
  plafond: number | null
  statut: StatutDepense
  justificatif: PieceJointe | null
  decisionPar: string | null
  decisionLe: string | null
  motifDecision: string | null
  creePar: string
  modifieLe: string
  decidable: boolean
}

export interface DepensesJournee {
  journeeId: string
  stationId: string
  stationCode: string
  date: string
  statut: StatutJournee
  version: number
  modifiable: boolean
  justificatifObligatoire: boolean
  depenses: Depense[]
  totalAutorise: number
  totalEnAttente: number
  totalRefuse: number
  anomalies: string[]
}

export interface DepenseAValider {
  id: string
  journeeId: string
  journeeVersion: number
  stationId: string
  stationCode: string
  date: string
  natureLibelle: string
  libelle: string
  beneficiaire: string | null
  montant: number
  plafond: number | null
  creePar: string
  justificatif: boolean
  decidable: boolean
}

export interface SaisieDepense {
  version: number
  natureId: string
  libelle: string
  beneficiaire: string | null
  montant: number
}

// ---------------------------------------------------------------- Trésorerie (M5)

export type StatutVersement = 'SAISI' | 'VERIFIE' | 'REJETE'

export interface Versement {
  id: string
  stationId: string
  stationCode: string
  dateDepot: string
  banque: string
  referenceBv: string
  montant: number
  deposant: string | null
  observation: string | null
  statut: StatutVersement
  piece: PieceJointe | null
  verifiePar: string | null
  verifieLe: string | null
  motifRejet: string | null
  creePar: string
  creeLe: string
  version: number
  modifiable: boolean
  verifiable: boolean
  /** Date du crédit au relevé bancaire lettré avec ce versement */
  dateReleve?: string | null
}

/** Banque où la recette peut être déposée ; seules les banques actives sont proposées à la saisie d'un versement. */
export interface Banque {
  id: string
  /** Sigle porté par les versements (CBAO, BIS, LBA…) */
  code: string
  nom: string
  actif: boolean
  version: number
}

export type NatureReleve ='BANQUE' | 'WAVE' | 'ORANGE_MONEY' | 'CARTE'

export interface FormatReleve {
  id: string
  code: string
  libelle: string
  nature: NatureReleve
  separateur: string
  formatDate: string
  lignesIgnorees: number
  colDate: number
  colLibelle: number
  colReference: number | null
  colCredit: number
  actif: boolean
  version: number
}

export interface BilanImport {
  releveId: string
  nomFichier: string
  format: string
  dateMin: string | null
  dateMax: string | null
  operations: number
  doublons: number
  ignorees: number
  lettrees: number
}

export interface VersementResume {
  id: string
  stationCode: string | null
  dateDepot: string
  banque: string
  referenceBv: string
  montant: number
  statut: StatutVersement
}

export interface OperationReleve {
  id: string
  date: string
  libelle: string
  reference: string | null
  montant: number
  format: string
  versement: VersementResume | null
  modeLettrage: 'AUTO' | 'MANUEL' | null
  lettrePar: string | null
  candidats: VersementResume[]
}

export interface ReleveImporte {
  id: string
  nomFichier: string
  format: string
  nature: NatureReleve
  stationCode: string | null
  dateMin: string | null
  dateMax: string | null
  operations: number
  doublons: number
  importePar: string
  importeLe: string
}

export interface Rapprochement {
  mois: string
  versements: number
  montantVersements: number
  versementsLettres: number
  montantLettre: number
  taux: number | null
  operations: OperationReleve[]
  versementsNonLettres: VersementResume[]
  releves: ReleveImporte[]
  peutLettrer: boolean
}

export interface RapprochementElectronique {
  mois: string
  lignes: {
    stationId: string
    stationCode: string
    date: string
    mode: ModeEncaissement
    libelle: string
    declare: number
    releve: number | null
    ecart: number | null
  }[]
  totalDeclare: number
  totalReleve: number
  ecarts: number
  nonCouverts: number
}

export interface SaisieVersement {
  version?: number
  stationId: string
  dateDepot: string
  banque: string
  referenceBv: string
  montant: number
  deposant: string | null
  observation: string | null
}

export type TypeArrete = 'REPRISE' | 'CONTROLE'

export interface ArreteCaisse {
  id: string
  stationId: string
  stationCode: string
  date: string
  type: TypeArrete
  soldeTheorique: number | null
  especesComptees: number
  ecart: number | null
  responsable: string | null
  observation: string | null
  creePar: string
  creeLe: string
  manquantId: string | null
  manquantStatut: StatutManquant | null
}

export interface RecetteJournee {
  journeeId: string
  stationId: string
  stationCode: string
  date: string
  statut: StatutJournee
  fige: boolean
  ventes: number | null
  /** Lubrifiants et articles boutique vendus dans la journée */
  ventesArticles: number
  bonsDirection: number
  bonsGerant: number
  /** Pleins payés par carte prépayée (valeur à la pompe) : hors caisse du gérant */
  cartes: number
  depenses: number | null
  complements: number | null
  /** Wave, Orange Money et carte : hors caisse du gérant */
  encaissements: number
  recouvrements: number
  montantAVerser: number | null
  soldeOuverture: number
  versements: Versement[]
  totalVersements: number
  soldeFin: number | null
  arrete: ArreteCaisse | null
  peutVerser: boolean
  plafondEspeces: number | null
  depassePlafond: boolean
}

export interface LigneCaisse {
  date: string
  journeeId: string | null
  statutJournee: StatutJournee | null
  ventes: number | null
  bons: number | null
  cartes: number | null
  depenses: number | null
  complements: number | null
  encaissements: number | null
  recouvrements: number | null
  montantAVerser: number | null
  soldeOuverture: number
  versements: number
  soldeTheorique: number
  arrete: ArreteCaisse | null
  ecart: number | null
  solde: number
}

export interface LivreCaisse {
  stationId: string
  stationCode: string
  stationNom: string
  mois: string
  soldeDebut: number
  totalVentes: number
  totalBonsDirection: number
  totalBonsGerant: number
  totalCartes: number
  totalDepenses: number
  totalComplements: number
  totalEncaissements: number
  totalRecouvrements: number
  totalMontants: number
  totalVersements: number
  totalEcarts: number
  soldeFin: number
  joursNonClotures: number
  lignes: LigneCaisse[]
  peutArreter: boolean
  plafondEspeces: number | null
}

export type StatutManquant = 'OUVERT' | 'SOLDE'
export type ModeReglementManquant = 'REMBOURSEMENT' | 'RETENUE' | 'PERTE'

export interface Manquant {
  id: string
  stationId: string
  stationCode: string
  date: string
  montant: number
  responsable: string | null
  observation: string | null
  statut: StatutManquant
  modeReglement: ModeReglementManquant | null
  referenceReglement: string | null
  regleLe: string | null
  reglePar: string | null
  version: number
  reglable: boolean
  montantRegle: number
  reste: number
  reconnaissanceReference: string | null
  reconnuLe: string | null
  echeances: { rang: number; date: string; montant: number; regle: number; statut: 'PAYEE' | 'EN_RETARD' | 'A_VENIR' }[]
  paiements: { date: string; montant: number; mode: ModeReglementManquant; reference: string | null; par: string }[]
  prochaineEcheance: string | null
  /** Échéances échues non honorées */
  retard: number
}

export interface SituationCaisse {
  stationId: string
  stationCode: string
  stationNom: string
  soldeNonVerse: number
  derniereRecette: string | null
  dernierVersement: string | null
  joursSansVersement: number | null
  retard: boolean
  versementsAVerifier: number
  manquantsOuverts: number
  montantManquants: number
  dernierArrete: string | null
  dernierEcart: number | null
  plafondEspeces: number | null
  depassePlafond: boolean
}

export interface SituationTresorerie {
  stations: SituationCaisse[]
  retards: number
  versementsAVerifier: number
  manquantsOuverts: number
  montantManquants: number
  delaiVersementJours: number
  depassementsPlafond: number
}

export type ModeEncaissement = 'WAVE' | 'ORANGE_MONEY' | 'CARTE'

export interface EncaissementsJournee {
  journeeId: string
  version: number
  statut: StatutJournee
  modifiable: boolean
  lignes: { mode: ModeEncaissement; libelle: string; montant: number | null }[]
  total: number
}

// ---------------------------------------------------------------- Clients en compte (Sprint 5)

export type Recouvrement = 'DIRECTION' | 'GERANT'
export type ModeReglementClient = 'ESPECES_STATION' | 'ESPECES_SIEGE' | 'VIREMENT' | 'CHEQUE' | 'COMPENSATION'

export interface Client {
  id: string
  code: string
  nom: string
  telephone: string | null
  recouvrement: Recouvrement
  plafond: number | null
  actif: boolean
  observation: string | null
  encours: number
  disponible: number | null
  /** Factures et reprises échues non réglées */
  echu: number
  contrat: ContratClient
  version: number
}

/** Contrat, coordonnées et garanties ; délai de paiement en jours après la facture. */
export interface ContratClient {
  adresse: string | null
  email: string | null
  ninea: string | null
  reference: string | null
  debut: string | null
  fin: string | null
  delaiPaiement: number | null
  cautionMontant: number | null
  cautionBanque: string | null
  cautionEcheance: string | null
}

export interface SaisieClient {
  code?: string
  nom: string
  telephone: string | null
  recouvrement: Recouvrement
  plafond: number | null
  actif: boolean
  observation: string | null
  contrat?: ContratClient
  version?: number
}

export type TypeEngin = 'VEHICULE' | 'PIROGUE'

export interface EnginClient {
  id: string
  type: TypeEngin
  immatriculation: string
  libelle: string | null
  actif: boolean
  version: number
}

export interface RemiseClient {
  id: string
  produitId: string
  produitCode: string
  produitLibelle: string
  debut: string
  remise: number
  enVigueur: boolean
}

export interface CompteStation {
  stationId: string
  stationCode: string
  stationNom: string
  creance: number
  dernierBon: string | null
  dernierReglement: string | null
}

export interface FicheClient {
  client: Client
  comptes: CompteStation[]
  engins: EnginClient[]
  remises: RemiseClient[]
  factures: Facture[]
  cartes: CarteCarburant[]
  relances: Relance[]
  peutGerer: boolean
}

// ---------------------------------------------------------------- Facturation, balance âgée, relances (M6)

export interface Facture {
  id: string
  numero: string
  clientId: string
  clientCode: string
  clientNom: string
  periodeDebut: string
  periodeFin: string
  dateFacture: string
  dateEcheance: string
  montant: number
  /** Part non réglée après imputation des règlements (plus ancien d'abord) */
  reste: number
  statut: 'EMISE' | 'ANNULEE'
  motifAnnulation: string | null
  bons: number
  creePar: string
  version: number
}

export interface LigneFacture {
  date: string
  stationCode: string
  numero: string
  produitCode: string
  litres: number
  prixPompe: number
  remise: number
  prixUnitaire: number
  montant: number
  immatriculation: string | null
}

export interface FactureDetail {
  facture: Facture
  lignes: LigneFacture[]
}

export interface ResultatFacturation {
  factures: Facture[]
  bonsFactures: number
  remarques: string[]
}

export interface LigneBalance {
  clientId: string
  code: string
  nom: string
  recouvrement: Recouvrement
  delaiPaiement: number | null
  plafond: number | null
  nonFacture: number
  jours0a30: number
  jours31a60: number
  jours61a90: number
  plusDe90: number
  total: number
  echu: number
  plusAncienneEcheance: string | null
  derniereRelance: string | null
  dernierNiveau: number | null
  /** 0 : rien d'échu ; 1 rappel, 2 relance, 3 mise en demeure */
  niveauSuggere: number
}

export interface BalanceAgee {
  au: string
  lignes: LigneBalance[]
  nonFacture: number
  jours0a30: number
  jours31a60: number
  jours61a90: number
  plusDe90: number
  total: number
  echu: number
}

export type ModeRelance = 'COURRIER' | 'EMAIL' | 'TELEPHONE' | 'VISITE'

export interface Relance {
  id: string
  date: string
  niveau: number
  mode: ModeRelance
  montantEchu: number
  commentaire: string | null
  creePar: string
}

// ---------------------------------------------------------------- Cartes carburant prépayées (M6)

export type StatutCarte = 'ACTIVE' | 'BLOQUEE' | 'ANNULEE'
export type ModeRecharge = 'ESPECES_SIEGE' | 'VIREMENT' | 'CHEQUE' | 'WAVE' | 'ORANGE_MONEY'

export interface CarteCarburant {
  id: string
  numero: string
  clientId: string
  clientCode: string
  clientNom: string
  porteur: string
  immatriculation: string | null
  statut: StatutCarte
  solde: number
  version: number
}

export interface MouvementCarte {
  id: string
  type: 'RECHARGE' | 'DEBIT'
  date: string
  montant: number
  mode: ModeRecharge | null
  reference: string | null
  stationCode: string | null
  produitCode: string | null
  litres: number | null
  prixUnitaire: number | null
  creePar: string
}

export interface CarteDetail {
  carte: CarteCarburant
  mouvements: MouvementCarte[]
  peutGerer: boolean
}

export interface PleinCarte {
  id: string
  carteNumero: string
  porteur: string
  clientCode: string
  clientNom: string
  immatriculation: string | null
  produitId: string
  produitCode: string
  litres: number
  prixUnitaire: number
  montant: number
  montantPompe: number
  reference: string | null
  creePar: string
}

export type StatutBon = 'VALIDE' | 'EN_ATTENTE' | 'ACCORDE' | 'REFUSE'

export interface BonEnAttente {
  id: string
  version: number
  journeeId: string
  stationId: string
  stationCode: string
  date: string
  journeeStatut: StatutJournee
  clientId: string
  clientCode: string
  clientNom: string
  numero: string
  produitCode: string
  litres: number
  montant: number
  immatriculation: string | null
  motifBlocage: string
  creePar: string
}

export interface LigneReleveClient {
  date: string
  type: 'BON' | 'REPRISE' | 'REGLEMENT'
  mode: string | null
  numero: string | null
  produitCode: string | null
  litres: number | null
  prixUnitaire: number | null
  reference: string | null
  observation: string | null
  debit: number | null
  credit: number | null
  solde: number
  operationId: string | null
  version: number | null
  supprimable: boolean
  creePar: string
}

export interface Releve {
  clientId: string
  clientCode: string
  clientNom: string
  stationId: string
  stationCode: string
  du: string | null
  au: string | null
  soldeDebut: number
  totalDebit: number
  totalCredit: number
  soldeFin: number
  lignes: LigneReleveClient[]
}

export interface SaisieOperation {
  stationId: string
  type: 'REPRISE' | 'REGLEMENT'
  mode: ModeReglementClient | null
  date: string
  montant: number
  reference: string | null
  observation: string | null
}

export interface Bon {
  id: string
  numero: string
  clientId: string
  clientCode: string
  clientNom: string
  produitId: string
  produitCode: string
  produitLibelle: string
  litres: number
  /** Prix pompe diminué de la remise du client */
  prixUnitaire: number
  remiseUnitaire: number
  /** Dû par le client */
  montant: number
  /** Valeur à la pompe, sortie de la recette */
  montantPompe: number
  recouvrement: Recouvrement
  immatriculation: string | null
  porteur: string | null
  statut: StatutBon
  motifBlocage: string | null
  derogationPar: string | null
  derogationMotif: string | null
  facture: boolean
  creePar: string
  modifieLe: string
  version: number
}

export interface RecouvrementJournee {
  id: string
  clientId: string
  clientCode: string
  clientNom: string
  montant: number
  reference: string | null
  observation: string | null
  creePar: string
}

export interface ProduitCredit {
  id: string
  code: string
  libelle: string
  prixUnitaire: number | null
}

export interface CreditJournee {
  journeeId: string
  stationId: string
  stationCode: string
  date: string
  statut: StatutJournee
  version: number
  modifiable: boolean
  produits: ProduitCredit[]
  bons: Bon[]
  cartes: PleinCarte[]
  recouvrements: RecouvrementJournee[]
  totalBonsDirection: number
  totalBonsGerant: number
  totalCartes: number
  totalRecouvrements: number
  bonsEnAttente: number
  anomalies: string[]
}

export interface SaisieBon {
  version: number
  clientId: string
  numero: string
  produitId: string
  litres: number
  immatriculation: string | null
  porteur: string | null
}

// ---------------------------------------------------------------- Synthèses mensuelles (Sprint 5)

export interface ProduitColonne {
  code: string
  libelle: string
}

export interface LigneVentes {
  stationId: string | null
  stationCode: string
  stationNom: string
  litres: Record<string, number>
  recettes: number
  soldeOuverture: number
  creancesDebut: number
  aVerser: number
  versements: number
  soldeCaisse: number
  creancesFin: number
  depenses: number
  complements: number
  /** N : Wave, Orange Money, carte bancaire, cartes prépayées et remises aux clients */
  horsCaisse: number
  reglementsSiege: number
  totalVersements: number
  ecart: number
  bonsDirection: number
  bonsGerant: number
  recouvrements: number
  creancesGerantFin: number
  ecartsArretes: number
  joursNonClotures: number
  observation: string | null
  commentaire: string | null
  commentaireVersion: number | null
}

export interface SyntheseVentes {
  mois: string
  produits: ProduitColonne[]
  lignes: LigneVentes[]
  cumul: LigneVentes
  peutCommenter: boolean
}

export interface LigneStocks {
  stationId: string
  stationCode: string
  stationNom: string
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
  ecartValeur: number | null
  tolerancePct: number | null
  niveau: string
  jours: number
}

export interface TotalProduitStock {
  produitCode: string
  produitLibelle: string
  receptions: number
  sorties: number
  ecartLitres: number
  ecartPct: number | null
  ecartValeur: number
}

export interface SyntheseStocks {
  mois: string
  lignes: LigneStocks[]
  totaux: TotalProduitStock[]
}

export interface CommentaireMensuel {
  stationId: string
  mois: string
  texte: string | null
  version: number | null
}
// ---------------------------------------------------------------- Tableau de bord du réseau

export interface EcartProduitReseau {
  produitCode: string
  produitLibelle: string
  ecartLitres: number | null
  ecartPct: number | null
  niveau: NiveauEcart | null
}

export interface StationReseau {
  stationId: string
  code: string
  nom: string
  latitude: number | null
  longitude: number | null
  niveau: NiveauEcart
  motifs: string[]
  derniereCloture: string | null
  joursSansCloture: number | null
  journeesAValider: number
  litresMois: Record<string, number>
  litresMoisTotal: number
  litresVeille: number
  ventesMois: number | null
  versementsMois: number | null
  soldeCaisse: number | null
  ecartsCaisseMois: number | null
  ecartStockValeurMois: number
  ecartsStock: EcartProduitReseau[]
}

export interface TableauReseau {
  date: string
  mois: string
  parNiveau: Record<NiveauEcart, number>
  litresMois: number
  litresVeille: number
  ventesMois: number
  versementsMois: number
  ecartStockValeurMois: number
  stations: StationReseau[]
}

// Vue de la Direction : mois arrêté à la veille, comparé à la même période du mois précédent
export interface PeriodeDirection {
  du: string
  au: string
  litres: number
  ventes: number
}

export interface ProduitDirection {
  code: string
  libelle: string
  litres: number
  ventes: number
  litresPrecedent: number
  ventesPrecedent: number
}

export interface JourDirection {
  date: string
  litres: Record<string, number>
  ventes: Record<string, number>
  ventesTotal: number
}

export interface StationDirection {
  stationId: string
  code: string
  nom: string
  litres: number
  ventes: number
  ventesPrecedent: number
  montantDu: number | null
  versements: number | null
  soldeCaisse: number | null
  joursNonClotures: number
}

export interface VueDirection {
  date: string
  mois: string
  periode: PeriodeDirection
  precedente: PeriodeDirection
  ventesMoisPrecedent: number
  montantDu: number
  versements: number
  soldeCaisse: number
  produits: ProduitDirection[]
  jours: JourDirection[]
  stations: StationDirection[]
  /** Marge sur carburants de la période ; nulle si un prix d'achat convenu manque */
  marge: number | null
  tauxMarge: number | null
}
// ------------------------------------------------------------ Lubrifiants et boutique (lot 2, M4)

export type TypeMouvementArticle = 'ENTREE' | 'VENTE' | 'INVENTAIRE'
export type StatutEntreeArticle = 'ANNONCEE' | 'RECUE' | 'ANNULEE'

export interface ArticleStation {
  produitId: string
  code: string
  libelle: string
  unite: string
  quantite: number
  ventesEnCours: number
  disponible: number
  cmup: number
  valeur: number
  prixVente: number | null
  dernierComptage: string | null
  aCompter: boolean
  enRetard: boolean
}

export interface EntreeArticle {
  id: string
  version: number
  produitId: string
  produitCode: string
  produitLibelle: string
  unite: string
  quantite: number
  coutUnitaire: number
  valeur: number
  fournisseur: string
  reference: string
  statut: StatutEntreeArticle
  quantiteRecue: number | null
  recueLe: string | null
  recuePar: string | null
  observation: string | null
  annonceeLe: string
  annonceePar: string
}

export interface StockArticles {
  stationId: string
  stationCode: string
  stationNom: string
  articles: ArticleStation[]
  valeurStock: number
  nombreACompter: number
  entreesAnnoncees: EntreeArticle[]
  peutAnnoncer: boolean
  peutRecevoir: boolean
  peutCompter: boolean
}

export interface MouvementArticle {
  date: string
  produitCode: string
  produitLibelle: string
  type: TypeMouvementArticle
  quantite: number
  coutUnitaire: number
  valeur: number
  stockApres: number
  cmupApres: number
  libelle: string | null
}

export interface MouvementsArticles {
  stationId: string
  stationCode: string
  mois: string
  mouvements: MouvementArticle[]
  valeurEntrees: number
  valeurVentes: number
  valeurEcarts: number
}

export interface VenteArticle {
  produitId: string
  code: string
  libelle: string
  unite: string
  disponible: number
  prixUnitaire: number | null
  quantite: number | null
  montant: number | null
  cmup: number | null
}

export interface VentesArticlesJournee {
  journeeId: string
  version: number
  statut: StatutJournee
  modifiable: boolean
  articles: VenteArticle[]
  total: number
}

// ---------------------------------------------------------------- Approvisionnement (M8)

export interface PrixAchat {
  id: string
  produitId: string
  produitCode: string
  produitLibelle: string
  debut: string
  prix: number
  enVigueur: boolean
}

export interface FournisseurAppro {
  id: string
  nom: string
  telephone: string | null
  adresse: string | null
  delaiLivraison: number
  principal: boolean
  factureA15: boolean
  actif: boolean
  version: number
  prix: PrixAchat[]
}

export interface Citerne {
  id: string
  immatriculation: string
  capaciteLitres: number
  actif: boolean
  version: number
}

export interface Notation {
  note: number | null
  livraisons: number
  ponctualitePct: number | null
  conformitePct: number | null
  tauxManquantPct: number | null
  horsTolerance: number
  jours: number
}

export interface Transporteur {
  id: string
  nom: string
  telephone: string | null
  contratReference: string | null
  contratDebut: string | null
  contratFin: string | null
  tolerancePct: number | null
  toleranceAppliquee: number
  penalite: number | null
  actif: boolean
  observation: string | null
  version: number
  citernes: Citerne[]
  notation: Notation
  penalitesARegler: number
}

export type StatutPenalite = 'A_REGLER' | 'REGLE'

export interface PenaliteTransport {
  id: string
  transporteurId: string
  transporteurNom: string
  tourneeNumero: string | null
  stationCode: string | null
  date: string
  journeeId: string | null
  numeroBl: string | null
  manquantLitres: number
  manquantPct: number
  tolerancePct: number
  montant: number
  statut: StatutPenalite
  referenceReglement: string | null
  regleLe: string | null
  reglePar: string | null
  version: number
}

export interface LivraisonNotee {
  tourneeNumero: string
  stationCode: string
  produitCode: string
  prevue: string
  recue: string
  quantiteLivree: number
  manquant: number
  manquantPct: number
  tolerancePct: number
  aLHeure: boolean
  horsTolerance: boolean
}

export interface TransporteurDetail {
  transporteur: Transporteur
  penalites: PenaliteTransport[]
  livraisons: LivraisonNotee[]
}

export interface PropositionLigne {
  stationId: string
  stationCode: string
  stationNom: string
  produitId: string
  produitCode: string
  produitLibelle: string
  dateStock: string | null
  stockAujourdhui: number
  capaciteUtile: number | null
  ventesMoyennes: number | null
  autonomieJours: number | null
  enCours: number
  stockArrivee: number
  quantiteProposee: number | null
  ruptureLe: string | null
  aCommander: boolean
  motif: string | null
}

export interface Propositions {
  fournisseurId: string | null
  fournisseurNom: string | null
  delaiLivraison: number
  margeJours: number
  tauxRemplissagePct: number
  date: string
  lignes: PropositionLigne[]
}

export type StatutTournee = 'COMMANDEE' | 'CHARGEE' | 'EN_ROUTE' | 'DEPOTEE' | 'ANNULEE'

export interface ReceptionLivraison {
  date: string
  numeroBl: string
  quantiteLivree: number
  quantiteMesuree: number
  manquant: number
  manquantPct: number
  tolerancePct: number
  horsTolerance: boolean
  compareeA15: boolean
  aLHeure: boolean
}

export interface LivraisonTournee {
  id: string
  ordre: number
  stationId: string
  stationCode: string
  stationNom: string
  produitId: string
  produitCode: string
  quantiteCommandee: number
  numeroBl: string | null
  quantiteBl: number | null
  quantiteBl15: number | null
  reception: ReceptionLivraison | null
  version: number
}

export interface Tournee {
  id: string
  numero: string
  statut: StatutTournee
  fournisseurId: string
  fournisseurNom: string
  transporteurId: string
  transporteurNom: string
  citerneId: string | null
  immatriculation: string | null
  capaciteCiterne: number | null
  chauffeur: string | null
  dateChargementPrevue: string
  dateLivraisonPrevue: string
  chargeLe: string | null
  scelles: string | null
  partiLe: string | null
  motifAnnulation: string | null
  observation: string | null
  quantiteCommandee: number
  livraisons: number
  receptionnees: number
  version: number
  lignes: LivraisonTournee[]
}

export interface SaisieTournee {
  fournisseurId: string
  transporteurId: string
  citerneId: string | null
  immatriculation: string | null
  chauffeur: string | null
  dateChargementPrevue: string
  dateLivraisonPrevue: string
  observation: string | null
  livraisons: { stationId: string; produitId: string; quantite: number }[]
  version?: number
}

export type StatutFactureFournisseur = 'SAISIE' | 'BON_A_PAYER' | 'EN_LITIGE' | 'ANNULEE'

export interface FactureFournisseur {
  id: string
  fournisseurId: string
  fournisseurNom: string
  numero: string
  dateFacture: string
  statut: StatutFactureFournisseur
  montant: number
  montantAttendu: number
  ecart: number
  lignes: number
  anomalies: number
  motifDecision: string | null
  decidePar: string | null
  decideLe: string | null
  observation: string | null
  version: number
}

export interface LigneFactureControlee {
  numeroBl: string
  produitId: string
  produitCode: string
  quantite: number
  prixUnitaire: number
  montant: number
  dateReception: string | null
  stationCode: string | null
  /** Base de comparaison : 15 °C ou ambiante */
  base: string | null
  quantiteBl: number | null
  quantiteRecue: number | null
  manquantFournisseur: number | null
  prixConvenu: number | null
  montantAttendu: number | null
  anomalies: string[]
}

export interface FactureFournisseurDetail {
  facture: FactureFournisseur
  lignes: LigneFactureControlee[]
}

export interface SaisieFactureFournisseur {
  fournisseurId: string
  numero: string
  dateFacture: string
  observation: string | null
  lignes: { numeroBl: string; produitId: string; quantite: number; prixUnitaire: number }[]
  version?: number
}

// ---------------------------------------------------------------- Personnel (M9)

export type FonctionEmploye = 'POMPISTE' | 'CAISSIER' | 'CHEF_PISTE' | 'GERANT' | 'GARDIEN' | 'AUTRE'

export type TypePoste = 'MATIN' | 'SOIR' | 'NUIT'

export interface Employe {
  id: string
  stationId: string
  stationCode: string
  matricule: string | null
  nom: string
  prenom: string
  nomComplet: string
  telephone: string | null
  fonction: FonctionEmploye
  dateEntree: string | null
  dateSortie: string | null
  /** Compte Jaugeo de l'employé, s'il saisit lui-même */
  identifiant: string | null
  actif: boolean
  version: number
}

export interface SaisieEmploye {
  matricule: string | null
  nom: string
  prenom: string
  telephone: string | null
  fonction: FonctionEmploye
  dateEntree: string | null
  dateSortie: string | null
  identifiant: string | null
  actif: boolean
  stationId?: string
  version?: number
}

export interface PersonnelStation {
  stationId: string
  stationCode: string
  stationNom: string
  /** La station tient des feuilles de caisse par pompiste */
  feuillesCaisse: boolean
  gerer: boolean
  reglerFeuilles: boolean
  employes: Employe[]
}

export interface FeuilleResume {
  date: string
  journeeId: string | null
  stationCode: string
  poste: TypePoste
  du: number
  remis: number
  /** remis − dû : négatif = manquant */
  ecart: number
  observation: string | null
}

export interface EmployeDetail {
  employe: Employe
  affectations: { stationCode: string; stationNom: string; debut: string; fin: string | null }[]
  du: string
  au: string
  feuilles: FeuilleResume[]
  manquants: number
  excedents: number
}

export interface EcartEmploye {
  employeId: string
  employe: string
  fonction: FonctionEmploye
  actif: boolean
  feuilles: number
  du: number
  remis: number
  manquants: number
  excedents: number
  net: number
}

export interface EcartsStation {
  stationId: string
  mois: string
  lignes: EcartEmploye[]
  feuilles: number
  du: number
  remis: number
  manquants: number
  excedents: number
  net: number
}

export interface Creneau {
  date: string
  poste: TypePoste
  employeId: string
}

export interface PlanningSemaine {
  stationId: string
  du: string
  au: string
  modifiable: boolean
  employes: Employe[]
  creneaux: Creneau[]
}

export interface PistoletPoste {
  pistoletId: string
  code: string
  pompeCode: string
  produitCode: string
  indexDepart: number
  indexArrivee: number | null
  litresJournee: number | null
  prixUnitaire: number | null
}

export interface LigneFeuille {
  pistoletId: string
  pistoletCode: string
  pompeCode: string
  produitCode: string
  indexDebut: number
  indexFin: number
  remiseCuve: number
  litres: number | null
  prixUnitaire: number | null
  montant: number | null
  anomalie: string | null
}

export interface FeuilleCaisse {
  id: string
  employeId: string
  employe: string
  lignes: LigneFeuille[]
  litres: number
  du: number
  especes: number
  electronique: number
  bons: number
  cartes: number
  remis: number
  ecart: number
  observation: string | null
}

export interface PosteJournee {
  poste: TypePoste
  libelle: string
  observation: string | null
  feuilles: FeuilleCaisse[]
  du: number
  remis: number
  ecart: number
}

export interface PostesJournee {
  journeeId: string
  version: number
  statut: StatutJournee
  actif: boolean
  modifiable: boolean
  pistolets: PistoletPoste[]
  employes: Employe[]
  planning: Creneau[]
  postes: PosteJournee[]
  totaux: {
    litres: number
    du: number
    especes: number
    electronique: number
    bons: number
    cartes: number
    remis: number
    ecart: number
  }
  /** Ce qui s'oppose à la clôture */
  problemes: string[]
}

export interface SaisiePoste {
  version: number
  observation: string | null
  feuilles: {
    employeId: string
    especes: number
    electronique: number
    bons: number
    cartes: number
    observation: string | null
    lignes: { pistoletId: string; indexFin: number; remiseCuve: number | null }[]
  }[]
}

// ------------------------------------------------------------ Clôture mensuelle, compte d'exploitation, budget (lot 2, M12)

/** Un carburant dans le compte d'exploitation ; coût, marge ou valeur d'écart nuls = prix d'achat inconnu. */
export interface ProduitCompte {
  code: string
  libelle: string
  litres: number
  chiffreAffaires: number
  coutAchat: number | null
  marge: number | null
  ecartStockLitres: number
  ecartStockValeur: number | null
}

/**
 * Compte d'exploitation d'une station (ou du réseau : stationId nul) pour un mois.
 * Marge brute = marge carburants + marge articles − remises ;
 * résultat = marge brute + écarts de stock − dépenses + régularisations.
 */
export interface CompteExploitation {
  stationId: string | null
  stationCode: string
  stationNom: string
  produits: ProduitCompte[]
  chiffreAffaires: number
  coutAchat: number
  margeCarburants: number
  ventesArticles: number
  coutArticles: number
  margeArticles: number
  remises: number
  margeBrute: number
  tauxMarge: number | null
  ecartsStock: number
  depenses: number
  regularisations: number
  resultat: number
  /** Faux : un prix d'achat manque, la marge ne couvre que les produits dont le coût est connu */
  coutComplet: boolean
  prixManquants: string[]
  clos: boolean
  cloturePar: string | null
  clotureLe: string | null
}

export interface CompteReseau {
  mois: string
  stations: CompteExploitation[]
  total: CompteExploitation
}

export type StatutClotureMois = 'CLOS' | 'PRET' | 'BLOQUE'

export interface EtatClotureStation {
  stationId: string
  stationCode: string
  stationNom: string
  statut: StatutClotureMois
  blocages: string[]
  journees: number
  journeesValidees: number
  cloturePar: string | null
  clotureLe: string | null
}

export interface EtatClotureMois {
  mois: string
  stations: EtatClotureStation[]
  closes: number
  pretes: number
}

export type PosteRegularisation = 'CHIFFRE_AFFAIRES' | 'COUT_ACHAT' | 'STOCK' | 'DEPENSES' | 'AUTRE'

export interface Regularisation {
  id: string
  stationId: string
  stationCode: string
  stationNom: string
  moisConcerne: string
  moisComptable: string
  poste: PosteRegularisation
  /** Effet sur le résultat : positif = l'améliore */
  montant: number
  motif: string
  reference: string | null
  par: string
  le: string
}

export interface MoisBudget {
  litres: number
  montant: number
}

export interface ProduitBudget {
  produitId: string
  code: string
  libelle: string
  /** Douze mois, de janvier à décembre */
  mois: MoisBudget[]
}

export interface BudgetStation {
  stationId: string
  stationCode: string
  stationNom: string
  annee: number
  produits: ProduitBudget[]
  depenses: number[]
}

export interface SuiviMoisBudget {
  /** Nul pour le cumul des mois écoulés */
  mois: string | null
  ecoule: boolean
  litresBudget: number
  litresReel: number
  caBudget: number
  caReel: number
  depensesBudget: number
  depensesReel: number
}

export interface SuiviBudget {
  annee: number
  stationId: string | null
  mois: SuiviMoisBudget[]
  produits: { code: string; libelle: string; litresBudget: number; litresReel: number; caBudget: number; caReel: number }[]
  cumul: SuiviMoisBudget
}
