import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
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
import { EnTeteTri, PuceEtat } from '../../shared/tableaux'
import { useMaintenant } from '../../shared/temps'
import { useTri } from '../../shared/tri'

export function PrixDeLaStation({ stationId }: { stationId: string }) {
  const prix = usePrixStation(stationId)
  const maintenant = new Date(useMaintenant()).toISOString()
  const tri = useTri<PeriodePrix, 'produit' | 'prix' | 'debut' | 'fin' | 'motif'>(
    { produit: (p) => p.produitLibelle, prix: (p) => p.prixUnitaire, debut: (p) => p.debut, fin: (p) => p.fin, motif: (p) => p.motif },
    'debut',
    'desc',
  )
  if (prix.isLoading) return <Chargement />
  if (prix.error) return <AlerteErreur erreur={prix.error} />
  const periodes = tri.trier(prix.data ?? [])
  if (periodes.length === 0) return <Typography color="text.secondary">Aucun prix défini pour cette station.</Typography>
  const etat = (p: PeriodePrix) =>
    p.debut > maintenant ? 'avenir' : p.fin === null || p.fin > maintenant ? 'courant' : 'passe'
  return (
    <TableContainer>
      <Table size="small" aria-label="Historique des prix">
        <TableHead>
          <TableRow>
            <EnTeteTri tri={tri} cle="produit">
              Produit
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="prix" align="right">
              Prix
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="debut">
              Du
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="fin">
              Au
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="motif">
              Motif
            </EnTeteTri>
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
                  {e === 'courant' && p.fin === null ? <PuceEtat ton="success" libelle="En vigueur" /> : null}
                  {e === 'avenir' && (
                    <Box component="span" sx={{ mr: 1 }}>
                      <PuceEtat ton="info" libelle="Programmé" />
                    </Box>
                  )}
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
  const tri = useTri<Evenement, 'date' | 'equipement' | 'type' | 'verification' | 'auteur'>(
    {
      date: (e) => e.date,
      equipement: (e) => e.equipement,
      type: (e) => TYPES_EVENEMENT[e.type],
      verification: (e) => e.prochaineVerification,
      auteur: (e) => e.creePar,
    },
    'date',
    'desc',
  )
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
                <EnTeteTri tri={tri} cle="date">
                  Date
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="equipement">
                  Équipement
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="type">
                  Événement
                </EnTeteTri>
                <TableCell>Détails</TableCell>
                <EnTeteTri tri={tri} cle="verification">
                  Prochaine vérif.
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="auteur">
                  Saisi par
                </EnTeteTri>
              </TableRow>
            </TableHead>
            <TableBody>
              {tri.trier(evenements).map((e) => (
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
