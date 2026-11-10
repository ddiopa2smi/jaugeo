import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import {
  cles,
  useFournisseursAppro,
  useProduits,
  usePropositions,
  useStations,
  useTournee,
  useTournees,
  useTransporteurs,
} from '../../api/requetes'
import type { PropositionLigne, SaisieTournee, StatutTournee, Tournee } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterDateHeure, formaterNombre, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { formaterPct } from '../../shared/stock'

const STATUTS_TOURNEE: Record<StatutTournee, { libelle: string; couleur: 'default' | 'info' | 'primary' | 'success' | 'error' }> = {
  COMMANDEE: { libelle: 'Commandée', couleur: 'default' },
  CHARGEE: { libelle: 'Chargée', couleur: 'info' },
  EN_ROUTE: { libelle: 'En route', couleur: 'primary' },
  DEPOTEE: { libelle: 'Dépotée', couleur: 'success' },
  ANNULEE: { libelle: 'Annulée', couleur: 'error' },
}

const aujourdhui = () => new Date().toISOString().slice(0, 10)
const plusJours = (iso: string, n: number) => {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/** Invalide l'approvisionnement et les vues de stock des journées (livraisons attendues). */
function useInvalidation() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: cles.appro })
    queryClient.invalidateQueries({ queryKey: cles.journees })
  }
}

// ---------------------------------------------------------------- Saisie d'une tournee

interface LigneSaisie {
  stationId: string
  produitId: string
  quantite: string
}

export function DialogueTournee({
  tournee,
  initiale,
  onFermer,
}: {
  tournee?: Tournee
  initiale?: { fournisseurId: string | null; livraisons: LigneSaisie[] }
  onFermer: (creee?: Tournee) => void
}) {
  const invalider = useInvalidation()
  const fournisseurs = useFournisseursAppro()
  const transporteurs = useTransporteurs()
  const stations = useStations()
  const produits = useProduits()
  const [fournisseurId, setFournisseurId] = useState(tournee?.fournisseurId ?? initiale?.fournisseurId ?? '')
  const [transporteurId, setTransporteurId] = useState(tournee?.transporteurId ?? '')
  const [citerneId, setCiterneId] = useState(tournee?.citerneId ?? '')
  const [immatriculation, setImmatriculation] = useState(tournee?.citerneId ? '' : (tournee?.immatriculation ?? ''))
  const [chauffeur, setChauffeur] = useState(tournee?.chauffeur ?? '')
  const [chargement, setChargement] = useState(tournee?.dateChargementPrevue ?? aujourdhui())
  const [livraison, setLivraison] = useState(tournee?.dateLivraisonPrevue ?? plusJours(aujourdhui(), 1))
  const [observation, setObservation] = useState(tournee?.observation ?? '')
  const [lignes, setLignes] = useState<LigneSaisie[]>(
    tournee
      ? tournee.lignes.map((l) => ({ stationId: l.stationId, produitId: l.produitId, quantite: String(l.quantiteCommandee) }))
      : (initiale?.livraisons ?? [{ stationId: '', produitId: '', quantite: '' }]),
  )
  const livraisonsModifiables = !tournee || (tournee.statut === 'COMMANDEE' && tournee.receptionnees === 0)

  const transporteur = transporteurs.data?.find((t) => t.id === transporteurId)
  const citernes = (transporteur?.citernes ?? []).filter((c) => c.actif || c.id === tournee?.citerneId)
  const citerne = citernes.find((c) => c.id === citerneId)
  const total = lignes.reduce((s, l) => s + (versNombre(l.quantite) ?? 0), 0)
  const carburants = (produits.data ?? []).filter((p) => p.actif && p.suiviJaugeage)
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))

  const changerLigne = (i: number, champ: keyof LigneSaisie, valeur: string) =>
    setLignes((ls) => ls.map((l, j) => (j === i ? { ...l, [champ]: valeur } : l)))

  const enregistrement = useMutation({
    mutationFn: () => {
      const corps: SaisieTournee = {
        fournisseurId,
        transporteurId,
        citerneId: citerneId || null,
        immatriculation: citerneId ? null : videVersNull(immatriculation),
        chauffeur: videVersNull(chauffeur),
        dateChargementPrevue: chargement,
        dateLivraisonPrevue: livraison,
        observation: videVersNull(observation),
        livraisons: lignes.map((l) => ({ stationId: l.stationId, produitId: l.produitId, quantite: versNombre(l.quantite) ?? 0 })),
      }
      if (!tournee) return api.post<Tournee>('/appro/tournees', corps)
      return api.put<Tournee>(`/appro/tournees/${tournee.id}`, {
        ...corps,
        livraisons: livraisonsModifiables ? corps.livraisons : null,
        version: tournee.version,
      })
    },
    onSuccess: (t) => {
      invalider()
      notifier(tournee ? `Tournée ${t.numero} modifiée.` : `Tournée ${t.numero} commandée.`)
      onFermer(t)
    },
  })

  const complet =
    !!fournisseurId &&
    !!transporteurId &&
    !!chargement &&
    !!livraison &&
    lignes.length > 0 &&
    lignes.every((l) => l.stationId && l.produitId && (versNombre(l.quantite) ?? 0) > 0)

  return (
    <Dialog open onClose={() => onFermer()} maxWidth="md" fullWidth>
      <DialogTitle>{tournee ? `Tournée ${tournee.numero}` : 'Nouvelle tournée'}</DialogTitle>
      <DialogContent>
        <AlerteErreur erreur={fournisseurs.error ?? transporteurs.error} />
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select fullWidth label="Dépôt de chargement" value={fournisseurId} onChange={(e) => setFournisseurId(e.target.value)} required>
              {(fournisseurs.data ?? [])
                .filter((f) => f.actif || f.id === tournee?.fournisseurId)
                .map((f) => (
                  <MenuItem key={f.id} value={f.id}>
                    {f.nom} · {f.delaiLivraison} j
                  </MenuItem>
                ))}
            </TextField>
            <TextField
              select
              fullWidth
              label="Transporteur"
              value={transporteurId}
              onChange={(e) => {
                setTransporteurId(e.target.value)
                setCiterneId('')
              }}
              required
            >
              {(transporteurs.data ?? [])
                .filter((t) => t.actif || t.id === tournee?.transporteurId)
                .map((t) => (
                  <MenuItem key={t.id} value={t.id}>
                    {t.nom}
                    {t.notation.note !== null ? ` · ${formaterNombre(t.notation.note)}/5` : ''}
                  </MenuItem>
                ))}
            </TextField>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select fullWidth label="Citerne" value={citerneId} onChange={(e) => setCiterneId(e.target.value)} disabled={!transporteur}>
              <MenuItem value="">Non précisée</MenuItem>
              {citernes.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.immatriculation} · {formaterNombre(c.capaciteLitres)} L
                </MenuItem>
              ))}
            </TextField>
            {!citerneId && (
              <TextField fullWidth label="Immatriculation" value={immatriculation} onChange={(e) => setImmatriculation(e.target.value)} />
            )}
            <TextField fullWidth label="Chauffeur" value={chauffeur} onChange={(e) => setChauffeur(e.target.value)} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              type="date"
              fullWidth
              label="Chargement prévu"
              value={chargement}
              onChange={(e) => setChargement(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              type="date"
              fullWidth
              label="Livraison prévue"
              value={livraison}
              onChange={(e) => setLivraison(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>

          <Typography variant="subtitle2">Livraisons (arrêts)</Typography>
          {!livraisonsModifiables && (
            <Alert severity="info">La citerne est chargée ou une livraison est réceptionnée : les livraisons ne changent plus.</Alert>
          )}
          {lignes.map((l, i) => (
            <Stack key={i} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
              <TextField
                select
                size="small"
                label="Station"
                value={l.stationId}
                onChange={(e) => changerLigne(i, 'stationId', e.target.value)}
                disabled={!livraisonsModifiables}
                sx={{ minWidth: 220 }}
              >
                {actives.map((s) => (
                  <MenuItem key={s.id} value={s.id}>
                    {s.code} · {s.nom}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                size="small"
                label="Produit"
                value={l.produitId}
                onChange={(e) => changerLigne(i, 'produitId', e.target.value)}
                disabled={!livraisonsModifiables}
                sx={{ minWidth: 160 }}
              >
                {carburants.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.libelle}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                size="small"
                label="Quantité (L)"
                value={l.quantite}
                onChange={(e) => changerLigne(i, 'quantite', e.target.value)}
                disabled={!livraisonsModifiables}
                slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                sx={{ width: 140 }}
              />
              {livraisonsModifiables && lignes.length > 1 && (
                <IconButton aria-label="Retirer la livraison" onClick={() => setLignes((ls) => ls.filter((_x, j) => j !== i))}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
          {livraisonsModifiables && (
            <Button
              startIcon={<AddIcon />}
              onClick={() => setLignes((ls) => [...ls, { stationId: '', produitId: '', quantite: '' }])}
              sx={{ alignSelf: 'flex-start' }}
            >
              Ajouter un arrêt
            </Button>
          )}
          <Typography variant="body2" color={citerne && total > citerne.capaciteLitres ? 'error' : 'text.secondary'}>
            Total {formaterNombre(total)} L{citerne ? ` pour une citerne de ${formaterNombre(citerne.capaciteLitres)} L` : ''}
          </Typography>
          <TextField label="Observation" value={observation} onChange={(e) => setObservation(e.target.value)} multiline minRows={2} />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onFermer()}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={!complet || enregistrement.isPending}>
          {tournee ? 'Enregistrer' : 'Commander'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// ---------------------------------------------------------------- Chargement, depart, annulation

function DialogueChargement({ tournee, onFermer }: { tournee: Tournee; onFermer: () => void }) {
  const invalider = useInvalidation()
  const [date, setDate] = useState(tournee.chargeLe ?? aujourdhui())
  const [scelles, setScelles] = useState(tournee.scelles ?? '')
  const [bls, setBls] = useState(() =>
    tournee.lignes.map((l) => ({
      livraisonId: l.id,
      numeroBl: l.numeroBl ?? '',
      quantiteBl: String(l.quantiteBl ?? l.quantiteCommandee),
      quantiteBl15: l.quantiteBl15 === null ? '' : String(l.quantiteBl15),
    })),
  )
  const changer = (i: number, champ: 'numeroBl' | 'quantiteBl' | 'quantiteBl15', v: string) =>
    setBls((b) => b.map((x, j) => (j === i ? { ...x, [champ]: v } : x)))
  const chargement = useMutation({
    mutationFn: () =>
      api.post<Tournee>(`/appro/tournees/${tournee.id}/chargement`, {
        version: tournee.version,
        date,
        scelles: videVersNull(scelles),
        livraisons: bls.map((b) => ({
          livraisonId: b.livraisonId,
          numeroBl: b.numeroBl.trim(),
          quantiteBl: versNombre(b.quantiteBl),
          quantiteBl15: versNombre(b.quantiteBl15),
        })),
      }),
    onSuccess: () => {
      invalider()
      notifier(`Chargement de la tournée ${tournee.numero} enregistré.`)
      onFermer()
    },
  })
  const complet = !!date && bls.every((b) => b.numeroBl.trim() && (versNombre(b.quantiteBl) ?? 0) > 0)
  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>Chargement · {tournee.numero}</DialogTitle>
      <DialogContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1, mb: 2 }}>
          <TextField type="date" label="Chargé le" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField label="Scellés" value={scelles} onChange={(e) => setScelles(e.target.value)} sx={{ flex: 1 }} />
        </Stack>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Livraison</TableCell>
              <TableCell>N° BL</TableCell>
              <TableCell>Quantité BL (L)</TableCell>
              <TableCell>À 15 °C (L)</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {tournee.lignes.map((l, i) => (
              <TableRow key={l.id}>
                <TableCell>
                  {l.stationCode} · {l.produitCode}
                  <Typography variant="caption" color="text.secondary" component="div">
                    commandé {formaterNombre(l.quantiteCommandee)} L{l.reception ? ' · réceptionnée' : ''}
                  </Typography>
                </TableCell>
                <TableCell>
                  <TextField size="small" value={bls[i]!.numeroBl} onChange={(e) => changer(i, 'numeroBl', e.target.value)} disabled={!!l.reception}
                    slotProps={{ htmlInput: { 'aria-label': `BL ${l.stationCode}` } }} />
                </TableCell>
                <TableCell>
                  <TextField size="small" value={bls[i]!.quantiteBl} onChange={(e) => changer(i, 'quantiteBl', e.target.value)} disabled={!!l.reception}
                    slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `Quantité BL ${l.stationCode}` } }} />
                </TableCell>
                <TableCell>
                  <TextField size="small" value={bls[i]!.quantiteBl15} onChange={(e) => changer(i, 'quantiteBl15', e.target.value)} disabled={!!l.reception}
                    slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <AlerteErreur erreur={chargement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => chargement.mutate()} disabled={!complet || chargement.isPending}>
          Enregistrer le chargement
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueAnnulation({ tournee, onFermer }: { tournee: Tournee; onFermer: () => void }) {
  const invalider = useInvalidation()
  const [motif, setMotif] = useState('')
  const annulation = useMutation({
    mutationFn: () => api.post<Tournee>(`/appro/tournees/${tournee.id}/annulation`, { version: tournee.version, motif: motif.trim() }),
    onSuccess: () => {
      invalider()
      notifier(`Tournée ${tournee.numero} annulée.`)
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Annuler la tournée {tournee.numero}</DialogTitle>
      <DialogContent>
        <TextField label="Motif" value={motif} onChange={(e) => setMotif(e.target.value)} fullWidth multiline minRows={2} autoFocus sx={{ mt: 1 }} />
        <AlerteErreur erreur={annulation.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Retour</Button>
        <Button color="error" variant="contained" onClick={() => annulation.mutate()} disabled={!motif.trim() || annulation.isPending}>
          Annuler la tournée
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DetailTournee({ id, onFermer }: { id: string; onFermer: () => void }) {
  const tournee = useTournee(id)
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const invalider = useInvalidation()
  const [dialogue, setDialogue] = useState<'modifier' | 'charger' | 'annuler' | null>(null)
  const depart = useMutation({
    mutationFn: (t: Tournee) => api.post<Tournee>(`/appro/tournees/${t.id}/depart`, { version: t.version }),
    onSuccess: (t) => {
      invalider()
      notifier(`Tournée ${t.numero} en route.`)
    },
  })
  const t = tournee.data
  return (
    <Dialog open onClose={onFermer} maxWidth="lg" fullWidth>
      <DialogTitle>
        Tournée {t?.numero ?? ''}{' '}
        {t && <Chip size="small" color={STATUTS_TOURNEE[t.statut].couleur} label={STATUTS_TOURNEE[t.statut].libelle} />}
      </DialogTitle>
      <DialogContent>
        <AlerteErreur erreur={tournee.error ?? depart.error} />
        {tournee.isLoading && <Chargement />}
        {t && (
          <>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              {t.fournisseurNom} → {t.transporteurNom}
              {t.immatriculation ? ` · ${t.immatriculation}` : ''}
              {t.capaciteCiterne ? ` (${formaterNombre(t.capaciteCiterne)} L)` : ''}
              {t.chauffeur ? ` · ${t.chauffeur}` : ''}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Chargement prévu le {formaterDate(t.dateChargementPrevue)}, livraison le {formaterDate(t.dateLivraisonPrevue)}
              {t.chargeLe ? ` · chargée le ${formaterDate(t.chargeLe)}` : ''}
              {t.scelles ? ` · scellés ${t.scelles}` : ''}
              {t.partiLe ? ` · partie le ${formaterDateHeure(t.partiLe)}` : ''}
            </Typography>
            {t.motifAnnulation && <Alert severity="error" sx={{ mb: 2 }}>Annulée : {t.motifAnnulation}</Alert>}
            {t.observation && <Alert severity="info" sx={{ mb: 2 }}>{t.observation}</Alert>}
            <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Arrêt</TableCell>
                    <TableCell align="right">Commandé</TableCell>
                    <TableCell>BL</TableCell>
                    <TableCell>Réception</TableCell>
                    <TableCell align="right">Manquant</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {t.lignes.map((l) => (
                    <TableRow key={l.id} data-testid={`livraison-${l.stationCode}`}>
                      <TableCell>
                        {l.ordre}. {l.stationCode} · {l.produitCode}
                      </TableCell>
                      <TableCell align="right">{formaterNombre(l.quantiteCommandee)} L</TableCell>
                      <TableCell>
                        {l.numeroBl ? (
                          <>
                            {l.numeroBl} · {formaterNombre(l.quantiteBl)} L
                            {l.quantiteBl15 !== null && (
                              <Typography variant="caption" color="text.secondary" component="div">
                                {formaterNombre(l.quantiteBl15)} L à 15 °C
                              </Typography>
                            )}
                          </>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>
                        {l.reception ? (
                          <>
                            {formaterDate(l.reception.date)} · {formaterNombre(l.reception.quantiteMesuree)} L
                            {l.reception.compareeA15 ? ' à 15 °C' : ''}
                            {!l.reception.aLHeure && <Chip size="small" color="warning" label="En retard" sx={{ ml: 1 }} />}
                          </>
                        ) : (
                          'Attendue'
                        )}
                      </TableCell>
                      <TableCell align="right">
                        {l.reception ? (
                          <>
                            {formaterNombre(l.reception.manquant)} L ({formaterPct(l.reception.manquantPct)})
                            <Typography variant="caption" component="div" color={l.reception.horsTolerance ? 'error' : 'text.secondary'}>
                              {l.reception.horsTolerance ? 'Hors tolérance' : 'Dans la tolérance'} de {formaterPct(l.reception.tolerancePct)}
                            </Typography>
                          </>
                        ) : (
                          ''
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </>
        )}
      </DialogContent>
      <DialogActions>
        {t && peutGerer && t.statut !== 'ANNULEE' && t.statut !== 'DEPOTEE' && (
          <>
            {t.receptionnees === 0 && (
              <Button color="error" onClick={() => setDialogue('annuler')}>
                Annuler la tournée
              </Button>
            )}
            <Button onClick={() => setDialogue('modifier')}>Modifier</Button>
            {(t.statut === 'COMMANDEE' || t.statut === 'CHARGEE') && (
              <Button onClick={() => setDialogue('charger')}>{t.statut === 'COMMANDEE' ? 'Chargement' : 'Corriger les BL'}</Button>
            )}
            {t.statut === 'CHARGEE' && (
              <Button variant="contained" onClick={() => depart.mutate(t)} disabled={depart.isPending}>
                Départ
              </Button>
            )}
          </>
        )}
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
      {t && dialogue === 'modifier' && <DialogueTournee tournee={t} onFermer={() => setDialogue(null)} />}
      {t && dialogue === 'charger' && <DialogueChargement tournee={t} onFermer={() => setDialogue(null)} />}
      {t && dialogue === 'annuler' && <DialogueAnnulation tournee={t} onFermer={() => setDialogue(null)} />}
    </Dialog>
  )
}

// ---------------------------------------------------------------- Listes

export function Tournees() {
  const [enCours, setEnCours] = useState(true)
  const liste = useTournees(enCours)
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [ouverte, setOuverte] = useState<string | null>(null)
  const [nouvelle, setNouvelle] = useState(false)
  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center', justifyContent: 'space-between' }}>
        <FormControlLabel control={<Switch checked={enCours} onChange={(e) => setEnCours(e.target.checked)} />} label="En cours seulement" />
        {peutGerer && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNouvelle(true)}>
            Nouvelle tournée
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="info">Aucune tournée{enCours ? ' en cours' : ''}.</Alert>}
      {liste.data && liste.data.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Tournées">
            <TableHead>
              <TableRow>
                <TableCell>Tournée</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell>Dépôt → transporteur</TableCell>
                <TableCell>Livraison prévue</TableCell>
                <TableCell align="right">Quantité</TableCell>
                <TableCell align="right">Dépotées</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {liste.data.map((t) => (
                <TableRow key={t.id} hover sx={{ cursor: 'pointer' }} onClick={() => setOuverte(t.id)}>
                  <TableCell>
                    {t.numero}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {t.lignes.map((l) => l.stationCode).join(' → ')}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={STATUTS_TOURNEE[t.statut].couleur} label={STATUTS_TOURNEE[t.statut].libelle} />
                  </TableCell>
                  <TableCell>
                    {t.fournisseurNom} → {t.transporteurNom}
                    {t.immatriculation && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {t.immatriculation}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{formaterDate(t.dateLivraisonPrevue)}</TableCell>
                  <TableCell align="right">{formaterNombre(t.quantiteCommandee)} L</TableCell>
                  <TableCell align="right">
                    {t.receptionnees} / {t.livraisons}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {ouverte && <DetailTournee id={ouverte} onFermer={() => setOuverte(null)} />}
      {nouvelle && <DialogueTournee onFermer={() => setNouvelle(false)} />}
    </>
  )
}

/** Proposition de commande : autonomie de chaque station et produit, quantité pour remplir les cuves. */
export function Propositions({ versTournees }: { versTournees: () => void }) {
  const propositions = usePropositions()
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [selection, setSelection] = useState<string[]>([])
  const [commande, setCommande] = useState<PropositionLigne[] | null>(null)
  const p = propositions.data
  if (propositions.isLoading) return <Chargement />
  if (!p) return <AlerteErreur erreur={propositions.error} />
  const cle = (l: PropositionLigne) => `${l.stationId}-${l.produitId}`
  const choisies = p.lignes.filter((l) => selection.includes(cle(l)))
  return (
    <>
      {p.fournisseurId ? (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Dépôt principal {p.fournisseurNom} : délai de {p.delaiLivraison} j, plus {p.margeJours} j de marge. Une commande est
          proposée quand l'autonomie (livraisons en cours comprises) ne couvre plus ce délai, ou sous le seuil de
          réapprovisionnement ; elle remplit la cuve à {formaterNombre(p.tauxRemplissagePct)} % à l'arrivée de la citerne.
        </Typography>
      ) : (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Aucun dépôt principal : le délai de livraison est compté à 0 jour. Déclarez-le dans l'onglet Fournisseurs.
        </Alert>
      )}
      {p.lignes.length === 0 && <Alert severity="info">Aucune cuve jaugée à une clôture.</Alert>}
      {p.lignes.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Propositions de commande">
            <TableHead>
              <TableRow>
                {peutGerer && <TableCell padding="checkbox" />}
                <TableCell>Station · produit</TableCell>
                <TableCell align="right">Stock estimé</TableCell>
                <TableCell align="right">Ventes / j</TableCell>
                <TableCell align="right">Autonomie</TableCell>
                <TableCell align="right">En cours</TableCell>
                <TableCell align="right">À l'arrivée</TableCell>
                <TableCell align="right">À commander</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {p.lignes.map((l) => (
                <TableRow key={cle(l)} data-testid={`proposition-${l.stationCode}-${l.produitCode}`}>
                  {peutGerer && (
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selection.includes(cle(l))}
                        onChange={(e) => setSelection((s) => (e.target.checked ? [...s, cle(l)] : s.filter((x) => x !== cle(l))))}
                        slotProps={{ input: { 'aria-label': `Sélectionner ${l.stationCode} ${l.produitCode}` } }}
                      />
                    </TableCell>
                  )}
                  <TableCell>
                    {l.stationCode} · {l.produitLibelle}
                    {l.motif && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {l.motif}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterNombre(Math.round(l.stockAujourdhui))} L
                    <Typography variant="caption" color="text.secondary" component="div">
                      jaugé le {formaterDate(l.dateStock)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">{l.ventesMoyennes === null ? '—' : `${formaterNombre(Math.round(l.ventesMoyennes))} L`}</TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', color: l.aCommander ? 'error.main' : undefined }}>
                    {l.autonomieJours === null ? '—' : `${formaterNombre(l.autonomieJours)} j`}
                    {l.ruptureLe && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        rupture le {formaterDate(l.ruptureLe)}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">{l.enCours ? `${formaterNombre(l.enCours)} L` : ''}</TableCell>
                  <TableCell align="right">{formaterNombre(Math.round(l.stockArrivee))} L</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>
                    {l.aCommander && l.quantiteProposee !== null ? `${formaterNombre(l.quantiteProposee)} L` : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {peutGerer && (
        <Button
          variant="contained"
          sx={{ mt: 2 }}
          disabled={choisies.length === 0}
          onClick={() => setCommande(choisies)}
        >
          Commander la sélection ({choisies.length})
        </Button>
      )}
      {commande && (
        <DialogueTournee
          initiale={{
            fournisseurId: p.fournisseurId,
            livraisons: commande.map((l) => ({
              stationId: l.stationId,
              produitId: l.produitId,
              quantite: l.quantiteProposee === null ? '' : String(l.quantiteProposee),
            })),
          }}
          onFermer={(creee) => {
            setCommande(null)
            if (creee) {
              setSelection([])
              versTournees()
            }
          }}
        />
      )}
    </>
  )
}
