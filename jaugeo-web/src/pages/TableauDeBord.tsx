import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined'
import PropaneTankOutlinedIcon from '@mui/icons-material/PropaneTankOutlined'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined'
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined'
import WaterDropOutlinedIcon from '@mui/icons-material/WaterDropOutlined'
import LocalGasStationOutlinedIcon from '@mui/icons-material/LocalGasStationOutlined'
import OpacityOutlinedIcon from '@mui/icons-material/OpacityOutlined'
import PublicOutlinedIcon from '@mui/icons-material/PublicOutlined'
import SellOutlinedIcon from '@mui/icons-material/SellOutlined'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import LinearProgress from '@mui/material/LinearProgress'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { alpha } from '@mui/material/styles'
import { lazy, Suspense, type ReactElement, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useGrillePrix, useProduits, useSituationStock, useSituationTresorerie, useStations } from '../api/requetes'
import { usePermission, useSession } from '../auth/session'
import { CarteIndicateur } from '../shared/CarteIndicateur'
import { CarteStation, NomStation } from '../shared/CarteStation'
import { AlerteErreur, Chargement } from '../shared/composants'
import { formaterDate, formaterDateHeureCourte, formaterNombre, formaterPrix } from '../shared/libelles'
import { formaterPct, NIVEAUX_ECART } from '../shared/stock'
import { PuceEtat } from '../shared/tableaux'

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
  const produits = [...parProduit.entries()]
  return (
    <CarteIndicateur icone={<SellOutlinedIcon />} libelle="Prix en vigueur" ton="info" onClick={() => navigate('/prix')}>
      {/* Un bloc par carburant ; ils passent à la ligne d'eux-mêmes quand la carte est étroite */}
      <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))' }}>
        {produits.map(([code, p]) => {
          const min = Math.min(...p.prix)
          const max = Math.max(...p.prix)
          return (
            <Box
              key={code}
              sx={(t) => ({ px: 1.25, py: 0.75, borderRadius: '10px', bgcolor: alpha(t.palette.info.main, 0.07), minWidth: 0 })}
            >
              <Typography variant="caption" color="text.secondary" component="div" noWrap>
                {p.libelle}
              </Typography>
              <Typography sx={{ fontSize: { xs: 17, md: 19 }, fontWeight: 700, lineHeight: 1.2, fontVariantNumeric: 'tabular-nums' }}>
                {p.prix.length === 0 ? '—' : min === max ? formaterPrix(min) : `${formaterPrix(min)} – ${formaterPrix(max)}`}
              </Typography>
              {p.manquants > 0 && (
                <Chip size="small" color="warning" variant="outlined" label={`${p.manquants} manquant(s)`} sx={{ mt: 0.5, height: 20 }} />
              )}
              {p.prochain && (
                <Typography variant="caption" color="info.main" component="div">
                  Changement le {formaterDateHeureCourte(p.prochain)}
                </Typography>
              )}
            </Box>
          )
        })}
      </Box>
    </CarteIndicateur>
  )
}

type Ton = 'primary' | 'success' | 'warning' | 'error' | 'info'

/** Masque une colonne secondaire sur téléphone. */
const SAUF_TELEPHONE = { display: { xs: 'none', sm: 'table-cell' } }

/**
 * Bloc de synthèse de la page d'accueil : en-tête (pictogramme, titre, chiffre clé, lien « Tout voir »),
 * bandeau d'indicateurs cliquables, puis tableau. Les lignes se survolent et ouvrent le détail.
 */
function SectionAccueil({
  titre,
  icone,
  ton,
  chiffre,
  libelleChiffre,
  versPage,
  indicateurs,
  children,
  note,
  libelle,
}: {
  titre: string
  icone: ReactNode
  ton: Ton
  chiffre: ReactNode
  libelleChiffre: string
  versPage: () => void
  indicateurs: ReactNode
  children: ReactNode
  note?: ReactNode
  libelle: string
}) {
  return (
    <Paper
      variant="outlined"
      aria-label={libelle}
      sx={(t) => ({
        mb: 3,
        borderRadius: '14px',
        overflow: 'hidden',
        backgroundImage: `radial-gradient(circle at 100% 0%, ${alpha(t.palette[ton].main, 0.07)} 0, transparent 40%)`,
      })}
    >
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        sx={{ px: { xs: 2, md: 2.5 }, pt: { xs: 2, md: 2.5 }, pb: 1.5, gap: { xs: 1.5, md: 3 }, alignItems: { md: 'center' } }}
      >
        <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5, minWidth: 0, flexGrow: 1 }}>
          <Box
            aria-hidden
            sx={(t) => ({
              width: 44,
              height: 44,
              flexShrink: 0,
              borderRadius: '12px',
              display: 'grid',
              placeItems: 'center',
              color: t.palette[ton].main,
              bgcolor: alpha(t.palette[ton].main, 0.12),
              '& svg': { fontSize: 24 },
            })}
          >
            {icone}
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h6" component="h2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
              {titre}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {libelleChiffre}{' '}
              <Box component="span" sx={{ fontWeight: 700, color: 'text.primary', fontVariantNumeric: 'tabular-nums' }}>
                {chiffre}
              </Box>
            </Typography>
          </Box>
          <Button
            size="small"
            endIcon={<ArrowForwardIcon />}
            onClick={versPage}
            sx={{ ml: 'auto', flexShrink: 0, display: { xs: 'inline-flex', md: 'none' } }}
          >
            Tout voir
          </Button>
        </Stack>
        <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap', alignItems: 'center', justifyContent: { md: 'flex-end' } }}>
          {indicateurs}
          <Button size="small" variant="outlined" endIcon={<ArrowForwardIcon />} onClick={versPage} sx={{ display: { xs: 'none', md: 'inline-flex' } }}>
            Tout voir
          </Button>
        </Stack>
      </Stack>
      <Box
        sx={(t) => ({
          overflowX: 'auto',
          '& .MuiTableCell-head': {
            bgcolor: alpha(t.palette[ton].main, 0.05),
            color: 'text.secondary',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: '0.05em',
            textTransform: 'uppercase',
            whiteSpace: 'nowrap',
            py: 1,
          },
          '& .MuiTableCell-root:first-of-type': { pl: { xs: 2, md: 2.5 } },
          '& .MuiTableCell-root:last-of-type': { pr: { xs: 2, md: 2.5 } },
          '& .MuiTableBody-root .MuiTableCell-root': { py: 1, fontVariantNumeric: 'tabular-nums' },
          '& .MuiTableBody-root .MuiTableRow-root': { cursor: 'pointer', transition: 'background-color .12s ease' },
          '& .MuiTableBody-root .MuiTableRow-root:hover': { bgcolor: alpha(t.palette[ton].main, 0.08) },
          '& .MuiTableBody-root .MuiTableRow-root:last-of-type .MuiTableCell-root': { borderBottom: 0 },
        })}
      >
        {children}
      </Box>
      {note && (
        <Typography
          variant="caption"
          color="text.secondary"
          component="div"
          sx={{ px: { xs: 2, md: 2.5 }, py: 1.25, borderTop: 1, borderColor: 'divider' }}
        >
          {note}
        </Typography>
      )}
    </Paper>
  )
}

/** Indicateur du bandeau d'une section : pastille teintée, cliquable quand elle mène à une liste. */
function Indicateur({
  ton,
  icone,
  libelle,
  onClick,
  testid,
}: {
  ton: Ton
  icone: ReactElement
  libelle: string
  onClick?: () => void
  testid?: string
}) {
  return (
    <Chip
      icon={icone}
      label={libelle}
      onClick={onClick}
      data-testid={testid}
      sx={(t) => ({
        fontWeight: 600,
        borderRadius: '8px',
        color: t.palette[ton].dark,
        bgcolor: alpha(t.palette[ton].main, 0.12),
        '& .MuiChip-icon': { color: t.palette[ton].main, fontSize: 18 },
        ...(onClick && { '&:hover': { bgcolor: alpha(t.palette[ton].main, 0.2) } }),
      })}
    />
  )
}

/**
 * Stock d'une cuve avec une jauge de remplissage à logique inversée : plus la cuve est vide, plus la
 * couleur est alarmante. La couleur ne dépend que du taux de remplissage (rouge sous 20 %, orange sous
 * 40 %) ; l'alerte de réapprovisionnement, fondée sur l'autonomie, reste portée par la colonne Autonomie.
 */
function NiveauCuve({ stock, capacite }: { stock: number; capacite: number | null }) {
  if (!capacite || capacite <= 0) return <>{formaterNombre(stock)} L</>
  const pct = Math.max(0, Math.min(100, (stock / capacite) * 100))
  const couleur = pct < 20 ? 'error' : pct < 40 ? 'warning' : 'success'
  return (
    <Box>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {formaterNombre(stock)} L
      </Typography>
      <LinearProgress
        variant="determinate"
        value={pct}
        color={couleur}
        aria-label={`Niveau de la cuve : ${Math.round(pct)} %`}
        sx={{ height: 14, borderRadius: 7, my: 0.5, '& .MuiLinearProgress-bar': { borderRadius: 7 } }}
      />
      <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
        {Math.round(pct)} % de {formaterNombre(capacite)} L
      </Typography>
    </Box>
  )
}

/** Solde non versé de chaque station, retards de versement, versements à vérifier et manquants ouverts. */
function SituationTresorerie() {
  const situation = useSituationTresorerie()
  const navigate = useNavigate()
  const s = situation.data
  if (!s || s.stations.length === 0) return null
  const total = s.stations.reduce((t, x) => t + x.soldeNonVerse, 0)
  // Les stations qui demandent une action d'abord : retard, plafond dépassé, puis le plus gros solde
  const stations = [...s.stations].sort(
    (a, b) =>
      Number(b.retard) - Number(a.retard) ||
      Number(b.depassePlafond) - Number(a.depassePlafond) ||
      Math.abs(b.soldeNonVerse) - Math.abs(a.soldeNonVerse) ||
      a.stationNom.localeCompare(b.stationNom),
  )
  return (
    <SectionAccueil
      libelle="Trésorerie"
      titre="Trésorerie"
      icone={<AccountBalanceWalletOutlinedIcon />}
      ton="primary"
      libelleChiffre="Solde non versé du réseau"
      chiffre={formaterPrix(total)}
      versPage={() => navigate('/tresorerie')}
      note={`Retard : solde non versé supérieur aux recettes des ${s.delaiVersementJours} derniers jours.`}
      indicateurs={
        <>
          <Indicateur
            ton={s.retards > 0 ? 'error' : 'success'}
            icone={s.retards > 0 ? <ScheduleOutlinedIcon /> : <CheckCircleOutlineIcon />}
            libelle={s.retards > 0 ? `${s.retards} station(s) en retard de versement` : 'Versements à jour'}
            testid="retards-versement"
          />
          {s.versementsAVerifier > 0 && (
            <Indicateur
              ton="warning"
              icone={<FactCheckOutlinedIcon />}
              libelle={`${s.versementsAVerifier} versement(s) à vérifier`}
              onClick={() => navigate('/tresorerie?onglet=verification')}
            />
          )}
          {s.depassementsPlafond > 0 && (
            <Indicateur
              ton="warning"
              icone={<SavingsOutlinedIcon />}
              libelle={`${s.depassementsPlafond} station(s) au-delà du plafond d’espèces`}
              testid="depassements-plafond"
            />
          )}
          {s.manquantsOuverts > 0 && (
            <Indicateur
              ton="error"
              icone={<ReportProblemOutlinedIcon />}
              libelle={`${s.manquantsOuverts} manquant(s) · ${formaterPrix(s.montantManquants)}`}
              onClick={() => navigate('/tresorerie?onglet=manquants')}
            />
          )}
        </>
      }
    >
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Station</TableCell>
            <TableCell align="right">Solde non versé</TableCell>
            <TableCell>Dernier versement</TableCell>
            <TableCell sx={SAUF_TELEPHONE}>Dernier arrêté</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {stations.map((x) => {
            const nul = x.soldeNonVerse === 0
            return (
              <TableRow key={x.stationId} onClick={() => navigate(`/tresorerie?station=${x.stationId}`)}>
                <TableCell>
                  <NomStation nom={x.stationNom} />
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Typography
                    component="span"
                    variant="body2"
                    sx={{ fontWeight: nul ? 400 : 700, color: nul ? 'text.disabled' : x.retard ? 'error.main' : 'text.primary' }}
                  >
                    {formaterPrix(x.soldeNonVerse)}
                  </Typography>
                  {(x.retard || x.depassePlafond) && (
                    <Stack direction="row" sx={{ gap: 0.5, justifyContent: 'flex-end', mt: 0.25 }}>
                      {x.retard && <PuceEtat ton="error" libelle="Retard" />}
                      {x.depassePlafond && <PuceEtat ton="warning" libelle="Plafond" />}
                    </Stack>
                  )}
                </TableCell>
                <TableCell>
                  {x.dernierVersement ? (
                    <>
                      {formaterDate(x.dernierVersement)}
                      {x.joursSansVersement !== null && x.joursSansVersement > 0 && (
                        <Typography variant="caption" color="text.secondary" component="div">
                          il y a {x.joursSansVersement} jour(s)
                        </Typography>
                      )}
                    </>
                  ) : (
                    <Typography variant="body2" color="text.disabled">
                      Aucun versement
                    </Typography>
                  )}
                </TableCell>
                <TableCell sx={SAUF_TELEPHONE}>
                  {x.dernierArrete ? (
                    <>
                      {formaterDate(x.dernierArrete)}
                      {x.dernierEcart !== null && x.dernierEcart !== 0 && (
                        <Typography variant="caption" color={x.dernierEcart < 0 ? 'error' : 'text.secondary'} component="div">
                          écart {formaterPrix(x.dernierEcart)}
                        </Typography>
                      )}
                    </>
                  ) : (
                    <Typography variant="body2" color="text.disabled">
                      Aucun arrêté
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </SectionAccueil>
  )
}

/** Dernier stock jaugé de chaque cuve et alertes d'écart (cumul du mois au-delà de la tolérance). */
function SituationStocks() {
  const situation = useSituationStock()
  const navigate = useNavigate()
  const s = situation.data
  if (!s || (s.cuves.length === 0 && s.complementsARegler === 0)) return null
  const alertes = s.cuves.filter((c) => c.niveau !== 'NORMAL')
  // Les cuves à réapprovisionner d'abord, puis par autonomie croissante
  const cuves = [...(alertes.length > 0 ? alertes : s.cuves)].sort(
    (a, b) => Number(b.aReapprovisionner) - Number(a.aReapprovisionner) || (a.autonomieJours ?? 1e9) - (b.autonomieJours ?? 1e9),
  )
  const stockTotal = s.cuves.reduce((t, c) => t + c.stockPhysique, 0)
  return (
    <SectionAccueil
      libelle="Stocks et écarts"
      titre="Stocks et écarts"
      icone={<PropaneTankOutlinedIcon />}
      ton="success"
      libelleChiffre={`${s.cuves.length} cuve(s) jaugée(s) ·`}
      chiffre={`${formaterNombre(stockTotal)} L en stock`}
      versPage={() => navigate('/stocks')}
      note={alertes.length > 0 ? `Seules les ${alertes.length} cuve(s) dont l'écart du mois sort de la tolérance sont listées.` : undefined}
      indicateurs={
        <>
          <Indicateur
            ton={s.alertes > 0 ? 'error' : 'success'}
            icone={s.alertes > 0 ? <ReportProblemOutlinedIcon /> : <CheckCircleOutlineIcon />}
            libelle={s.alertes > 0 ? `${s.alertes} écart(s) hors tolérance` : 'Écarts dans la tolérance'}
            testid="alertes-stock"
          />
          {s.cuvesAReapprovisionner > 0 && (
            <Indicateur
              ton="error"
              icone={<LocalShippingOutlinedIcon />}
              libelle={`${s.cuvesAReapprovisionner} cuve(s) à réapprovisionner`}
              onClick={() => navigate('/stocks?onglet=autonomie')}
              testid="reappro-stock"
            />
          )}
          {s.cuvesEau > 0 && (
            <Indicateur
              ton="warning"
              icone={<WaterDropOutlinedIcon />}
              libelle={`${s.cuvesEau} cuve(s) avec eau`}
              onClick={() => navigate('/stocks?onglet=autonomie')}
            />
          )}
          {s.complementsARegler > 0 && (
            <Indicateur
              ton="warning"
              icone={<ReceiptLongOutlinedIcon />}
              libelle={`${s.complementsARegler} complément(s) citerne à régler · ${formaterPrix(s.montantComplementsARegler)}`}
              onClick={() => navigate('/stocks?onglet=complements')}
            />
          )}
        </>
      }
    >
      {cuves.length > 0 && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Cuve</TableCell>
              <TableCell sx={SAUF_TELEPHONE}>Dernier jaugeage</TableCell>
              <TableCell align="right">Stock</TableCell>
              <TableCell align="right">Autonomie</TableCell>
              <TableCell align="right" sx={SAUF_TELEPHONE}>
                Écart du mois
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {cuves.map((c) => (
              <TableRow key={c.cuveId} onClick={() => navigate(`/stocks?station=${c.stationId}&mois=${c.date.slice(0, 7)}`)}>
                <TableCell>
                  <NomStation nom={c.stationNom} detail={`${c.cuveCode} · ${c.produitLibelle}`} />
                </TableCell>
                <TableCell sx={SAUF_TELEPHONE}>{formaterDate(c.date)}</TableCell>
                <TableCell align="right" sx={{ minWidth: { xs: 120, sm: 160 } }}>
                  <NiveauCuve stock={c.stockPhysique} capacite={c.capaciteLitres} />
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {c.autonomieJours === null ? (
                    '—'
                  ) : (
                    <Typography component="span" variant="body2" sx={{ fontWeight: 700, color: c.aReapprovisionner ? 'error.main' : 'text.primary' }}>
                      {formaterNombre(c.autonomieJours)} j
                    </Typography>
                  )}
                  {c.aReapprovisionner && (
                    <Box sx={{ mt: 0.25 }}>
                      <PuceEtat ton="error" libelle="Réappro" />
                    </Box>
                  )}
                </TableCell>
                <TableCell align="right" sx={SAUF_TELEPHONE}>
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
      )}
    </SectionAccueil>
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
      {/*
        Chiffres clés et prix sur une seule ligne sur grand écran (le bloc des prix prend deux parts) ;
        tablette : les trois chiffres sur une ligne, les prix dessous ; téléphone : deux chiffres par ligne.
      */}
      <Box
        sx={{
          display: 'grid',
          gap: { xs: 1.5, md: 2 },
          gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(3, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr)) minmax(0, 2fr)' },
          mb: 3,
        }}
      >
        <CarteIndicateur
          icone={<LocalGasStationOutlinedIcon />}
          libelle="Stations actives"
          valeur={actives.length}
          sous={`${liste.length - actives.length} fermée(s)`}
          onClick={() => navigate('/stations')}
        />
        <CarteIndicateur
          icone={<PublicOutlinedIcon />}
          libelle="Régions couvertes"
          valeur={regions.size}
          sous="sur 14 régions du Sénégal"
          ton="success"
        />
        <Box sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' } }}>
          <CarteIndicateur
            icone={<OpacityOutlinedIcon />}
            libelle="Carburants commercialisés"
            valeur={carburants.length}
            sous={carburants.map((p) => p.libelle).join(' · ')}
            ton="warning"
          />
        </Box>
        <Box sx={{ gridColumn: { xs: '1 / -1', lg: 'auto' } }}>
          <PrixEnVigueur />
        </Box>
      </Box>
      <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>
        Vos stations
      </Typography>
      <Box sx={{ display: 'grid', gap: { xs: 1.5, sm: 2 }, gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 280px), 1fr))' }}>
        {liste.map((s) => (
          <CarteStation key={s.id} station={s} onOuvrir={() => navigate(`/stations/${s.id}`)} />
        ))}
      </Box>
    </>
  )
}

// Vue de la Direction chargée à la demande (graphique) : les autres rôles ne la téléchargent jamais
const VueDirection = lazy(async () => ({ default: (await import('./direction/VueDirection')).VueDirection }))

export function TableauDeBord() {
  const utilisateur = useSession((e) => e.utilisateur)!
  const voitReferentiel = usePermission('REFERENTIEL_CONSULTER')
  const voitReseau = usePermission('TABLEAU_BORD_RESEAU')
  const prenom = utilisateur.nomComplet.split(' ')[0]

  if (utilisateur.role === 'DIRECTION' && voitReseau && voitReferentiel) {
    return (
      <Suspense fallback={<Chargement />}>
        <VueDirection />
      </Suspense>
    )
  }

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
