import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useNavigate } from 'react-router'
import { useGrillePrix, useProduits, useSituationStock, useSituationTresorerie, useStations } from '../api/requetes'
import { usePermission, useSession } from '../auth/session'
import { AlerteErreur, Chargement } from '../shared/composants'
import { formaterDate, formaterDateHeureCourte, formaterNombre, formaterPrix, TYPES_STATION } from '../shared/libelles'
import { formaterPct, NIVEAUX_ECART } from '../shared/stock'

function Indicateur({ libelle, valeur, detail }: { libelle: string; valeur: string | number; detail?: string }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="body2" color="text.secondary">
        {libelle}
      </Typography>
      <Typography variant="h4" component="p" sx={{ my: 0.5 }}>
        {valeur}
      </Typography>
      {detail && (
        <Typography variant="caption" color="text.secondary">
          {detail}
        </Typography>
      )}
    </Paper>
  )
}

/** Prix en vigueur par produit (fourchette sur le périmètre) et prochains changements. */
function PrixEnVigueur() {
  const grille = useGrillePrix()
  const navigate = useNavigate()
  if (!grille.data || grille.data.length === 0) return null
  const parProduit = new Map<string, { libelle: string; prix: number[]; manquants: number; prochain: string | null }>()
  for (const l of grille.data) {
    const p = parProduit.get(l.produitCode) ?? { libelle: l.produitLibelle, prix: [], manquants: 0, prochain: null }
    if (l.prix === null) p.manquants++
    else p.prix.push(l.prix)
    if (l.prochainDebut && (!p.prochain || l.prochainDebut < p.prochain)) p.prochain = l.prochainDebut
    parProduit.set(l.produitCode, p)
  }
  return (
    <Paper sx={{ p: 2, mb: 3, cursor: 'pointer' }} onClick={() => navigate('/prix')} aria-label="Prix en vigueur">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Prix en vigueur
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
        {[...parProduit.entries()].map(([code, p]) => {
          const min = Math.min(...p.prix)
          const max = Math.max(...p.prix)
          return (
            <Box key={code}>
              <Typography variant="subtitle2">{p.libelle}</Typography>
              <Typography variant="h6" component="p">
                {p.prix.length === 0 ? '—' : min === max ? formaterPrix(min) : `${formaterPrix(min)} – ${formaterPrix(max)}`}
              </Typography>
              {p.manquants > 0 && <Chip size="small" color="warning" variant="outlined" label={`${p.manquants} manquant(s)`} />}
              {p.prochain && (
                <Typography variant="caption" color="info.main" sx={{ display: 'block' }}>
                  Changement le {formaterDateHeureCourte(p.prochain)}
                </Typography>
              )}
            </Box>
          )
        })}
      </Box>
    </Paper>
  )
}

/** Solde non versé de chaque station, retards de versement, versements à vérifier et manquants ouverts. */
function SituationTresorerie() {
  const situation = useSituationTresorerie()
  const navigate = useNavigate()
  const s = situation.data
  if (!s || s.stations.length === 0) return null
  const total = s.stations.reduce((t, x) => t + x.soldeNonVerse, 0)
  return (
    <Paper sx={{ p: 2, mb: 3 }} aria-label="Trésorerie">
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          Trésorerie · solde non versé {formaterPrix(total)}
        </Typography>
        <Chip
          size="small"
          color={s.retards > 0 ? 'error' : 'success'}
          label={s.retards > 0 ? `${s.retards} station(s) en retard de versement` : 'Versements à jour'}
          data-testid="retards-versement"
        />
        {s.versementsAVerifier > 0 && (
          <Chip
            size="small"
            color="warning"
            label={`${s.versementsAVerifier} versement(s) à vérifier`}
            onClick={() => navigate('/tresorerie?onglet=verification')}
          />
        )}
        {s.manquantsOuverts > 0 && (
          <Chip
            size="small"
            color="error"
            variant="outlined"
            label={`${s.manquantsOuverts} manquant(s) · ${formaterPrix(s.montantManquants)}`}
            onClick={() => navigate('/tresorerie?onglet=manquants')}
          />
        )}
      </Stack>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Station</TableCell>
              <TableCell align="right">Solde non versé</TableCell>
              <TableCell>Dernier versement</TableCell>
              <TableCell>Dernier arrêté</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {s.stations.map((x) => (
              <TableRow key={x.stationId} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/tresorerie?station=${x.stationId}`)}>
                <TableCell>
                  {x.stationCode} · {x.stationNom}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {formaterPrix(x.soldeNonVerse)}
                  {x.retard && <Chip size="small" color="error" sx={{ ml: 1 }} label="Retard" />}
                </TableCell>
                <TableCell>
                  {x.dernierVersement ? formaterDate(x.dernierVersement) : '—'}
                  {x.joursSansVersement !== null && x.joursSansVersement > 0 && (
                    <Typography variant="caption" color="text.secondary" component="div">
                      il y a {x.joursSansVersement} jour(s)
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  {x.dernierArrete ? formaterDate(x.dernierArrete) : '—'}
                  {x.dernierEcart !== null && x.dernierEcart !== 0 && (
                    <Typography variant="caption" color={x.dernierEcart < 0 ? 'error' : 'text.secondary'} component="div">
                      écart {formaterPrix(x.dernierEcart)}
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <Typography variant="caption" color="text.secondary">
        Retard : solde non versé supérieur aux recettes des {s.delaiVersementJours} derniers jours.
      </Typography>
    </Paper>
  )
}

/** Dernier stock jaugé de chaque cuve et alertes d'écart (cumul du mois au-delà de la tolérance). */
function SituationStocks() {
  const situation = useSituationStock()
  const navigate = useNavigate()
  const s = situation.data
  if (!s || (s.cuves.length === 0 && s.complementsARegler === 0)) return null
  const alertes = s.cuves.filter((c) => c.niveau !== 'NORMAL')
  return (
    <Paper sx={{ p: 2, mb: 3 }} aria-label="Stocks et écarts">
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          Stocks et écarts
        </Typography>
        <Chip
          size="small"
          color={s.alertes > 0 ? 'error' : 'success'}
          label={s.alertes > 0 ? `${s.alertes} écart(s) hors tolérance` : 'Écarts dans la tolérance'}
          data-testid="alertes-stock"
        />
        {s.complementsARegler > 0 && (
          <Chip
            size="small"
            color="warning"
            label={`${s.complementsARegler} complément(s) citerne à régler · ${formaterPrix(s.montantComplementsARegler)}`}
            onClick={() => navigate('/stocks?onglet=complements')}
          />
        )}
      </Stack>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Cuve</TableCell>
              <TableCell>Dernier jaugeage</TableCell>
              <TableCell align="right">Stock</TableCell>
              <TableCell align="right">Écart du mois</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(alertes.length > 0 ? alertes : s.cuves).map((c) => (
              <TableRow
                key={c.cuveId}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navigate(`/stocks?station=${c.stationId}&mois=${c.date.slice(0, 7)}`)}
              >
                <TableCell>
                  {c.stationCode} · {c.cuveCode} · {c.produitLibelle}
                </TableCell>
                <TableCell>{formaterDate(c.date)}</TableCell>
                <TableCell align="right">{formaterNombre(c.stockPhysique)} L</TableCell>
                <TableCell align="right">
                  <Chip
                    size="small"
                    variant="outlined"
                    color={NIVEAUX_ECART[c.niveau].couleur}
                    label={`${formaterNombre(c.cumulEcartMois)} L · ${formaterPct(c.ecartPctMois)}`}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
    </Paper>
  )
}

function SyntheseReferentiel() {
  const stations = useStations()
  const produits = useProduits()
  const navigate = useNavigate()
  if (stations.isLoading || produits.isLoading) return <Chargement />
  if (stations.error || produits.error) return <AlerteErreur erreur={stations.error ?? produits.error} />

  const liste = stations.data ?? []
  const actives = liste.filter((s) => s.actif)
  const regions = new Set(actives.map((s) => s.regionCode))
  const carburants = (produits.data ?? []).filter((p) => p.actif && p.famille === 'CARBURANT')

  return (
    <>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, mb: 3 }}>
        <Indicateur libelle="Stations actives" valeur={actives.length} detail={`${liste.length - actives.length} fermée(s)`} />
        <Indicateur libelle="Régions couvertes" valeur={regions.size} />
        <Indicateur libelle="Carburants commercialisés" valeur={carburants.length} detail={carburants.map((p) => p.libelle).join(' · ')} />
      </Box>
      <PrixEnVigueur />
      <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>
        Vos stations
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' } }}>
        {liste.map((s) => (
          <Paper
            key={s.id}
            onClick={() => navigate(`/stations/${s.id}`)}
            sx={{ p: 2, cursor: 'pointer', '&:hover': { borderColor: 'primary.main' }, opacity: s.actif ? 1 : 0.6 }}
          >
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                {s.nom}
              </Typography>
              <Chip size="small" label={s.code} />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {TYPES_STATION[s.type]} · {s.regionNom}
              {s.localite ? ` · ${s.localite}` : ''}
            </Typography>
          </Paper>
        ))}
      </Box>
    </>
  )
}

export function TableauDeBord() {
  const utilisateur = useSession((e) => e.utilisateur)!
  const voitReferentiel = usePermission('REFERENTIEL_CONSULTER')
  const prenom = utilisateur.nomComplet.split(' ')[0]

  return (
    <>
      <Typography variant="h5" component="h1">
        Bonjour {prenom}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {utilisateur.roleLibelle} · {utilisateur.perimetre === 'RESEAU' ? 'tout le réseau' : `${utilisateur.stationIds.length} station(s)`}
      </Typography>
      {voitReferentiel && <SituationTresorerie />}
      {voitReferentiel && <SituationStocks />}
      {voitReferentiel && <SyntheseReferentiel />}
    </>
  )
}
