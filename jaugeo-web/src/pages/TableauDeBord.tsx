import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useNavigate } from 'react-router'
import { useGrillePrix, useProduits, useStations } from '../api/requetes'
import { usePermission, useSession } from '../auth/session'
import { AlerteErreur, Chargement } from '../shared/composants'
import { formaterDateHeureCourte, formaterPrix, TYPES_STATION } from '../shared/libelles'

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
      <Alert severity="info" sx={{ mb: 3 }}>
        Les indicateurs de ventes, de stock et de trésorerie s'afficheront ici dès la mise en service des modules
        d'exploitation quotidienne.
      </Alert>
      {voitReferentiel && <SyntheseReferentiel />}
    </>
  )
}
