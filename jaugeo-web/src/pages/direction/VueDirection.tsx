import ArrowForward from '@mui/icons-material/ArrowForward'
import TrendingDown from '@mui/icons-material/TrendingDown'
import TrendingFlat from '@mui/icons-material/TrendingFlat'
import TrendingUp from '@mui/icons-material/TrendingUp'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import {
  useClients,
  useDepensesAValider,
  useGrillePrix,
  useSituationStock,
  useSituationTresorerie,
  useTableauReseau,
  useVueDirection,
} from '../../api/requetes'
import type { NiveauEcart, VueDirection as Vue } from '../../api/types'
import { useSession } from '../../auth/session'
import { NomStation, PastilleStation } from '../../shared/CarteStation'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDateHeureCourte, formaterNombre, formaterPrix } from '../../shared/libelles'
import { aujourdhui } from '../../shared/tresorerie'
import { couleurProduit } from './couleursProduits'
import { GraphiqueVentesJour } from './GraphiqueVentesJour'

const jourMois = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', timeZone: 'UTC' })
const jourMoisAnnee = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** « du 1er au 8 novembre 2026 » */
function periodeTexte(du: string, au: string, annee = true): string {
  const d = new Date(`${du}T00:00:00Z`)
  const a = new Date(`${au}T00:00:00Z`)
  const debut = d.getUTCDate() === 1 ? '1er' : String(d.getUTCDate())
  return `du ${debut} au ${(annee ? jourMoisAnnee : jourMois).format(a)}`
}

/** Variation en % (null si la base est nulle). */
function variation(valeur: number, base: number): number | null {
  return base > 0 ? ((valeur - base) / base) * 100 : null
}

function Variation({ v, libelle }: { v: number | null; libelle: string }) {
  if (v === null) {
    return (
      <Typography variant="caption" color="text.secondary">
        pas de comparaison {libelle}
      </Typography>
    )
  }
  const arrondi = Math.round(v * 10) / 10
  const [Icone, couleur] = arrondi > 0 ? [TrendingUp, 'success.main'] : arrondi < 0 ? [TrendingDown, 'error.main'] : [TrendingFlat, 'text.secondary']
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
      <Icone sx={{ fontSize: 16, color: couleur }} />
      <Typography variant="caption" sx={{ color: couleur, fontWeight: 600 }}>
        {arrondi > 0 ? '+' : ''}
        {arrondi.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {libelle}
      </Typography>
    </Stack>
  )
}

function Tuile({ libelle, valeur, children }: { libelle: string; valeur: string; children?: ReactNode }) {
  return (
    <Paper sx={{ p: 2, minWidth: 0 }}>
      <Typography variant="body2" color="text.secondary">
        {libelle}
      </Typography>
      <Typography variant="h5" component="p" sx={{ my: 0.5, fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}>
        {valeur}
      </Typography>
      {children}
    </Paper>
  )
}

function Bloc({ titre, action, children, label }: { titre: string; action?: ReactNode; children: ReactNode; label?: string }) {
  return (
    <Paper sx={{ p: 2, minWidth: 0 }} aria-label={label ?? titre}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 1.5, gap: 1 }}>
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, flexGrow: 1 }}>
          {titre}
        </Typography>
        {action}
      </Stack>
      {children}
    </Paper>
  )
}

/** Une ligne « libellé … montant » cliquable vers l'écran qui en donne le détail. */
function Ligne({ libelle, detail, valeur, vers, alerte }: { libelle: string; detail?: ReactNode; valeur: string; vers?: string; alerte?: boolean }) {
  const navigate = useNavigate()
  return (
    <Stack
      direction="row"
      onClick={vers ? () => navigate(vers) : undefined}
      sx={{
        alignItems: 'flex-start',
        gap: 1,
        py: 1,
        px: 1,
        mx: -1,
        borderRadius: 1,
        cursor: vers ? 'pointer' : 'default',
        '&:hover': vers ? { bgcolor: 'action.hover' } : undefined,
      }}
    >
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography variant="body2">{libelle}</Typography>
        {detail && (
          <Typography variant="caption" color="text.secondary" component="div">
            {detail}
          </Typography>
        )}
      </Box>
      <Typography
        variant="body2"
        sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', color: alerte ? 'error.main' : 'text.primary' }}
      >
        {valeur}
      </Typography>
    </Stack>
  )
}

const NIVEAUX: Record<NiveauEcart, { libelle: string; couleur: 'success' | 'warning' | 'error' }> = {
  NORMAL: { libelle: 'normal', couleur: 'success' },
  ALERTE: { libelle: 'en alerte', couleur: 'warning' },
  ENQUETE: { libelle: 'en enquête', couleur: 'error' },
}

function ArgentDu({ vue }: { vue: Vue }) {
  const clients = useClients()
  const tresorerie = useSituationTresorerie()
  const stock = useSituationStock()
  const liste = (clients.data ?? []).filter((c) => c.encours > 0).sort((a, b) => b.encours - a.encours)
  const creances = liste.reduce((t, c) => t + c.encours, 0)
  const depassements = liste.filter((c) => c.plafond !== null && c.encours > c.plafond)
  const manquants = tresorerie.data?.montantManquants ?? 0
  const complements = stock.data?.montantComplementsARegler ?? 0
  const total = vue.soldeCaisse + creances + manquants + complements
  const auDelaPlafond = (tresorerie.data?.stations ?? []).filter((s) => s.depassePlafond)

  return (
    <Bloc titre="Argent dû au réseau" action={<Typography sx={{ fontWeight: 700 }}>{formaterPrix(total)}</Typography>}>
      <Ligne
        libelle="Caisse non versée des gérants"
        detail={
          <>
            Ce mois : {formaterPrix(vue.versements)} versés sur {formaterPrix(vue.montantDu)} dus
            {auDelaPlafond.length > 0 && (
              <Box component="span" sx={{ display: 'block', color: 'warning.main' }} data-testid="direction-plafond">
                Espèces au-delà du plafond : {auDelaPlafond.map((s) => s.stationNom).join(', ')}
              </Box>
            )}
          </>
        }
        alerte={auDelaPlafond.length > 0}
        valeur={formaterPrix(vue.soldeCaisse)}
        vers="/tresorerie"
      />
      <Divider />
      <Ligne
        libelle={`Créances clients (${liste.length} client${liste.length > 1 ? 's' : ''})`}
        detail={
          depassements.length > 0 ? (
            <Box component="span" sx={{ color: 'error.main' }}>
              {depassements.length} au-delà de leur plafond : {depassements.map((c) => c.nom).join(', ')}
            </Box>
          ) : (
            'Aucun plafond dépassé'
          )
        }
        valeur={formaterPrix(creances)}
        vers="/clients"
      />
      {liste.length > 0 && (
        <Box sx={{ pl: 1.5, borderLeft: '2px solid #e3e7eb', mb: 1 }}>
          {liste.slice(0, 5).map((c) => (
            <Stack key={c.id} direction="row" sx={{ justifyContent: 'space-between', gap: 1, py: 0.25 }}>
              <Typography variant="caption" sx={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {c.nom}
              </Typography>
              <Typography variant="caption" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                {formaterPrix(c.encours)}
                {c.plafond !== null && ` / ${formaterPrix(c.plafond)}`}
              </Typography>
            </Stack>
          ))}
        </Box>
      )}
      <Divider />
      <Ligne
        libelle="Manquants des gérants"
        detail={`${tresorerie.data?.manquantsOuverts ?? 0} ouvert(s)`}
        valeur={formaterPrix(manquants)}
        vers="/tresorerie?onglet=manquants"
        alerte={manquants > 0}
      />
      <Divider />
      <Ligne
        libelle="Compléments citerne à recouvrer"
        detail={`${stock.data?.complementsARegler ?? 0} livraison(s) avec manquant dû par le transporteur ou le fournisseur`}
        valeur={formaterPrix(complements)}
        vers="/stocks?onglet=complements"
      />
    </Bloc>
  )
}

function Risques({ date }: { date: string }) {
  const reseau = useTableauReseau(date)
  const stock = useSituationStock()
  const navigate = useNavigate()
  const reappro = (stock.data?.cuves ?? []).filter((c) => c.aReapprovisionner)
  const eau = (stock.data?.cuves ?? []).filter((c) => c.eauAlerte)
  const t = reseau.data
  if (!t) return <Bloc titre="Risques">{reseau.error ? <AlerteErreur erreur={reseau.error} /> : <Chargement />}</Bloc>
  const aSurveiller = t.stations.filter((s) => s.niveau !== 'NORMAL').sort((a, b) => (a.niveau === 'ENQUETE' ? -1 : 1) - (b.niveau === 'ENQUETE' ? -1 : 1))
  return (
    <Bloc
      titre="Risques"
      action={
        <Button size="small" endIcon={<ArrowForward />} onClick={() => navigate('/reseau')}>
          Réseau
        </Button>
      }
    >
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1, mb: 1.5 }}>
        {(['ENQUETE', 'ALERTE', 'NORMAL'] as NiveauEcart[]).map((n) => (
          <Chip key={n} size="small" variant={n === 'NORMAL' ? 'outlined' : 'filled'} color={NIVEAUX[n].couleur} label={`${t.parNiveau[n]} ${NIVEAUX[n].libelle}`} />
        ))}
      </Stack>
      <Ligne
        libelle="Écarts de stock du mois (valeur)"
        detail="Jaugé moins théorique, au prix du jour"
        valeur={formaterPrix(t.ecartStockValeurMois)}
        vers="/stocks"
        alerte={t.ecartStockValeurMois < 0}
      />
      <Divider />
      <Ligne
        libelle="Cuves à réapprovisionner"
        detail={
          reappro.length > 0
            ? reappro.map((c) => `${c.stationNom} ${c.produitLibelle}${c.autonomieJours === null ? '' : ` (${formaterNombre(c.autonomieJours)} j)`}`).join(' · ')
            : 'Autonomie suffisante partout'
        }
        valeur={String(reappro.length)}
        vers="/stocks?onglet=autonomie"
        alerte={reappro.length > 0}
      />
      {eau.length > 0 && (
        <>
          <Divider />
          <Ligne
            libelle="Eau en fond de cuve"
            detail={eau.map((c) => `${c.stationNom} ${c.cuveCode} : ${c.hauteurEauMm} mm`).join(' · ')}
            valeur={String(eau.length)}
            vers="/stocks?onglet=autonomie"
            alerte
          />
        </>
      )}
      <Divider sx={{ mb: 1 }} />
      {aSurveiller.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Toutes les stations sont au niveau normal.
        </Typography>
      ) : (
        aSurveiller.map((s) => (
          <Stack key={s.stationId} direction="row" spacing={1.5} sx={{ py: 0.75, alignItems: 'center' }}>
            <PastilleStation teinte={NIVEAUX[s.niveau].couleur} taille={36} />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2">{s.nom}</Typography>
              <Typography variant="caption" color="text.secondary">
                {s.motifs.join(' · ')}
              </Typography>
            </Box>
          </Stack>
        ))
      )}
    </Bloc>
  )
}

function Decisions({ date }: { date: string }) {
  const reseau = useTableauReseau(date)
  const depenses = useDepensesAValider()
  const tresorerie = useSituationTresorerie()
  const grille = useGrillePrix()
  const aValider = (reseau.data?.stations ?? []).reduce((t, s) => t + s.journeesAValider, 0)
  const depensesDecidables = (depenses.data ?? []).filter((d) => d.decidable)
  const programmes = new Map<string, { libelle: string; debut: string; stations: number }>()
  for (const l of grille.data ?? []) {
    if (!l.prochainDebut) continue
    const cle = `${l.produitCode}|${l.prochainDebut}`
    const p = programmes.get(cle) ?? { libelle: l.produitLibelle, debut: l.prochainDebut, stations: 0 }
    p.stations++
    programmes.set(cle, p)
  }
  const prix = [...programmes.values()].sort((a, b) => a.debut.localeCompare(b.debut))

  return (
    <Bloc titre="Décisions en attente">
      <Ligne libelle="Journées clôturées à valider" valeur={String(aValider)} vers="/journees" alerte={aValider > 0} />
      <Divider />
      <Ligne
        libelle="Dépenses au-delà du plafond"
        detail={depensesDecidables.length > 0 ? formaterPrix(depensesDecidables.reduce((t, d) => t + d.montant, 0)) : undefined}
        valeur={String(depensesDecidables.length)}
        vers="/tresorerie?onglet=depenses"
        alerte={depensesDecidables.length > 0}
      />
      <Divider />
      <Ligne libelle="Versements à vérifier par la comptabilité" valeur={String(tresorerie.data?.versementsAVerifier ?? 0)} vers="/tresorerie?onglet=verification" />
      <Divider />
      <Ligne
        libelle="Changements de prix programmés"
        detail={
          prix.length > 0
            ? prix.map((p) => `${p.libelle} le ${formaterDateHeureCourte(p.debut)} (${p.stations} station${p.stations > 1 ? 's' : ''})`).join(' · ')
            : 'Aucun'
        }
        valeur={String(prix.length)}
        vers="/prix"
      />
    </Bloc>
  )
}

function Stations({ vue }: { vue: Vue }) {
  const navigate = useNavigate()
  const ecart = (a: number, b: number) => {
    const v = variation(a, b)
    if (v === null) return '—'
    const r = Math.round(v * 10) / 10
    return `${r > 0 ? '+' : ''}${r.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`
  }
  return (
    <Bloc titre="Stations" action={<Typography variant="caption" color="text.secondary">{periodeTexte(vue.periode.du, vue.periode.au, false)}</Typography>}>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label="Activité et caisse par station">
          <TableHead>
            <TableRow>
              <TableCell>Station</TableCell>
              <TableCell align="right">Ventes</TableCell>
              <TableCell align="right">Évolution</TableCell>
              <TableCell align="right">Litres</TableCell>
              <TableCell align="right">Versé / dû</TableCell>
              <TableCell align="right">Caisse non versée</TableCell>
              <TableCell align="right">Jours non clôturés</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {vue.stations.map((s) => {
              const v = variation(s.ventes, s.ventesPrecedent)
              return (
                <TableRow key={s.stationId} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/stations/${s.stationId}`)}>
                  <TableCell>
                    <NomStation nom={s.nom} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterPrix(s.ventes)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', color: v === null ? 'text.secondary' : v < 0 ? 'error.main' : 'success.main' }}>
                    {ecart(s.ventes, s.ventesPrecedent)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterNombre(s.litres)} L
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterPrix(s.versements)} / {formaterPrix(s.montantDu)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterPrix(s.soldeCaisse)}
                  </TableCell>
                  <TableCell align="right" sx={{ color: s.joursNonClotures > 1 ? 'error.main' : 'text.primary' }}>
                    {s.joursNonClotures}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </Box>
    </Bloc>
  )
}

/** Page d'accueil de la Direction : synthèse du réseau, tendances, argent dû, risques et décisions. */
export function VueDirection() {
  const utilisateur = useSession((e) => e.utilisateur)!
  const navigate = useNavigate()
  const date = aujourdhui()
  const requete = useVueDirection(date)
  const clients = useClients()
  const tresorerie = useSituationTresorerie()
  const stock = useSituationStock()
  const prenom = utilisateur.nomComplet.split(' ')[0]

  if (requete.isLoading) return <Chargement />
  if (requete.error || !requete.data) return <AlerteErreur erreur={requete.error} />
  const vue = requete.data
  const comparaison = `vs ${periodeTexte(vue.precedente.du, vue.precedente.au, false).replace(/^du /, '')}`
  const creances = (clients.data ?? []).reduce((t, c) => t + Math.max(0, c.encours), 0)
  const argentDu = vue.soldeCaisse + creances + (tresorerie.data?.montantManquants ?? 0) + (stock.data?.montantComplementsARegler ?? 0)
  const tauxVersement = vue.montantDu > 0 ? Math.round((vue.versements / vue.montantDu) * 100) : null

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-end' }, gap: 1, mb: 3 }}>
        <Box>
          <Typography variant="h5" component="h1">
            Bonjour {prenom}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {utilisateur.roleLibelle} · {utilisateur.perimetre === 'RESEAU' ? 'tout le réseau' : `${utilisateur.stationIds.length} station(s)`} · activité{' '}
            {periodeTexte(vue.periode.du, vue.periode.au)}, journées clôturées
          </Typography>
        </Box>
        <Button variant="outlined" size="small" endIcon={<ArrowForward />} onClick={() => navigate('/reseau')}>
          Détail par station
        </Button>
      </Stack>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' }, mb: 3 }}>
        <Tuile libelle="Ventes" valeur={formaterPrix(vue.periode.ventes)}>
          <Variation v={variation(vue.periode.ventes, vue.precedente.ventes)} libelle={comparaison} />
        </Tuile>
        <Tuile libelle="Litres vendus" valeur={`${formaterNombre(vue.periode.litres)} L`}>
          <Variation v={variation(vue.periode.litres, vue.precedente.litres)} libelle={comparaison} />
        </Tuile>
        <Tuile libelle="Versé en banque" valeur={formaterPrix(vue.versements)}>
          <Typography variant="caption" color="text.secondary">
            {tauxVersement === null ? 'Aucun montant dû ce mois' : `${tauxVersement} % des ${formaterPrix(vue.montantDu)} dus par les stations`}
          </Typography>
        </Tuile>
        <Tuile libelle="Argent dû au réseau" valeur={formaterPrix(argentDu)}>
          <Typography variant="caption" color="text.secondary">
            Caisse des gérants, créances, manquants, compléments
          </Typography>
        </Tuile>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Bloc titre="Ventes par jour" label="Ventes par jour et par produit">
          {vue.periode.ventes === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Aucune journée clôturée {periodeTexte(vue.periode.du, vue.periode.au)}.
            </Typography>
          ) : (
            <GraphiqueVentesJour jours={vue.jours} produits={vue.produits} />
          )}
          <Box sx={{ overflowX: 'auto', mt: 2 }}>
            <Table size="small" aria-label="Ventes par produit" sx={{ '& td, & th': { px: { xs: 0.75, sm: 2 } } }}>
              <TableHead>
                <TableRow>
                  <TableCell>Produit</TableCell>
                  <TableCell align="right">Litres</TableCell>
                  <TableCell align="right">Ventes</TableCell>
                  <TableCell align="right">Évolution</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {vue.produits.map((p) => (
                  <TableRow key={p.code}>
                    <TableCell sx={{ whiteSpace: { xs: 'normal', sm: 'nowrap' } }}>
                      <Box component="span" sx={{ display: 'inline-block', width: 10, height: 10, borderRadius: '3px', mr: 1, bgcolor: couleurProduit(p.code, vue.produits) }} />
                      {p.libelle}
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {formaterNombre(p.litres)} L
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {formaterPrix(p.ventes)}
                    </TableCell>
                    <TableCell align="right">
                      <Box sx={{ display: 'inline-flex' }}>
                        <Variation v={variation(p.ventes, p.ventesPrecedent)} libelle="" />
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Mois précédent complet : {formaterPrix(vue.ventesMoisPrecedent)}. Marge non calculée : le coût d'achat arrive au lot 2.
          </Typography>
        </Bloc>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, mb: 3, alignItems: 'start' }}>
        <ArgentDu vue={vue} />
        <Stack spacing={2}>
          <Decisions date={date} />
          <Risques date={date} />
        </Stack>
      </Box>

      <Stations vue={vue} />
    </>
  )
}
