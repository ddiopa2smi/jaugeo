import AttachFileIcon from '@mui/icons-material/AttachFile'
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import { useRef } from 'react'
import { messageErreur, ouvrirPiece } from '../api/client'
import type { PieceJointe } from '../api/types'
import { notifier } from './notifications'
import { erreurPiece, TYPES_PIECE } from './tresorerie'

/** Bouton « photo / fichier » : sur téléphone, propose l'appareil photo ou la galerie. */
export function BoutonPiece({
  libelle,
  onFichier,
  disabled,
  piece,
}: {
  libelle: string
  onFichier: (f: File) => void
  disabled?: boolean
  piece?: PieceJointe | null
}) {
  const champ = useRef<HTMLInputElement>(null)
  return (
    <>
      <input
        ref={champ}
        type="file"
        hidden
        accept={TYPES_PIECE}
        aria-label={libelle}
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (!f) return
          const erreur = erreurPiece(f)
          if (erreur) notifier(erreur, 'error')
          else onFichier(f)
        }}
      />
      <Button size="small" startIcon={<PhotoCameraIcon />} onClick={() => champ.current?.click()} disabled={disabled}>
        {piece ? 'Remplacer' : libelle}
      </Button>
    </>
  )
}

/** Ouvre la pièce jointe (requête authentifiée, affichée dans un nouvel onglet). */
export function VoirPiece({ chemin, piece }: { chemin: string; piece: PieceJointe }) {
  return (
    <Tooltip title={`${piece.nomFichier} (${Math.max(1, Math.round(piece.taille / 1024))} Ko)`}>
      <IconButton
        size="small"
        aria-label={`Voir ${piece.nomFichier}`}
        onClick={() => ouvrirPiece(chemin).catch((e) => notifier(messageErreur(e), 'error'))}
      >
        <AttachFileIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  )
}
