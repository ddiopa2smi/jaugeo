import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import SaveIcon from '@mui/icons-material/Save'
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
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useImperativeHandle, useState, type Ref } from 'react'
import { api } from '../../api/client'
import { cles, useStockJournee } from '../../api/requetes'
import type { CuveStock, Imputation, SaisieJaugeage, SaisieReception, StockJournee } from '../../api/types'
import { envoyerSaisie } from '../../horsligne/file'
import { AlerteErreur } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import {
  apercuEcart,
  corpsJauge,
  facteur15,
  formaterPct,
  IMPUTATIONS,
  jaugeInitiale,
  memeJauge,
  NIVEAUX_ECART,
  type SaisieJauge,
} from '../../shared/stock'

/** Ce que la fiche journée pilote avant la clôture : enregistrer les jaugeages encore en cours de saisie. */
export interface PoigneeStock {
  modifie: boolean
  /** null : réseau coupé, jaugeages gardés dans la tablette jusqu'à l'envoi. */
  enregistrer: (version: number) => Promise<StockJournee | null>
}

interface SaisieCuve {
  ouverture?: SaisieJauge
  cloture?: SaisieJauge
  observation?: string
  /** Hauteur d'eau en fond de cuve (mm) relevée au jaugeage de fin */
  eau?: string
}

const eauInitiale = (c: CuveStock) => (c.cloture?.hauteurEauMm == null ? '' : String(c.cloture.hauteurEauMm))

const litres = (v: number | null | undefined) => (v === null || v === undefined ? '—' : `${formaterNombre(v)} L`)

function ChampJauge({
  libelle,
  cuve,
  valeur,
  modifiable,
  onChange,
}: {
  libelle: string
  cuve: CuveStock
  valeur: SaisieJauge
  modifiable: boolean
  onChange: (s: SaisieJauge) => void
}) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <TextField
        label={valeur.mode === 'hauteur' ? `${libelle} (mm)` : `${libelle} (L)`}
        value={valeur.valeur}
        onChange={(e) => onChange({ ...valeur, valeur: e.target.value })}
        disabled={!modifiable}
        slotProps={{ htmlInput: { inputMode: 'decimal', autoComplete: 'off', 'aria-label': `${libelle} ${cuve.cuveCode}` } }}
        sx={{ width: 170, '& input': { fontSize: 18 } }}
      />
      {modifiable && cuve.bareme && (
        <ToggleButtonGroup
          size="small"
          exclusive
          value={valeur.mode}
          onChange={(_e, mode) => mode && onChange({ mode, valeur: '' })}
          aria-label={`Unité ${libelle} ${cuve.cuveCode}`}
        >
          <ToggleButton value="hauteur">mm</ToggleButton>
          <ToggleButton value="volume">L</ToggleButton>
        </ToggleButtonGroup>
      )}
    </Stack>
  )
}

function Valeur({ libelle, valeur, testid, couleur }: { libelle: string; valeur: string; testid?: string; couleur?: string }) {
  return (
    <Box sx={{ minWidth: 110 }}>
      <Typography variant="caption" color="text.secondary" component="div">
        {libelle}
      </Typography>
      <Typography sx={{ fontVariantNumeric: 'tabular-nums', fontSize: 17, fontWeight: 600, color: couleur }} data-testid={testid}>
        {valeur}
      </Typography>
    </Box>
  )
}

function CarteCuve({
  cuve,
  saisie,
  modifiable,
  onChange,
}: {
  cuve: CuveStock
  saisie: SaisieCuve
  modifiable: boolean
  onChange: (s: SaisieCuve) => void
}) {
  const cloture = saisie.cloture ?? jaugeInitiale(cuve, cuve.cloture)
  const ouverture = saisie.ouverture ?? jaugeInitiale(cuve, cuve.ouverture)
  const observation = saisie.observation ?? cuve.cloture?.observation ?? ''
  const eau = saisie.eau ?? eauInitiale(cuve)
  const retouchee = saisie.cloture !== undefined || saisie.ouverture !== undefined || saisie.eau !== undefined
  const ecart = retouchee ? apercuEcart(saisie.ouverture ? null : cuve.stockTheorique, cloture) : cuve.ecartLitres
  const niveau = !retouchee && cuve.niveau ? NIVEAUX_ECART[cuve.niveau] : null

  return (
    <Box sx={{ py: 1.5 }} data-testid={`cuve-${cuve.cuveCode}`}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Cuve {cuve.cuveCode} · {cuve.produitLibelle}
        </Typography>
        {cuve.capaciteLitres !== null && (
          <Typography variant="caption" color="text.secondary">
            capacité {litres(cuve.capaciteLitres)}
          </Typography>
        )}
        {niveau && <Chip size="small" color={niveau.couleur} label={niveau.libelle} data-testid={`niveau-${cuve.cuveCode}`} />}
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'flex-end' }, flexWrap: 'wrap', rowGap: 2 }}>
        {cuve.departReporte ? (
          <Valeur libelle="Stock de départ (veille)" valeur={litres(cuve.stockDepart)} />
        ) : (
          <ChampJauge
            libelle="Stock de départ"
            cuve={cuve}
            valeur={ouverture}
            modifiable={modifiable}
            onChange={(s) => onChange({ ...saisie, ouverture: s })}
          />
        )}
        <Valeur libelle="+ Réceptions" valeur={litres(cuve.receptions)} />
        <Valeur libelle="− Sorties" valeur={litres(cuve.sorties)} />
        <Valeur libelle="= Théorique" valeur={litres(cuve.stockTheorique)} testid={`theorique-${cuve.cuveCode}`} />
        <ChampJauge
          libelle="Jaugeage de fin"
          cuve={cuve}
          valeur={cloture}
          modifiable={modifiable}
          onChange={(s) => onChange({ ...saisie, cloture: s })}
        />
        <TextField
          label={`Eau (mm) · seuil ${cuve.seuilEauMm}`}
          value={eau}
          onChange={(e) => onChange({ ...saisie, eau: e.target.value })}
          disabled={!modifiable}
          slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off', 'aria-label': `Eau en fond de cuve ${cuve.cuveCode}` } }}
          sx={{ width: 170, '& input': { fontSize: 18 } }}
        />
        <Valeur
          libelle="Écart"
          valeur={ecart === null ? (retouchee ? 'à l’enregistrement' : '—') : `${ecart > 0 ? '+' : ''}${formaterNombre(ecart)} L`}
          testid={`ecart-${cuve.cuveCode}`}
          couleur={ecart !== null && ecart < 0 ? 'error.main' : undefined}
        />
      </Stack>

      {!retouchee && cuve.cloture && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {cuve.cloture.hauteurMm !== null && `${formaterNombre(cuve.cloture.hauteurMm)} mm = ${litres(cuve.cloture.volumeLitres)} · `}
          {cuve.cloture.volumeEauLitres ? `eau déduite ${litres(cuve.cloture.volumeEauLitres)} · ` : ''}
          Écart du jour {formaterPct(cuve.ecartPctJour)}
          {cuve.ecartValeur !== null && ` (${formaterPrix(cuve.ecartValeur)})`} · cumul du mois {litres(cuve.cumulEcartMois)} sur{' '}
          {litres(cuve.cumulSortiesMois)} vendus, soit <strong>{formaterPct(cuve.ecartPctMois)}</strong> (tolérance{' '}
          {formaterPct(cuve.tolerancePct)})
        </Typography>
      )}

      {(cuve.observationRequise || observation) && (
        <TextField
          label="Observation sur l'écart"
          value={observation}
          onChange={(e) => onChange({ ...saisie, observation: e.target.value })}
          disabled={!modifiable}
          required={cuve.observationRequise}
          fullWidth
          multiline
          slotProps={{ htmlInput: { maxLength: 500, 'aria-label': `Observation ${cuve.cuveCode}` } }}
          helperText={cuve.observationRequise ? 'Écart hors tolérance : expliquez-le (fuite, erreur de jaugeage, livraison…).' : undefined}
          sx={{ mt: 1.5 }}
        />
      )}
      {!retouchee &&
        cuve.anomalies.map((a) => (
          <Alert key={a.code} severity="error" sx={{ mt: 1 }}>
            {a.message.charAt(0).toUpperCase() + a.message.slice(1)}
          </Alert>
        ))}
      {!retouchee &&
        cuve.alertes.map((a) => (
          <Alert key={a.code} severity="warning" sx={{ mt: 1 }}>
            {a.message.charAt(0).toUpperCase() + a.message.slice(1)}
          </Alert>
        ))}
    </Box>
  )
}

const receptionVide = (cuveId: string) => ({
  cuveId,
  numeroBl: '',
  fournisseur: '',
  transporteur: '',
  chauffeur: '',
  immatriculation: '',
  scelles: '',
  quantiteBl: '',
  jaugeAvant: '',
  jaugeApres: '',
  quantiteRecue: '',
  temperatureC: '',
  densite: '',
  observation: '',
  quantiteBl15: '',
  /** 'oui' | 'non' | '' (non vérifié) */
  scellesIntacts: '',
  /** '' = automatique */
  imputation: '',
})

type FormulaireReception = ReturnType<typeof receptionVide>

function DialogueReception({ stock, onFermer }: { stock: StockJournee; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [f, setF] = useState<FormulaireReception>(() => receptionVide(stock.cuves[0]?.cuveId ?? ''))
  const maj = (champ: keyof FormulaireReception) => (e: { target: { value: string } }) => setF({ ...f, [champ]: e.target.value })
  const ajout = useMutation({
    mutationFn: () =>
      api.post<StockJournee>(`/journees/${stock.journeeId}/receptions`, {
        version: stock.version,
        cuveId: f.cuveId,
        numeroBl: f.numeroBl.trim(),
        fournisseur: f.fournisseur.trim(),
        transporteur: videVersNull(f.transporteur),
        chauffeur: videVersNull(f.chauffeur),
        immatriculation: videVersNull(f.immatriculation),
        scelles: videVersNull(f.scelles),
        quantiteBl: versNombre(f.quantiteBl)!,
        jaugeAvant: versNombre(f.jaugeAvant),
        jaugeApres: versNombre(f.jaugeApres),
        quantiteRecue: versNombre(f.quantiteRecue),
        temperatureC: versNombre(f.temperatureC),
        densite: versNombre(f.densite),
        observation: videVersNull(f.observation),
        quantiteBl15: versNombre(f.quantiteBl15),
        scellesIntacts: f.scellesIntacts === '' ? null : f.scellesIntacts === 'oui',
        imputation: f.imputation === '' ? null : (f.imputation as Imputation),
      } satisfies SaisieReception),
    onSuccess: (s) => {
      queryClient.setQueryData(cles.stockJournee(s.journeeId), s)
      queryClient.invalidateQueries({ queryKey: cles.journee(s.journeeId), exact: true })
      const r = s.receptions[s.receptions.length - 1]
      notifier(
        r?.complementLitres
          ? `Réception enregistrée. Manquant de ${formaterNombre(r.complementLitres)} L${r.compareeA15 ? ' à 15 °C' : ''} : un complément citerne sera réclamé au ${IMPUTATIONS[r.imputation]}.`
          : 'Réception enregistrée.',
        r?.complementLitres ? 'warning' : 'success',
      )
      onFermer()
    },
  })

  const bl = versNombre(f.quantiteBl)
  const avant = versNombre(f.jaugeAvant)
  const apres = versNombre(f.jaugeApres)
  const recue = versNombre(f.quantiteRecue) ?? (avant !== null && apres !== null ? apres - avant : bl)
  // Livraison jugée à 15 °C quand le BL porte la quantité à 15 °C et que température et densité sont relevées
  const temperature = versNombre(f.temperatureC)
  const densite = versNombre(f.densite)
  const vcf = temperature !== null && densite !== null ? facteur15(temperature, densite) : null
  const bl15 = versNombre(f.quantiteBl15)
  const recue15 = vcf !== null && recue !== null ? Math.round(recue * vcf * 100) / 100 : null
  const a15 = bl15 !== null && recue15 !== null
  const livre = a15 ? bl15 : bl
  const mesure = a15 ? recue15 : recue
  const manquant = livre !== null && mesure !== null ? Math.round((livre - mesure) * 100) / 100 : null
  const horsTolerance = manquant !== null && livre !== null && manquant > (livre * stock.toleranceTransportPct) / 100
  const imputation: Imputation = f.imputation
    ? (f.imputation as Imputation)
    : f.scellesIntacts === 'oui'
      ? 'FOURNISSEUR'
      : 'TRANSPORTEUR'

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Réception citerne</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, pt: 1 }}>
          <TextField select label="Cuve" value={f.cuveId} onChange={maj('cuveId')} required>
            {stock.cuves.map((c) => (
              <MenuItem key={c.cuveId} value={c.cuveId}>
                {c.cuveCode} · {c.produitLibelle}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="N° de BL" value={f.numeroBl} onChange={maj('numeroBl')} required slotProps={{ htmlInput: { maxLength: 30 } }} />
          <TextField label="Fournisseur / dépôt" value={f.fournisseur} onChange={maj('fournisseur')} required slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField label="Transporteur" value={f.transporteur} onChange={maj('transporteur')} slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField label="Chauffeur" value={f.chauffeur} onChange={maj('chauffeur')} slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField label="Immatriculation" value={f.immatriculation} onChange={maj('immatriculation')} slotProps={{ htmlInput: { maxLength: 20 } }} />
          <TextField label="Scellés" value={f.scelles} onChange={maj('scelles')} slotProps={{ htmlInput: { maxLength: 120 } }} />
          <TextField select label="Scellés à l'arrivée" value={f.scellesIntacts} onChange={maj('scellesIntacts')}>
            <MenuItem value="">Non vérifiés</MenuItem>
            <MenuItem value="oui">Intacts</MenuItem>
            <MenuItem value="non">Rompus ou absents</MenuItem>
          </TextField>
          <TextField label="Quantité BL (L)" value={f.quantiteBl} onChange={maj('quantiteBl')} required slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
          <TextField
            label="Quantité BL à 15 °C (L)"
            value={f.quantiteBl15}
            onChange={maj('quantiteBl15')}
            helperText="Si le BL l'indique : l'écart se juge alors à 15 °C"
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          />
          <TextField label="Jauge avant dépotage (L)" value={f.jaugeAvant} onChange={maj('jaugeAvant')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
          <TextField label="Jauge après dépotage (L)" value={f.jaugeApres} onChange={maj('jaugeApres')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
          <TextField
            label="Quantité reçue (L)"
            value={f.quantiteRecue}
            onChange={maj('quantiteRecue')}
            helperText="Vide : jauge après − jauge avant, sinon quantité du BL"
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          />
          <Stack direction="row" spacing={2}>
            <TextField
              label="Température (°C)"
              value={f.temperatureC}
              onChange={maj('temperatureC')}
              helperText="Du produit au dépotage"
              slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            />
            <TextField
              label="Densité à 15 °C"
              value={f.densite}
              onChange={maj('densite')}
              helperText="Portée sur le BL"
              error={densite !== null && vcf === null && temperature !== null}
              slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            />
          </Stack>
          <TextField select label="Manquant à la charge de" value={f.imputation} onChange={maj('imputation')}>
            <MenuItem value="">Automatique ({IMPUTATIONS[f.scellesIntacts === 'oui' ? 'FOURNISSEUR' : 'TRANSPORTEUR']})</MenuItem>
            <MenuItem value="TRANSPORTEUR">Transporteur</MenuItem>
            <MenuItem value="FOURNISSEUR">Fournisseur</MenuItem>
          </TextField>
        </Box>
        <TextField label="Observation" value={f.observation} onChange={maj('observation')} fullWidth multiline sx={{ mt: 2 }} slotProps={{ htmlInput: { maxLength: 500 } }} />
        {recue !== null && bl !== null && (
          <Alert severity={horsTolerance ? 'warning' : 'info'} sx={{ mt: 2 }} data-testid="apercu-reception">
            Entrée en stock : {litres(recue)}.{' '}
            {recue15 !== null && `À 15 °C : ${litres(recue15)} (facteur ${vcf!.toLocaleString('fr-FR', { minimumFractionDigits: 4 })}). `}
            {a15 ? 'Comparaison à 15 °C. ' : ''}
            {manquant! > 0
              ? `Manquant ${litres(manquant)}${horsTolerance ? ` au-delà de la tolérance transport (${formaterPct(stock.toleranceTransportPct)}) : complément citerne à la charge du ${IMPUTATIONS[imputation]}.` : ', dans la tolérance transport.'}`
              : manquant! < 0
                ? `Excédent ${litres(-manquant!)}.`
                : 'Conforme au BL.'}
          </Alert>
        )}
        <AlerteErreur erreur={ajout.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => ajout.mutate()}
          disabled={ajout.isPending || !f.cuveId || !f.numeroBl.trim() || !f.fournisseur.trim() || !bl || bl <= 0}
        >
          Enregistrer la réception
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function Receptions({ stock }: { stock: StockJournee }) {
  const queryClient = useQueryClient()
  const [dialogue, setDialogue] = useState(false)
  const suppression = useMutation({
    mutationFn: (id: string) => api.delete<StockJournee>(`/journees/${stock.journeeId}/receptions/${id}?version=${stock.version}`),
    onSuccess: (s) => {
      queryClient.setQueryData(cles.stockJournee(s.journeeId), s)
      queryClient.invalidateQueries({ queryKey: cles.journee(s.journeeId), exact: true })
      notifier('Réception retirée.')
    },
  })

  return (
    <Box sx={{ mt: 1 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" color="text.secondary">
          Réceptions du jour
        </Typography>
        {stock.modifiable && (
          <Button startIcon={<AddIcon />} onClick={() => setDialogue(true)}>
            Ajouter une réception
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={suppression.error} />
      {stock.receptions.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Aucune livraison ce jour.
        </Typography>
      ) : (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>BL</TableCell>
                <TableCell>Cuve</TableCell>
                <TableCell>Transport</TableCell>
                <TableCell align="right">BL</TableCell>
                <TableCell align="right">Reçu</TableCell>
                <TableCell align="right">Écart</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {stock.receptions.map((r) => (
                <TableRow key={r.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {r.numeroBl}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {r.fournisseur}
                    </Typography>
                  </TableCell>
                  <TableCell>{r.cuveCode}</TableCell>
                  <TableCell>
                    {[r.transporteur, r.immatriculation].filter(Boolean).join(' · ') || '—'}
                    {r.scelles && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        scellés {r.scelles}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {formaterNombre(r.quantiteBl)}
                    {r.quantiteBl15 !== null && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        à 15 °C {formaterNombre(r.quantiteBl15)}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {formaterNombre(r.quantiteRecue)}
                    {r.quantiteRecue15 !== null && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        à 15 °C {formaterNombre(r.quantiteRecue15)}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {formaterNombre(r.ecartLivraison)} ({formaterPct(r.ecartLivraisonPct)})
                    {r.compareeA15 && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        à 15 °C
                      </Typography>
                    )}
                    {r.complementLitres && (
                      <Chip
                        size="small"
                        sx={{ ml: 1 }}
                        color={r.complementStatut === 'REGLE' ? 'success' : 'warning'}
                        icon={<LocalShippingIcon />}
                        label={`Complément ${formaterNombre(r.complementLitres)} L · ${IMPUTATIONS[r.imputation]}${r.complementStatut === 'REGLE' ? ' · réglé' : ''}`}
                      />
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {stock.modifiable && (
                      <IconButton
                        size="small"
                        aria-label={`Retirer le BL ${r.numeroBl}`}
                        onClick={() => suppression.mutate(r.id)}
                        disabled={suppression.isPending}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      {dialogue && <DialogueReception stock={stock} onFermer={() => setDialogue(false)} />}
    </Box>
  )
}

/** Récapitulatif des écarts de stock affiché avant de confirmer la clôture. */
export function RecapStock({ stock }: { stock: StockJournee }) {
  if (stock.cuves.length === 0) return null
  return (
    <Table size="small" sx={{ mt: 2 }}>
      <TableHead>
        <TableRow>
          <TableCell>Cuve</TableCell>
          <TableCell align="right">Théorique</TableCell>
          <TableCell align="right">Jaugé</TableCell>
          <TableCell align="right">Écart</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {stock.cuves.map((c) => (
          <TableRow key={c.cuveId}>
            <TableCell>
              {c.cuveCode} · {c.produitLibelle}
            </TableCell>
            <TableCell align="right">{formaterNombre(c.stockTheorique)}</TableCell>
            <TableCell align="right">{formaterNombre(c.cloture?.volumeLitres)}</TableCell>
            <TableCell align="right" data-testid={`recap-ecart-${c.cuveCode}`}>
              {formaterNombre(c.ecartLitres)} L
              {c.niveau && c.niveau !== 'NORMAL' && (
                <Chip size="small" sx={{ ml: 1 }} color={NIVEAUX_ECART[c.niveau].couleur} label={formaterPct(c.ecartPctMois)} />
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export function SectionStock({ journeeId, ref }: { journeeId: string; ref?: Ref<PoigneeStock> }) {
  const queryClient = useQueryClient()
  const stock = useStockJournee(journeeId)
  const [saisies, setSaisies] = useState<Record<string, SaisieCuve>>({})
  const s = stock.data

  const modifiees = (s?.cuves ?? []).filter((c) => {
    const x = saisies[c.cuveId]
    if (!x) return false
    return (
      (x.eau !== undefined && versNombre(x.eau) !== versNombre(eauInitiale(c))) ||
      (x.cloture !== undefined && !memeJauge(x.cloture, jaugeInitiale(c, c.cloture))) ||
      (x.ouverture !== undefined && !memeJauge(x.ouverture, jaugeInitiale(c, c.ouverture))) ||
      (x.observation !== undefined && x.observation.trim() !== (c.cloture?.observation ?? ''))
    )
  })

  const enregistrement = useMutation({
    mutationFn: async (version: number) => {
      const jaugeages: SaisieJaugeage[] = []
      for (const c of modifiees) {
        const x = saisies[c.cuveId]!
        if (x.ouverture !== undefined && !c.departReporte) {
          jaugeages.push({
            cuveId: c.cuveId,
            moment: 'OUVERTURE',
            ...corpsJauge(x.ouverture),
            observation: null,
            hauteurEauMm: c.ouverture?.hauteurEauMm ?? null,
          })
        }
        if (x.cloture !== undefined || x.observation !== undefined || x.eau !== undefined) {
          const j = x.cloture ?? jaugeInitiale(c, c.cloture)
          const observation = (x.observation ?? c.cloture?.observation ?? '').trim()
          const eau = versNombre(x.eau ?? eauInitiale(c))
          jaugeages.push({
            cuveId: c.cuveId,
            moment: 'CLOTURE',
            ...corpsJauge(j),
            observation: observation || null,
            hauteurEauMm: eau === null ? null : Math.round(eau),
          })
        }
      }
      const r = await envoyerSaisie<StockJournee>({
        journeeId,
        journeeLibelle: `${s!.stationCode} · journée du ${formaterDate(s!.date)}`,
        type: 'jaugeages',
        methode: 'PUT',
        chemin: `/journees/${journeeId}/jaugeages`,
        corps: { version, jaugeages },
      })
      return r.envoye ? r.resultat : null
    },
    onSuccess: (r) => {
      if (!r) {
        notifier('Réseau indisponible : jaugeages gardés dans la tablette, envoyés au retour du réseau.', 'warning')
        return
      }
      setSaisies({})
      queryClient.setQueryData(cles.stockJournee(journeeId), r)
      queryClient.invalidateQueries({ queryKey: cles.journee(journeeId), exact: true })
    },
  })

  useImperativeHandle(
    ref,
    () => ({ modifie: modifiees.length > 0, enregistrer: (version: number) => enregistrement.mutateAsync(version) }),
    [modifiees.length, enregistrement],
  )

  if (!s) return <AlerteErreur erreur={stock.error} />

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1 }} aria-label="Stock et jaugeages">
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" color="text.secondary">
          Stock et jaugeages
        </Typography>
        {s.modifiable && (
          <Button
            startIcon={<SaveIcon />}
            onClick={() =>
              enregistrement
                .mutateAsync(s.version)
                .then((r) =>
                  r &&
                  notifier(
                    r.anomalies > 0 ? `Jaugeages enregistrés. ${r.anomalies} point(s) de stock à régler.` : 'Jaugeages enregistrés.',
                    r.anomalies > 0 ? 'warning' : 'success',
                  ),
                )
                .catch(() => undefined)
            }
            disabled={enregistrement.isPending || modifiees.length === 0}
          >
            Enregistrer les jaugeages
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={enregistrement.error} />
      {s.cuves.length === 0 && !s.modifiable && (
        <Typography variant="body2" color="text.secondary" sx={{ pb: 1 }}>
          Journée antérieure au suivi des stocks.
        </Typography>
      )}
      {s.cuves.map((c) => (
        <CarteCuve
          key={c.cuveId}
          cuve={c}
          saisie={saisies[c.cuveId] ?? {}}
          modifiable={s.modifiable}
          onChange={(x) => setSaisies((e) => ({ ...e, [c.cuveId]: x }))}
        />
      ))}
      <Receptions stock={s} />
    </Paper>
  )
}
