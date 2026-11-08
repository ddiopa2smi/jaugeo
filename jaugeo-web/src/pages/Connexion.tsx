import { zodResolver } from '@hookform/resolvers/zod'
import Visibility from '@mui/icons-material/Visibility'
import VisibilityOff from '@mui/icons-material/VisibilityOff'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { messageErreur } from '../api/client'
import { connecter } from '../auth/authApi'
import { useSession } from '../auth/session'
import { ChampTexte } from '../shared/composants'

const schema = z.object({
  identifiant: z.string().trim().min(1, "Saisissez votre identifiant"),
  motDePasse: z.string().min(1, 'Saisissez votre mot de passe'),
})
type Saisie = z.infer<typeof schema>

export function PageConnexion() {
  const utilisateur = useSession((e) => e.utilisateur)
  const motif = useSession((e) => e.motif)
  const navigate = useNavigate()
  const location = useLocation()
  const depuis = (location.state as { depuis?: string } | null)?.depuis ?? '/'
  const [erreur, setErreur] = useState<string | null>(null)
  const [visible, setVisible] = useState(false)
  const { control, handleSubmit, formState } = useForm<Saisie>({
    resolver: zodResolver(schema),
    defaultValues: { identifiant: '', motDePasse: '' },
  })

  if (utilisateur) return <Navigate to={depuis} replace />

  const soumettre = handleSubmit(async ({ identifiant, motDePasse }) => {
    setErreur(null)
    try {
      await connecter(identifiant, motDePasse)
      navigate(depuis, { replace: true })
    } catch (e) {
      setErreur(messageErreur(e))
    }
  })

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        p: 2,
        background: 'linear-gradient(135deg, #0b4f6c 0%, #08384d 60%, #f5a623 160%)',
      }}
    >
      <Paper component="form" onSubmit={soumettre} noValidate sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 400 }}>
        <Stack spacing={2.5}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Box component="img" src="/favicon.svg" alt="" sx={{ width: 44, height: 44 }} />
            <Box>
              <Typography variant="h5" component="h1">
                Jaugeo
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Pilotage du réseau de stations
              </Typography>
            </Box>
          </Stack>

          {motif && !erreur && <Alert severity="info">{motif}</Alert>}
          {erreur && <Alert severity="error">{erreur}</Alert>}

          <ChampTexte control={control} nom="identifiant" libelle="Identifiant" autoFocus autoComplete="username" />
          <ChampTexte
            control={control}
            nom="motDePasse"
            libelle="Mot de passe"
            type={visible ? 'text' : 'password'}
            autoComplete="current-password"
            slotProps={{
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                      onClick={() => setVisible((v) => !v)}
                      edge="end"
                      size="small"
                    >
                      {visible ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />
          <Button type="submit" variant="contained" size="large" loading={formState.isSubmitting}>
            Se connecter
          </Button>
          <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
            Mot de passe oublié ? Demandez une réinitialisation à votre administrateur.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  )
}
