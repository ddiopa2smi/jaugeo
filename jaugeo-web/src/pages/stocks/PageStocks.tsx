import Alert from '@mui/material/Alert'
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined'
import OpacityOutlinedIcon from '@mui/icons-material/OpacityOutlined'
import PropaneTankOutlinedIcon from '@mui/icons-material/PropaneTankOutlined'
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
import { useComplements, useSituationStock, useStations, useStockMensuel } from '../../api/requetes'
import type { ComplementCiterne, LigneStock, SituationCuve } from '../../api/types'
import { CarteIndicateur } from '../../shared/CarteIndicateur'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { formaterPct, IMPUTATIONS, moisCourant, NIVEAUX_ECART } from '../../shared/stock'
import { BarreFiltres, CadreTableau, ChampRecherche, EnTeteTri, FiltrePuces, PuceEtat } from '../../shared/tableaux'
import { correspond, useLigneChoisie, useTri } from '../../shared/tri'

type ColonneStock = 'date' | 'cuve' | 'depart' | 'receptions' | 'sorties' | 'theorique' | 'jauge' | 'ecart' | 'cumul' | 'observation'

/** Observation longue coupée avec « … » (texte complet au survol) : une ligne par cuve et par jour. */
const OBSERVATION_COUPEE = {
  '& .MuiTableBody-root .MuiTableCell-root:last-of-type': { maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' },
}

function AnalyseMensuelle() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const mois = params.get('mois') ?? moisCourant()
  const analyse = useStockMensuel(stationId, mois)
  const [recherche, setRecherche] = useState('')
  const [cuve, setCuve] = useState('TOUTES')
  const [filtre, setFiltre] = useState<'TOUTES' | 'HORS_TOLERANCE' | 'RECEPTIONS' | 'ECART_NEGATIF'>('TOUTES')
  // Tri par défaut : les journées les plus récentes d'abord
  const tri = useTri<LigneStock, ColonneStock>(
    {
      date: (l) => l.date,
      cuve: (l) => l.cuveCode,
      depart: (l) => l.stockDepart,
      receptions: (l) => l.receptions || null,
      sorties: (l) => l.sorties,
      theorique: (l) => l.stockTheorique,
      jauge: (l) => l.stockPhysique,
      ecart: (l) => l.ecartLitres,
      cumul: (l) => l.ecartPctMois,
      observation: (l) => l.observation,
    },
    'date',
    'desc',
  )
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    if (stationId && !p.get('station')) p.set('station', stationId)
    setParams(p)
  }
  // Ligne cliquée, gardée dans l'adresse : elle reste marquée au retour de la journée ouverte
  const [ligneChoisie, marquer] = useLigneChoisie()
  const choisir = (cle: string, journeeId: string | null) => {
    marquer(cle)
    if (journeeId) navigate(`/journees/${journeeId}`)
  }
  const toutes = analyse.data?.lignes ?? []
  const codesCuves = [...new Set(toutes.map((l) => l.cuveCode))].sort((a, b) => a.localeCompare(b, 'fr', { numeric: true }))
  const lignes = tri.trier(
    toutes.filter(
      (l) =>
        (cuve === 'TOUTES' || l.cuveCode === cuve) &&
        (filtre === 'TOUTES' ||
          (filtre === 'HORS_TOLERANCE' && l.niveau !== 'NORMAL') ||
          (filtre === 'ECART_NEGATIF' && l.ecartLitres < 0) ||
          (filtre === 'RECEPTIONS' && l.receptions > 0)) &&
        correspond(recherche, formaterDate(l.date), l.date, l.cuveCode, l.observation),
    ),
  )

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
          <BarreFiltres resume={`${lignes.length} ligne(s) sur ${toutes.length}`}>
            <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher une date, une observation" />
            {codesCuves.length > 1 && (
              <FiltrePuces
                libelle="Filtrer par cuve"
                valeur={cuve}
                onChange={setCuve}
                options={[
                  { valeur: 'TOUTES', libelle: 'Toutes les cuves' },
                  ...codesCuves.map((c) => ({ valeur: c, libelle: c, nombre: toutes.filter((l) => l.cuveCode === c).length })),
                ]}
              />
            )}
            <FiltrePuces
              libelle="Filtrer les journées"
              valeur={filtre}
              onChange={setFiltre}
              options={[
                { valeur: 'TOUTES', libelle: 'Toutes', nombre: toutes.length },
                { valeur: 'HORS_TOLERANCE', libelle: 'Hors tolérance', nombre: toutes.filter((l) => l.niveau !== 'NORMAL').length },
                { valeur: 'ECART_NEGATIF', libelle: 'Écart négatif', nombre: toutes.filter((l) => l.ecartLitres < 0).length },
                { valeur: 'RECEPTIONS', libelle: 'Avec réception', nombre: toutes.filter((l) => l.receptions > 0).length },
              ]}
            />
          </BarreFiltres>
          <CadreTableau compact sx={OBSERVATION_COUPEE}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <EnTeteTri tri={tri} cle="date">Date</EnTeteTri>
                  <EnTeteTri tri={tri} cle="cuve">Cuve</EnTeteTri>
                  <EnTeteTri tri={tri} cle="depart" align="right">Départ</EnTeteTri>
                  <EnTeteTri tri={tri} cle="receptions" align="right">Réceptions</EnTeteTri>
                  <EnTeteTri tri={tri} cle="sorties" align="right">Sorties</EnTeteTri>
                  <EnTeteTri tri={tri} cle="theorique" align="right">Théorique</EnTeteTri>
                  <EnTeteTri tri={tri} cle="jauge" align="right">Jauge</EnTeteTri>
                  <EnTeteTri tri={tri} cle="ecart" align="right">Écart</EnTeteTri>
                  <EnTeteTri tri={tri} cle="cumul" align="right">Cumul mois</EnTeteTri>
                  <EnTeteTri tri={tri} cle="observation">Observation</EnTeteTri>
                </TableRow>
              </TableHead>
              <TableBody>
                {lignes.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={10} align="center" sx={{ color: 'text.secondary' }}>
                      Aucune ligne ne correspond aux filtres.
                    </TableCell>
                  </TableRow>
                )}
                {lignes.map((l) => (
                  <TableRow
                    key={`${l.date}-${l.cuveId}`}
                    hover
                    selected={ligneChoisie === `${l.date}-${l.cuveId}`}
                    sx={{ cursor: 'pointer' }}
                    onClick={() => choisir(`${l.date}-${l.cuveId}`, l.journeeId)}
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
                    <TableCell title={l.observation ?? undefined}>{l.observation}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CadreTableau>
        </>
      )}
    </>
  )
}

const CACHE_TELEPHONE = { xs: 'none', sm: 'table-cell' }

/** Autonomie de chaque cuve : dernier stock jaugé / ventes moyennes des 7 derniers jours clôturés. */
function Autonomie() {
  const situation = useSituationStock()
  const navigate = useNavigate()
  const s = situation.data
  const [recherche, setRecherche] = useState('')
  const [filtre, setFiltre] = useState<'TOUTES' | 'REAPPRO' | 'EAU'>('TOUTES')
  // Tri initial : les cuves à réapprovisionner d'abord, puis par autonomie croissante
  const tri = useTri<SituationCuve, 'priorite' | 'cuve' | 'stock' | 'ventes' | 'autonomie' | 'eau'>(
    {
      priorite: (c) => (c.aReapprovisionner ? 0 : 1e9) + (c.autonomieJours ?? 1e8),
      cuve: (c) => `${c.stationCode} ${c.cuveCode}`,
      stock: (c) => c.stockPhysique,
      ventes: (c) => c.ventesMoyennes,
      autonomie: (c) => c.autonomieJours,
      eau: (c) => c.hauteurEauMm,
    },
    'priorite',
  )
  if (situation.isLoading) return <Chargement />
  if (!s) return <AlerteErreur erreur={situation.error} />
  if (s.cuves.length === 0) return <Alert severity="info">Aucune cuve jaugée à une clôture.</Alert>
  const parCode = [...s.cuves].sort((a, b) => a.stationCode.localeCompare(b.stationCode))
  const aReappro = s.cuves.filter((c) => c.aReapprovisionner).length
  const avecEau = s.cuves.filter((c) => c.eauAlerte).length
  const cuves = tri.trier(
    parCode.filter(
      (c) =>
        (filtre === 'TOUTES' || (filtre === 'REAPPRO' ? c.aReapprovisionner : c.eauAlerte)) &&
        correspond(recherche, c.stationCode, c.stationNom, c.cuveCode, c.produitLibelle),
    ),
  )
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Autonomie = stock au-dessus du creux de sécurité ÷ ventes moyennes des 7 derniers jours clôturés. Une cuve est à
        réapprovisionner sous son seuil en litres, ou, sans seuil, sous {formaterNombre(s.autonomieAlerteJours)} jours
        d'autonomie.
      </Typography>
      <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)' }, mb: 2.5 }}>
        <CarteIndicateur icone={<PropaneTankOutlinedIcon />} libelle="Cuves suivies" valeur={s.cuves.length} sous="jaugées à une clôture" />
        <CarteIndicateur
          icone={<LocalShippingOutlinedIcon />}
          libelle="À réapprovisionner"
          valeur={aReappro}
          ton={aReappro > 0 ? 'error' : 'success'}
          accent={aReappro > 0}
          sous={aReappro > 0 ? 'sous le seuil ou l’autonomie d’alerte' : 'aucune cuve sous le seuil'}
        />
        <CarteIndicateur
          icone={<OpacityOutlinedIcon />}
          libelle="Eau en fond de cuve"
          valeur={avecEau}
          ton={avecEau > 0 ? 'warning' : 'success'}
          sous={avecEau > 0 ? 'au-dessus du seuil toléré' : 'aucune alerte'}
        />
      </Box>
      <BarreFiltres resume={`${cuves.length} cuve(s) sur ${s.cuves.length}`}>
        <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher une station, un produit" />
        {(aReappro > 0 || avecEau > 0) && (
          <FiltrePuces
            libelle="Filtrer les cuves"
            valeur={filtre}
            onChange={setFiltre}
            options={[
              { valeur: 'TOUTES', libelle: 'Toutes', nombre: s.cuves.length },
              { valeur: 'REAPPRO', libelle: 'À réapprovisionner', nombre: aReappro },
              { valeur: 'EAU', libelle: 'Avec eau', nombre: avecEau },
            ]}
          />
        )}
      </BarreFiltres>
      {cuves.length === 0 && <Alert severity="info">Aucune cuve ne correspond à ces filtres.</Alert>}
      <CadreTableau sx={{ display: cuves.length === 0 ? 'none' : undefined }}>
        <Table size="small" aria-label="Autonomie des cuves" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
          <TableHead>
            <TableRow>
              <EnTeteTri tri={tri} cle="cuve">
                Cuve
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="stock" align="right">
                Stock
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="ventes" align="right" sx={{ display: CACHE_TELEPHONE }}>
                Ventes / jour
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="autonomie" align="right">
                Autonomie
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="eau" align="right" sx={{ display: CACHE_TELEPHONE }}>
                Eau
              </EnTeteTri>
            </TableRow>
          </TableHead>
          <TableBody>
            {cuves.map((c) => (
              <TableRow
                key={c.cuveId}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navigate(`/stocks?station=${c.stationId}&mois=${c.date.slice(0, 7)}`)}
                data-testid={`autonomie-${c.stationCode}-${c.cuveCode}`}
              >
                <TableCell>
                  {c.stationCode} · {c.cuveCode} · {c.produitLibelle}
                  <Typography variant="caption" color="text.secondary" component="div">
                    jaugé le {formaterDate(c.date)}
                  </Typography>
                  <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5, mt: 0.5 }}>
                    {c.aReapprovisionner && <PuceEtat ton="error" libelle="À réapprovisionner" />}
                    {c.eauAlerte && <PuceEtat ton="warning" libelle={`Eau ${c.hauteurEauMm} mm > ${c.seuilEauMm} mm`} />}
                  </Stack>
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {formaterNombre(c.stockPhysique)} L
                  {c.seuilReapproLitres !== null && (
                    <Typography variant="caption" color="text.secondary" component="div">
                      seuil {formaterNombre(c.seuilReapproLitres)} L
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right" sx={{ display: CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                  {c.ventesMoyennes === null ? '—' : `${formaterNombre(Math.round(c.ventesMoyennes))} L`}
                  {c.joursMoyenne > 0 && c.joursMoyenne < 7 && (
                    <Typography variant="caption" color="text.secondary" component="div">
                      sur {c.joursMoyenne} j
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, whiteSpace: 'nowrap', color: c.aReapprovisionner ? 'error.main' : undefined }}>
                  {c.autonomieJours === null ? '—' : `${formaterNombre(c.autonomieJours)} j`}
                </TableCell>
                <TableCell align="right" sx={{ display: CACHE_TELEPHONE, whiteSpace: 'nowrap', color: c.eauAlerte ? 'warning.dark' : undefined }}>
                  {c.hauteurEauMm === null ? '—' : `${c.hauteurEauMm} mm`}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CadreTableau>
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
          BL {complement.numeroBl} ·{' '}
          {complement.imputeA === 'FOURNISSEUR' ? complement.fournisseur : (complement.transporteur ?? 'transporteur')} :{' '}
          {formaterNombre(complement.litres)} L{complement.a15 ? ' à 15 °C' : ''}, soit{' '}
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
  const [recherche, setRecherche] = useState('')
  const tri = useTri<ComplementCiterne, 'date' | 'station' | 'bl' | 'transporteur' | 'charge' | 'manquant' | 'montant' | 'statut'>(
    {
      date: (c) => c.date,
      station: (c) => c.stationCode,
      bl: (c) => c.numeroBl,
      transporteur: (c) => c.transporteur,
      charge: (c) => IMPUTATIONS[c.imputeA],
      manquant: (c) => c.litres,
      montant: (c) => c.montant,
      statut: (c) => c.statut,
    },
    'date',
    'desc',
  )
  const complements = liste.data ?? []
  const visibles = tri.trier(
    complements.filter((c) => correspond(recherche, c.stationCode, c.numeroBl, c.fournisseur, c.transporteur, c.immatriculation, c.referenceReglement)),
  )

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
      {complements.length > 0 && (
        <BarreFiltres resume={`${visibles.length} complément(s) · ${formaterPrix(visibles.reduce((t, c) => t + (c.montant ?? 0), 0))}`}>
          <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un BL, un transporteur" />
        </BarreFiltres>
      )}
      {complements.length > 0 && visibles.length === 0 && <Alert severity="info">Aucun complément ne correspond à la recherche.</Alert>}
      {visibles.length > 0 && (
        <CadreTableau>
          <Table size="small" aria-label="Compléments citerne">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="date">
                  Date
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="station">
                  Station
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="bl">
                  BL
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="transporteur">
                  Transporteur
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="charge">
                  À la charge du
                </EnTeteTri>
                <TableCell align="right">BL / reçu</TableCell>
                <EnTeteTri tri={tri} cle="manquant" align="right">
                  Manquant
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="montant" align="right">
                  Montant
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="statut">
                  Statut
                </EnTeteTri>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((c) => (
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
                  <TableCell>{IMPUTATIONS[c.imputeA]}</TableCell>
                  <TableCell align="right">
                    {formaterNombre(c.quantiteBl)} / {formaterNombre(c.quantiteRecue)}
                  </TableCell>
                  <TableCell align="right">
                    {formaterNombre(c.litres)} L
                    {c.a15 && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        à 15 °C
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">{formaterPrix(c.montant)}</TableCell>
                  <TableCell>
                    {c.statut === 'REGLE' ? (
                      <PuceEtat ton="success" libelle={`Réglé le ${formaterDate(c.regleLe)} · ${c.referenceReglement}`} />
                    ) : (
                      <PuceEtat ton="warning" libelle="À régler" />
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
        </CadreTableau>
      )}
      {aRegler && <DialogueReglement complement={aRegler} onFermer={() => setARegler(null)} />}
    </>
  )
}

export function PageStocks() {
  const [params, setParams] = useSearchParams()
  const o = params.get('onglet')
  const onglet = o === 'complements' || o === 'autonomie' ? o : 'analyse'
  return (
    <>
      <EnTetePage titre="Stocks" sousTitre="Stock théorique contre jauges, écarts du mois par cuve, autonomie, compléments citerne." />
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
        <Tab value="autonomie" label="Autonomie" />
        <Tab value="complements" label="Compléments citerne" />
      </Tabs>
      {onglet === 'analyse' ? <AnalyseMensuelle /> : onglet === 'autonomie' ? <Autonomie /> : <Complements />}
    </>
  )
}
