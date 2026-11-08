import Alert from '@mui/material/Alert'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { usePrixStation } from '../../api/requetes'
import type { Evenement, PeriodePrix } from '../../api/types'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { echeances } from '../../shared/echeances'
import { formaterDate, formaterDateHeureCourte, formaterNombre, formaterPrix, TYPES_EVENEMENT } from '../../shared/libelles'
import { useMaintenant } from '../../shared/temps'

export function PrixDeLaStation({ stationId }: { stationId: string }) {
  const prix = usePrixStation(stationId)
  const maintenant = new Date(useMaintenant()).toISOString()
  if (prix.isLoading) return <Chargement />
  if (prix.error) return <AlerteErreur erreur={prix.error} />
  const periodes = [...(prix.data ?? [])].sort((a, b) => b.debut.localeCompare(a.debut))
  if (periodes.length === 0) return <Typography color="text.secondary">Aucun prix défini pour cette station.</Typography>
  const etat = (p: PeriodePrix) =>
    p.debut > maintenant ? 'avenir' : p.fin === null || p.fin > maintenant ? 'courant' : 'passe'
  return (
    <TableContainer>
      <Table size="small" aria-label="Historique des prix">
        <TableHead>
          <TableRow>
            <TableCell>Produit</TableCell>
            <TableCell align="right">Prix</TableCell>
            <TableCell>Du</TableCell>
            <TableCell>Au</TableCell>
            <TableCell>Motif</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {periodes.map((p) => {
            const e = etat(p)
            return (
              <TableRow key={p.id} sx={{ opacity: e === 'passe' ? 0.6 : 1 }}>
                <TableCell>{p.produitLibelle}</TableCell>
                <TableCell align="right" sx={{ fontWeight: e === 'courant' ? 600 : 400 }}>
                  {formaterPrix(p.prixUnitaire)}
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDateHeureCourte(p.debut)}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {e === 'courant' && p.fin === null ? <Chip size="small" color="success" variant="outlined" label="En vigueur" /> : null}
                  {e === 'avenir' && <Chip size="small" color="info" label="Programmé" sx={{ mr: 1 }} />}
                  {p.fin !== null && formaterDateHeureCourte(p.fin)}
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{p.motif}</Typography>
                  {p.reference && (
                    <Typography variant="caption" color="text.secondary">
                      {p.reference}
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function details(e: Evenement): string {
  const morceaux: string[] = []
  if (e.indexAvant !== null) morceaux.push(`index ${formaterNombre(e.indexAvant)} → ${formaterNombre(e.indexApres)}`)
  if (e.referenceScelles) morceaux.push(`scellés ${e.referenceScelles}`)
  if (e.intervenant) morceaux.push(e.intervenant)
  if (e.commentaire) morceaux.push(e.commentaire)
  return morceaux.join(' · ')
}

export function HistoriqueEquipements({ evenements }: { evenements: Evenement[] }) {
  const alertes = echeances(evenements, useMaintenant())
  return (
    <>
      {alertes.length > 0 && (
        <Stack spacing={1} sx={{ mb: 2 }}>
          {alertes.map(({ evenement: e, joursRestants }) => (
            <Alert key={e.id} severity={joursRestants < 0 ? 'error' : 'warning'} sx={{ py: 0 }}>
              {e.equipement} : vérification {joursRestants < 0 ? 'dépassée depuis le' : 'prévue le'} {formaterDate(e.prochaineVerification)}
              {joursRestants >= 0 && ` (dans ${joursRestants} jour${joursRestants > 1 ? 's' : ''})`}.
            </Alert>
          ))}
        </Stack>
      )}
      {evenements.length === 0 ? (
        <Typography color="text.secondary">Aucun événement consigné.</Typography>
      ) : (
        <TableContainer>
          <Table size="small" aria-label="Historique des équipements">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Équipement</TableCell>
                <TableCell>Événement</TableCell>
                <TableCell>Détails</TableCell>
                <TableCell>Prochaine vérif.</TableCell>
                <TableCell>Saisi par</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {evenements.map((e) => (
                <TableRow key={e.id} hover>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDateHeureCourte(e.date)}</TableCell>
                  <TableCell>{e.equipement}</TableCell>
                  <TableCell>{TYPES_EVENEMENT[e.type]}</TableCell>
                  <TableCell>{details(e)}</TableCell>
                  <TableCell>{formaterDate(e.prochaineVerification)}</TableCell>
                  <TableCell>{e.creePar}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </>
  )
}
