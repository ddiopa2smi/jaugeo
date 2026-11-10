import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, useEtatCloture, useRegularisations } from '../../api/requetes'
import type { PosteRegularisation, Regularisation } from '../../api/types'
import { NomStation } from '../../shared/CarteStation'
import { libelleMois, moisPrecedent, POSTES_REGULARISATION } from '../../shared/cloture'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { moisCourant } from '../../shared/stock'

const chiffres = { whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' } as const

function DialogueRegularisation({ moisInitial, onFermer }: { moisInitial: string; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [mois, setMois] = useState(moisInitial)
  const [stationId, setStationId] = useState('')
  const [poste, setPoste] = useState<PosteRegularisation>('DEPENSES')
  const [sens, setSens] = useState<'charge' | 'produit'>('charge')
  const [montant, setMontant] = useState('')
  const [motif, setMotif] = useState('')
  const [reference, setReference] = useState('')
  // Seules les stations dont ce mois est clos se régularisent
  const etat = useEtatCloture(mois)
  const closes = (etat.data?.stations ?? []).filter((s) => s.statut === 'CLOS')
  const valeur = versNombre(montant)
  const enregistrement = useMutation({
    mutationFn: () =>
      api.post<Regularisation>('/cloture/regularisations', {
        stationId,
        moisConcerne: mois,
        poste,
        montant: (sens === 'charge' ? -1 : 1) * Math.abs(valeur ?? 0),
        motif: motif.trim(),
        reference: videVersNull(reference),
      }),
    onSuccess: (r) => {
      notifier(`Régularisation enregistrée : elle compte dans le résultat de ${libelleMois(r.moisComptable)}.`)
      onFermer()
      return queryClient.invalidateQueries({ queryKey: cles.cloture })
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Régulariser un mois clos</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Le mois clos ne change pas : la correction s'ajoute au résultat du mois en cours, sur une ligne à part. Elle reste dans
          l'historique ; une erreur se corrige par une régularisation contraire.
        </Typography>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
          <TextField
            type="month"
            label="Mois concerné"
            value={mois}
            onChange={(e) => {
              if (!e.target.value) return
              setMois(e.target.value)
              setStationId('')
            }}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: moisPrecedent(moisCourant()) } }}
            required
          />
          <TextField
            select
            label="Station"
            value={stationId}
            onChange={(e) => setStationId(e.target.value)}
            required
            disabled={closes.length === 0}
            helperText={etat.isLoading ? 'Chargement…' : closes.length === 0 ? `Aucune station n'a clôturé ${libelleMois(mois)}.` : undefined}
          >
            {closes.map((s) => (
              <MenuItem key={s.stationId} value={s.stationId}>
                {s.stationNom}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Poste corrigé" value={poste} onChange={(e) => setPoste(e.target.value as PosteRegularisation)} required>
            {Object.entries(POSTES_REGULARISATION).map(([v, l]) => (
              <MenuItem key={v} value={v}>
                {l}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Montant (F CFA)"
            value={montant}
            onChange={(e) => setMontant(e.target.value)}
            required
            slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          />
        </Box>
        <ToggleButtonGroup
          exclusive
          fullWidth
          size="small"
          value={sens}
          onChange={(_e, v: 'charge' | 'produit' | null) => v && setSens(v)}
          aria-label="Effet sur le résultat"
          sx={{ mt: 2 }}
        >
          <ToggleButton value="charge" color="error">
            Diminue le résultat
          </ToggleButton>
          <ToggleButton value="produit" color="success">
            Augmente le résultat
          </ToggleButton>
        </ToggleButtonGroup>
        <TextField
          label="Motif"
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          required
          fullWidth
          multiline
          minRows={2}
          helperText="Ce qui a été découvert et pourquoi le montant est corrigé."
          slotProps={{ htmlInput: { maxLength: 500 } }}
          sx={{ mt: 2 }}
        />
        <TextField
          label="Référence de la pièce (facultatif)"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          fullWidth
          slotProps={{ htmlInput: { maxLength: 80 } }}
          sx={{ mt: 2, mb: 1 }}
        />
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !stationId || !valeur || valeur <= 0 || !motif.trim()}
        >
          Enregistrer la régularisation
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export function OngletRegularisations({ mois }: { mois: string }) {
  const annee = Number(mois.slice(0, 4))
  const requete = useRegularisations(annee)
  const [dialogue, setDialogue] = useState(false)
  const liste = requete.data
  // Par défaut, on régularise le dernier mois terminé
  const moisInitial = mois < moisCourant() ? mois : moisPrecedent(moisCourant())

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          Corrections des mois clos comptées en {annee}. Chacune vise un mois clôturé et s'ajoute au résultat du mois où elle est saisie.
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogue(true)} sx={{ flexShrink: 0 }}>
          Nouvelle régularisation
        </Button>
      </Stack>
      {!liste ? (
        requete.isLoading ? (
          <Chargement />
        ) : (
          <AlerteErreur erreur={requete.error} />
        )
      ) : liste.length === 0 ? (
        <Alert severity="info">Aucune régularisation en {annee}.</Alert>
      ) : (
        <Paper sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Régularisations">
            <TableHead>
              <TableRow>
                <TableCell>Station</TableCell>
                <TableCell>Mois corrigé</TableCell>
                <TableCell>Compté en</TableCell>
                <TableCell>Poste</TableCell>
                <TableCell align="right">Effet sur le résultat</TableCell>
                <TableCell>Motif</TableCell>
                <TableCell>Saisie</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {liste.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>
                    <NomStation nom={r.stationNom} />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{libelleMois(r.moisConcerne)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{libelleMois(r.moisComptable)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{POSTES_REGULARISATION[r.poste]}</TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 600, color: r.montant < 0 ? 'error.main' : 'success.main' }}>
                    {r.montant < 0 ? '−' : '+'} {formaterPrix(Math.abs(r.montant))}
                  </TableCell>
                  <TableCell sx={{ minWidth: 220 }}>
                    {r.motif}
                    {r.reference && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        Pièce : {r.reference}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {r.par}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {formaterDate(r.le.slice(0, 10))}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {dialogue && <DialogueRegularisation moisInitial={moisInitial} onFermer={() => setDialogue(false)} />}
    </>
  )
}
