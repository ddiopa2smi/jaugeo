import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import InputAdornment from '@mui/material/InputAdornment'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api, messageErreur } from '../../api/client'
import { cles, useBareme } from '../../api/requetes'
import type { Bareme, Cuve } from '../../api/types'
import { analyserBareme, volumePour, type UniteHauteur } from '../../shared/bareme'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterNombre, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const TOLERANCE_CAPACITE = 1.1

function enTexte(b: Bareme): string {
  return b.points.map((p) => `${p.hauteurMm}\t${String(p.volumeLitres).replace('.', ',')}`).join('\n')
}

function Calculateur({ points, unite }: { points: Bareme['points']; unite: UniteHauteur }) {
  const [hauteur, setHauteur] = useState('')
  const h = versNombre(hauteur)
  const mm = h === null ? null : Math.round(unite === 'cm' ? h * 10 : h)
  const volume = mm === null ? null : volumePour(points, mm)
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
      <TextField
        id="champ-hauteur-test"
        size="small"
        label="Hauteur jaugée"
        value={hauteur}
        onChange={(e) => setHauteur(e.target.value)}
        slotProps={{
          htmlInput: { inputMode: 'decimal' },
          input: {
            endAdornment: <InputAdornment position="end">{unite}</InputAdornment>,
          },
        }}
        sx={{ width: 180 }}
      />
      <Typography data-testid="volume-calcule">
        {points.length < 2
          ? 'Aucun barème exploitable.'
          : mm === null
            ? 'Saisissez une hauteur pour vérifier le barème.'
            : volume === null
              ? 'Hors barème.'
              : `≈ ${formaterNombre(volume)} L`}
      </Typography>
    </Stack>
  )
}

interface Proprietes {
  stationId: string
  cuve: Cuve
  peutGerer: boolean
  onFermer: () => void
}

export function DialogueBareme(props: Proprietes) {
  const bareme = useBareme(props.cuve.id)
  if (!bareme.data) {
    return (
      <Dialog open onClose={props.onFermer} fullWidth maxWidth="md">
        <DialogTitle>Barème de jaugeage — cuve {props.cuve.code}</DialogTitle>
        <DialogContent>{bareme.error ? <AlerteErreur erreur={bareme.error} /> : <Chargement />}</DialogContent>
        <DialogActions>
          <Button onClick={props.onFermer}>Fermer</Button>
        </DialogActions>
      </Dialog>
    )
  }
  return <FormulaireBareme {...props} enregistre={bareme.data} />
}

function FormulaireBareme({ stationId, cuve, peutGerer, onFermer, enregistre }: Proprietes & { enregistre: Bareme }) {
  const queryClient = useQueryClient()
  // Préremplit avec le barème enregistré (en mm) pour pouvoir le corriger.
  const [texte, setTexte] = useState(() => enTexte(enregistre))
  const [unite, setUnite] = useState<UniteHauteur>('mm')
  const [certificat, setCertificat] = useState(enregistre.certificat ?? '')
  const [dateCertificat, setDateCertificat] = useState(enregistre.dateCertificat ?? '')
  const [erreur, setErreur] = useState<string | null>(null)

  const analyse = useMemo(() => analyserBareme(texte, unite), [texte, unite])
  const volumeMax = analyse.points.at(-1)?.volumeLitres ?? null
  const alerteCapacite =
    cuve.capaciteLitres !== null && volumeMax !== null && volumeMax > cuve.capaciteLitres * TOLERANCE_CAPACITE
      ? `Le volume maximal du barème (${formaterNombre(volumeMax)} L) dépasse de plus de 10 % la capacité déclarée (${formaterNombre(cuve.capaciteLitres)} L) : vérifiez le barème ou corrigez la capacité de la cuve.`
      : null
  const enregistrable = peutGerer && analyse.points.length >= 2 && analyse.erreurs.length === 0 && !alerteCapacite

  const mutation = useMutation({
    mutationFn: () =>
      api.put<Bareme>(`/cuves/${cuve.id}/bareme`, {
        certificat: certificat.trim() || null,
        dateCertificat: dateCertificat || null,
        points: analyse.points,
        version: enregistre.cuveVersion,
      }),
    onSuccess: (b) => {
      queryClient.setQueryData(cles.bareme(cuve.id), b)
      queryClient.invalidateQueries({ queryKey: cles.equipements(stationId) })
      notifier(`Barème de la cuve ${cuve.code} enregistré (${b.points.length} points).`)
      onFermer()
    },
    onError: (e) => setErreur(messageErreur(e)),
  })

  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="md">
      <DialogTitle>
        Barème de jaugeage — cuve {cuve.code} · {cuve.produitLibelle}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {erreur && <Alert severity="error">{erreur}</Alert>}
          {enregistre.points.length === 0 && (
            <Alert severity="info">
              Aucun barème : les jaugeages de cette cuve ne pourront pas être convertis en litres. Collez ci-dessous le tableau du
              certificat de jaugeage (deux colonnes : hauteur, volume).
            </Alert>
          )}
          {enregistre.points.length > 0 && (
            <Typography variant="body2" color="text.secondary">
              Barème enregistré : {enregistre.points.length} points
              {enregistre.certificat && ` · certificat ${enregistre.certificat}`}
              {enregistre.dateCertificat && ` du ${formaterDate(enregistre.dateCertificat)}`}
              {enregistre.capaciteLitres !== null && ` · capacité ${formaterNombre(enregistre.capaciteLitres)} L`}
            </Typography>
          )}
          <Box
            sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: { xs: '1fr', md: '3fr 2fr' },
            }}
          >
            <Stack spacing={2}>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Typography variant="body2">Hauteurs en</Typography>
                <ToggleButtonGroup size="small" exclusive value={unite} onChange={(_, v: UniteHauteur | null) => v && setUnite(v)}>
                  <ToggleButton value="mm">mm</ToggleButton>
                  <ToggleButton value="cm">cm</ToggleButton>
                </ToggleButtonGroup>
              </Stack>
              <TextField
                id="champ-bareme"
                label="Tableau hauteur → volume (L)"
                multiline
                minRows={10}
                maxRows={18}
                value={texte}
                onChange={(e) => setTexte(e.target.value)}
                disabled={!peutGerer}
                placeholder={'0\t0\n100\t310,5\n200\t880\n…'}
                helperText="Collez directement depuis Excel. Virgule ou point décimal, ligne d'en-tête acceptée."
                slotProps={{
                  htmlInput: {
                    style: { fontFamily: 'monospace', fontSize: 13 },
                    spellCheck: false,
                  },
                }}
              />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                <TextField
                  id="champ-certificat"
                  label="N° de certificat"
                  value={certificat}
                  onChange={(e) => setCertificat(e.target.value)}
                  disabled={!peutGerer}
                  sx={{ flex: 1 }}
                />
                <TextField
                  id="champ-date-certificat"
                  label="Date du certificat"
                  type="date"
                  value={dateCertificat}
                  onChange={(e) => setDateCertificat(e.target.value)}
                  disabled={!peutGerer}
                  slotProps={{ inputLabel: { shrink: true } }}
                  sx={{ flex: 1 }}
                />
              </Stack>
            </Stack>
            <Stack spacing={1.5}>
              <Typography variant="subtitle2">Contrôle</Typography>
              <Typography variant="body2" data-testid="resume-bareme">
                {analyse.points.length} point(s) lu(s)
                {analyse.ignorees > 0 && ` · ${analyse.ignorees} ligne(s) d'en-tête ignorée(s)`}
              </Typography>
              {analyse.points.length >= 2 && (
                <Typography variant="body2">
                  De {formaterNombre(analyse.points[0]!.hauteurMm)} à {formaterNombre(analyse.points.at(-1)!.hauteurMm)} mm ·{' '}
                  {formaterNombre(volumeMax)} L au maximum
                </Typography>
              )}
              {analyse.erreurs.slice(0, 6).map((e) => (
                <Alert key={e} severity="error" sx={{ py: 0 }}>
                  {e}
                </Alert>
              ))}
              {analyse.erreurs.length > 6 && (
                <Typography variant="caption" color="error">
                  … et {analyse.erreurs.length - 6} autre(s) erreur(s).
                </Typography>
              )}
              {alerteCapacite && <Alert severity="error">{alerteCapacite}</Alert>}
              {analyse.points.length >= 2 && analyse.erreurs.length === 0 && !alerteCapacite && (
                <Alert severity="success" sx={{ py: 0 }}>
                  Barème cohérent.
                </Alert>
              )}
              <Typography variant="subtitle2" sx={{ pt: 1 }}>
                Calculateur
              </Typography>
              <Calculateur points={analyse.erreurs.length === 0 ? analyse.points : enregistre.points} unite={unite} />
            </Stack>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onFermer}>{peutGerer ? 'Annuler' : 'Fermer'}</Button>
        {peutGerer && (
          <Button variant="contained" disabled={!enregistrable} loading={mutation.isPending} onClick={() => mutation.mutate()}>
            Enregistrer le barème
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
