import SaveIcon from '@mui/icons-material/Save'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
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
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, useBudget, useStations, useSuiviBudget } from '../../api/requetes'
import type { BudgetStation, SuiviMoisBudget } from '../../api/types'
import { ecartBudgetPct, formaterEcartPct, MOIS_ANNEE, repartir } from '../../shared/cloture'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterNombre, formaterPrix, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { CadreTableau } from '../../shared/tableaux'

const chiffres = { whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' } as const
const RESEAU = 'RESEAU'
const texte = (v: number) => (v === 0 ? '' : String(v))
const somme = (valeurs: string[]) => valeurs.reduce((t, v) => t + (versNombre(v) ?? 0), 0)

// ---------------------------------------------------------------- Suivi : réel contre budget

/** Écart du réel au budget ; pour une dépense, dépasser le budget est défavorable. */
function Ecart({ reel, budget, depense }: { reel: number; budget: number; depense?: boolean }) {
  const pct = ecartBudgetPct(reel, budget)
  const favorable = pct === null ? null : depense ? pct <= 0 : pct >= 0
  return (
    <Typography variant="caption" component="div" sx={{ color: favorable === null ? 'text.secondary' : favorable ? 'success.main' : 'error.main', fontWeight: 600 }}>
      {formaterEcartPct(pct)}
    </Typography>
  )
}

function LigneSuivi({ libelle, m, fort }: { libelle: string; m: SuiviMoisBudget; fort?: boolean }) {
  const sx = { ...chiffres, fontWeight: fort ? 700 : undefined, color: !fort && !m.ecoule ? 'text.disabled' : undefined }
  // Un mois à venir n'a pas encore de réel : on ne le compare pas
  const compare = fort || m.ecoule
  return (
    <TableRow data-testid={fort ? 'suivi-cumul' : undefined}>
      <TableCell sx={{ fontWeight: fort ? 700 : undefined, whiteSpace: 'nowrap' }}>{libelle}</TableCell>
      <TableCell align="right" sx={sx}>
        {formaterNombre(m.litresBudget)}
      </TableCell>
      <TableCell align="right" sx={sx}>
        {formaterNombre(m.litresReel)}
        {compare && <Ecart reel={m.litresReel} budget={m.litresBudget} />}
      </TableCell>
      <TableCell align="right" sx={sx}>
        {formaterPrix(m.caBudget)}
      </TableCell>
      <TableCell align="right" sx={sx}>
        {formaterPrix(m.caReel)}
        {compare && <Ecart reel={m.caReel} budget={m.caBudget} />}
      </TableCell>
      <TableCell align="right" sx={sx}>
        {formaterPrix(m.depensesBudget)}
      </TableCell>
      <TableCell align="right" sx={sx}>
        {formaterPrix(m.depensesReel)}
        {compare && <Ecart reel={m.depensesReel} budget={m.depensesBudget} depense />}
      </TableCell>
    </TableRow>
  )
}

function Suivi({ stationId, annee }: { stationId: string | null; annee: number }) {
  const requete = useSuiviBudget(stationId, annee)
  const s = requete.data
  if (!s) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  const sansBudget = s.mois.every((m) => m.litresBudget === 0 && m.caBudget === 0 && m.depensesBudget === 0)
  return (
    <>
      {sansBudget && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Aucun budget saisi pour {annee}. Passez en saisie pour le renseigner, station par station.
        </Alert>
      )}
      <CadreTableau>
        <Table size="small" aria-label="Réel contre budget">
          <TableHead>
            <TableRow>
              <TableCell>Mois</TableCell>
              <TableCell align="right">Litres budget</TableCell>
              <TableCell align="right">Litres réel</TableCell>
              <TableCell align="right">CA budget</TableCell>
              <TableCell align="right">CA réel</TableCell>
              <TableCell align="right">Dépenses budget</TableCell>
              <TableCell align="right">Dépenses réel</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {s.mois.map((m, i) => (
              <LigneSuivi key={m.mois} libelle={MOIS_ANNEE[i]} m={m} />
            ))}
            <LigneSuivi libelle="Cumul des mois écoulés" m={s.cumul} fort />
          </TableBody>
        </Table>
      </CadreTableau>
      {s.produits.length > 0 && (
        <>
          <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>
            Par produit, sur les mois écoulés
          </Typography>
          <CadreTableau>
            <Table size="small" aria-label="Réel contre budget par produit">
              <TableHead>
                <TableRow>
                  <TableCell>Produit</TableCell>
                  <TableCell align="right">Litres budget</TableCell>
                  <TableCell align="right">Litres réel</TableCell>
                  <TableCell align="right">CA budget</TableCell>
                  <TableCell align="right">CA réel</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {s.produits.map((p) => (
                  <TableRow key={p.code}>
                    <TableCell>{p.libelle}</TableCell>
                    <TableCell align="right" sx={chiffres}>
                      {formaterNombre(p.litresBudget)}
                    </TableCell>
                    <TableCell align="right" sx={chiffres}>
                      {formaterNombre(p.litresReel)}
                      <Ecart reel={p.litresReel} budget={p.litresBudget} />
                    </TableCell>
                    <TableCell align="right" sx={chiffres}>
                      {formaterPrix(p.caBudget)}
                    </TableCell>
                    <TableCell align="right" sx={chiffres}>
                      {formaterPrix(p.caReel)}
                      <Ecart reel={p.caReel} budget={p.caBudget} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CadreTableau>
        </>
      )}
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
        Réel calculé sur les journées clôturées. Le cumul ne compte que les mois terminés, pour comparer des périodes complètes.
      </Typography>
    </>
  )
}

// ---------------------------------------------------------------- Saisie

interface ProduitSaisi {
  produitId: string
  libelle: string
  litres: string[]
  montants: string[]
}

/** Une colonne de douze mois, avec un total annuel à répartir. */
function Colonne({
  titre,
  unite,
  valeurs,
  decimales,
  onChange,
  etiquette,
}: {
  titre: string
  unite: string
  valeurs: string[]
  decimales: number
  onChange: (valeurs: string[]) => void
  etiquette: string
}) {
  const [total, setTotal] = useState('')
  const annuel = versNombre(total)
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="subtitle2">{titre}</Typography>
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1, fontVariantNumeric: 'tabular-nums' }}>
        Total : {formaterNombre(somme(valeurs))} {unite}
      </Typography>
      <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
        <TextField
          label="Annuel"
          value={total}
          onChange={(e) => setTotal(e.target.value)}
          slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `Total annuel ${etiquette}` } }}
        />
        <Button
          variant="outlined"
          size="small"
          disabled={annuel === null || annuel < 0}
          onClick={() => {
            onChange(repartir(annuel ?? 0, decimales).map(texte))
            setTotal('')
          }}
          sx={{ flexShrink: 0 }}
          aria-label={`Répartir ${etiquette} sur 12 mois`}
        >
          Répartir
        </Button>
      </Stack>
      <Stack spacing={1}>
        {MOIS_ANNEE.map((m, i) => (
          <TextField
            key={m}
            label={m}
            value={valeurs[i]}
            onChange={(e) => onChange(valeurs.map((v, k) => (k === i ? e.target.value : v)))}
            slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `${etiquette} ${m}` } }}
          />
        ))}
      </Stack>
    </Box>
  )
}

function Saisie({ budget }: { budget: BudgetStation }) {
  const queryClient = useQueryClient()
  const [produits, setProduits] = useState<ProduitSaisi[]>(() =>
    budget.produits.map((p) => ({
      produitId: p.produitId,
      libelle: p.libelle,
      litres: p.mois.map((m) => texte(m.litres)),
      montants: p.mois.map((m) => texte(m.montant)),
    })),
  )
  const [depenses, setDepenses] = useState<string[]>(() => budget.depenses.map(texte))
  const majProduit = (id: string, champ: 'litres' | 'montants', valeurs: string[]) =>
    setProduits(produits.map((p) => (p.produitId === id ? { ...p, [champ]: valeurs } : p)))
  const enregistrement = useMutation({
    mutationFn: () =>
      api.put<BudgetStation>('/cloture/budget', {
        stationId: budget.stationId,
        annee: budget.annee,
        produits: produits.map((p) => ({
          produitId: p.produitId,
          mois: p.litres.map((l, i) => ({ litres: versNombre(l) ?? 0, montant: versNombre(p.montants[i]) ?? 0 })),
        })),
        depenses: depenses.map((d) => versNombre(d) ?? 0),
      }),
    onSuccess: (b) => {
      queryClient.setQueryData(cles.budget(b.stationId, b.annee), b)
      notifier(`Budget ${b.annee} de ${b.stationNom} enregistré.`)
      return queryClient.invalidateQueries({ queryKey: cles.cloture })
    },
  })

  return (
    <>
      {produits.length === 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Cette station n'a aucun carburant en cuve : seul le budget de dépenses se saisit.
        </Alert>
      )}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 340px), 1fr))', alignItems: 'start' }}>
        {produits.map((p) => (
          <Paper key={p.produitId} sx={{ p: 2 }} aria-label={`Budget ${p.libelle}`}>
            <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, mb: 1.5 }}>
              {p.libelle}
            </Typography>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: '1fr 1fr' }}>
              <Colonne
                titre="Litres"
                unite="L"
                valeurs={p.litres}
                decimales={0}
                etiquette={`litres ${p.libelle}`}
                onChange={(v) => majProduit(p.produitId, 'litres', v)}
              />
              <Colonne
                titre="Chiffre d'affaires"
                unite="F"
                valeurs={p.montants}
                decimales={0}
                etiquette={`chiffre d'affaires ${p.libelle}`}
                onChange={(v) => majProduit(p.produitId, 'montants', v)}
              />
            </Box>
          </Paper>
        ))}
        <Paper sx={{ p: 2 }} aria-label="Budget de dépenses">
          <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, mb: 1.5 }}>
            Dépenses d'exploitation
          </Typography>
          <Colonne titre="Montant" unite="F" valeurs={depenses} decimales={0} etiquette="dépenses" onChange={setDepenses} />
        </Paper>
      </Box>
      <AlerteErreur erreur={enregistrement.error} />
      <Stack direction="row" sx={{ mt: 2, justifyContent: 'flex-end', position: 'sticky', bottom: 16 }}>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={() => enregistrement.mutate()} disabled={enregistrement.isPending}>
          Enregistrer le budget
        </Button>
      </Stack>
    </>
  )
}

function ChargementSaisie({ stationId, annee }: { stationId: string; annee: number }) {
  const requete = useBudget(stationId, annee)
  const b = requete.data
  if (!b) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  // Le formulaire repart du budget enregistré quand on change de station ou d'année
  return <Saisie key={`${b.stationId}-${b.annee}`} budget={b} />
}

export function OngletBudget({ mois }: { mois: string }) {
  const annee = Number(mois.slice(0, 4))
  const stations = useStations()
  const [vue, setVue] = useState<'suivi' | 'saisie'>('suivi')
  const [choix, setChoix] = useState(RESEAU)
  const actives = (stations.data ?? []).filter((s) => s.actif)
  // La saisie se fait station par station : sans choix, on prend la première
  const stationId = choix === RESEAU ? (vue === 'saisie' ? (actives[0]?.id ?? null) : null) : choix

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <ToggleButtonGroup exclusive size="small" value={vue} onChange={(_e, v: 'suivi' | 'saisie' | null) => v && setVue(v)} aria-label="Vue du budget">
          <ToggleButton value="suivi">Réel contre budget</ToggleButton>
          <ToggleButton value="saisie">Saisie du budget</ToggleButton>
        </ToggleButtonGroup>
        <TextField
          select
          label="Station"
          value={vue === 'saisie' ? (stationId ?? '') : choix}
          onChange={(e) => setChoix(e.target.value)}
          sx={{ width: { xs: '100%', sm: 260 } }}
        >
          {vue === 'suivi' && <MenuItem value={RESEAU}>Réseau</MenuItem>}
          {actives.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.nom}
            </MenuItem>
          ))}
        </TextField>
        <Typography variant="body2" color="text.secondary">
          Année {annee}
        </Typography>
      </Stack>
      <AlerteErreur erreur={stations.error} />
      {vue === 'suivi' ? <Suivi stationId={stationId} annee={annee} /> : stationId ? <ChargementSaisie stationId={stationId} annee={annee} /> : <Chargement />}
    </>
  )
}
