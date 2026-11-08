import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tabs from '@mui/material/Tabs'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { useComplements, useStations, useStockMensuel } from '../../api/requetes'
import type { ComplementCiterne } from '../../api/types'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { formaterPct, moisCourant, NIVEAUX_ECART } from '../../shared/stock'

function AnalyseMensuelle() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const mois = params.get('mois') ?? moisCourant()
  const analyse = useStockMensuel(stationId, mois)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    if (stationId && !p.get('station')) p.set('station', stationId)
    setParams(p)
  }

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        {actives.length > 1 && (
          <TextField select label="Station" value={stationId ?? ''} onChange={(e) => changer('station', e.target.value)} sx={{ minWidth: 280 }}>
            {actives.map((s) => (
              <MenuItem key={s.id} value={s.id}>
                {s.code} · {s.nom}
              </MenuItem>
            ))}
          </TextField>
        )}
        <TextField
          type="month"
          label="Mois"
          value={mois}
          onChange={(e) => e.target.value && changer('mois', e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Stack>
      <AlerteErreur erreur={stations.error ?? analyse.error} />
      {!stationId && !stations.isLoading && <Alert severity="info">Choisissez une station.</Alert>}
      {analyse.isLoading && <Chargement />}
      {analyse.data && analyse.data.lignes.length === 0 && (
        <Alert severity="info">Aucune journée clôturée avec jaugeage sur ce mois.</Alert>
      )}
      {analyse.data && analyse.data.totaux.length > 0 && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, mb: 3 }}>
            {analyse.data.totaux.map((t) => {
              const n = NIVEAUX_ECART[t.niveau]
              return (
                <Paper key={t.cuveId} variant="outlined" sx={{ p: 2 }} data-testid={`total-${t.cuveCode}`}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                      Cuve {t.cuveCode} · {t.produitLibelle}
                    </Typography>
                    <Chip size="small" color={n.couleur} label={`${formaterPct(t.ecartPct)} · ${n.libelle}`} />
                  </Stack>
                  <Table size="small">
                    <TableBody>
                      {[
                        ['Stock de départ', t.stockDepart],
                        ['+ Réceptions', t.receptions],
                        ['− Sorties', t.sorties],
                        ['= Stock théorique', t.stockTheorique],
                        ['Stock physique (jauge)', t.stockPhysique],
                      ].map(([l, v]) => (
                        <TableRow key={l as string}>
                          <TableCell>{l}</TableCell>
                          <TableCell align="right">{formaterNombre(v as number)} L</TableCell>
                        </TableRow>
                      ))}
                      <TableRow>
                        <TableCell sx={{ fontWeight: 700 }}>Écart</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, color: t.ecartLitres < 0 ? 'error.main' : undefined }}>
                          {formaterNombre(t.ecartLitres)} L · {formaterPrix(t.ecartValeur)}
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                  <Typography variant="caption" color="text.secondary">
                    {t.jours} jour(s) · tolérance {formaterPct(t.tolerancePct)}
                  </Typography>
                </Paper>
              )
            })}
          </Box>
          <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Cuve</TableCell>
                  <TableCell align="right">Départ</TableCell>
                  <TableCell align="right">Réceptions</TableCell>
                  <TableCell align="right">Sorties</TableCell>
                  <TableCell align="right">Théorique</TableCell>
                  <TableCell align="right">Jauge</TableCell>
                  <TableCell align="right">Écart</TableCell>
                  <TableCell align="right">Cumul mois</TableCell>
                  <TableCell>Observation</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {analyse.data.lignes.map((l) => (
                  <TableRow
                    key={`${l.date}-${l.cuveId}`}
                    hover
                    sx={{ cursor: l.journeeId ? 'pointer' : undefined }}
                    onClick={() => l.journeeId && navigate(`/journees/${l.journeeId}`)}
                  >
                    <TableCell>{formaterDate(l.date)}</TableCell>
                    <TableCell>{l.cuveCode}</TableCell>
                    <TableCell align="right">{formaterNombre(l.stockDepart)}</TableCell>
                    <TableCell align="right">{l.receptions ? formaterNombre(l.receptions) : ''}</TableCell>
                    <TableCell align="right">{formaterNombre(l.sorties)}</TableCell>
                    <TableCell align="right">{formaterNombre(l.stockTheorique)}</TableCell>
                    <TableCell align="right">{formaterNombre(l.stockPhysique)}</TableCell>
                    <TableCell align="right" sx={{ color: l.ecartLitres < 0 ? 'error.main' : undefined }}>
                      {formaterNombre(l.ecartLitres)}
                    </TableCell>
                    <TableCell align="right">
                      <Chip size="small" variant="outlined" color={NIVEAUX_ECART[l.niveau].couleur} label={formaterPct(l.ecartPctMois)} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>{l.observation}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
    </>
  )
}

function DialogueReglement({ complement, onFermer }: { complement: ComplementCiterne; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [reference, setReference] = useState('')
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const reglement = useMutation({
    mutationFn: () =>
      api.post<ComplementCiterne>(`/stock/complements/${complement.id}/reglement`, {
        version: complement.version,
        reference: reference.trim(),
        date,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] })
      notifier('Règlement du complément enregistré.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Règlement du complément</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          BL {complement.numeroBl} · {complement.transporteur ?? 'transporteur'} : {formaterNombre(complement.litres)} L, soit{' '}
          {formaterPrix(complement.montant)}.
        </Typography>
        <Stack spacing={2}>
          <TextField label="Référence du règlement" value={reference} onChange={(e) => setReference(e.target.value)} autoFocus required />
          <TextField type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        </Stack>
        <AlerteErreur erreur={reglement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => reglement.mutate()} disabled={reglement.isPending || !reference.trim() || !date}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function Complements() {
  const [statut, setStatut] = useState('A_REGLER')
  const liste = useComplements(statut)
  const [aRegler, setARegler] = useState<ComplementCiterne | null>(null)
  const navigate = useNavigate()

  return (
    <>
      <TextField select label="Statut" value={statut} onChange={(e) => setStatut(e.target.value)} sx={{ mb: 2, minWidth: 200 }}>
        <MenuItem value="A_REGLER">À régler</MenuItem>
        <MenuItem value="REGLE">Réglés</MenuItem>
        <MenuItem value="">Tous</MenuItem>
      </TextField>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="success">Aucun complément citerne {statut === 'A_REGLER' ? 'en attente' : ''}.</Alert>}
      {liste.data && liste.data.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Station</TableCell>
                <TableCell>BL</TableCell>
                <TableCell>Transporteur</TableCell>
                <TableCell align="right">BL / reçu</TableCell>
                <TableCell align="right">Manquant</TableCell>
                <TableCell align="right">Montant</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {liste.data.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ cursor: 'pointer' }} onClick={() => c.journeeId && navigate(`/journees/${c.journeeId}`)}>
                    {formaterDate(c.date)}
                  </TableCell>
                  <TableCell>{c.stationCode}</TableCell>
                  <TableCell>
                    {c.numeroBl}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {c.fournisseur}
                    </Typography>
                  </TableCell>
                  <TableCell>{[c.transporteur, c.immatriculation].filter(Boolean).join(' · ') || '—'}</TableCell>
                  <TableCell align="right">
                    {formaterNombre(c.quantiteBl)} / {formaterNombre(c.quantiteRecue)}
                  </TableCell>
                  <TableCell align="right">{formaterNombre(c.litres)} L</TableCell>
                  <TableCell align="right">{formaterPrix(c.montant)}</TableCell>
                  <TableCell>
                    {c.statut === 'REGLE' ? (
                      <Chip size="small" color="success" label={`Réglé le ${formaterDate(c.regleLe)} · ${c.referenceReglement}`} />
                    ) : (
                      <Chip size="small" color="warning" label="À régler" />
                    )}
                  </TableCell>
                  <TableCell>
                    {c.reglable && (
                      <Button size="small" onClick={() => setARegler(c)}>
                        Régler
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {aRegler && <DialogueReglement complement={aRegler} onFermer={() => setARegler(null)} />}
    </>
  )
}

export function PageStocks() {
  const [params, setParams] = useSearchParams()
  const onglet = params.get('onglet') === 'complements' ? 'complements' : 'analyse'
  return (
    <>
      <EnTetePage titre="Stocks" sousTitre="Stock théorique contre jauges, écarts du mois par cuve, compléments citerne." />
      <Tabs
        value={onglet}
        onChange={(_e, v) => {
          const p = new URLSearchParams(params)
          p.set('onglet', v)
          setParams(p)
        }}
        sx={{ mb: 2 }}
      >
        <Tab value="analyse" label="Analyse mensuelle" />
        <Tab value="complements" label="Compléments citerne" />
      </Tabs>
      {onglet === 'analyse' ? <AnalyseMensuelle /> : <Complements />}
    </>
  )
}
