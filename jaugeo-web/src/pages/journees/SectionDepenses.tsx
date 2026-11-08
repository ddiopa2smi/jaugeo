import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import GavelIcon from '@mui/icons-material/Gavel'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, rafraichirJournee, useDepensesJournee, useNaturesDepense } from '../../api/requetes'
import type { Depense, DepensesJournee, SaisieDepense } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { BoutonPiece, VoirPiece } from '../../shared/PieceJointe'
import { STATUTS_DEPENSE } from '../../shared/tresorerie'

/** La saisie d'une dépense incrémente la version de la journée : fiche, stock et recette à relire. */
function useMajDepenses(journeeId: string) {
  const queryClient = useQueryClient()
  return (d: DepensesJournee) => {
    queryClient.setQueryData(cles.depensesJournee(journeeId), d)
    rafraichirJournee(queryClient, journeeId, cles.depensesJournee(journeeId))
    queryClient.invalidateQueries({ queryKey: cles.depensesAValider })
  }
}

function DialogueDepense({ depenses, depense, onFermer }: { depenses: DepensesJournee; depense: Depense | null; onFermer: () => void }) {
  const natures = useNaturesDepense()
  const maj = useMajDepenses(depenses.journeeId)
  const [natureId, setNatureId] = useState(depense?.natureId ?? '')
  const [libelle, setLibelle] = useState(depense?.libelle ?? '')
  const [beneficiaire, setBeneficiaire] = useState(depense?.beneficiaire ?? '')
  const [montant, setMontant] = useState(depense ? String(depense.montant) : '')
  const [fichier, setFichier] = useState<File | null>(null)

  const choix = (natures.data ?? []).filter((n) => n.actif || n.id === depense?.natureId)
  const nature = choix.find((n) => n.id === natureId)
  const m = versNombre(montant)
  const plafond = nature?.plafond ?? null
  const auDela = plafond !== null && m !== null && m > plafond

  const enregistrement = useMutation({
    mutationFn: async () => {
      const corps: SaisieDepense = {
        version: depenses.version,
        natureId,
        libelle: libelle.trim(),
        beneficiaire: videVersNull(beneficiaire),
        montant: m!,
      }
      const base = `/journees/${depenses.journeeId}/depenses`
      let r = depense ? await api.put<DepensesJournee>(`${base}/${depense.id}`, corps) : await api.post<DepensesJournee>(base, corps)
      if (fichier) {
        const avant = new Set(depenses.depenses.map((x) => x.id))
        const cible = depense ?? r.depenses.find((x) => !avant.has(x.id))
        if (cible) {
          // Dépense enregistrée même si la pièce est ensuite refusée
          maj(r)
          r = await api.fichier<DepensesJournee>(`${base}/${cible.id}/justificatif?version=${r.version}`, fichier)
        }
      }
      return r
    },
    onSuccess: (r) => {
      maj(r)
      const avant = new Set(depenses.depenses.map((x) => x.id))
      const d = r.depenses.find((x) => (depense ? x.id === depense.id : !avant.has(x.id)))
      const attente = d?.statut === 'A_VALIDER'
      notifier(
        attente ? 'Dépense enregistrée. Au-delà du plafond : le superviseur doit l’autoriser avant la clôture.' : 'Dépense enregistrée.',
        attente ? 'warning' : 'success',
      )
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>{depense ? 'Corriger la dépense' : 'Dépense payée sur la recette'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField select label="Nature" value={natureId} onChange={(e) => setNatureId(e.target.value)} required>
            {choix.map((n) => (
              <MenuItem key={n.id} value={n.id}>
                {n.libelle}
                {n.plafond !== null ? ` (plafond ${formaterPrix(n.plafond)})` : ''}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Objet" value={libelle} onChange={(e) => setLibelle(e.target.value)} required slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField
            label="Bénéficiaire"
            value={beneficiaire}
            onChange={(e) => setBeneficiaire(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 120 } }}
          />
          <TextField
            label="Montant (F)"
            value={montant}
            onChange={(e) => setMontant(e.target.value)}
            required
            error={auDela}
            slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
            helperText={
              auDela
                ? `Au-delà du plafond de ${formaterPrix(plafond)} : validation du superviseur nécessaire.`
                : plafond !== null
                  ? `Plafond sans validation : ${formaterPrix(plafond)}`
                  : undefined
            }
          />
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <BoutonPiece libelle="Photo du justificatif" onFichier={setFichier} piece={depense?.justificatif ?? null} />
            <Typography variant="body2" color="text.secondary">
              {fichier
                ? fichier.name
                : (depense?.justificatif?.nomFichier ?? (depenses.justificatifObligatoire ? 'Justificatif obligatoire' : 'Facultatif'))}
            </Typography>
          </Stack>
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !natureId || !libelle.trim() || m === null || m <= 0 || !Number.isInteger(m)}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueDecision({ depenses, depense, onFermer }: { depenses: DepensesJournee; depense: Depense; onFermer: () => void }) {
  const maj = useMajDepenses(depenses.journeeId)
  const [motif, setMotif] = useState('')
  const decision = useMutation({
    mutationFn: (autoriser: boolean) =>
      api.post<DepensesJournee>(`/journees/${depenses.journeeId}/depenses/${depense.id}/decision`, {
        version: depenses.version,
        autoriser,
        motif: videVersNull(motif),
      }),
    onSuccess: (r, autoriser) => {
      maj(r)
      notifier(autoriser ? 'Dépense autorisée.' : 'Dépense refusée : elle reste à la charge du gérant.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Dépense au-delà du plafond</DialogTitle>
      <DialogContent>
        <Typography>
          {depense.natureLibelle} · {depense.libelle}
          {depense.beneficiaire ? ` · ${depense.beneficiaire}` : ''}
        </Typography>
        <Typography variant="h6" sx={{ my: 1 }}>
          {formaterPrix(depense.montant)}{' '}
          <Typography component="span" color="text.secondary">
            (plafond {formaterPrix(depense.plafond)})
          </Typography>
        </Typography>
        {!depense.justificatif && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Aucun justificatif joint.
          </Alert>
        )}
        <TextField
          label="Motif (obligatoire pour un refus)"
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
        <AlerteErreur erreur={decision.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button color="error" onClick={() => decision.mutate(false)} disabled={decision.isPending || !motif.trim()}>
          Refuser
        </Button>
        <Button variant="contained" color="success" onClick={() => decision.mutate(true)} disabled={decision.isPending}>
          Autoriser
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Récapitulatif des dépenses avant de confirmer la clôture. */
export function RecapDepenses({ depenses }: { depenses: DepensesJournee }) {
  if (depenses.depenses.length === 0) return null
  return (
    <Typography variant="body2" sx={{ mt: 2 }}>
      Dépenses autorisées : <strong>{formaterPrix(depenses.totalAutorise)}</strong>
      {depenses.totalRefuse > 0 && ` · refusées (à la charge du gérant) : ${formaterPrix(depenses.totalRefuse)}`}
    </Typography>
  )
}

export function SectionDepenses({ journeeId }: { journeeId: string }) {
  const requete = useDepensesJournee(journeeId)
  const maj = useMajDepenses(journeeId)
  const [edition, setEdition] = useState<Depense | 'nouvelle' | null>(null)
  const [decision, setDecision] = useState<Depense | null>(null)
  const d = requete.data

  const suppression = useMutation({
    mutationFn: (id: string) => api.delete<DepensesJournee>(`/journees/${journeeId}/depenses/${id}?version=${d!.version}`),
    onSuccess: (r) => {
      maj(r)
      notifier('Dépense retirée.')
    },
  })
  const jointure = useMutation({
    mutationFn: ({ id, fichier }: { id: string; fichier: File }) =>
      api.fichier<DepensesJournee>(`/journees/${journeeId}/depenses/${id}/justificatif?version=${d!.version}`, fichier),
    onSuccess: (r) => {
      maj(r)
      notifier('Justificatif joint.')
    },
  })

  if (!d) return <AlerteErreur erreur={requete.error} />

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1 }} aria-label="Dépenses de la journée">
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" color="text.secondary">
          Dépenses payées sur la recette
        </Typography>
        {d.modifiable && (
          <Button startIcon={<AddIcon />} onClick={() => setEdition('nouvelle')}>
            Ajouter une dépense
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={suppression.error ?? jointure.error} />
      {d.depenses.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ pb: 1 }}>
          Aucune dépense ce jour.
        </Typography>
      ) : (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Dépense</TableCell>
                <TableCell align="right">Montant</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell>Justificatif</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {d.depenses.map((x) => (
                <TableRow key={x.id} data-testid={`depense-${x.id}`}>
                  <TableCell>
                    {x.libelle}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {x.natureLibelle}
                      {x.beneficiaire ? ` · ${x.beneficiaire}` : ''}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterPrix(x.montant)}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={STATUTS_DEPENSE[x.statut].couleur} label={STATUTS_DEPENSE[x.statut].libelle} />
                    {x.decisionPar && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {x.decisionPar}
                        {x.motifDecision ? ` : ${x.motifDecision}` : ''}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {x.justificatif && <VoirPiece chemin={`/depenses/${x.id}/justificatif`} piece={x.justificatif} />}
                    {d.modifiable && (
                      <BoutonPiece
                        libelle="Joindre"
                        piece={x.justificatif}
                        disabled={jointure.isPending}
                        onFichier={(fichier) => jointure.mutate({ id: x.id, fichier })}
                      />
                    )}
                    {!x.justificatif && !d.modifiable && '—'}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {x.decidable && (
                      <Button size="small" startIcon={<GavelIcon />} onClick={() => setDecision(x)}>
                        Décider
                      </Button>
                    )}
                    {d.modifiable && (
                      <>
                        <IconButton size="small" aria-label={`Corriger ${x.libelle}`} onClick={() => setEdition(x)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label={`Retirer ${x.libelle}`}
                          onClick={() => suppression.mutate(x.id)}
                          disabled={suppression.isPending}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      {d.depenses.length > 0 && (
        <Typography variant="body2" sx={{ py: 1 }} data-testid="total-depenses">
          Autorisées : <strong>{formaterPrix(d.totalAutorise)}</strong>
          {d.totalEnAttente > 0 && ` · en attente : ${formaterPrix(d.totalEnAttente)}`}
          {d.totalRefuse > 0 && ` · refusées : ${formaterPrix(d.totalRefuse)}`}
        </Typography>
      )}
      {d.anomalies.map((a) => (
        <Alert key={a} severity="error" sx={{ mb: 1 }}>
          {a}
        </Alert>
      ))}
      {edition && <DialogueDepense depenses={d} depense={edition === 'nouvelle' ? null : edition} onFermer={() => setEdition(null)} />}
      {decision && <DialogueDecision depenses={d} depense={decision} onFermer={() => setDecision(null)} />}
    </Paper>
  )
}
