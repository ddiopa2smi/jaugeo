import { zodResolver } from '@hookform/resolvers/zod'
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined'
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined'
import OpacityOutlined from '@mui/icons-material/OpacityOutlined'
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
import { type ReactNode, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { messageErreur } from '../api/client'
import { connecter } from '../auth/authApi'
import { useSession } from '../auth/session'
import { ChampTexte } from '../shared/composants'
import { Marque } from '../shared/Marque'
import { IllustrationStation } from './IllustrationStation'

const schema = z.object({
  identifiant: z.string().trim().min(1, "Saisissez votre identifiant"),
  motDePasse: z.string().min(1, 'Saisissez votre mot de passe'),
})
type Saisie = z.infer<typeof schema>

// Deux colonnes uniquement sur un grand écran piloté à la souris : tablettes et téléphones,
// même en paysage, gardent la carte de connexion centrée.
const BUREAU = '@media (min-width: 1200px) and (hover: hover) and (pointer: fine)'
const DEGRADE = 'linear-gradient(135deg, #0b4f6c 0%, #08384d 60%, #f5a623 160%)'

const POINTS: { icone: ReactNode; titre: string; texte: string }[] = [
  {
    icone: <EventAvailableOutlined />,
    titre: 'Journées clôturées sur tablette',
    texte: 'Index, ventes et contrôles à la clôture, même sans réseau.',
  },
  {
    icone: <OpacityOutlined />,
    titre: 'Stocks jaugés cuve par cuve',
    texte: 'Réceptions, écarts et alertes suivis au litre près.',
  },
  {
    icone: <AccountBalanceWalletOutlined />,
    titre: 'Recettes et versements rapprochés',
    texte: 'Caisse des gérants, bordereaux et créances clients.',
  },
]

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
        background: DEGRADE,
        [BUREAU]: { gridTemplateColumns: 'minmax(0, 1.15fr) minmax(460px, 1fr)', placeItems: 'stretch', p: 0, background: '#f4f6f8' },
      }}
    >
      {/* Présentation : grand écran seulement */}
      <Box
        component="aside"
        sx={{
          display: 'none',
          [BUREAU]: {
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            gap: 3,
            px: { lg: 7, xl: 10 },
            py: 5,
            color: '#ffffff',
            background: DEGRADE,
          },
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Box component="img" src="/favicon.svg" alt="" sx={{ width: 40, height: 40 }} />
          <Marque variant="h5" />
        </Stack>

        {/* Hauteur bornée par celle de la fenêtre : titre et points restent visibles sur un écran de 720 px */}
        <Box sx={{ display: 'grid', placeItems: 'center', minHeight: 0, '& > svg': { maxHeight: '36vh' } }}>
          <IllustrationStation />
        </Box>

        <Box>
          <Typography variant="h4" component="p" sx={{ fontWeight: 700, lineHeight: 1.25, maxWidth: 560 }}>
            Votre réseau de stations, piloté jour après jour.
          </Typography>
          <Stack spacing={2} sx={{ mt: 3 }}>
            {POINTS.map((p) => (
              <Stack key={p.titre} direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
                <Box
                  sx={{
                    flexShrink: 0,
                    display: 'grid',
                    placeItems: 'center',
                    width: 40,
                    height: 40,
                    borderRadius: 2,
                    bgcolor: 'rgba(255, 255, 255, 0.12)',
                    color: '#f5a623',
                  }}
                >
                  {p.icone}
                </Box>
                <Box>
                  <Typography sx={{ fontWeight: 600 }}>{p.titre}</Typography>
                  <Typography variant="body2" sx={{ opacity: 0.8 }}>
                    {p.texte}
                  </Typography>
                </Box>
              </Stack>
            ))}
          </Stack>
        </Box>
      </Box>

      {/* Connexion */}
      <Box
        sx={{
          width: '100%',
          display: 'grid',
          placeItems: 'center',
          [BUREAU]: { px: 6, py: 6 },
        }}
      >
        <Paper
          component="form"
          onSubmit={soumettre}
          noValidate
          sx={{
            p: { xs: 3, sm: 4 },
            width: '100%',
            maxWidth: 400,
            [BUREAU]: { maxWidth: 420, p: 5, boxShadow: '0 12px 40px rgba(11, 79, 108, 0.10)' },
          }}
        >
          <Stack spacing={2.5}>
            {/* En-tête de la carte : marque sur tablette et téléphone, titre sur grand écran */}
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', [BUREAU]: { display: 'none' } }}>
              <Box component="img" src="/favicon.svg" alt="" sx={{ width: 44, height: 44 }} />
              <Box>
                <Marque variant="h5" component="h1" sx={{ color: 'primary.main' }} />
                <Typography variant="body2" color="text.secondary">
                  Pilotage du réseau de stations
                </Typography>
              </Box>
            </Stack>
            <Box sx={{ display: 'none', [BUREAU]: { display: 'block' } }}>
              <Typography variant="h5" component="h1">
                Connexion
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Accédez à votre espace avec l'identifiant fourni par votre administrateur.
              </Typography>
            </Box>

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
    </Box>
  )
}
