import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useSession } from '../auth/session'
import { EnTetePage } from '../shared/composants'
import { PERIMETRES } from '../shared/libelles'
import { FormulaireMotDePasse } from './ChangementMotDePasse'

export function MonCompte() {
  const u = useSession((e) => e.utilisateur)!
  return (
    <>
      <EnTetePage titre="Mon compte" />
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, alignItems: 'start' }}>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
            Profil
          </Typography>
          <Stack spacing={1.5}>
            <Box>
              <Typography variant="caption" color="text.secondary">Nom</Typography>
              <Typography>{u.nomComplet}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Identifiant</Typography>
              <Typography>{u.identifiant}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Rôle</Typography>
              <Typography>{u.roleLibelle}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Périmètre</Typography>
              <Typography>{PERIMETRES[u.perimetre]}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 0.5 }}>
                Droits
              </Typography>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                {u.permissions.map((p) => (
                  <Chip key={p} size="small" variant="outlined" label={p.replaceAll('_', ' ').toLowerCase()} />
                ))}
              </Stack>
            </Box>
          </Stack>
        </Paper>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
            Changer mon mot de passe
          </Typography>
          <FormulaireMotDePasse apresSucces={() => undefined} />
        </Paper>
      </Box>
    </>
  )
}
