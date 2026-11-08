import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import PaymentsIcon from '@mui/icons-material/Payments'
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
import { cles, rafraichirJournee, useClients, useCreditJournee } from '../../api/requetes'
import type { Bon, CreditJournee, SaisieBon } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { RECOUVREMENTS } from '../../shared/clients'
import { formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

/** Un bon ou un recouvrement incrémente la version de la journée ; les comptes clients changent aussi. */
function useMajCredit(journeeId: string) {
  const queryClient = useQueryClient()
  return (c: CreditJournee) => {
    queryClient.setQueryData(cles.creditJournee(journeeId), c)
    rafraichirJournee(queryClient, journeeId, cles.creditJournee(journeeId))
    queryClient.invalidateQueries({ queryKey: cles.clients })
  }
}

function DialogueBon({ credit, bon, onFermer }: { credit: CreditJournee; bon: Bon | null; onFermer: () => void }) {
  const clients = useClients()
  const maj = useMajCredit(credit.journeeId)
  const [clientId, setClientId] = useState(bon?.clientId ?? '')
  const [numero, setNumero] = useState(bon?.numero ?? '')
  const [produitId, setProduitId] = useState(bon?.produitId ?? (credit.produits.length === 1 ? credit.produits[0]!.id : ''))
  const [litres, setLitres] = useState(bon ? String(bon.litres) : '')
  const [immatriculation, setImmatriculation] = useState(bon?.immatriculation ?? '')
  const [porteur, setPorteur] = useState(bon?.porteur ?? '')

  const choix = (clients.data ?? []).filter((c) => c.actif || c.id === bon?.clientId)
  const client = choix.find((c) => c.id === clientId)
  const produit = credit.produits.find((p) => p.id === produitId)
  const l = versNombre(litres)
  const montant = l !== null && produit?.prixUnitaire != null ? Math.round(l * produit.prixUnitaire) : null
  // Disponible sous plafond : le bon corrigé est déjà compté dans l'encours
  const disponible = client?.disponible != null ? client.disponible + (bon && bon.clientId === client.id ? bon.montant : 0) : null
  const auDela = disponible !== null && montant !== null && montant > disponible
  const litresValides = l !== null && l > 0 && Math.round(l * 100) === l * 100

  const enregistrement = useMutation({
    mutationFn: () => {
      const corps: SaisieBon = {
        version: credit.version,
        clientId,
        numero: numero.trim(),
        produitId,
        litres: l!,
        immatriculation: videVersNull(immatriculation),
        porteur: videVersNull(porteur),
      }
      const base = `/journees/${credit.journeeId}/bons`
      return bon ? api.put<CreditJournee>(`${base}/${bon.id}`, corps) : api.post<CreditJournee>(base, corps)
    },
    onSuccess: (r) => {
      maj(r)
      notifier('Bon enregistré.')
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>{bon ? 'Corriger le bon' : 'Vente à crédit (bon)'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            select
            label="Client"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            required
            helperText={
              client
                ? `${RECOUVREMENTS[client.recouvrement]} · encours ${formaterPrix(client.encours)}${
                    client.plafond !== null ? ` · plafond ${formaterPrix(client.plafond)}` : ''
                  }`
                : undefined
            }
          >
            {choix.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.code} · {c.nom}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Numéro du bon"
            value={numero}
            onChange={(e) => setNumero(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 30, autoComplete: 'off' } }}
          />
          <Stack direction="row" spacing={2}>
            <TextField select label="Produit" value={produitId} onChange={(e) => setProduitId(e.target.value)} required sx={{ flex: 1 }}>
              {credit.produits.map((p) => (
                <MenuItem key={p.id} value={p.id} disabled={p.prixUnitaire === null}>
                  {p.libelle} {p.prixUnitaire !== null ? `(${formaterPrix(p.prixUnitaire)}/L)` : '(sans prix)'}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Litres"
              value={litres}
              onChange={(e) => setLitres(e.target.value)}
              required
              sx={{ flex: 1 }}
              error={auDela}
              slotProps={{ htmlInput: { inputMode: 'decimal', autoComplete: 'off' } }}
              helperText={montant !== null ? `Montant : ${formaterPrix(montant)}` : undefined}
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField
              label="Véhicule ou pirogue"
              value={immatriculation}
              onChange={(e) => setImmatriculation(e.target.value)}
              sx={{ flex: 1 }}
              slotProps={{ htmlInput: { maxLength: 30 } }}
            />
            <TextField
              label="Porteur du bon"
              value={porteur}
              onChange={(e) => setPorteur(e.target.value)}
              sx={{ flex: 1 }}
              slotProps={{ htmlInput: { maxLength: 80 } }}
            />
          </Stack>
          {auDela && (
            <Alert severity="error">
              Plafond dépassé : il reste {formaterPrix(disponible)} disponibles pour ce client. Le bon sera refusé.
            </Alert>
          )}
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !clientId || !numero.trim() || !produitId || !litresValides}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueRecouvrement({ credit, onFermer }: { credit: CreditJournee; onFermer: () => void }) {
  const clients = useClients()
  const maj = useMajCredit(credit.journeeId)
  const [clientId, setClientId] = useState('')
  const [montant, setMontant] = useState('')
  const [reference, setReference] = useState('')
  const m = versNombre(montant)
  const choix = (clients.data ?? []).filter((c) => c.encours > 0)
  const client = choix.find((c) => c.id === clientId)

  const enregistrement = useMutation({
    mutationFn: () =>
      api.post<CreditJournee>(`/journees/${credit.journeeId}/recouvrements`, {
        version: credit.version,
        clientId,
        montant: m,
        reference: videVersNull(reference),
      }),
    onSuccess: (r) => {
      maj(r)
      notifier('Recouvrement enregistré : il s’ajoute au montant à verser.')
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Créance recouvrée en espèces</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            select
            label="Client"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            required
            helperText={client ? `Encours sur le réseau : ${formaterPrix(client.encours)}` : 'Clients ayant une créance'}
          >
            {choix.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.code} · {c.nom}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Montant reçu (F)"
            value={montant}
            onChange={(e) => setMontant(e.target.value)}
            required
            slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
          />
          <TextField
            label="Référence (reçu)"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 80 } }}
          />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !clientId || m === null || m <= 0 || !Number.isInteger(m)}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Récapitulatif des ventes à crédit avant de confirmer la clôture. */
export function RecapCredit({ credit }: { credit: CreditJournee }) {
  if (credit.bons.length === 0 && credit.recouvrements.length === 0) return null
  return (
    <Typography variant="body2" sx={{ mt: 2 }}>
      {credit.bons.length} bon(s) : direction <strong>{formaterPrix(credit.totalBonsDirection)}</strong>, gérant{' '}
      <strong>{formaterPrix(credit.totalBonsGerant)}</strong>
      {credit.totalRecouvrements > 0 && ` · créances recouvrées : ${formaterPrix(credit.totalRecouvrements)}`}
    </Typography>
  )
}

export function SectionCredit({ journeeId }: { journeeId: string }) {
  const requete = useCreditJournee(journeeId)
  const maj = useMajCredit(journeeId)
  const [edition, setEdition] = useState<Bon | 'nouveau' | null>(null)
  const [recouvrement, setRecouvrement] = useState(false)
  const c = requete.data

  const suppression = useMutation({
    mutationFn: (chemin: string) => api.delete<CreditJournee>(`/journees/${journeeId}/${chemin}?version=${c!.version}`),
    onSuccess: (r) => {
      maj(r)
      notifier('Ligne retirée.')
    },
  })

  if (!c) return <AlerteErreur erreur={requete.error} />

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1 }} aria-label="Ventes à crédit de la journée">
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <Typography variant="overline" color="text.secondary">
          Ventes à crédit (bons)
        </Typography>
        {c.modifiable && (
          <Stack direction="row" spacing={1}>
            <Button startIcon={<AddIcon />} onClick={() => setEdition('nouveau')}>
              Ajouter un bon
            </Button>
            <Button startIcon={<PaymentsIcon />} onClick={() => setRecouvrement(true)}>
              Créance recouvrée
            </Button>
          </Stack>
        )}
      </Stack>
      <AlerteErreur erreur={suppression.error} />
      {c.bons.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ pb: 1 }}>
          Aucun bon ce jour.
        </Typography>
      ) : (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Bon</TableCell>
                <TableCell>Client</TableCell>
                <TableCell align="right">Litres</TableCell>
                <TableCell align="right">Montant</TableCell>
                <TableCell>Recouvrement</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {c.bons.map((b) => (
                <TableRow key={b.id} data-testid={`bon-${b.numero}`}>
                  <TableCell>
                    {b.numero}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {[b.immatriculation, b.porteur].filter(Boolean).join(' · ')}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {b.clientNom}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {b.clientCode}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterNombre(b.litres)} L
                    <Typography variant="caption" color="text.secondary" component="div">
                      {b.produitLibelle} à {formaterPrix(b.prixUnitaire)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterPrix(b.montant)}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={b.recouvrement === 'DIRECTION' ? 'Direction' : 'Gérant'} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {c.modifiable && (
                      <>
                        <IconButton size="small" aria-label={`Corriger le bon ${b.numero}`} onClick={() => setEdition(b)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label={`Retirer le bon ${b.numero}`}
                          onClick={() => suppression.mutate(`bons/${b.id}`)}
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
      {c.recouvrements.length > 0 && (
        <>
          <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 1 }}>
            Créances recouvrées en espèces
          </Typography>
          {c.recouvrements.map((r) => (
            <Stack key={r.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flexGrow: 1 }}>
                {r.clientNom} · <strong>{formaterPrix(r.montant)}</strong>
                {r.reference ? ` · ${r.reference}` : ''}
              </Typography>
              {c.modifiable && (
                <IconButton
                  size="small"
                  aria-label={`Retirer le recouvrement de ${r.clientNom}`}
                  onClick={() => suppression.mutate(`recouvrements/${r.id}`)}
                  disabled={suppression.isPending}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
        </>
      )}
      {(c.bons.length > 0 || c.recouvrements.length > 0) && (
        <Typography variant="body2" sx={{ py: 1 }} data-testid="total-credit">
          Bons direction : <strong>{formaterPrix(c.totalBonsDirection)}</strong> · bons gérant :{' '}
          <strong>{formaterPrix(c.totalBonsGerant)}</strong>
          {c.totalRecouvrements > 0 && ` · recouvré : ${formaterPrix(c.totalRecouvrements)}`}
        </Typography>
      )}
      {c.anomalies.map((a) => (
        <Alert key={a} severity="error" sx={{ mb: 1 }}>
          {a}
        </Alert>
      ))}
      {edition && <DialogueBon credit={c} bon={edition === 'nouveau' ? null : edition} onFermer={() => setEdition(null)} />}
      {recouvrement && <DialogueRecouvrement credit={c} onFermer={() => setRecouvrement(false)} />}
    </Paper>
  )
}
