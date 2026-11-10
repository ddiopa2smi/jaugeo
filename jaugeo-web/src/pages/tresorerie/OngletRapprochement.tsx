import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh'
import LinkIcon from '@mui/icons-material/Link'
import LinkOffIcon from '@mui/icons-material/LinkOff'
import SettingsIcon from '@mui/icons-material/Settings'
import UploadFileIcon from '@mui/icons-material/UploadFile'
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
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { useFormatsReleve, useRafraichirTresorerie, useRapprochement, useRapprochementElectronique, useStations } from '../../api/requetes'
import type { BilanImport, FormatReleve, NatureReleve, OperationReleve, VersementResume } from '../../api/types'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterDate, formaterPrix, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { moisCourant } from '../../shared/stock'
import { formaterEcart } from '../../shared/tresorerie'

const NATURES_RELEVE: Record<NatureReleve, string> = {
  BANQUE: 'Banque',
  WAVE: 'Wave',
  ORANGE_MONEY: 'Orange Money',
  CARTE: 'Carte bancaire',
}

const SEPARATEURS: Record<string, string> = { ';': 'Point-virgule ( ; )', ',': 'Virgule ( , )', '\t': 'Tabulation', '|': 'Barre verticale ( | )' }

const versementCourt = (v: VersementResume) => `${v.stationCode ?? '?'} · BV ${v.referenceBv} du ${formaterDate(v.dateDepot)}`

// ------------------------------------------------------------------ Import

function DialogueImport({ formats, onFermer }: { formats: FormatReleve[]; onFermer: () => void }) {
  const rafraichir = useRafraichirTresorerie()
  const stations = useStations()
  const actifs = formats.filter((f) => f.actif)
  const [formatId, setFormatId] = useState(actifs[0]?.id ?? '')
  const [stationId, setStationId] = useState('')
  const [fichier, setFichier] = useState<File | null>(null)
  const [bilan, setBilan] = useState<BilanImport | null>(null)
  const champ = useRef<HTMLInputElement>(null)
  const format = actifs.find((f) => f.id === formatId)
  const marchand = format !== undefined && format.nature !== 'BANQUE'

  const envoi = useMutation({
    mutationFn: () => {
      const formulaire = new FormData()
      formulaire.append('fichier', fichier!)
      const p = new URLSearchParams({ format: formatId })
      if (marchand) p.set('station', stationId)
      return api.post<BilanImport>(`/tresorerie/releves?${p}`, formulaire)
    },
    onSuccess: (b) => {
      setBilan(b)
      rafraichir()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Importer un relevé</DialogTitle>
      <DialogContent>
        {bilan ? (
          <Stack spacing={1} data-testid="bilan-import">
            <Alert severity="success">
              {bilan.nomFichier} : {bilan.operations} opération(s) au crédit importée(s)
              {bilan.dateMin && ` du ${formaterDate(bilan.dateMin)} au ${formaterDate(bilan.dateMax!)}`}.
            </Alert>
            {bilan.lettrees > 0 && <Typography variant="body2">{bilan.lettrees} rapprochée(s) automatiquement d’un versement.</Typography>}
            {bilan.doublons > 0 && (
              <Typography variant="body2" color="text.secondary">
                {bilan.doublons} opération(s) déjà importée(s) par un relevé précédent, ignorée(s).
              </Typography>
            )}
            {bilan.ignorees > 0 && (
              <Typography variant="body2" color="text.secondary">
                {bilan.ignorees} ligne(s) sans crédit (débits, soldes) ignorée(s).
              </Typography>
            )}
          </Stack>
        ) : actifs.length === 0 ? (
          <Alert severity="info">Paramétrez d’abord le format du relevé de votre banque ou opérateur.</Alert>
        ) : (
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField select label="Format du relevé" value={formatId} onChange={(e) => setFormatId(e.target.value)}>
              {actifs.map((f) => (
                <MenuItem key={f.id} value={f.id}>
                  {f.libelle} · {NATURES_RELEVE[f.nature]}
                </MenuItem>
              ))}
            </TextField>
            {marchand && (
              <TextField
                select
                label="Station du compte marchand"
                value={stationId}
                onChange={(e) => setStationId(e.target.value)}
                helperText="Les paiements du relevé sont comparés aux encaissements déclarés par cette station."
              >
                {(stations.data ?? [])
                  .filter((s) => s.actif)
                  .map((s) => (
                    <MenuItem key={s.id} value={s.id}>
                      {s.code} · {s.nom}
                    </MenuItem>
                  ))}
              </TextField>
            )}
            <input
              ref={champ}
              type="file"
              hidden
              accept=".csv,.txt,.xlsx,.xls"
              aria-label="Fichier du relevé"
              onChange={(e) => setFichier(e.target.files?.[0] ?? null)}
            />
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Button variant="outlined" startIcon={<UploadFileIcon />} onClick={() => champ.current?.click()} sx={{ flexShrink: 0 }}>
                Choisir le fichier
              </Button>
              <Typography variant="body2" color={fichier ? 'text.primary' : 'text.secondary'} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                {fichier ? fichier.name : 'CSV ou Excel, 5 Mo au plus'}
              </Typography>
            </Stack>
            <AlerteErreur erreur={envoi.error} />
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>{bilan ? 'Fermer' : 'Annuler'}</Button>
        {!bilan && (
          <Button
            variant="contained"
            onClick={() => envoi.mutate()}
            disabled={envoi.isPending || !fichier || !formatId || (marchand && !stationId)}
          >
            Importer
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}

// ------------------------------------------------------------------ Formats

const VIDE = {
  code: '',
  libelle: '',
  nature: 'BANQUE' as NatureReleve,
  separateur: ';',
  formatDate: 'dd/MM/yyyy',
  lignesIgnorees: '1',
  colDate: '1',
  colLibelle: '2',
  colReference: '',
  colCredit: '',
  actif: true,
}

function FormulaireFormat({ format, onFin }: { format: FormatReleve | null; onFin: () => void }) {
  const rafraichir = useRafraichirTresorerie()
  const [v, setV] = useState(
    format
      ? {
          ...format,
          lignesIgnorees: String(format.lignesIgnorees),
          colDate: String(format.colDate),
          colLibelle: String(format.colLibelle),
          colReference: format.colReference === null ? '' : String(format.colReference),
          colCredit: String(format.colCredit),
        }
      : VIDE,
  )
  const maj = (cle: keyof typeof v) => (e: { target: { value: string } }) => setV((x) => ({ ...x, [cle]: e.target.value }))
  const enregistrement = useMutation({
    mutationFn: () => {
      const corps = {
        version: format?.version,
        code: v.code,
        libelle: v.libelle,
        nature: v.nature,
        separateur: v.separateur,
        formatDate: v.formatDate,
        lignesIgnorees: versNombre(v.lignesIgnorees) ?? 0,
        colDate: versNombre(v.colDate),
        colLibelle: versNombre(v.colLibelle),
        colReference: versNombre(v.colReference),
        colCredit: versNombre(v.colCredit),
        actif: v.actif,
      }
      return format ? api.put(`/tresorerie/formats-releve/${format.id}`, corps) : api.post('/tresorerie/formats-releve', corps)
    },
    onSuccess: () => {
      rafraichir()
      notifier('Format de relevé enregistré.')
      onFin()
    },
  })
  const colonne = (cle: 'colDate' | 'colLibelle' | 'colReference' | 'colCredit', libelle: string) => (
    <TextField label={libelle} value={v[cle]} onChange={maj(cle)} slotProps={{ htmlInput: { inputMode: 'numeric' } }} sx={{ flex: 1 }} />
  )
  return (
    <Stack spacing={2} sx={{ pt: 1 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Code" value={v.code} onChange={maj('code')} sx={{ width: { sm: 140 } }} />
        <TextField label="Libellé" value={v.libelle} onChange={maj('libelle')} sx={{ flex: 1 }} />
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField select label="Nature" value={v.nature} onChange={maj('nature')} sx={{ flex: 1 }}>
          {Object.entries(NATURES_RELEVE).map(([c, l]) => (
            <MenuItem key={c} value={c}>
              {l}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label="Séparateur (CSV)" value={v.separateur} onChange={maj('separateur')} sx={{ flex: 1 }}>
          {Object.entries(SEPARATEURS).map(([c, l]) => (
            <MenuItem key={c} value={c}>
              {l}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Format des dates" value={v.formatDate} onChange={maj('formatDate')} helperText="dd/MM/yyyy, yyyy-MM-dd…" sx={{ flex: 1 }} />
        <TextField
          label="Lignes d’en-tête à ignorer"
          value={v.lignesIgnorees}
          onChange={maj('lignesIgnorees')}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          sx={{ flex: 1 }}
        />
      </Stack>
      <Typography variant="caption" color="text.secondary">
        Numéro de colonne (1 = colonne A). Seules les lignes avec un montant au crédit sont importées.
      </Typography>
      <Stack direction="row" spacing={1}>
        {colonne('colDate', 'Date')}
        {colonne('colLibelle', 'Libellé')}
        {colonne('colReference', 'Référence')}
        {colonne('colCredit', 'Crédit')}
      </Stack>
      <AlerteErreur erreur={enregistrement.error} />
      <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
        <Button onClick={onFin}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !v.code.trim() || !v.libelle.trim() || !versNombre(v.colCredit)}
        >
          Enregistrer le format
        </Button>
      </Stack>
    </Stack>
  )
}

function DialogueFormats({ formats, modifiable, onFermer }: { formats: FormatReleve[]; modifiable: boolean; onFermer: () => void }) {
  const [edition, setEdition] = useState<FormatReleve | 'nouveau' | null>(null)
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Formats de relevé</DialogTitle>
      <DialogContent>
        {edition ? (
          <FormulaireFormat format={edition === 'nouveau' ? null : edition} onFin={() => setEdition(null)} />
        ) : (
          <>
            {formats.length === 0 && <Alert severity="info">Aucun format paramétré.</Alert>}
            {formats.map((f) => (
              <Stack key={f.id} direction="row" spacing={1} sx={{ alignItems: 'center', py: 1, borderBottom: 1, borderColor: 'divider' }}>
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {f.code} · {f.libelle}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {NATURES_RELEVE[f.nature]} · date col. {f.colDate} ({f.formatDate}) · crédit col. {f.colCredit}
                  </Typography>
                </Box>
                {!f.actif && <Chip size="small" label="Inactif" />}
                {modifiable && (
                  <Button size="small" onClick={() => setEdition(f)}>
                    Modifier
                  </Button>
                )}
              </Stack>
            ))}
          </>
        )}
      </DialogContent>
      {!edition && (
        <DialogActions>
          <Button onClick={onFermer}>Fermer</Button>
          {modifiable && (
            <Button variant="contained" onClick={() => setEdition('nouveau')}>
              Nouveau format
            </Button>
          )}
        </DialogActions>
      )}
    </Dialog>
  )
}

// ------------------------------------------------------------------ Lettrage

function LigneOperation({ o, peutLettrer }: { o: OperationReleve; peutLettrer: boolean }) {
  const rafraichir = useRafraichirTresorerie()
  const [choix, setChoix] = useState(o.candidats.length === 1 ? o.candidats[0]!.id : '')
  const lettrage = useMutation({
    mutationFn: (lettrer: boolean) =>
      lettrer
        ? api.put(`/tresorerie/rapprochement/operations/${o.id}/lettrage`, { versementId: choix })
        : api.delete(`/tresorerie/rapprochement/operations/${o.id}/lettrage`),
    onSuccess: (_r, lettrer) => {
      rafraichir()
      notifier(lettrer ? 'Opération rapprochée du versement.' : 'Rapprochement défait.')
    },
    onError: () => notifier('Rapprochement impossible : rechargez la page.', 'error'),
  })
  return (
    <TableRow data-testid={`operation-${o.id}`}>
      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDate(o.date)}</TableCell>
      <TableCell>
        {o.libelle}
        <Typography variant="caption" color="text.secondary" component="div">
          {o.format}
          {o.reference && ` · ${o.reference}`}
        </Typography>
      </TableCell>
      <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
        {formaterPrix(o.montant)}
      </TableCell>
      <TableCell>
        {o.versement ? (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Chip
              size="small"
              color="success"
              variant="outlined"
              label={versementCourt(o.versement)}
              sx={{ maxWidth: '100%' }}
            />
            <Typography variant="caption" color="text.secondary">
              {o.modeLettrage === 'AUTO' ? 'auto' : 'manuel'}
            </Typography>
            {peutLettrer && (
              <Tooltip title="Défaire le rapprochement">
                <IconButton size="small" aria-label="Défaire le rapprochement" disabled={lettrage.isPending}
                  onClick={() =>
                    confirmerPuis(
                      {
                        titre: 'Défaire le rapprochement ?',
                        message: `L'opération « ${o.libelle} » de ${formaterPrix(o.montant)} ne sera plus rapprochée de son versement.`,
                        action: 'Défaire',
                      },
                      () => lettrage.mutate(false),
                    )
                  }
                >
                  <LinkOffIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        ) : o.candidats.length === 0 ? (
          <Typography variant="body2" color="warning.main">
            Aucun versement de ce montant
          </Typography>
        ) : peutLettrer ? (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <TextField
              select
              size="small"
              value={choix}
              onChange={(e) => setChoix(e.target.value)}
              slotProps={{ htmlInput: { 'aria-label': `Versement pour ${o.libelle}` } }}
              sx={{ minWidth: 200, flex: 1 }}
            >
              {o.candidats.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {versementCourt(c)}
                </MenuItem>
              ))}
            </TextField>
            <Tooltip title="Rapprocher">
              <span>
                <IconButton aria-label="Rapprocher" onClick={() => lettrage.mutate(true)} disabled={!choix || lettrage.isPending} color="primary">
                  <LinkIcon />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {o.candidats.length} versement(s) possible(s)
          </Typography>
        )}
      </TableCell>
    </TableRow>
  )
}

// ------------------------------------------------------------------ Onglet

export function OngletRapprochement() {
  const [params, setParams] = useSearchParams()
  const mois = params.get('mois') ?? moisCourant()
  const r = useRapprochement(mois)
  const el = useRapprochementElectronique(mois)
  const formats = useFormatsReleve()
  const rafraichir = useRafraichirTresorerie()
  const [dialogue, setDialogue] = useState<'import' | 'formats' | null>(null)
  const auto = useMutation({
    mutationFn: () => api.post<{ lettrees: number }>('/tresorerie/rapprochement/lettrage-automatique'),
    onSuccess: (x) => {
      rafraichir()
      notifier(x.lettrees > 0 ? `${x.lettrees} opération(s) rapprochée(s).` : 'Aucun nouveau rapprochement certain.', x.lettrees > 0 ? 'success' : 'info')
    },
  })
  const d = r.data
  const peut = d?.peutLettrer ?? false
  const enAttente = d?.operations.filter((o) => !o.versement).length ?? 0

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <TextField
          type="month"
          label="Mois"
          value={mois}
          onChange={(e) => {
            if (!e.target.value) return
            const p = new URLSearchParams(params)
            p.set('mois', e.target.value)
            setParams(p)
          }}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Box sx={{ flexGrow: 1 }} />
        {peut && (
          <Button variant="contained" startIcon={<UploadFileIcon />} onClick={() => setDialogue('import')} sx={{ flexShrink: 0 }}>
            Importer un relevé
          </Button>
        )}
        {peut && (
          <Button variant="outlined" startIcon={<AutoFixHighIcon />} onClick={() => auto.mutate()} disabled={auto.isPending} sx={{ flexShrink: 0 }}>
            Lettrage automatique
          </Button>
        )}
        <Button variant="outlined" startIcon={<SettingsIcon />} onClick={() => setDialogue('formats')} sx={{ flexShrink: 0 }}>
          Formats
        </Button>
      </Stack>
      <AlerteErreur erreur={r.error ?? el.error ?? auto.error} />
      {r.isLoading && <Chargement />}
      {d && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, mb: 2 }}>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary" component="div">
                Taux de rapprochement
              </Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 600 }} data-testid="taux-rapprochement">
                {d.taux === null ? '—' : `${d.taux.toLocaleString('fr-FR')} %`}
              </Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary" component="div">
                Versements rapprochés
              </Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                {d.versementsLettres} / {d.versements}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formaterPrix(d.montantLettre)} sur {formaterPrix(d.montantVersements)}
              </Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary" component="div">
                Crédits du relevé à rapprocher
              </Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 600, color: enAttente > 0 ? 'warning.main' : undefined }}>{enAttente}</Typography>
            </Paper>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary" component="div">
                Écarts Wave, Orange Money, carte
              </Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 600, color: (el.data?.ecarts ?? 0) > 0 ? 'error.main' : undefined }}>
                {el.data?.ecarts ?? '—'}
              </Typography>
            </Paper>
          </Box>

          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Relevé bancaire
          </Typography>
          {d.operations.length === 0 ? (
            <Alert severity="info" sx={{ mb: 3 }}>
              Aucun relevé bancaire importé pour ce mois.
            </Alert>
          ) : (
            <Paper variant="outlined" sx={{ overflowX: 'auto', mb: 3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Libellé</TableCell>
                    <TableCell align="right">Crédit</TableCell>
                    <TableCell>Versement</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.operations.map((o) => (
                    <LigneOperation key={`${o.id}-${o.versement?.id ?? ''}`} o={o} peutLettrer={peut} />
                  ))}
                </TableBody>
              </Table>
            </Paper>
          )}

          {d.versementsNonLettres.length > 0 && (
            <>
              <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
                Versements non retrouvés au relevé
              </Typography>
              <Paper variant="outlined" sx={{ overflowX: 'auto', mb: 3 }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Dépôt</TableCell>
                      <TableCell>Station</TableCell>
                      <TableCell>Bordereau</TableCell>
                      <TableCell align="right">Montant</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {d.versementsNonLettres.map((v) => (
                      <TableRow key={v.id}>
                        <TableCell>{formaterDate(v.dateDepot)}</TableCell>
                        <TableCell>{v.stationCode}</TableCell>
                        <TableCell>
                          {v.banque} · {v.referenceBv}
                        </TableCell>
                        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                          {formaterPrix(v.montant)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            </>
          )}
        </>
      )}

      {el.data && (
        <>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Wave, Orange Money et carte
          </Typography>
          {el.data.lignes.length === 0 ? (
            <Alert severity="info" sx={{ mb: 3 }}>
              Aucun encaissement électronique déclaré ni relevé marchand importé ce mois.
            </Alert>
          ) : (
            <Paper variant="outlined" sx={{ overflowX: 'auto', mb: 3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Station</TableCell>
                    <TableCell>Mode</TableCell>
                    <TableCell align="right">Déclaré</TableCell>
                    <TableCell align="right">Relevé</TableCell>
                    <TableCell align="right">Écart</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {el.data.lignes.map((l) => (
                    <TableRow key={`${l.stationId}-${l.date}-${l.mode}`} data-testid={`electronique-${l.stationCode}-${l.date}-${l.mode}`}>
                      <TableCell>{formaterDate(l.date)}</TableCell>
                      <TableCell>{l.stationCode}</TableCell>
                      <TableCell>{l.libelle}</TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {formaterPrix(l.declare)}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {l.releve === null ? (
                          <Typography variant="caption" color="text.secondary">
                            relevé non importé
                          </Typography>
                        ) : (
                          formaterPrix(l.releve)
                        )}
                      </TableCell>
                      <TableCell
                        align="right"
                        sx={{ whiteSpace: 'nowrap', fontWeight: 600, color: l.ecart ? 'error.main' : undefined }}
                      >
                        {l.ecart === null ? '' : formaterEcart(l.ecart, formaterPrix)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          )}
        </>
      )}

      {d && d.releves.length > 0 && (
        <>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Relevés importés
          </Typography>
          {d.releves.map((x) => (
            <Typography key={x.id} variant="body2" color="text.secondary" sx={{ py: 0.25 }}>
              {x.nomFichier} · {x.format}
              {x.stationCode && ` (${x.stationCode})`} · {x.dateMin && `${formaterDate(x.dateMin)} → ${formaterDate(x.dateMax!)}`} ·{' '}
              {x.operations} opération(s){x.doublons > 0 && `, ${x.doublons} déjà connue(s)`} · importé par {x.importePar}
            </Typography>
          ))}
        </>
      )}

      {dialogue === 'import' && formats.data && <DialogueImport formats={formats.data} onFermer={() => setDialogue(null)} />}
      {dialogue === 'formats' && formats.data && (
        <DialogueFormats formats={formats.data} modifiable={peut} onFermer={() => setDialogue(null)} />
      )}
    </>
  )
}
