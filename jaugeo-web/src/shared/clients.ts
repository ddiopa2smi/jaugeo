import type { ModeReglementClient, Recouvrement } from '../api/types'

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

