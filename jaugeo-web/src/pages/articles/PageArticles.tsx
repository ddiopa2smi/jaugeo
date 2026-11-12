import AddIcon from '@mui/icons-material/Add'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import MoveToInboxOutlinedIcon from '@mui/icons-material/MoveToInboxOutlined'
import ShoppingBasketOutlinedIcon from '@mui/icons-material/ShoppingBasketOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
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
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useMouvementsArticles, useStations, useStockArticles } from '../../api/requetes'
import type { ArticleStation, EntreeArticle, MouvementArticle, StockArticles, TypeMouvementArticle } from '../../api/types'
import { CarteIndicateur } from '../../shared/CarteIndicateur'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { moisCourant } from '../../shared/stock'
import { BarreFiltres, CadreTableau, ChampRecherche, EnTeteTri, PuceEtat } from '../../shared/tableaux'
import { correspond, useTri } from '../../shared/tri'

/** Colonnes secondaires masquées sur téléphone. */
const CACHE_TELEPHONE = { display: { xs: 'none', md: 'table-cell' } } as const
const CACHE_TELEPHONE_SM = { display: { xs: 'none', sm: 'table-cell' } } as const

const TYPES_MOUVEMENT: Record<TypeMouvementArticle, { libelle: string; couleur: 'success' | 'info' | 'warning' }> = {
  ENTREE: { libelle: 'Entrée', couleur: 'success' },
  VENTE: { libelle: 'Ventes', couleur: 'info' },
  INVENTAIRE: { libelle: 'Inventaire', couleur: 'warning' },
}

function useRafraichirArticles() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: cles.articles })
}

function DialogueEntree({ stock, onFermer }: { stock: StockArticles; onFermer: () => void }) {
  const rafraichir = useRafraichirArticles()
  const [produitId, setProduitId] = useState('')
  const [quantite, setQuantite] = useState('')
  const [cout, setCout] = useState('')
  const [fournisseur, setFournisseur] = useState('')
  const [reference, setReference] = useState('')
  const q = versNombre(quantite)
  const c = versNombre(cout)
  const annonce = useMutation({
    mutationFn: () =>
      api.post<EntreeArticle>(`/stations/${stock.stationId}/articles/entrees`, {
        produitId,
        quantite: q,
        coutUnitaire: c,
        fournisseur: fournisseur.trim(),
        reference: reference.trim(),
      }),
    onSuccess: () => {
      rafraichir()
      notifier('Entrée annoncée : le gérant la confirme à réception.')
      onFermer()
    },
  })
  const valide = produitId && q !== null && q > 0 && c !== null && c >= 0 && fournisseur.trim() && reference.trim()
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Annoncer une entrée · {stock.stationCode}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField select label="Article" value={produitId} onChange={(e) => setProduitId(e.target.value)} required>
            {stock.articles.map((a) => (
              <MenuItem key={a.produitId} value={a.produitId}>
                {a.libelle}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Quantité" value={quantite} onChange={(e) => setQuantite(e.target.value)} required fullWidth slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
            <TextField
              label="Coût d'achat unitaire (F)"
              value={cout}
              onChange={(e) => setCout(e.target.value)}
              required
              fullWidth
              slotProps={{ htmlInput: { inputMode: 'decimal' } }}
              helperText={q !== null && c !== null ? `Valeur : ${formaterPrix(Math.round(q * c))}` : ' '}
            />
          </Stack>
          <TextField label="Fournisseur" value={fournisseur} onChange={(e) => setFournisseur(e.target.value)} required slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField
            label="Référence (BL, facture ou « stock initial »)"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 50 } }}
          />
        </Stack>
        <AlerteErreur erreur={annonce.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => annonce.mutate()} disabled={annonce.isPending || !valide}>
          Annoncer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueReception({ entree, onFermer }: { entree: EntreeArticle; onFermer: () => void }) {
  const rafraichir = useRafraichirArticles()
  const [quantite, setQuantite] = useState(String(entree.quantite))
  const [observation, setObservation] = useState('')
  const q = versNombre(quantite)
  const ecart = q !== null && q !== entree.quantite
  const reception = useMutation({
    mutationFn: () =>
      api.post<EntreeArticle>(`/articles/entrees/${entree.id}/reception`, {
        version: entree.version,
        quantiteRecue: q,
        observation: videVersNull(observation),
      }),
    onSuccess: () => {
      rafraichir()
      notifier('Réception confirmée : le stock est à jour.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Confirmer la réception</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {entree.produitLibelle} · {entree.fournisseur} · réf. {entree.reference} : {formaterNombre(entree.quantite)} annoncé(s).
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Quantité reçue"
            value={quantite}
            onChange={(e) => setQuantite(e.target.value)}
            required
            autoFocus
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          />
          <TextField
            label="Observation"
            value={observation}
            onChange={(e) => setObservation(e.target.value)}
            required={ecart}
            error={ecart && !observation.trim()}
            helperText={ecart ? 'Quantité différente de l’annonce : indiquez la raison.' : ' '}
            multiline
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={reception.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => reception.mutate()}
          disabled={reception.isPending || q === null || q <= 0 || (ecart && !observation.trim())}
        >
          Confirmer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueAnnulation({ entree, onFermer }: { entree: EntreeArticle; onFermer: () => void }) {
  const rafraichir = useRafraichirArticles()
  const [motif, setMotif] = useState('')
  const annulation = useMutation({
    mutationFn: () => api.post<EntreeArticle>(`/articles/entrees/${entree.id}/annulation`, { version: entree.version, motif: motif.trim() }),
    onSuccess: () => {
      rafraichir()
      notifier('Entrée annulée.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Annuler l'entrée {entree.reference} ?</DialogTitle>
      <DialogContent>
        <TextField label="Motif" value={motif} onChange={(e) => setMotif(e.target.value)} required fullWidth autoFocus sx={{ mt: 1 }} />
        <AlerteErreur erreur={annulation.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Retour</Button>
        <Button color="error" variant="contained" onClick={() => annulation.mutate()} disabled={annulation.isPending || !motif.trim()}>
          Annuler l'entrée
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function OngletStock({ stock }: { stock: StockArticles }) {
  const [annonce, setAnnonce] = useState(false)
  const [aRecevoir, setARecevoir] = useState<EntreeArticle | null>(null)
  const [aAnnuler, setAAnnuler] = useState<EntreeArticle | null>(null)
  const [recherche, setRecherche] = useState('')
  const tri = useTri<ArticleStation, 'article' | 'stock' | 'cmup' | 'valeur' | 'prix' | 'comptage'>(
    {
      article: (a) => a.libelle,
      stock: (a) => a.quantite,
      cmup: (a) => a.cmup,
      valeur: (a) => a.valeur,
      prix: (a) => a.prixVente,
      comptage: (a) => a.dernierComptage,
    },
    'article',
  )
  const visibles = tri.trier(stock.articles.filter((a) => correspond(recherche, a.libelle, a.code)))
  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <Paper variant="outlined" sx={{ px: 2, py: 1, flexGrow: 1 }}>
          <Typography variant="caption" color="text.secondary">
            Valeur du stock au coût moyen
          </Typography>
          <Typography variant="h6" sx={{ fontVariantNumeric: 'tabular-nums' }} data-testid="valeur-stock">
            {formaterPrix(stock.valeurStock)}
          </Typography>
        </Paper>
        {stock.peutAnnoncer && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAnnonce(true)}>
            Annoncer une entrée
          </Button>
        )}
      </Stack>

      {stock.entreesAnnoncees.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }} aria-label="Entrées à confirmer">
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
            Entrées annoncées à confirmer
          </Typography>
          {stock.entreesAnnoncees.map((e) => (
            <Stack
              key={e.id}
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1}
              sx={{ py: 1, borderTop: 1, borderColor: 'divider', alignItems: { sm: 'center' } }}
            >
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="body2">
                  {e.produitLibelle} · {formaterNombre(e.quantite)} à {formaterPrix(e.coutUnitaire)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {e.fournisseur} · réf. {e.reference} · annoncée le {formaterDate(e.annonceeLe.slice(0, 10))} par {e.annonceePar}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1}>
                {stock.peutRecevoir && (
                  <Button size="small" variant="outlined" onClick={() => setARecevoir(e)}>
                    Confirmer la réception
                  </Button>
                )}
                {stock.peutAnnoncer && (
                  <Button size="small" color="error" onClick={() => setAAnnuler(e)}>
                    Annuler
                  </Button>
                )}
              </Stack>
            </Stack>
          ))}
        </Paper>
      )}

      {stock.articles.length === 0 ? (
        <Alert severity="info">Aucun lubrifiant ni article boutique au référentiel : créez-les dans Produits (sans suivi en cuve).</Alert>
      ) : (
        <>
        <BarreFiltres resume={`${visibles.length} article(s) · ${formaterPrix(visibles.reduce((t, a) => t + a.valeur, 0))}`}>
          <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un article" />
        </BarreFiltres>
        {visibles.length === 0 && <Alert severity="info">Aucun article ne correspond à la recherche.</Alert>}
        <CadreTableau sx={{ display: visibles.length === 0 ? 'none' : undefined }}>
          <Table size="small" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="article">
                  Article
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="stock" align="right">
                  Stock
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="cmup" align="right" sx={CACHE_TELEPHONE}>
                  Coût moyen
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="valeur" align="right">
                  Valeur
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="prix" align="right" sx={CACHE_TELEPHONE}>
                  Prix de vente
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="comptage" sx={CACHE_TELEPHONE}>
                  Dernier comptage
                </EnTeteTri>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((a) => (
                <TableRow key={a.produitId} data-testid={`article-${a.code}`}>
                  <TableCell>
                    {a.libelle}
                    {a.ventesEnCours > 0 && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        dont {formaterNombre(a.ventesEnCours)} vendu(s) dans une journée en cours
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                    {formaterNombre(a.quantite)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                    {a.quantite === 0 && a.cmup === 0 ? '—' : formaterPrix(a.cmup)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterPrix(a.valeur)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                    {a.prixVente === null ? '—' : formaterPrix(a.prixVente)}
                  </TableCell>
                  <TableCell sx={CACHE_TELEPHONE}>
                    {a.dernierComptage ? formaterDate(a.dernierComptage) : 'jamais'}
                    {a.enRetard && (
                      <Box component="span" sx={{ ml: 1 }}>
                        <PuceEtat ton="warning" libelle="à compter" />
                      </Box>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
        </>
      )}
      {annonce && <DialogueEntree stock={stock} onFermer={() => setAnnonce(false)} />}
      {aRecevoir && <DialogueReception entree={aRecevoir} onFermer={() => setARecevoir(null)} />}
      {aAnnuler && <DialogueAnnulation entree={aAnnuler} onFermer={() => setAAnnuler(null)} />}
    </>
  )
}

function OngletInventaire({ stock }: { stock: StockArticles }) {
  const rafraichir = useRafraichirArticles()
  const [tous, setTous] = useState(false)
  const [comptes, setComptes] = useState<Record<string, string>>({})
  const [observations, setObservations] = useState<Record<string, string>>({})
  const liste = stock.articles.filter((a) => tous || a.aCompter)
  const ecart = (a: ArticleStation) => {
    const c = versNombre(comptes[a.produitId])
    return c === null ? null : c - a.disponible
  }
  const saisis = stock.articles.filter((a) => versNombre(comptes[a.produitId]) !== null)
  const manqueObservation = saisis.some((a) => ecart(a) !== 0 && !observations[a.produitId]?.trim())
  const comptage = useMutation({
    mutationFn: () =>
      api.post<StockArticles>(`/stations/${stock.stationId}/articles/comptages`, {
        comptages: saisis.map((a) => ({
          produitId: a.produitId,
          compte: versNombre(comptes[a.produitId]),
          observation: videVersNull(observations[a.produitId]),
        })),
      }),
    onSuccess: () => {
      rafraichir()
      setComptes({})
      setObservations({})
      notifier('Comptage enregistré : le stock est corrigé.')
    },
  })

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          Chaque semaine, comptez les articles proposés : un quart du rayon, et tout article non compté depuis 30 jours. Les ventes
          saisies dans une journée en cours sont déjà déduites du stock attendu.
        </Typography>
        <FormControlLabel control={<Switch checked={tous} onChange={(e) => setTous(e.target.checked)} />} label="Tous les articles" />
      </Stack>
      {liste.length === 0 ? (
        <Alert severity="success">Rien à compter cette semaine.</Alert>
      ) : (
        <CadreTableau>
          <Table size="small" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
            <TableHead>
              <TableRow>
                <TableCell>Article</TableCell>
                <TableCell align="right">Attendu</TableCell>
                <TableCell align="right" sx={{ width: { xs: 84, sm: 110 } }}>
                  Compté
                </TableCell>
                <TableCell align="right" sx={CACHE_TELEPHONE_SM}>
                  Écart
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {liste.map((a) => {
                const e = ecart(a)
                return (
                  <TableRow key={a.produitId} sx={{ verticalAlign: 'top' }}>
                    <TableCell>
                      {a.libelle}
                      <Typography variant="caption" color="text.secondary" component="div">
                        {a.dernierComptage ? `compté le ${formaterDate(a.dernierComptage)}` : 'jamais compté'}
                      </Typography>
                      {e !== null && e !== 0 && (
                        <TextField
                          size="small"
                          label="Raison de l'écart"
                          value={observations[a.produitId] ?? ''}
                          onChange={(ev) => setObservations((o) => ({ ...o, [a.produitId]: ev.target.value }))}
                          required
                          error={!observations[a.produitId]?.trim()}
                          fullWidth
                          sx={{ mt: 1 }}
                          slotProps={{ htmlInput: { maxLength: 500 } }}
                        />
                      )}
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {formaterNombre(a.disponible)}
                    </TableCell>
                    <TableCell align="right">
                      <TextField
                        size="small"
                        value={comptes[a.produitId] ?? ''}
                        onChange={(ev) => setComptes((c) => ({ ...c, [a.produitId]: ev.target.value }))}
                        disabled={!stock.peutCompter}
                        slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `Quantité comptée ${a.libelle}`, style: { textAlign: 'right' } } }}
                        sx={{ width: { xs: 72, sm: 88 } }}
                      />
                      {e !== null && e !== 0 && (
                        <Typography
                          variant="caption"
                          component="div"
                          sx={{ display: { xs: 'block', sm: 'none' }, mt: 0.5, whiteSpace: 'nowrap', color: e < 0 ? 'error.main' : undefined }}
                        >
                          écart {e > 0 ? '+' : ''}
                          {formaterNombre(e)}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{
                        ...CACHE_TELEPHONE_SM,
                        whiteSpace: 'nowrap',
                        color: e !== null && e < 0 ? 'error.main' : undefined,
                        fontVariantNumeric: 'tabular-nums',
                      }}
                      data-testid={`ecart-${a.code}`}
                    >
                      {e === null ? '—' : `${e > 0 ? '+' : ''}${formaterNombre(e)}`}
                      {e !== null && e !== 0 && (
                        <Typography variant="caption" color="text.secondary" component="div">
                          {formaterPrix(Math.round(e * a.cmup))}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </CadreTableau>
      )}
      <AlerteErreur erreur={comptage.error} />
      {stock.peutCompter && liste.length > 0 && (
        <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 2 }}>
          <Button variant="contained" onClick={() => comptage.mutate()} disabled={comptage.isPending || saisis.length === 0 || manqueObservation}>
            Enregistrer le comptage
          </Button>
        </Stack>
      )}
    </>
  )
}

function OngletMouvements({ stationId }: { stationId: string }) {
  const [mois, setMois] = useState(moisCourant())
  const [recherche, setRecherche] = useState('')
  const tri = useTri<MouvementArticle, 'date' | 'article' | 'quantite' | 'valeur' | 'stock' | 'cmup'>(
    {
      date: (x) => x.date,
      article: (x) => x.produitLibelle,
      quantite: (x) => x.quantite,
      valeur: (x) => x.valeur,
      stock: (x) => x.stockApres,
      cmup: (x) => x.cmupApres,
    },
    'date',
    'desc',
  )
  const mouvements = useMouvementsArticles(stationId, mois)
  const m = mouvements.data
  const mouvementsVisibles = tri.trier((m?.mouvements ?? []).filter((x) => correspond(recherche, x.produitLibelle, x.produitCode, x.libelle)))
  return (
    <>
      <TextField
        type="month"
        label="Mois"
        value={mois}
        onChange={(e) => e.target.value && setMois(e.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
        sx={{ mb: 2 }}
      />
      <AlerteErreur erreur={mouvements.error} />
      {mouvements.isLoading && <Chargement />}
      {m && (
        <>
          <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, mb: 2.5 }}>
            <CarteIndicateur icone={<MoveToInboxOutlinedIcon />} libelle="Entrées" valeur={formaterPrix(m.valeurEntrees)} sous="réceptions confirmées" />
            <CarteIndicateur icone={<ShoppingBasketOutlinedIcon />} libelle="Ventes au coût moyen" valeur={formaterPrix(m.valeurVentes)} ton="success" />
            <CarteIndicateur
              icone={<FactCheckOutlinedIcon />}
              libelle="Écarts d'inventaire"
              valeur={formaterPrix(m.valeurEcarts)}
              ton={m.valeurEcarts < 0 ? 'error' : 'primary'}
              accent={m.valeurEcarts < 0}
              sous={m.valeurEcarts < 0 ? 'manquant constaté au comptage' : undefined}
            />
          </Box>
          {m.mouvements.length === 0 ? (
            <Alert severity="info">Aucun mouvement ce mois-ci.</Alert>
          ) : (
            <>
            <BarreFiltres resume={`${mouvementsVisibles.length} mouvement(s) sur ${m.mouvements.length}`}>
              <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un article, un libellé" />
            </BarreFiltres>
            <CadreTableau>
              <Table size="small" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
                <TableHead>
                  <TableRow>
                    <EnTeteTri tri={tri} cle="date">
                      Date
                    </EnTeteTri>
                    <EnTeteTri tri={tri} cle="article">
                      Article
                    </EnTeteTri>
                    <EnTeteTri tri={tri} cle="quantite" align="right">
                      Quantité
                    </EnTeteTri>
                    <EnTeteTri tri={tri} cle="valeur" align="right" sx={CACHE_TELEPHONE}>
                      Valeur
                    </EnTeteTri>
                    <EnTeteTri tri={tri} cle="stock" align="right" sx={CACHE_TELEPHONE}>
                      Stock après
                    </EnTeteTri>
                    <EnTeteTri tri={tri} cle="cmup" align="right" sx={CACHE_TELEPHONE}>
                      Coût moyen après
                    </EnTeteTri>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {mouvementsVisibles.map((x, i) => (
                    <TableRow key={i}>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDate(x.date)}</TableCell>
                      <TableCell>
                        {x.produitLibelle}
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0.25, sm: 1 }} sx={{ alignItems: { sm: 'center' }, mt: 0.25 }}>
                          <Chip
                            size="small"
                            variant="outlined"
                            color={TYPES_MOUVEMENT[x.type].couleur}
                            label={TYPES_MOUVEMENT[x.type].libelle}
                            sx={{ alignSelf: 'flex-start' }}
                          />
                          {x.libelle && (
                            <Typography variant="caption" color="text.secondary">
                              {x.libelle}
                            </Typography>
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                        {x.quantite > 0 ? '+' : ''}
                        {formaterNombre(x.quantite)}
                      </TableCell>
                      <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                        {formaterPrix(x.valeur)}
                      </TableCell>
                      <TableCell align="right" sx={CACHE_TELEPHONE}>
                        {formaterNombre(x.stockApres)}
                      </TableCell>
                      <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                        {formaterPrix(x.cmupApres)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CadreTableau>
            </>
          )}
        </>
      )}
    </>
  )
}

export function PageArticles() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const o = params.get('onglet')
  const onglet = o === 'inventaire' || o === 'mouvements' ? o : 'stock'
  const stock = useStockArticles(stationId)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    if (stationId && !p.get('station')) p.set('station', stationId)
    setParams(p)
  }
  const aCompter = stock.data?.nombreACompter ?? 0

  return (
    <>
      <EnTetePage titre="Lubrifiants et boutique" sousTitre="Stock au coût moyen pondéré, entrées à confirmer, inventaire tournant." />
      {actives.length > 1 && (
        <TextField select label="Station" value={stationId ?? ''} onChange={(e) => changer('station', e.target.value)} sx={{ minWidth: 280, mb: 2 }}>
          {actives.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.code} · {s.nom}
            </MenuItem>
          ))}
        </TextField>
      )}
      <AlerteErreur erreur={stations.error ?? stock.error} />
      {!stationId && !stations.isLoading && <Alert severity="info">Choisissez une station.</Alert>}
      {stationId && (
        <>
          <Tabs value={onglet} onChange={(_e, v) => changer('onglet', v)} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
            <Tab value="stock" label="Stock" />
            <Tab value="inventaire" label={aCompter > 0 ? `Inventaire (${aCompter})` : 'Inventaire'} />
            <Tab value="mouvements" label="Mouvements" />
          </Tabs>
          {onglet === 'mouvements' ? (
            <OngletMouvements stationId={stationId} />
          ) : stock.isLoading ? (
            <Chargement />
          ) : stock.data ? (
            onglet === 'stock' ? (
              <OngletStock stock={stock.data} />
            ) : (
              <OngletInventaire key={stock.data.stationId} stock={stock.data} />
            )
          ) : null}
        </>
      )}
    </>
  )
}
