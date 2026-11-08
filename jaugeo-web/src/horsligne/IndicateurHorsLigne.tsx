import CloudOffIcon from '@mui/icons-material/CloudOff'
import CloudUploadIcon from '@mui/icons-material/CloudUpload'
import SyncProblemIcon from '@mui/icons-material/SyncProblem'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemText from '@mui/material/ListItemText'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { Link as LienRouteur } from 'react-router'
import { formaterDateHeureCourte } from '../shared/libelles'
import { abandonner, LIBELLES_ENVOI, reessayer, synchroniser, useHorsLigne, type Envoi } from './file'

/** Saisie gardée dans la tablette : en attente d'envoi, ou refusée par le serveur. */
export function LigneEnvoi({ envoi, lien = false }: { envoi: Envoi; lien?: boolean }) {
  const conflit = envoi.etat === 'CONFLIT'
  return (
    <ListItem
      disableGutters
      alignItems="flex-start"
      secondaryAction={
        conflit && (
          <Stack direction="row" spacing={1}>
            <Button size="small" onClick={() => void reessayer(envoi.id!)}>
              Réessayer
            </Button>
            <Button size="small" color="error" onClick={() => void abandonner(envoi.id!)}>
              Abandonner
            </Button>
          </Stack>
        )
      }
      sx={{ pr: conflit ? 24 : 0 }}
      data-testid={`envoi-${envoi.type}`}
    >
      <ListItemText
        primary={
          <>
            {LIBELLES_ENVOI[envoi.type]}
            {lien ? (
              <>
                {' · '}
                <LienRouteur to={`/journees/${envoi.journeeId}`}>{envoi.journeeLibelle}</LienRouteur>
              </>
            ) : null}
          </>
        }
        secondary={
          <>
            Saisie le {formaterDateHeureCourte(envoi.creeLe)} ·{' '}
            {conflit ? (
              <Typography component="span" variant="body2" color="error">
                refusée par le serveur : {envoi.erreur?.message}
              </Typography>
            ) : (
              'en attente d’envoi'
            )}
          </>
        }
      />
    </ListItem>
  )
}

/** Barre d'application : réseau coupé, saisies en attente ou en conflit. */
export function IndicateurHorsLigne() {
  const enLigne = useHorsLigne((e) => e.enLigne)
  const envois = useHorsLigne((e) => e.envois)
  const synchronisation = useHorsLigne((e) => e.synchronisation)
  const [ouvert, setOuvert] = useState(false)
  const conflits = envois.filter((e) => e.etat === 'CONFLIT').length
  const attente = envois.length - conflits

  if (enLigne && envois.length === 0) return null
  const libelle = [
    !enLigne && 'Hors ligne',
    attente > 0 && `${attente} en attente`,
    conflits > 0 && `${conflits} refusée${conflits > 1 ? 's' : ''}`,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <>
      <Chip
        icon={conflits > 0 ? <SyncProblemIcon /> : enLigne ? <CloudUploadIcon /> : <CloudOffIcon />}
        label={libelle}
        color={conflits > 0 ? 'error' : 'warning'}
        onClick={() => setOuvert(true)}
        sx={{ mr: 1.5, '& .MuiChip-icon': { color: 'inherit' } }}
        aria-label="Saisies hors ligne"
      />
      <Dialog open={ouvert} onClose={() => setOuvert(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Saisies gardées dans la tablette</DialogTitle>
        <DialogContent>
          {!enLigne && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Réseau indisponible. Gardez cette page ouverte : les saisies partent automatiquement au retour du réseau,
              dans l’ordre où elles ont été faites. Elles restent aussi dans la tablette si elle est éteinte.
            </Alert>
          )}
          {conflits > 0 && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Le serveur a refusé une saisie : les saisies suivantes de la même journée attendent. Ouvrez la journée pour
              vérifier son état, puis abandonnez la saisie refusée (et refaites-la si besoin) ou réessayez-la.
            </Alert>
          )}
          {envois.length === 0 ? (
            <Typography color="text.secondary">Aucune saisie en attente.</Typography>
          ) : (
            <List dense>
              {envois.map((e) => (
                <LigneEnvoi key={e.id} envoi={e} lien />
              ))}
            </List>
          )}
        </DialogContent>
        <DialogActions>
          {attente > 0 && (
            <Button onClick={() => void synchroniser()} disabled={synchronisation}>
              Envoyer maintenant
            </Button>
          )}
          <Button onClick={() => setOuvert(false)}>Fermer</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

/** Fiche journée : saisies de cette journée encore dans la tablette. */
export function EnvoisJournee({ journeeId }: { journeeId: string }) {
  const envois = useHorsLigne((e) => e.envois).filter((e) => e.journeeId === journeeId)
  if (envois.length === 0) return null
  const conflit = envois.some((e) => e.etat === 'CONFLIT')
  return (
    <Alert severity={conflit ? 'error' : 'warning'} sx={{ mb: 2 }} data-testid="envois-journee">
      {conflit
        ? 'Une saisie faite hors ligne a été refusée par le serveur. Les valeurs affichées sont celles du serveur.'
        : 'Saisies faites hors ligne, pas encore reçues par le serveur : elles partiront au retour du réseau. Les totaux affichés seront mis à jour à l’envoi.'}
      <List dense disablePadding>
        {envois.map((e) => (
          <LigneEnvoi key={e.id} envoi={e} />
        ))}
      </List>
    </Alert>
  )
}
