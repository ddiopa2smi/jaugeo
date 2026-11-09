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
  bonsDirection: number
  bonsGerant: number
  depenses: number | null
  complements: number | null
  recouvrements: number
  montantAVerser: number | null
  soldeOuverture: number
  versements: Versement[]
  totalVersements: number
  soldeFin: number | null
  arrete: ArreteCaisse | null
  peutVerser: boolean
}

export interface LigneCaisse {
  date: string
  journeeId: string | null
  statutJournee: StatutJournee | null
  ventes: number | null
  bons: number | null
  depenses: number | null
  complements: number | null
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
  totalDepenses: number
  totalComplements: number
  totalRecouvrements: number
  totalMontants: number
  totalVersements: number
  totalEcarts: number
  soldeFin: number
  joursNonClotures: number
  lignes: LigneCaisse[]
  peutArreter: boolean
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
}

export interface SituationTresorerie {
  stations: SituationCaisse[]
  retards: number
  versementsAVerifier: number
  manquantsOuverts: number
  montantManquants: number
  delaiVersementJours: number
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
  version: number
}

export interface SaisieClient {
  code?: string
  nom: string
  telephone: string | null
  recouvrement: Recouvrement
  plafond: number | null
  actif: boolean
  observation: string | null
  version?: number
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
  peutGerer: boolean
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
  prixUnitaire: number
  montant: number
  recouvrement: Recouvrement
  immatriculation: string | null
  porteur: string | null
  creePar: string
  modifieLe: string
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
  recouvrements: RecouvrementJournee[]
  totalBonsDirection: number
  totalBonsGerant: number
  totalRecouvrements: number
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
}