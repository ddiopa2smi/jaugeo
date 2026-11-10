import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import LockIcon from '@mui/icons-material/Lock'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { cles, useEtatCloture } from '../../api/requetes'
import type { EtatClotureMois, EtatClotureStation, StatutClotureMois } from '../../api/types'
import { PastilleStation } from '../../shared/CarteStation'
import { libelleMois } from '../../shared/cloture'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterDateHeureCourte } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const STATUTS: Record<StatutClotureMois, { libelle: string; couleur: 'success' | 'primary' | 'warning' }> = {
  CLOS: { libelle: 'Clôturé', couleur: 'success' },
  PRET: { libelle: 'Prêt à clôturer', couleur: 'primary' },
  BLOQUE: { libelle: 'À compléter', couleur: 'warning' },
}

function CarteEtat({ e, mois, onCloturer, occupe }: { e: EtatClotureStation; mois: string; onCloturer: () => void; occupe: boolean }) {
  const statut = STATUTS[e.statut]
  return (
    <Paper sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }} data-testid={`etat-${e.stationCode}`}>
      <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
        <PastilleStation teinte={statut.couleur} taille={40} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" noWrap sx={{ fontWeight: 600, lineHeight: 1.3 }}>
            {e.stationNom}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {e.journees === 0 ? 'Aucune journée' : `${e.journeesValidees} journée${e.journeesValidees > 1 ? 's' : ''} validée${e.journeesValidees > 1 ? 's' : ''} sur ${e.journees}`}
          </Typography>
        </Box>
        <Chip size="small" color={statut.couleur} variant={e.statut === 'BLOQUE' ? 'outlined' : 'filled'} label={statut.libelle} />
      </Stack>

      {e.statut === 'CLOS' && (
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}>
          <LockIcon fontSize="small" />
          <Typography variant="body2">
            Clôturé par {e.cloturePar} le {formaterDateHeureCourte(e.clotureLe)}
          </Typography>
        </Stack>
      )}
      {e.statut === 'BLOQUE' && (
        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
          {e.blocages.map((b) => (
            <Typography key={b} component="li" variant="body2">
              {b}
            </Typography>
          ))}
        </Box>
      )}
      {e.statut === 'PRET' && (
        <Button
          variant="contained"
          startIcon={<LockIcon />}
          disabled={occupe}
          onClick={() =>
            confirmerPuis(
              {
                titre: `Clôturer ${libelleMois(mois)} ?`,
                message: `Le compte d'exploitation de ${e.stationNom} sera figé et le mois verrouillé : plus aucune écriture ne pourra y être datée. Cette clôture ne se défait pas ; une erreur se corrigera par une régularisation.`,
                action: 'Clôturer le mois',
              },
              onCloturer,
            )
          }
          sx={{ alignSelf: 'flex-start' }}
        >
          Clôturer le mois
        </Button>
      )}
    </Paper>
  )
}

export function OngletEtat({ mois }: { mois: string }) {
  const requete = useEtatCloture(mois)
  const queryClient = useQueryClient()
  const rafraichir = () => queryClient.invalidateQueries({ queryKey: cles.cloture })
  const cloture = useMutation({
    mutationFn: (e: EtatClotureStation) => api.post<EtatClotureStation>(`/cloture/stations/${e.stationId}/mois/${mois}`),
    onSuccess: (e) => {
      notifier(`${e.stationNom} : ${libelleMois(mois)} clôturé.`)
      return rafraichir()
    },
  })
  const toutes = useMutation({
    mutationFn: () => api.post<EtatClotureMois>(`/cloture/mois/${mois}`),
    onSuccess: (e) => {
      notifier(`${e.closes} station${e.closes > 1 ? 's' : ''} clôturée${e.closes > 1 ? 's' : ''} pour ${libelleMois(mois)}.`)
      return rafraichir()
    },
  })
  const etat = requete.data
  if (!etat) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  if (etat.stations.length === 0) return <Alert severity="info">Aucune station dans votre périmètre pour ce mois.</Alert>
  const occupe = cloture.isPending || toutes.isPending
  const tout = etat.closes === etat.stations.length

  return (
    <>
      <Paper sx={{ p: 2, mb: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        {tout && <CheckCircleIcon color="success" />}
        <Box sx={{ flexGrow: 1, minWidth: 200 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }} data-testid="avancement-cloture">
            {etat.closes} station{etat.closes > 1 ? 's' : ''} clôturée{etat.closes > 1 ? 's' : ''} sur {etat.stations.length}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Une station se clôture quand le mois est terminé, que toutes ses journées sont validées, ses versements vérifiés et le mois
            précédent clos.
          </Typography>
        </Box>
        {etat.pretes > 1 && (
          <Button
            variant="outlined"
            startIcon={<LockIcon />}
            disabled={occupe}
            onClick={() =>
              confirmerPuis(
                {
                  titre: `Clôturer ${etat.pretes} stations ?`,
                  message: `Le mois de ${libelleMois(mois)} sera figé et verrouillé pour les ${etat.pretes} stations prêtes. Cette clôture ne se défait pas.`,
                  action: 'Clôturer',
                },
                () => toutes.mutate(),
              )
            }
          >
            Clôturer les {etat.pretes} stations prêtes
          </Button>
        )}
      </Paper>
      <AlerteErreur erreur={cloture.error ?? toutes.error} />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))', alignItems: 'start' }}>
        {etat.stations.map((e) => (
          <CarteEtat key={e.stationId} e={e} mois={mois} occupe={occupe} onCloturer={() => cloture.mutate(e)} />
        ))}
      </Box>
    </>
  )
}
