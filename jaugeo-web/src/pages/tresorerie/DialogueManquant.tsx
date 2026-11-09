import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { useRafraichirTresorerie } from '../../api/requetes'
import type { Manquant, ModeReglementManquant } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterDate, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { aujourdhui, MODES_REGLEMENT } from '../../shared/tresorerie'

const STATUTS_ECHEANCE = {
  PAYEE: { libelle: 'Payée', couleur: 'success' },
  EN_RETARD: { libelle: 'En retard', couleur: 'error' },
  A_VENIR: { libelle: 'À venir', couleur: 'default' },
} as const

/** Fin du mois suivant : date usuelle de la première retenue sur commission. */
function finMoisSuivant(): string {
  const d = new Date()
  const fin = new Date(Date.UTC(d.getFullYear(), d.getMonth() + 2, 0))
  return fin.toISOString().slice(0, 10)
}

function Echeancier({ manquant, onMaj }: { manquant: Manquant; onMaj: (m: Manquant) => void }) {
  const rafraichir = useRafraichirTresorerie()
  const [reference, setReference] = useState(manquant.reconnaissanceReference ?? '')
  const [reconnuLe, setReconnuLe] = useState(manquant.reconnuLe ?? aujourdhui())
  const [nombre, setNombre] = useState('3')
  const [premiere, setPremiere] = useState(finMoisSuivant())
  const n = versNombre(nombre)
  const enregistrement = useMutation({
    mutationFn: () =>
      api.put<Manquant>(`/tresorerie/manquants/${manquant.id}/echeancier`, {
        version: manquant.version,
        reference: reference.trim(),
        reconnuLe,
        nombre: n,
        premiereEcheance: premiere,
      }),
    onSuccess: (m) => {
      rafraichir()
      onMaj(m)
      notifier('Échéancier enregistré.')
    },
  })
  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          label="Reconnaissance de dette n°"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 80 } }}
          sx={{ flex: 1 }}
        />
        <TextField type="date" label="Signée le" value={reconnuLe} onChange={(e) => setReconnuLe(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          label="Nombre d’échéances mensuelles"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          helperText={n && n > 0 ? `≈ ${formaterPrix(Math.ceil(manquant.reste / n))} par mois` : ' '}
          sx={{ flex: 1 }}
        />
        <TextField type="date" label="Première échéance" value={premiere} onChange={(e) => setPremiere(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <AlerteErreur erreur={enregistrement.error} />
      <Box sx={{ textAlign: 'right' }}>
        <Button
          variant="outlined"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !reference.trim() || !reconnuLe || !premiere || !n || n < 1 || n > 24}
        >
          {manquant.echeances.length ? 'Refaire l’échéancier' : 'Établir l’échéancier'}
        </Button>
      </Box>
    </Stack>
  )
}

function Reglement({ manquant, onMaj }: { manquant: Manquant; onMaj: (m: Manquant) => void }) {
  const rafraichir = useRafraichirTresorerie()
  const prochaine = manquant.echeances.find((e) => e.statut !== 'PAYEE')
  const [montant, setMontant] = useState(String(prochaine ? prochaine.montant - prochaine.regle : manquant.reste))
  const [mode, setMode] = useState<ModeReglementManquant>('RETENUE')
  const [reference, setReference] = useState('')
  const [date, setDate] = useState(aujourdhui())
  const m = versNombre(montant)
  const paiement = useMutation({
    mutationFn: () =>
      api.post<Manquant>(`/tresorerie/manquants/${manquant.id}/paiements`, {
        version: manquant.version,
        montant: m,
        mode,
        reference: videVersNull(reference),
        date,
      }),
    onSuccess: (r) => {
      rafraichir()
      onMaj(r)
      setReference('')
      notifier(r.statut === 'SOLDE' ? 'Manquant soldé.' : `Règlement enregistré. Reste dû : ${formaterPrix(r.reste)}.`)
    },
  })
  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          label="Montant (F CFA)"
          value={montant}
          onChange={(e) => setMontant(e.target.value)}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          error={m !== null && m > manquant.reste}
          helperText={m !== null && m > manquant.reste ? 'Supérieur au reste dû' : `Reste dû : ${formaterPrix(manquant.reste)}`}
          sx={{ flex: 1 }}
        />
        <TextField select label="Mode" value={mode} onChange={(e) => setMode(e.target.value as ModeReglementManquant)} sx={{ flex: 1 }}>
          {Object.entries(MODES_REGLEMENT).map(([v, l]) => (
            <MenuItem key={v} value={v}>
              {l}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField
          label="Référence (reçu, bulletin…)"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 80 } }}
          sx={{ flex: 1 }}
        />
        <TextField type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <AlerteErreur erreur={paiement.error} />
      <Box sx={{ textAlign: 'right' }}>
        <Button
          variant="contained"
          onClick={() => paiement.mutate()}
          disabled={paiement.isPending || !m || m <= 0 || m > manquant.reste || !date || (mode !== 'PERTE' && !reference.trim())}
        >
          Enregistrer le règlement
        </Button>
      </Box>
    </Stack>
  )
}

/** Fiche d'un manquant : reconnaissance de dette, échéancier, règlements partiels. */
export function DialogueManquant({ manquant: initial, onFermer }: { manquant: Manquant; onFermer: () => void }) {
  const [m, setM] = useState(initial)
  const [refaire, setRefaire] = useState(false)
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>
        Manquant · {m.stationCode} · {formaterDate(m.date)}
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary">
          {m.responsable ?? 'Responsable non indiqué'}
          {m.observation && ` · ${m.observation}`}
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1, my: 2 }}>
          {[
            ['Montant', m.montant, undefined],
            ['Réglé', m.montantRegle, undefined],
            ['Reste dû', m.reste, m.reste > 0 ? 'error.main' : 'success.main'],
          ].map(([l, v, c]) => (
            <Box key={l as string}>
              <Typography variant="caption" color="text.secondary">
                {l}
              </Typography>
              <Typography sx={{ fontWeight: 600, color: c as string | undefined, fontVariantNumeric: 'tabular-nums' }} data-testid={`manquant-${l}`}>
                {formaterPrix(v as number)}
              </Typography>
            </Box>
          ))}
        </Box>
        {m.retard > 0 && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {formaterPrix(m.retard)} d’échéances non honorées.
          </Alert>
        )}

        <Typography variant="overline" color="text.secondary">
          Échéancier
        </Typography>
        {m.reconnaissanceReference && (
          <Typography variant="body2" sx={{ mb: 1 }}>
            Reconnaissance de dette {m.reconnaissanceReference} signée le {formaterDate(m.reconnuLe)}
          </Typography>
        )}
        {m.echeances.length > 0 ? (
          <Table size="small" sx={{ mb: 2, '& td, & th': { px: 1 }, '& td:first-of-type, & th:first-of-type': { pl: 0 } }}>
            <TableHead>
              <TableRow>
                <TableCell>Échéance</TableCell>
                <TableCell align="right">Montant</TableCell>
                <TableCell align="right">Réglé</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {m.echeances.map((e) => (
                <TableRow key={e.rang}>
                  <TableCell>
                    {formaterDate(e.date)}
                    <Typography
                      variant="caption"
                      component="div"
                      color={e.statut === 'EN_RETARD' ? 'error.main' : e.statut === 'PAYEE' ? 'success.main' : 'text.secondary'}
                      sx={{ display: { sm: 'none' } }}
                    >
                      {STATUTS_ECHEANCE[e.statut].libelle}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterPrix(e.montant)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {e.regle ? formaterPrix(e.regle) : ''}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                    <Chip size="small" color={STATUTS_ECHEANCE[e.statut].couleur} label={STATUTS_ECHEANCE[e.statut].libelle} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Pas d’échéancier : le manquant est dû en une fois.
          </Typography>
        )}
        {m.reglable && (m.echeances.length === 0 || refaire) && (
          <Echeancier
            key={m.version}
            manquant={m}
            onMaj={(x) => {
              setM(x)
              setRefaire(false)
            }}
          />
        )}
        {m.reglable && m.echeances.length > 0 && !refaire && (
          <Button size="small" onClick={() => setRefaire(true)}>
            Refaire l’échéancier
          </Button>
        )}

        <Divider sx={{ my: 2 }} />
        <Typography variant="overline" color="text.secondary">
          Règlements
        </Typography>
        {m.paiements.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Aucun règlement.
          </Typography>
        )}
        {m.paiements.map((p, i) => (
          <Stack key={i} direction="row" spacing={1} sx={{ py: 0.5, justifyContent: 'space-between' }}>
            <Typography variant="body2">
              {formaterDate(p.date)} · {MODES_REGLEMENT[p.mode]}
              {p.reference && ` · ${p.reference}`}
            </Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
              {formaterPrix(p.montant)}
            </Typography>
          </Stack>
        ))}
        {m.reglable && (
          <Box sx={{ mt: 1 }}>
            <Reglement key={m.version} manquant={m} onMaj={setM} />
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}
