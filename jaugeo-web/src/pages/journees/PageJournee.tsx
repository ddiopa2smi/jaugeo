import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import LockIcon from '@mui/icons-material/Lock'
import ReplyIcon from '@mui/icons-material/Reply'
import SaveIcon from '@mui/icons-material/Save'
import VerifiedIcon from '@mui/icons-material/Verified'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
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
import { useRef, useState } from 'react'
import { Link as LienRouteur, useParams } from 'react-router'
import { api, messageErreur } from '../../api/client'
import { cles, useJournee } from '../../api/requetes'
import type { DetailJournee, LigneReleve, SaisieJournee, StockJournee } from '../../api/types'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { apercu, memeSaisie, saisieInitiale, STATUTS_JOURNEE, type SaisieLigne } from '../../shared/journee'
import { formaterDate, formaterDateHeureCourte, formaterNombre, formaterPrix, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { RecapStock, SectionStock, type PoigneeStock } from './SectionStock'

const heure = (iso: string) => new Date(iso).toISOString().slice(11, 16)

function LigneSaisie({
  ligne,
  saisie,
  modifiable,
  modifiee,
  onChange,
}: {
  ligne: LigneReleve
  saisie: SaisieLigne
  modifiable: boolean
  modifiee: boolean
  onChange: (s: SaisieLigne) => void
}) {
  const local = modifiee ? apercu(ligne, saisie) : null
  // Valeurs serveur tant que la ligne n'est pas retouchée ; aperçu local sinon (si calculable)
  const brute = modifiee ? (local?.sortieBrute ?? null) : ligne.sortieBrute
  const nette = modifiee ? (local?.sortieNette ?? null) : ligne.sortieNette
  const montant = modifiee ? (local?.montant ?? null) : ligne.montant
  const champ = { inputMode: 'decimal' as const, autoComplete: 'off' }
  const lectures = [...new Set([...ligne.lecturesAttendues, ...Object.keys(saisie.lectures)])].sort()

  return (
    <Box sx={{ py: 1.5 }} data-testid={`ligne-${ligne.pistoletCode}`}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' } }}>
        <Box sx={{ minWidth: 120 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {ligne.pistoletCode} · {ligne.produitLibelle}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {ligne.pistoletLibelle} · cuve {ligne.cuveCode}
          </Typography>
        </Box>
        <Box sx={{ minWidth: 110 }}>
          <Typography variant="caption" color="text.secondary">
            Index de départ
          </Typography>
          <Typography
            sx={{ fontVariantNumeric: 'tabular-nums', fontSize: 18 }}
            data-testid={`depart-${ligne.pistoletCode}`}
            data-valeur={ligne.indexDepart}
          >
            {formaterNombre(ligne.indexDepart)}
          </Typography>
        </Box>
        <TextField
          label="Index d'arrivée"
          value={saisie.arrivee}
          onChange={(e) => onChange({ ...saisie, arrivee: e.target.value })}
          disabled={!modifiable}
          slotProps={{ htmlInput: { ...champ, 'aria-label': `Index d'arrivée ${ligne.pistoletCode}` } }}
          sx={{ width: { xs: '100%', md: 160 }, flexShrink: 0, '& input': { fontSize: 18 } }}
        />
        <TextField
          label="Remise (L)"
          value={saisie.remise}
          onChange={(e) => onChange({ ...saisie, remise: e.target.value })}
          disabled={!modifiable}
          slotProps={{ htmlInput: { ...champ, 'aria-label': `Remise en cuve ${ligne.pistoletCode}` } }}
          sx={{ width: { xs: '100%', md: 120 }, flexShrink: 0, '& input': { fontSize: 18 } }}
        />
        <Box sx={{ flexGrow: 1, minWidth: 120, textAlign: { md: 'right' }, whiteSpace: 'nowrap' }}>
          <Typography variant="caption" color="text.secondary" component="div">
            Sortie nette
          </Typography>
          <Typography
            sx={{ fontVariantNumeric: 'tabular-nums', fontSize: 18, fontWeight: 600 }}
            data-testid={`nette-${ligne.pistoletCode}`}
          >
            {nette === null ? '—' : `${formaterNombre(nette)} L`}
          </Typography>
          {brute !== null && nette !== null && brute !== nette && (
            <Typography variant="caption" color="text.secondary" component="div">
              brute {formaterNombre(brute)} L
            </Typography>
          )}
          <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }} data-testid={`montant-${ligne.pistoletCode}`}>
            {montant === null ? (modifiee && !local ? 'calculé à l’enregistrement' : '') : formaterPrix(montant)}
          </Typography>
        </Box>
      </Stack>

      {ligne.ruptures.map((r) => (
        <Alert key={r.date} severity="info" sx={{ mt: 1 }}>
          Compteur changé à {heure(r.date)} : ancien compteur arrêté à {formaterNombre(r.indexAvant)}, nouveau compteur parti de{' '}
          {formaterNombre(r.indexApres)}. Saisissez l'index d'arrivée lu sur le nouveau compteur.
        </Alert>
      ))}
      {lectures.length > 0 && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1.5, alignItems: { sm: 'center' } }}>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 320 }}>
            Changement de prix en cours de journée : index lu au moment du changement.
          </Typography>
          {lectures.map((instant) => (
            <TextField
              key={instant}
              label={`Index à ${heure(instant)}`}
              value={saisie.lectures[instant] ?? ''}
              onChange={(e) => onChange({ ...saisie, lectures: { ...saisie.lectures, [instant]: e.target.value } })}
              disabled={!modifiable}
              slotProps={{ htmlInput: { ...champ, 'aria-label': `Index à ${heure(instant)} ${ligne.pistoletCode}` } }}
              sx={{ width: { xs: '100%', sm: 170 } }}
            />
          ))}
        </Stack>
      )}
      {!modifiee && ligne.tranches.length > 1 && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {ligne.tranches
            .map(
              (t) =>
                `${heure(t.debut)}–${heure(t.fin) === '00:00' ? '24:00' : heure(t.fin)} : ${formaterNombre(t.litres)} L × ${formaterPrix(t.prixUnitaire)}`,
            )
            .join(' · ')}
        </Typography>
      )}
      {local?.alerte && (
        <Alert severity="warning" sx={{ mt: 1 }}>
          {local.alerte}
        </Alert>
      )}
      {!modifiee &&
        ligne.anomalies.map((a) => (
          <Alert key={a.code} severity="error" sx={{ mt: 1 }}>
            {a.message}
          </Alert>
        ))}
    </Box>
  )
}

function Totaux({ journee }: { journee: DetailJournee }) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Produit</TableCell>
          <TableCell align="right">Litres</TableCell>
          <TableCell align="right">Ventes</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {journee.totaux.map((t) => (
          <TableRow key={t.produitCode}>
            <TableCell>{t.produitLibelle}</TableCell>
            <TableCell align="right">{formaterNombre(t.litres)}</TableCell>
            <TableCell align="right">{formaterPrix(t.montant)}</TableCell>
          </TableRow>
        ))}
        <TableRow>
          <TableCell sx={{ fontWeight: 700 }}>Total</TableCell>
          <TableCell align="right" sx={{ fontWeight: 700 }}>
            {formaterNombre(journee.litres)}
          </TableCell>
          <TableCell align="right" sx={{ fontWeight: 700 }} data-testid="total-journee">
            {formaterPrix(journee.montant)}
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  )
}

function FicheJournee({ journee }: { journee: DetailJournee }) {
  const queryClient = useQueryClient()
  const [saisies, setSaisies] = useState<Record<string, SaisieLigne>>(() =>
    Object.fromEntries(journee.lignes.map((l) => [l.pistoletId, saisieInitiale(l)])),
  )
  const [observation, setObservation] = useState(journee.observation ?? '')
  const [recap, setRecap] = useState<{ journee: DetailJournee; stock: StockJournee } | null>(null)
  const poigneeStock = useRef<PoigneeStock>(null)
  const [renvoi, setRenvoi] = useState<string | null>(null)

  const modifiable = journee.actions.saisir
  const lignesModifiees = new Set(
    journee.lignes.filter((l) => !memeSaisie(saisies[l.pistoletId]!, saisieInitiale(l))).map((l) => l.pistoletId),
  )
  const modifiee = lignesModifiees.size > 0 || observation.trim() !== (journee.observation ?? '')

  const majCache = (j: DetailJournee) => {
    queryClient.setQueryData(cles.journee(j.id), j)
    queryClient.invalidateQueries({ queryKey: cles.journeesStation(j.stationId) })
    // Les sorties (donc le stock théorique) et la version de la journée ont changé
    queryClient.invalidateQueries({ queryKey: cles.stockJournee(j.id) })
  }

  const enregistrement = useMutation({
    mutationFn: () =>
      api.put<DetailJournee>(`/journees/${journee.id}/releves`, {
        version: journee.version,
        observation: observation.trim() || null,
        releves: journee.lignes.map((l) => {
          const s = saisies[l.pistoletId]!
          return {
            pistoletId: l.pistoletId,
            indexArrivee: versNombre(s.arrivee),
            remiseCuve: versNombre(s.remise),
            lectures: Object.entries(s.lectures)
              .filter(([, v]) => versNombre(v) !== null)
              .map(([instant, v]) => ({ instant, index: versNombre(v)! })),
          }
        }),
      } satisfies SaisieJournee),
    onSuccess: majCache,
  })

  const action = useMutation({
    mutationFn: ({ chemin, corps }: { chemin: string; corps: object }) =>
      api.post<DetailJournee>(`/journees/${journee.id}/${chemin}`, corps),
    onSuccess: (j, { chemin }) => {
      majCache(j)
      setRecap(null)
      setRenvoi(null)
      notifier(
        chemin === 'cloture'
          ? 'Journée clôturée : les ventes sont figées.'
          : chemin === 'validation'
            ? 'Journée validée.'
            : 'Journée renvoyée au gérant pour correction.',
      )
    },
  })

  const enregistrer = async () => {
    const j = await enregistrement.mutateAsync()
    notifier(
      j.anomalies > 0 ? `Enregistré. ${j.anomalies} point(s) à corriger avant la clôture.` : 'Saisie enregistrée.',
      j.anomalies > 0 ? 'warning' : 'success',
    )
    return j
  }

  const preparerCloture = async () => {
    const j = modifiee ? await enregistrement.mutateAsync() : journee
    if (poigneeStock.current?.modifie) await poigneeStock.current.enregistrer(j.version)
    // État serveur à jour (version et contrôles) avant de proposer la clôture
    const [jf, sf] = await Promise.all([
      api.get<DetailJournee>(`/journees/${journee.id}`),
      api.get<StockJournee>(`/journees/${journee.id}/stock`),
    ])
    queryClient.setQueryData(cles.journee(jf.id), jf)
    queryClient.setQueryData(cles.stockJournee(jf.id), sf)
    const points = jf.anomalies + sf.anomalies
    if (points > 0) {
      notifier(`Clôture impossible : ${points} point(s) à corriger (en rouge).`, 'error')
      return
    }
    setRecap({ journee: jf, stock: sf })
  }

  const groupes = new Map<string, LigneReleve[]>()
  for (const l of journee.lignes) groupes.set(l.pompeCode, [...(groupes.get(l.pompeCode) ?? []), l])

  return (
    <>
      {journee.statut === 'BROUILLON' && journee.motifRenvoi && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Journée renvoyée pour correction : {journee.motifRenvoi}
        </Alert>
      )}
      {journee.statut !== 'BROUILLON' && (
        <Alert severity={journee.statut === 'VALIDEE' ? 'success' : 'info'} icon={<LockIcon />} sx={{ mb: 2 }}>
          Clôturée par {journee.clotureePar} le {formaterDateHeureCourte(journee.clotureeLe)}
          {journee.valideePar
            ? ` · validée par ${journee.valideePar} le ${formaterDateHeureCourte(journee.valideeLe)}`
            : ' · en attente de validation'}
          . Les index et les ventes ne sont plus modifiables.
        </Alert>
      )}
      <AlerteErreur erreur={enregistrement.error} />

      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Stack spacing={2} sx={{ flexGrow: 1, width: '100%' }}>
          {[...groupes.entries()].map(([pompe, lignes]) => (
            <Paper key={pompe} variant="outlined" sx={{ px: 2, py: 1 }}>
              <Typography variant="overline" color="text.secondary">
                Pompe {pompe}
              </Typography>
              {lignes.map((l, i) => (
                <Box key={l.pistoletId}>
                  {i > 0 && <Divider />}
                  <LigneSaisie
                    ligne={l}
                    saisie={saisies[l.pistoletId]!}
                    modifiable={modifiable}
                    modifiee={lignesModifiees.has(l.pistoletId)}
                    onChange={(s) => setSaisies((e) => ({ ...e, [l.pistoletId]: s }))}
                  />
                </Box>
              ))}
            </Paper>
          ))}
          <SectionStock journeeId={journee.id} ref={poigneeStock} />
        </Stack>

        <Paper variant="outlined" sx={{ p: 2, width: { xs: '100%', lg: 360 }, flexShrink: 0, position: { lg: 'sticky' }, top: { lg: 80 } }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
            Ventes de la journée
          </Typography>
          {modifiee && (
            <Typography variant="body2" color="warning.main" sx={{ mb: 1 }}>
              Modifications non enregistrées : totaux mis à jour à l'enregistrement.
            </Typography>
          )}
          <Totaux journee={journee} />
          {journee.anomalies > 0 && !modifiee && (
            <Typography variant="body2" color="error" sx={{ mt: 1 }}>
              {journee.anomalies} point(s) à corriger avant la clôture.
            </Typography>
          )}
          <TextField
            label="Observation"
            value={observation}
            onChange={(e) => setObservation(e.target.value)}
            disabled={!modifiable}
            multiline
            minRows={2}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
            sx={{ mt: 2 }}
          />
          <Stack spacing={1} sx={{ mt: 2 }}>
            {modifiable && (
              <Button
                variant="outlined"
                size="large"
                startIcon={<SaveIcon />}
                onClick={() => enregistrer().catch(() => undefined)}
                disabled={enregistrement.isPending || !modifiee}
              >
                Enregistrer
              </Button>
            )}
            {journee.actions.cloturer && (
              <Button
                variant="contained"
                size="large"
                startIcon={<LockIcon />}
                onClick={() => preparerCloture().catch(() => undefined)}
                disabled={enregistrement.isPending}
              >
                Clôturer la journée
              </Button>
            )}
            {journee.actions.valider && (
              <Button
                variant="contained"
                color="success"
                size="large"
                startIcon={<VerifiedIcon />}
                onClick={() => action.mutate({ chemin: 'validation', corps: { version: journee.version } })}
                disabled={action.isPending}
              >
                Valider
              </Button>
            )}
            {journee.actions.renvoyer && (
              <Button variant="outlined" color="error" size="large" startIcon={<ReplyIcon />} onClick={() => setRenvoi('')}>
                Renvoyer pour correction
              </Button>
            )}
            {journee.statut === 'CLOTUREE' && !journee.actions.valider && journee.actions.renvoyer && (
              <Typography variant="caption" color="text.secondary">
                Vous avez clôturé cette journée : un autre superviseur doit la valider.
              </Typography>
            )}
          </Stack>
          {!recap && renvoi === null && <AlerteErreur erreur={action.error} />}
        </Paper>
      </Stack>

      <Dialog open={!!recap} onClose={() => setRecap(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Clôturer la journée du {formaterDate(journee.date)} ?</DialogTitle>
        <DialogContent>
          {recap && <Totaux journee={recap.journee} />}
          {recap && <RecapStock stock={recap.stock} />}
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Après clôture, les index, les ventes et les écarts de stock sont figés. Seul un superviseur peut renvoyer la journée pour correction.
          </Typography>
          {action.error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {messageErreur(action.error)}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRecap(null)}>Annuler</Button>
          <Button
            variant="contained"
            onClick={() => action.mutate({ chemin: 'cloture', corps: { version: recap!.journee.version } })}
            disabled={action.isPending}
          >
            Confirmer la clôture
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={renvoi !== null} onClose={() => setRenvoi(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Renvoyer la journée pour correction</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            La journée repasse en saisie et ses ventes sont retirées jusqu'à la nouvelle clôture.
          </Typography>
          <TextField
            label="Motif du renvoi"
            value={renvoi ?? ''}
            onChange={(e) => setRenvoi(e.target.value)}
            multiline
            minRows={2}
            fullWidth
            autoFocus
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          {action.error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {messageErreur(action.error)}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRenvoi(null)}>Annuler</Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => action.mutate({ chemin: 'renvoi', corps: { version: journee.version, motif: renvoi?.trim() } })}
            disabled={action.isPending || !renvoi?.trim()}
          >
            Renvoyer
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

export function PageJournee() {
  const { id } = useParams()
  const journee = useJournee(id!)

  if (journee.isLoading) return <Chargement />
  if (!journee.data) return <AlerteErreur erreur={journee.error} />
  const j = journee.data
  const statut = STATUTS_JOURNEE[j.statut]

  return (
    <>
      <Button component={LienRouteur} to={`/journees?station=${j.stationId}`} startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Journées de {j.stationNom}
      </Button>
      <Stack direction="row" spacing={2} sx={{ mb: 3, alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="h5" component="h1">
          Journée du {formaterDate(j.date)}
        </Typography>
        <Chip color={statut.couleur} label={statut.libelle} />
        <Typography color="text.secondary">
          {j.stationCode} · {j.stationNom}
        </Typography>
      </Stack>
      {/* Remonté à chaque changement de statut (clôture, renvoi) : la saisie repart de l'état serveur */}
      <FicheJournee key={`${j.id}-${j.statut}`} journee={j} />
    </>
  )
}
