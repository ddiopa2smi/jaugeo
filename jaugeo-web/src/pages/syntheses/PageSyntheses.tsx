import CommentIcon from '@mui/icons-material/Comment'
import DownloadIcon from '@mui/icons-material/Download'
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tabs from '@mui/material/Tabs'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useSyntheseStocks, useSyntheseVentes } from '../../api/requetes'
import type { CommentaireMensuel, LigneVentes, SyntheseVentes } from '../../api/types'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterNombre, formaterPrix, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { formaterPct, moisCourant, NIVEAUX_ECART } from '../../shared/stock'
import { formaterEcart } from '../../shared/tresorerie'
import { CadreTableau } from '../../shared/tableaux'

const f = (v: number) => formaterPrix(v)
const cellule = { whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' } as const

/** Colonnes de la feuille ANALYSE VENTES & RECETTES (lettres du classeur d'origine). */
const COLONNES: { cle: keyof LigneVentes; libelle: string; calcul?: boolean }[] = [
  { cle: 'recettes', libelle: 'Recettes du mois (E)' },
  { cle: 'soldeOuverture', libelle: 'Solde caisse ouverture (F)' },
  { cle: 'creancesDebut', libelle: 'Créances début (G)' },
  { cle: 'aVerser', libelle: 'Total à verser (H = E+F+G)', calcul: true },
  { cle: 'versements', libelle: 'Versements bancaires (I)' },
  { cle: 'soldeCaisse', libelle: 'Solde caisse gérant (J)' },
  { cle: 'creancesFin', libelle: 'Créances fin (K)' },
  { cle: 'depenses', libelle: 'Dépenses autorisées (L)' },
  { cle: 'complements', libelle: 'Compléments citerne (M)' },
  { cle: 'horsCaisse', libelle: 'Hors caisse : Wave, OM, cartes, remises (N)' },
  { cle: 'reglementsSiege', libelle: 'Règlements au siège (O)' },
  { cle: 'totalVersements', libelle: 'Total versements (Q)', calcul: true },
]

async function telecharger(chemin: string, nom: string) {
  const url = URL.createObjectURL(await api.blob(chemin))
  const lien = document.createElement('a')
  lien.href = url
  lien.download = nom
  lien.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

function DialogueCommentaire({ mois, ligne, onFermer }: { mois: string; ligne: LigneVentes; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [texte, setTexte] = useState(ligne.commentaire ?? '')
  const enregistrement = useMutation({
    mutationFn: () =>
      api.put<CommentaireMensuel>('/syntheses/commentaires', {
        stationId: ligne.stationId,
        mois,
        texte: videVersNull(texte),
        version: ligne.commentaireVersion,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cles.syntheses })
      notifier('Observation enregistrée.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Observation · {ligne.stationCode}</DialogTitle>
      <DialogContent>
        {ligne.observation && (
          <Alert severity="info" sx={{ mb: 2 }}>
            {ligne.observation}
          </Alert>
        )}
        <TextField
          label="Observation du contrôle de gestion"
          value={texte}
          onChange={(e) => setTexte(e.target.value)}
          fullWidth
          multiline
          minRows={3}
          helperText="Vide : efface l'observation."
          slotProps={{ htmlInput: { maxLength: 1000 } }}
        />
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={enregistrement.isPending}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function LigneTableau({ s, l, total, onCommenter }: { s: SyntheseVentes; l: LigneVentes; total?: boolean; onCommenter?: () => void }) {
  const sx = total ? { ...cellule, fontWeight: 700 } : cellule
  return (
    <TableRow data-testid={total ? 'synthese-cumul' : `synthese-${l.stationCode}`}>
      <TableCell sx={{ fontWeight: total ? 700 : undefined, position: 'sticky', left: 0, bgcolor: 'background.paper' }}>
        {total ? 'CUMUL' : l.stationCode}
        {!total && (
          <Typography variant="caption" color="text.secondary" component="div">
            {l.stationNom}
          </Typography>
        )}
      </TableCell>
      {s.produits.map((p) => (
        <TableCell key={p.code} align="right" sx={sx}>
          {formaterNombre(l.litres[p.code] ?? 0)}
        </TableCell>
      ))}
      {COLONNES.map((c) => (
        <TableCell key={c.cle} align="right" sx={{ ...sx, bgcolor: c.calcul ? 'action.hover' : undefined }}>
          {f(l[c.cle] as number)}
        </TableCell>
      ))}
      <TableCell
        align="right"
        sx={{ ...sx, fontWeight: 700, color: l.ecart < 0 ? 'error.main' : l.ecart > 0 ? 'success.main' : undefined }}
        data-testid={total ? undefined : `ecart-${l.stationCode}`}
      >
        {formaterEcart(l.ecart, formaterPrix)}
      </TableCell>
      <TableCell sx={{ minWidth: 260 }}>
        {l.commentaire && <Typography variant="body2">{l.commentaire}</Typography>}
        {l.observation && (
          <Typography variant="caption" color="text.secondary" component="div">
            {l.observation}
          </Typography>
        )}
        {l.joursNonClotures > 0 && !total && <Chip size="small" color="warning" label="Provisoire" sx={{ mt: 0.5 }} />}
      </TableCell>
      <TableCell>
        {onCommenter && (
          <Tooltip title="Observation">
            <IconButton size="small" aria-label={`Observation ${l.stationCode}`} onClick={onCommenter}>
              <CommentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </TableCell>
    </TableRow>
  )
}

function OngletVentes({ mois }: { mois: string }) {
  const requete = useSyntheseVentes(mois)
  const [commentaire, setCommentaire] = useState<LigneVentes | null>(null)
  const s = requete.data
  if (!s) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Q = I + J + K + L + M + O ; R = Q − H. Quand toutes les journées sont clôturées, R est la somme des écarts constatés aux
        arrêtés de caisse du mois.
      </Typography>
      <CadreTableau>
        <Table size="small" aria-label="Analyse des ventes et recettes">
          <TableHead>
            <TableRow>
              <TableCell sx={{ position: 'sticky', left: 0, bgcolor: 'background.paper' }}>Station</TableCell>
              {s.produits.map((p) => (
                <TableCell key={p.code} align="right">
                  {p.libelle} (L)
                </TableCell>
              ))}
              {COLONNES.map((c) => (
                <TableCell key={c.cle} align="right">
                  {c.libelle}
                </TableCell>
              ))}
              <TableCell align="right">Écart sur versement (R)</TableCell>
              <TableCell>Observations</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {s.lignes.map((l) => (
              <LigneTableau key={l.stationCode} s={s} l={l} onCommenter={s.peutCommenter ? () => setCommentaire(l) : undefined} />
            ))}
            {s.lignes.length > 1 && <LigneTableau s={s} l={s.cumul} total />}
          </TableBody>
        </Table>
      </CadreTableau>
      {s.lignes.length > 0 && (
        <Typography variant="body2" sx={{ mt: 1 }}>
          Dont bons direction {f(s.cumul.bonsDirection)} · bons gérant {f(s.cumul.bonsGerant)} · créances recouvrées en espèces{' '}
          {f(s.cumul.recouvrements)} · créances laissées aux gérants {f(s.cumul.creancesGerantFin)}
        </Typography>
      )}
      {commentaire && <DialogueCommentaire mois={mois} ligne={commentaire} onFermer={() => setCommentaire(null)} />}
    </>
  )
}

function OngletStocks({ mois }: { mois: string }) {
  const requete = useSyntheseStocks(mois)
  const s = requete.data
  if (!s) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  if (s.lignes.length === 0) return <Alert severity="info">Aucune journée clôturée ce mois-ci.</Alert>
  return (
    <>
      <CadreTableau>
        <Table size="small" aria-label="Analyse des stocks">
          <TableHead>
            <TableRow>
              <TableCell>Station</TableCell>
              <TableCell>Cuve</TableCell>
              <TableCell align="right">Stock départ</TableCell>
              <TableCell align="right">Réceptions</TableCell>
              <TableCell align="right">Sorties</TableCell>
              <TableCell align="right">Théorique</TableCell>
              <TableCell align="right">Physique</TableCell>
              <TableCell align="right">Écart (L)</TableCell>
              <TableCell align="right">Écart %</TableCell>
              <TableCell align="right">Valeur</TableCell>
              <TableCell>Niveau</TableCell>
              <TableCell align="right">Jours</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {s.lignes.map((l) => {
              const niveau = NIVEAUX_ECART[l.niveau as keyof typeof NIVEAUX_ECART]
              return (
                <TableRow key={`${l.stationCode}-${l.cuveCode}`}>
                  <TableCell>{l.stationCode}</TableCell>
                  <TableCell>
                    {l.cuveCode}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {l.produitLibelle}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.stockDepart)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.receptions)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.sorties)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.stockTheorique)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.stockPhysique)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterNombre(l.ecartLitres)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {formaterPct(l.ecartPct)}
                  </TableCell>
                  <TableCell align="right" sx={cellule}>
                    {l.ecartValeur !== null ? f(l.ecartValeur) : '—'}
                  </TableCell>
                  <TableCell>
                    {niveau ? <Chip size="small" color={niveau.couleur} label={niveau.libelle} /> : l.niveau}
                  </TableCell>
                  <TableCell align="right">{l.jours}</TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </CadreTableau>
      <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>
        Total du réseau par produit
      </Typography>
      <CadreTableau>
        <Table size="small" aria-label="Total par produit">
          <TableHead>
            <TableRow>
              <TableCell>Produit</TableCell>
              <TableCell align="right">Réceptions</TableCell>
              <TableCell align="right">Sorties</TableCell>
              <TableCell align="right">Écart (L)</TableCell>
              <TableCell align="right">Écart % des sorties</TableCell>
              <TableCell align="right">Valeur</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {s.totaux.map((t) => (
              <TableRow key={t.produitCode}>
                <TableCell>{t.produitLibelle}</TableCell>
                <TableCell align="right" sx={cellule}>
                  {formaterNombre(t.receptions)}
                </TableCell>
                <TableCell align="right" sx={cellule}>
                  {formaterNombre(t.sorties)}
                </TableCell>
                <TableCell align="right" sx={cellule}>
                  {formaterNombre(t.ecartLitres)}
                </TableCell>
                <TableCell align="right" sx={cellule}>
                  {formaterPct(t.ecartPct)}
                </TableCell>
                <TableCell align="right" sx={cellule}>
                  {f(t.ecartValeur)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CadreTableau>
    </>
  )
}

export function PageSyntheses() {
  const [params, setParams] = useSearchParams()
  const mois = params.get('mois') ?? moisCourant()
  const onglet = params.get('onglet') === 'stocks' ? 'stocks' : 'ventes'
  const [erreur, setErreur] = useState<unknown>(null)
  const [enCours, setEnCours] = useState(false)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    setParams(p)
  }
  const exporter = async (format: 'xlsx' | 'pdf') => {
    setErreur(null)
    setEnCours(true)
    try {
      await telecharger(`/syntheses/export.${format}?mois=${mois}`, `analyse-${mois}.${format}`)
    } catch (e) {
      setErreur(e)
    } finally {
      setEnCours(false)
    }
  }

  return (
    <>
      <EnTetePage
        titre="Synthèses mensuelles"
        sousTitre="Analyse des ventes, recettes et stocks, calculée sur les journées clôturées"
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<DownloadIcon />} disabled={enCours} onClick={() => exporter('xlsx')}>
              Excel
            </Button>
            <Button variant="outlined" startIcon={<PictureAsPdfIcon />} disabled={enCours} onClick={() => exporter('pdf')}>
              PDF
            </Button>
          </Stack>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <TextField
          type="month"
          label="Mois"
          value={mois}
          onChange={(e) => e.target.value && changer('mois', e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Box sx={{ flexGrow: 1 }} />
      </Stack>
      <AlerteErreur erreur={erreur} />
      <Tabs value={onglet} onChange={(_e, v: string) => changer('onglet', v)} sx={{ mb: 2 }}>
        <Tab value="ventes" label="Ventes et recettes" />
        <Tab value="stocks" label="Stocks" />
      </Tabs>
      {onglet === 'ventes' ? <OngletVentes mois={mois} /> : <OngletStocks mois={mois} />}
    </>
  )
}
