import PriceChangeIcon from '@mui/icons-material/PriceChange'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as LienRouteur } from 'react-router'
import { api, messageErreur } from '../../api/client'
import { cles, useChangementsPrix, useGrillePrix } from '../../api/requetes'
import type { ChangementPrix, LigneGrille } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDateHeureCourte, formaterPrix } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { useMaintenant } from '../../shared/temps'
import { DialogueChangementPrix } from './DialogueChangementPrix'

function CellulePrix({ ligne }: { ligne: LigneGrille | undefined }) {
  if (!ligne) return <TableCell align="right" sx={{ color: 'text.disabled' }}>—</TableCell>
  return (
    <TableCell align="right">
      {ligne.prix === null ? (
        <Chip size="small" color="warning" variant="outlined" label="Prix manquant" />
      ) : (
        <Typography variant="body2" sx={{ fontWeight: 600 }} title={`Depuis le ${formaterDateHeureCourte(ligne.depuis)}`}>
          {formaterPrix(ligne.prix)}
        </Typography>
      )}
      {ligne.prochainPrix !== null && (
        <Typography variant="caption" color="info.main" sx={{ display: 'block' }}>
          → {formaterPrix(ligne.prochainPrix)} le {formaterDateHeureCourte(ligne.prochainDebut)}
        </Typography>
      )}
    </TableCell>
  )
}

function GrillePrix({ grille }: { grille: LigneGrille[] }) {
  const produits = [...new Map(grille.map((l) => [l.produitCode, l.produitLibelle])).entries()]
  const stations = [...new Map(grille.map((l) => [l.stationId, l])).values()]
  const manquants = grille.filter((l) => l.prix === null).length
  return (
    <Paper sx={{ mb: 3 }}>
      {manquants > 0 && (
        <Alert severity="warning" sx={{ borderRadius: 0 }}>
          {manquants} prix manquant(s) : les ventes de ces produits ne pourront pas être valorisées.
        </Alert>
      )}
      <TableContainer>
        <Table size="small" aria-label="Prix en vigueur">
          <TableHead>
            <TableRow>
              <TableCell>Station</TableCell>
              {produits.map(([code, libelle]) => (
                <TableCell key={code} align="right">
                  {libelle}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {stations.map((s) => (
              <TableRow key={s.stationId} hover>
                <TableCell>
                  <Box component={LienRouteur} to={`/stations/${s.stationId}`} sx={{ color: 'inherit', textDecoration: 'none' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {s.stationNom}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {s.stationCode}
                    </Typography>
                  </Box>
                </TableCell>
                {produits.map(([code]) => (
                  <CellulePrix key={code} ligne={grille.find((l) => l.stationId === s.stationId && l.produitCode === code)} />
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  )
}

/** « Gasoil 680 F → 755 F » ; plusieurs anciens prix ⇒ fourchette ; nombre de stations. */
function resumeLignes(c: ChangementPrix): string[] {
  const parProduit = new Map<string, ChangementPrix['lignes']>()
  for (const l of c.lignes) parProduit.set(l.produitLibelle, [...(parProduit.get(l.produitLibelle) ?? []), l])
  return [...parProduit.entries()].map(([libelle, lignes]) => {
    const anciens = [...new Set(lignes.map((l) => l.ancienPrix))]
    const nouveaux = [...new Set(lignes.map((l) => l.nouveauPrix))]
    const avant = anciens.length === 1 ? (anciens[0] === null ? 'nouveau' : formaterPrix(anciens[0])) : 'divers'
    const apres = nouveaux.length === 1 ? formaterPrix(nouveaux[0]) : 'divers'
    return `${libelle} : ${avant} → ${apres} (${lignes.length} station${lignes.length > 1 ? 's' : ''})`
  })
}

function Statut({ c }: { c: ChangementPrix }) {
  const maintenant = useMaintenant()
  if (c.annule) return <Chip size="small" variant="outlined" label="Annulé" />
  if (Date.parse(c.debut) > maintenant) return <Chip size="small" color="info" label="Programmé" />
  return <Chip size="small" color="success" variant="outlined" label="Appliqué" />
}

function HistoriqueChangements({ peutModifier }: { peutModifier: boolean }) {
  const changements = useChangementsPrix()
  const queryClient = useQueryClient()
  const [aAnnuler, setAAnnuler] = useState<ChangementPrix | null>(null)
  const annulation = useMutation({
    mutationFn: (c: ChangementPrix) => api.post<ChangementPrix>(`/prix/changements/${c.id}/annulation`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cles.prix })
      notifier('Changement de prix annulé : les prix précédents restent en vigueur.')
      setAAnnuler(null)
    },
    onError: (e) => {
      notifier(messageErreur(e), 'error')
      setAAnnuler(null)
    },
  })

  if (changements.isLoading) return <Chargement />
  if (changements.error) return <AlerteErreur erreur={changements.error} />

  return (
    <Paper>
      <TableContainer>
        <Table size="small" aria-label="Historique des changements de prix">
          <TableHead>
            <TableRow>
              <TableCell>Date d'effet</TableCell>
              <TableCell>Motif</TableCell>
              <TableCell>Prix</TableCell>
              <TableCell>Saisi par</TableCell>
              <TableCell>Statut</TableCell>
              {peutModifier && <TableCell />}
            </TableRow>
          </TableHead>
          <TableBody>
            {(changements.data ?? []).map((c) => (
              <TableRow key={c.id} hover sx={{ opacity: c.annule ? 0.55 : 1 }}>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDateHeureCourte(c.debut)}</TableCell>
                <TableCell>
                  <Typography variant="body2">{c.motif}</Typography>
                  {c.reference && (
                    <Typography variant="caption" color="text.secondary">
                      {c.reference}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  {resumeLignes(c).map((r) => (
                    <Typography key={r} variant="body2">
                      {r}
                    </Typography>
                  ))}
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{c.creePar}</Typography>
                  {c.annule && (
                    <Typography variant="caption" color="text.secondary">
                      annulé par {c.annulePar} le {formaterDateHeureCourte(c.annuleLe)}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  <Statut c={c} />
                </TableCell>
                {peutModifier && (
                  <TableCell>
                    {c.annulable && (
                      <Button size="small" color="error" onClick={() => setAAnnuler(c)}>
                        Annuler
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {aAnnuler && (
        <Dialog open onClose={() => setAAnnuler(null)}>
          <DialogTitle>Annuler ce changement de prix ?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Le changement prévu le {formaterDateHeureCourte(aAnnuler.debut)} ({aAnnuler.motif}) ne s'appliquera pas ; les prix
              actuels resteront en vigueur. L'annulation est tracée dans l'historique.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setAAnnuler(null)}>Garder</Button>
            <Button color="error" variant="contained" loading={annulation.isPending} onClick={() => annulation.mutate(aAnnuler)}>
              Annuler le changement
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Paper>
  )
}

export function PagePrix() {
  const grille = useGrillePrix()
  const peutModifier = usePermission('PRIX_MODIFIER')
  const [ouvert, setOuvert] = useState(false)

  return (
    <>
      <EnTetePage
        titre="Prix de vente"
        sousTitre="Prix en vigueur par station et changements programmés (heure de Dakar)"
        actions={
          peutModifier && (
            <Button variant="contained" startIcon={<PriceChangeIcon />} onClick={() => setOuvert(true)} disabled={!grille.data}>
              Changement de prix
            </Button>
          )
        }
      />
      {grille.isLoading && <Chargement />}
      <AlerteErreur erreur={grille.error} />
      {grille.data && <GrillePrix grille={grille.data} />}
      <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>
        Historique des changements
      </Typography>
      <HistoriqueChangements peutModifier={peutModifier} />
      {ouvert && grille.data && <DialogueChangementPrix grille={grille.data} onFermer={() => setOuvert(false)} />}
    </>
  )
}
