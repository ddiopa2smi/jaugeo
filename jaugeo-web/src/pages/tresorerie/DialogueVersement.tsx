import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { api } from '../../api/client'
import { useRafraichirTresorerie } from '../../api/requetes'
import type { SaisieVersement, Versement } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { BoutonPiece } from '../../shared/PieceJointe'
import { aujourdhui } from '../../shared/tresorerie'


/**
 * Saisie d'un versement bancaire. Le gérant photographie le bordereau (BV) dans la foulée :
 * la comptabilité ne peut pas vérifier un versement sans sa pièce.
 */
export function DialogueVersement({
  stationId,
  stationCode,
  versement,
  montantSuggere,
  dateSuggeree,
  onFermer,
}: {
  stationId: string
  stationCode: string
  versement?: Versement | null
  montantSuggere?: number | null
  dateSuggeree?: string
  onFermer: () => void
}) {
  const rafraichir = useRafraichirTresorerie()
  const [dateDepot, setDateDepot] = useState(versement?.dateDepot ?? (dateSuggeree && dateSuggeree < aujourdhui() ? dateSuggeree : aujourdhui()))
  const [banque, setBanque] = useState(versement?.banque ?? '')
  const [referenceBv, setReferenceBv] = useState(versement?.referenceBv ?? '')
  const [montant, setMontant] = useState(
    versement ? String(versement.montant) : montantSuggere && montantSuggere > 0 ? String(Math.round(montantSuggere)) : '',
  )
  const [deposant, setDeposant] = useState(versement?.deposant ?? '')
  const [observation, setObservation] = useState(versement?.observation ?? '')
  const [fichier, setFichier] = useState<File | null>(null)
  // Versement déjà enregistré dont la pièce a été refusée : on ne renvoie que la pièce
  const enregistre = useRef<Versement | null>(null)
  const m = versNombre(montant)

  const enregistrement = useMutation({
    mutationFn: async () => {
      const corps: SaisieVersement = {
        version: versement?.version,
        stationId,
        dateDepot,
        banque: banque.trim(),
        referenceBv: referenceBv.trim(),
        montant: m!,
        deposant: videVersNull(deposant),
        observation: videVersNull(observation),
      }
      let v =
        enregistre.current ??
        (versement
          ? await api.put<Versement>(`/tresorerie/versements/${versement.id}`, corps)
          : await api.post<Versement>('/tresorerie/versements', corps))
      enregistre.current = v
      if (fichier) v = await api.fichier<Versement>(`/tresorerie/versements/${v.id}/piece?version=${v.version}`, fichier)
      return v
    },
    onSuccess: (v) => {
      rafraichir()
      notifier(
        v.piece ? 'Versement enregistré.' : 'Versement enregistré. Joignez la photo du bordereau pour la vérification.',
        v.piece ? 'success' : 'warning',
      )
      onFermer()
    },
    onError: () => rafraichir(),
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>
        {versement ? 'Corriger le versement' : 'Versement bancaire'} · {stationCode}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, pt: 1 }}>
          <TextField
            label="Date du dépôt"
            type="date"
            value={dateDepot}
            onChange={(e) => setDateDepot(e.target.value)}
            required
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: aujourdhui() } }}
          />
          <TextField label="Banque" value={banque} onChange={(e) => setBanque(e.target.value)} required slotProps={{ htmlInput: { maxLength: 60 } }} />
          <TextField
            label="N° du bordereau (BV)"
            value={referenceBv}
            onChange={(e) => setReferenceBv(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 40, autoComplete: 'off' } }}
          />
          <TextField
            label="Montant (F)"
            value={montant}
            onChange={(e) => setMontant(e.target.value)}
            required
            slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
            helperText={m !== null && m > 0 ? formaterPrix(m) : undefined}
          />
          <TextField label="Déposant" value={deposant} onChange={(e) => setDeposant(e.target.value)} slotProps={{ htmlInput: { maxLength: 80 } }} />
        </Box>
        <TextField
          label="Observation"
          value={observation}
          onChange={(e) => setObservation(e.target.value)}
          fullWidth
          multiline
          sx={{ mt: 2 }}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 2 }}>
          <BoutonPiece libelle="Photo du bordereau" onFichier={setFichier} piece={versement?.piece ?? null} />
          <Typography variant="body2" color="text.secondary">
            {fichier ? fichier.name : (versement?.piece?.nomFichier ?? 'Nécessaire pour la vérification')}
          </Typography>
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={
            enregistrement.isPending || !dateDepot || !banque.trim() || !referenceBv.trim() || m === null || m <= 0 || !Number.isInteger(m)
          }
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}
