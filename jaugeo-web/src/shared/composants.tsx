import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type ComponentProps, type ReactNode } from 'react'
import { Controller, type Control, type FieldPath, type FieldValues } from 'react-hook-form'
import { messageErreur } from '../api/client'
import { useConfirmation } from './confirmation'
import { useNotification } from './notifications'

export function EnTetePage({ titre, sousTitre, actions }: { titre: string; sousTitre?: string; actions?: ReactNode }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ mb: 3, alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between' }}
    >
      <Box>
        <Typography variant="h5" component="h1">
          {titre}
        </Typography>
        {sousTitre && (
          <Typography variant="body2" color="text.secondary">
            {sousTitre}
          </Typography>
        )}
      </Box>
      {actions && <Stack direction="row" spacing={1}>{actions}</Stack>}
    </Stack>
  )
}

export function Chargement({ texte = 'Chargement…' }: { texte?: string }) {
  return (
    <Stack direction="row" spacing={2} sx={{ p: 4, alignItems: 'center', justifyContent: 'center' }} role="status">
      <CircularProgress size={24} />
      <Typography color="text.secondary">{texte}</Typography>
    </Stack>
  )
}

export function AlerteErreur({ erreur }: { erreur: unknown }) {
  if (!erreur) return null
  return (
    <Alert severity="error" sx={{ mb: 2 }}>
      {messageErreur(erreur)}
    </Alert>
  )
}

export function PuceStatut({ actif, libelleInactif = 'Inactif' }: { actif: boolean; libelleInactif?: string }) {
  return actif ? (
    <Chip size="small" color="success" variant="outlined" label="Actif" />
  ) : (
    <Chip size="small" variant="outlined" label={libelleInactif} />
  )
}

/** Modale de confirmation commune à toutes les suppressions (voir `confirmer`). */
export function ZoneConfirmation() {
  const { demande, repondre } = useConfirmation()
  // Le dernier texte reste affiché pendant la fermeture de la modale
  const [affichee, setAffichee] = useState(demande)
  if (demande && demande !== affichee) setAffichee(demande)
  return (
    <Dialog open={demande !== null} onClose={() => repondre(false)} maxWidth="xs" fullWidth aria-labelledby="titre-confirmation">
      <DialogTitle id="titre-confirmation">{affichee?.titre}</DialogTitle>
      <DialogContent>
        <DialogContentText>{affichee?.message}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button autoFocus onClick={() => repondre(false)}>
          Annuler
        </Button>
        <Button color="error" variant="contained" onClick={() => repondre(true)}>
          {affichee?.action ?? 'Supprimer'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export function ZoneNotification() {
  const { message, gravite, cle, fermer } = useNotification()
  return (
    <Snackbar
      key={cle}
      open={message !== null}
      autoHideDuration={gravite === 'error' ? 8000 : 4000}
      onClose={(_, raison) => raison !== 'clickaway' && fermer()}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
    >
      <Alert severity={gravite} variant="filled" onClose={fermer} sx={{ width: '100%' }}>
        {message}
      </Alert>
    </Snackbar>
  )
}

/** Champ texte relié à react-hook-form. */
export function ChampTexte<T extends FieldValues>({
  control,
  nom,
  libelle,
  aide,
  ...autres
}: {
  control: Control<T>
  nom: FieldPath<T>
  libelle: string
  aide?: string
  type?: string
  required?: boolean
  disabled?: boolean
  autoFocus?: boolean
  autoComplete?: string
  multiline?: boolean
  select?: boolean
  children?: ReactNode
  slotProps?: ComponentProps<typeof TextField>['slotProps']
}) {
  return (
    <Controller
      control={control}
      name={nom}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          id={`champ-${nom}`}
          label={libelle}
          error={!!fieldState.error}
          helperText={fieldState.error?.message ?? aide}
          {...autres}
        />
      )}
    />
  )
}
