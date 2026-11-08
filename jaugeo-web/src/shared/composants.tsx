import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { ComponentProps, ReactNode } from 'react'
import { Controller, type Control, type FieldPath, type FieldValues } from 'react-hook-form'
import { messageErreur } from '../api/client'
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
