import SaveIcon from '@mui/icons-material/Save'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, rafraichirJournee, useEncaissements } from '../../api/requetes'
import type { EncaissementsJournee, ModeEncaissement } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterPrix, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const texte = (v: number | null) => (v === null ? '' : String(v))

/**
 * Encaissements électroniques de la journée (Wave, Orange Money, carte) : totaux du jour par mode,
 * déduits des espèces à verser par le gérant.
 */
export function SectionEncaissements({ journeeId }: { journeeId: string }) {
  const requete = useEncaissements(journeeId)
  const queryClient = useQueryClient()
  const [saisies, setSaisies] = useState<Partial<Record<ModeEncaissement, string>>>({})
  const e = requete.data

  const enregistrement = useMutation({
    mutationFn: (corps: { version: number; encaissements: { mode: ModeEncaissement; montant: number | null }[] }) =>
      api.put<EncaissementsJournee>(`/journees/${journeeId}/encaissements`, corps),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.encaissements(journeeId), r)
      rafraichirJournee(queryClient, journeeId, cles.encaissements(journeeId))
      setSaisies({})
      notifier('Encaissements enregistrés.')
    },
  })

  if (!e) return <AlerteErreur erreur={requete.error} />
  if (e.total === 0 && !e.modifiable) return null

  const valeur = (mode: ModeEncaissement, montant: number | null) => saisies[mode] ?? texte(montant)
  const modifiees = e.lignes.filter((l) => saisies[l.mode] !== undefined && (versNombre(saisies[l.mode]!) ?? null) !== l.montant)
  const total = e.lignes.reduce((t, l) => t + (versNombre(valeur(l.mode, l.montant)) ?? 0), 0)

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }} aria-label="Encaissements électroniques">
      <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Wave, Orange Money et carte
        </Typography>
        <Typography variant="body2" color="text.secondary" data-testid="total-encaissements">
          {formaterPrix(modifiees.length ? total : e.total)}
        </Typography>
      </Stack>
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1 }}>
        Totaux encaissés hors espèces : ils sont déduits du montant à verser.
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
        {e.lignes.map((l) =>
          e.modifiable ? (
            <TextField
              key={l.mode}
              size="small"
              label={l.libelle}
              value={valeur(l.mode, l.montant)}
              onChange={(ev) => setSaisies((s) => ({ ...s, [l.mode]: ev.target.value }))}
              slotProps={{ htmlInput: { inputMode: 'numeric', style: { textAlign: 'right' } } }}
              sx={{ flex: 1 }}
            />
          ) : (
            <Stack key={l.mode} direction="row" sx={{ flex: 1, justifyContent: 'space-between' }}>
              <Typography variant="body2">{l.libelle}</Typography>
              <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {l.montant === null ? '—' : formaterPrix(l.montant)}
              </Typography>
            </Stack>
          ),
        )}
      </Stack>
      <AlerteErreur erreur={enregistrement.error} />
      {e.modifiable && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1, alignItems: { sm: 'center' }, justifyContent: 'flex-end' }}>
          {modifiees.length > 0 && (
            <Typography variant="body2" color="warning.main" sx={{ flexGrow: 1 }}>
              Encaissements non enregistrés.
            </Typography>
          )}
          <Button
            variant="outlined"
            startIcon={<SaveIcon />}
            disabled={enregistrement.isPending || modifiees.length === 0}
            onClick={() =>
              enregistrement.mutate({
                version: e.version,
                encaissements: modifiees.map((l) => ({ mode: l.mode, montant: versNombre(saisies[l.mode]!) ?? null })),
              })
            }
          >
            Enregistrer les encaissements
          </Button>
        </Stack>
      )}
    </Paper>
  )
}
