import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles } from '../../api/requetes'
import type { BonEnAttente } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

/** Bon à décider : champs communs à la liste des bons en attente et à la section crédit de la journée. */
export interface BonADecider {
  id: string
  version: number
  numero: string
  clientNom: string
  montant: number
  litres: number
  motifBlocage: string | null
  date?: string
  stationCode?: string
}

/**
 * Décision de la Direction sur un bon en attente : accord (dérogation tracée, motif obligatoire) ou refus
 * (le bon reste dû par le client mais devient à recouvrer par le gérant).
 */
export function DialogueDecisionBon({ bon, journeeId, onFermer }: { bon: BonADecider; journeeId?: string; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [motif, setMotif] = useState('')
  const decision = useMutation({
    mutationFn: (accord: boolean) =>
      api.post<BonEnAttente[]>(`/clients/bons/${bon.id}/decision`, { version: bon.version, accord, motif: motif.trim() }),
    onSuccess: (liste, accord) => {
      queryClient.setQueryData(cles.bonsEnAttente, liste)
      queryClient.invalidateQueries({ queryKey: cles.clients })
      if (journeeId) {
        queryClient.invalidateQueries({ queryKey: cles.creditJournee(journeeId) })
        queryClient.invalidateQueries({ queryKey: cles.journee(journeeId) })
      }
      notifier(accord ? 'Dérogation accordée.' : 'Bon refusé : il est à recouvrer par le gérant.')
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Bon n° {bon.numero} en attente</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2">
            {bon.clientNom} · {formaterNombre(bon.litres)} L · <strong>{formaterPrix(bon.montant)}</strong>
            {bon.date && ` · ${formaterDate(bon.date)}`}
            {bon.stationCode && ` · ${bon.stationCode}`}
          </Typography>
          {bon.motifBlocage && <Alert severity="warning">{bon.motifBlocage}</Alert>}
          <TextField
            label="Motif de la décision"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            required
            multiline
            minRows={2}
            helperText="Tracé dans l'historique du bon. En cas de refus, le bon est à recouvrer par le gérant."
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={decision.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button color="error" onClick={() => decision.mutate(false)} disabled={decision.isPending || !motif.trim()}>
          Refuser
        </Button>
        <Button variant="contained" onClick={() => decision.mutate(true)} disabled={decision.isPending || !motif.trim()}>
          Accorder la dérogation
        </Button>
      </DialogActions>
    </Dialog>
  )
}
