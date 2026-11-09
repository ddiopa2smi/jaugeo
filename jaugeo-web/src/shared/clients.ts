import type { ModeRecharge, ModeReglementClient, ModeRelance, Recouvrement, StatutBon, StatutCarte, TypeEngin } from '../api/types'

export const RECOUVREMENTS: Record<Recouvrement, string> = {
  DIRECTION: 'Recouvré par la direction',
  GERANT: 'Recouvré par le gérant',
}

export const MODES_REGLEMENT_CLIENT: Record<ModeReglementClient, string> = {
  ESPECES_STATION: 'Espèces à la station',
  ESPECES_SIEGE: 'Espèces au siège',
  VIREMENT: 'Virement',
  CHEQUE: 'Chèque',
  COMPENSATION: 'Compensation',
}

/** Modes saisis par la comptabilité (les espèces de la station se saisissent dans la journée). */
export const MODES_SIEGE: ModeReglementClient[] = ['VIREMENT', 'CHEQUE', 'ESPECES_SIEGE', 'COMPENSATION']


export const STATUTS_BON: Record<StatutBon, string> = {
  VALIDE: 'Validé',
  EN_ATTENTE: 'En attente de dérogation',
  ACCORDE: 'Dérogation accordée',
  REFUSE: 'Refusé (à recouvrer par le gérant)',
}

export const TYPES_ENGIN: Record<TypeEngin, string> = { VEHICULE: 'Véhicule', PIROGUE: 'Pirogue' }

export const STATUTS_CARTE: Record<StatutCarte, string> = { ACTIVE: 'Active', BLOQUEE: 'Bloquée', ANNULEE: 'Annulée' }

export const MODES_RECHARGE: Record<ModeRecharge, string> = {
  VIREMENT: 'Virement',
  CHEQUE: 'Chèque',
  ESPECES_SIEGE: 'Espèces au siège',
  WAVE: 'Wave',
  ORANGE_MONEY: 'Orange Money',
}

export const MODES_RELANCE: Record<ModeRelance, string> = {
  COURRIER: 'Courrier',
  EMAIL: 'E-mail',
  TELEPHONE: 'Téléphone',
  VISITE: 'Visite',
}

export const NIVEAUX_RELANCE: Record<number, string> = { 1: 'Rappel', 2: 'Relance', 3: 'Mise en demeure' }