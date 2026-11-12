import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import StarIcon from '@mui/icons-material/Star'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
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
import { cles, useFournisseursAppro, usePenalites, useProduits, useTransporteur, useTransporteurs } from '../../api/requetes'
import type { Citerne, FournisseurAppro, PenaliteTransport, Transporteur, TransporteurDetail } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, Chargement, PuceStatut } from '../../shared/composants'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterDate, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { formaterPct } from '../../shared/stock'
import { CadreTableau, ChampRecherche, EnTeteTri, PuceEtat } from '../../shared/tableaux'
import { correspond, useTri } from '../../shared/tri'

function useInvaliderAppro() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: cles.appro })
}

/** Note sur 5 : vert à partir de 4, orange à partir de 2,5. */
function PuceNote({ note }: { note: number | null }) {
  if (note === null) return <Chip size="small" variant="outlined" label="Non noté" />
  const couleur = note >= 4 ? 'success' : note >= 2.5 ? 'warning' : 'error'
  return <Chip size="small" color={couleur} icon={<StarIcon />} label={`${formaterNombre(note)} / 5`} />
}

// ---------------------------------------------------------------- Transporteurs

function DialogueTransporteur({ transporteur, onFermer }: { transporteur?: Transporteur; onFermer: (id?: string) => void }) {
  const invalider = useInvaliderAppro()
  const t = transporteur
  const [nom, setNom] = useState(t?.nom ?? '')
  const [telephone, setTelephone] = useState(t?.telephone ?? '')
  const [contrat, setContrat] = useState(t?.contratReference ?? '')
  const [debut, setDebut] = useState(t?.contratDebut ?? '')
  const [fin, setFin] = useState(t?.contratFin ?? '')
  const [tolerance, setTolerance] = useState(t?.tolerancePct === null || t?.tolerancePct === undefined ? '' : String(t.tolerancePct))
  const [penalite, setPenalite] = useState(t?.penalite === null || t?.penalite === undefined ? '' : String(t.penalite))
  const [actif, setActif] = useState(t?.actif ?? true)
  const [observation, setObservation] = useState(t?.observation ?? '')
  const enregistrement = useMutation({
    mutationFn: () => {
      const corps = {
        nom: nom.trim(),
        telephone: videVersNull(telephone),
        contratReference: videVersNull(contrat),
        contratDebut: debut || null,
        contratFin: fin || null,
        tolerancePct: versNombre(tolerance),
        penalite: versNombre(penalite),
        actif,
        observation: videVersNull(observation),
      }
      return t
        ? api.put<TransporteurDetail>(`/appro/transporteurs/${t.id}`, { ...corps, version: t.version })
        : api.post<TransporteurDetail>('/appro/transporteurs', corps)
    },
    onSuccess: (d) => {
      invalider()
      notifier(`Transporteur ${d.transporteur.nom} enregistré.`)
      onFermer(d.transporteur.id)
    },
  })
  return (
    <Dialog open onClose={() => onFermer()} maxWidth="sm" fullWidth>
      <DialogTitle>{t ? t.nom : 'Nouveau transporteur'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField fullWidth label="Nom" value={nom} onChange={(e) => setNom(e.target.value)} required autoFocus />
            <TextField fullWidth label="Téléphone" value={telephone} onChange={(e) => setTelephone(e.target.value)} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField fullWidth label="Référence du contrat" value={contrat} onChange={(e) => setContrat(e.target.value)} />
            <TextField type="date" label="Début" value={debut} onChange={(e) => setDebut(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField type="date" label="Fin" value={fin} onChange={(e) => setFin(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              fullWidth
              label="Manquant toléré (%)"
              value={tolerance}
              onChange={(e) => setTolerance(e.target.value)}
              helperText="Vide : tolérance du réseau"
              slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            />
            <TextField
              fullWidth
              label="Pénalité par livraison hors tolérance (F)"
              value={penalite}
              onChange={(e) => setPenalite(e.target.value)}
              helperText="En plus du complément citerne"
              slotProps={{ htmlInput: { inputMode: 'numeric' } }}
            />
          </Stack>
          <TextField label="Observation" value={observation} onChange={(e) => setObservation(e.target.value)} multiline minRows={2} />
          <FormControlLabel control={<Switch checked={actif} onChange={(e) => setActif(e.target.checked)} />} label="Actif" />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onFermer()}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={!nom.trim() || enregistrement.isPending}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueCiterne({ transporteurId, citerne, onFermer }: { transporteurId: string; citerne?: Citerne; onFermer: () => void }) {
  const invalider = useInvaliderAppro()
  const [immatriculation, setImmatriculation] = useState(citerne?.immatriculation ?? '')
  const [capacite, setCapacite] = useState(citerne ? String(citerne.capaciteLitres) : '')
  const [actif, setActif] = useState(citerne?.actif ?? true)
  const enregistrement = useMutation({
    mutationFn: () => {
      const corps = { immatriculation: immatriculation.trim(), capaciteLitres: versNombre(capacite), actif }
      return citerne
        ? api.put(`/appro/transporteurs/${transporteurId}/citernes/${citerne.id}`, { ...corps, version: citerne.version })
        : api.post(`/appro/transporteurs/${transporteurId}/citernes`, corps)
    },
    onSuccess: () => {
      invalider()
      notifier('Citerne enregistrée.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>{citerne ? citerne.immatriculation : 'Nouvelle citerne'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Immatriculation" value={immatriculation} onChange={(e) => setImmatriculation(e.target.value)} required autoFocus />
          <TextField label="Capacité (L)" value={capacite} onChange={(e) => setCapacite(e.target.value)} required slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
          <FormControlLabel control={<Switch checked={actif} onChange={(e) => setActif(e.target.checked)} />} label="En service" />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={!immatriculation.trim() || !(versNombre(capacite) ?? 0) || enregistrement.isPending}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueReglementPenalite({ penalite, onFermer }: { penalite: PenaliteTransport; onFermer: () => void }) {
  const invalider = useInvaliderAppro()
  const [reference, setReference] = useState('')
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const reglement = useMutation({
    mutationFn: () =>
      api.post<PenaliteTransport>(`/appro/penalites/${penalite.id}/reglement`, { version: penalite.version, reference: reference.trim(), date }),
    onSuccess: () => {
      invalider()
      notifier('Règlement de la pénalité enregistré.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Règlement de la pénalité</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {penalite.transporteurNom} · BL {penalite.numeroBl} : {formaterPrix(penalite.montant)}.
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Référence (retenue sur facture, reçu…)"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            autoFocus
            required
          />
          <TextField type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        </Stack>
        <AlerteErreur erreur={reglement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => reglement.mutate()} disabled={!reference.trim() || !date || reglement.isPending}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function TablePenalites({ penalites, avecTransporteur }: { penalites: PenaliteTransport[]; avecTransporteur: boolean }) {
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [aRegler, setARegler] = useState<PenaliteTransport | null>(null)
  const tri = useTri<PenaliteTransport, 'date' | 'transporteur' | 'livraison' | 'manquant' | 'montant' | 'statut'>(
    {
      date: (p) => p.date,
      transporteur: (p) => p.transporteurNom,
      livraison: (p) => `${p.stationCode} ${p.numeroBl ?? ''}`,
      manquant: (p) => p.manquantLitres,
      montant: (p) => p.montant,
      statut: (p) => p.statut,
    },
    'date',
    'desc',
  )
  return (
    <>
      <CadreTableau>
        <Table size="small" aria-label="Pénalités">
          <TableHead>
            <TableRow>
              <EnTeteTri tri={tri} cle="date">
                Date
              </EnTeteTri>
              {avecTransporteur && (
                <EnTeteTri tri={tri} cle="transporteur">
                  Transporteur
                </EnTeteTri>
              )}
              <EnTeteTri tri={tri} cle="livraison">
                Livraison
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="manquant" align="right">
                Manquant
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="montant" align="right">
                Pénalité
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="statut">
                Statut
              </EnTeteTri>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {tri.trier(penalites).map((p) => (
              <TableRow key={p.id}>
                <TableCell>{formaterDate(p.date)}</TableCell>
                {avecTransporteur && <TableCell>{p.transporteurNom}</TableCell>}
                <TableCell>
                  {p.stationCode} · BL {p.numeroBl}
                  <Typography variant="caption" color="text.secondary" component="div">
                    {p.tourneeNumero}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  {formaterNombre(p.manquantLitres)} L ({formaterPct(p.manquantPct)})
                  <Typography variant="caption" color="text.secondary" component="div">
                    toléré {formaterPct(p.tolerancePct)}
                  </Typography>
                </TableCell>
                <TableCell align="right">{formaterPrix(p.montant)}</TableCell>
                <TableCell>
                  {p.statut === 'REGLE' ? (
                    <PuceEtat ton="success" libelle={`Réglée le ${formaterDate(p.regleLe)} · ${p.referenceReglement}`} />
                  ) : (
                    <PuceEtat ton="warning" libelle="À régler" />
                  )}
                </TableCell>
                <TableCell>
                  {peutGerer && p.statut === 'A_REGLER' && (
                    <Button size="small" onClick={() => setARegler(p)}>
                      Régler
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CadreTableau>
      {aRegler && <DialogueReglementPenalite penalite={aRegler} onFermer={() => setARegler(null)} />}
    </>
  )
}

function FicheTransporteur({ id, onFermer }: { id: string; onFermer: () => void }) {
  const fiche = useTransporteur(id)
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [modifier, setModifier] = useState(false)
  const [citerne, setCiterne] = useState<Citerne | 'nouvelle' | null>(null)
  const d = fiche.data
  const t = d?.transporteur
  const n = t?.notation
  return (
    <Dialog open onClose={onFermer} maxWidth="lg" fullWidth>
      <DialogTitle>
        {t?.nom ?? 'Transporteur'} {n && <PuceNote note={n.note} />}
      </DialogTitle>
      <DialogContent>
        <AlerteErreur erreur={fiche.error} />
        {fiche.isLoading && <Chargement />}
        {d && t && n && (
          <Stack spacing={3}>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' } }}>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Contrat
                </Typography>
                <Typography variant="body2">
                  {t.contratReference ?? 'Sans référence'}
                  {t.contratDebut ? ` · du ${formaterDate(t.contratDebut)}` : ''}
                  {t.contratFin ? ` au ${formaterDate(t.contratFin)}` : ''}
                </Typography>
                <Typography variant="body2">
                  Manquant toléré : {formaterPct(t.toleranceAppliquee)}
                  {t.tolerancePct === null ? ' (tolérance du réseau)' : ''}
                </Typography>
                <Typography variant="body2">
                  Pénalité par livraison hors tolérance : {t.penalite === null ? 'aucune' : formaterPrix(t.penalite)}
                </Typography>
                {t.telephone && <Typography variant="body2">Téléphone : {t.telephone}</Typography>}
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Note sur {n.jours} jours ({n.livraisons} livraison(s))
                </Typography>
                {n.livraisons === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Aucune livraison réceptionnée sur la période.
                  </Typography>
                ) : (
                  <>
                    <Typography variant="body2">Ponctualité : {formaterNombre(n.ponctualitePct)} % (40 % de la note)</Typography>
                    <Typography variant="body2">
                      Dans la tolérance : {formaterNombre(n.conformitePct)} % · {n.horsTolerance} hors tolérance (40 %)
                    </Typography>
                    <Typography variant="body2">Taux de manquant : {formaterPct(n.tauxManquantPct)} (20 %)</Typography>
                  </>
                )}
              </Paper>
            </Box>

            <Box>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="subtitle1">Citernes</Typography>
                {peutGerer && (
                  <Button size="small" startIcon={<AddIcon />} onClick={() => setCiterne('nouvelle')}>
                    Ajouter
                  </Button>
                )}
              </Stack>
              {t.citernes.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Aucune citerne enregistrée.
                </Typography>
              ) : (
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                  {t.citernes.map((c) => (
                    <Chip
                      key={c.id}
                      variant={c.actif ? 'filled' : 'outlined'}
                      label={`${c.immatriculation} · ${formaterNombre(c.capaciteLitres)} L${c.actif ? '' : ' · hors service'}`}
                      onClick={peutGerer ? () => setCiterne(c) : undefined}
                    />
                  ))}
                </Stack>
              )}
            </Box>

            <Box>
              <Typography variant="subtitle1" sx={{ mb: 1 }}>
                Livraisons notées
              </Typography>
              {d.livraisons.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Aucune.
                </Typography>
              ) : (
                <CadreTableau>
                  <Table size="small" aria-label="Livraisons notées">
                    <TableHead>
                      <TableRow>
                        <TableCell>Tournée</TableCell>
                        <TableCell>Prévue / reçue</TableCell>
                        <TableCell align="right">Livré</TableCell>
                        <TableCell align="right">Manquant</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {d.livraisons.map((l) => (
                        <TableRow key={`${l.tourneeNumero}-${l.stationCode}-${l.produitCode}`}>
                          <TableCell>
                            {l.tourneeNumero} · {l.stationCode} · {l.produitCode}
                          </TableCell>
                          <TableCell>
                            {formaterDate(l.prevue)} / {formaterDate(l.recue)}
                            {!l.aLHeure && <Chip size="small" color="warning" label="En retard" sx={{ ml: 1 }} />}
                          </TableCell>
                          <TableCell align="right">{formaterNombre(l.quantiteLivree)} L</TableCell>
                          <TableCell align="right" sx={{ color: l.horsTolerance ? 'error.main' : undefined }}>
                            {formaterNombre(l.manquant)} L ({formaterPct(l.manquantPct)})
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CadreTableau>
              )}
            </Box>

            <Box>
              <Typography variant="subtitle1" sx={{ mb: 1 }}>
                Pénalités
              </Typography>
              {d.penalites.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Aucune.
                </Typography>
              ) : (
                <TablePenalites penalites={d.penalites} avecTransporteur={false} />
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        {t && peutGerer && <Button onClick={() => setModifier(true)}>Modifier le contrat</Button>}
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
      {t && modifier && <DialogueTransporteur transporteur={t} onFermer={() => setModifier(false)} />}
      {t && citerne && (
        <DialogueCiterne transporteurId={t.id} citerne={citerne === 'nouvelle' ? undefined : citerne} onFermer={() => setCiterne(null)} />
      )}
    </Dialog>
  )
}

export function Transporteurs() {
  const liste = useTransporteurs()
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [statut, setStatut] = useState('A_REGLER')
  const penalites = usePenalites(statut)
  const [ouvert, setOuvert] = useState<string | null>(null)
  const [nouveau, setNouveau] = useState(false)
  const [recherche, setRecherche] = useState('')
  const tri = useTri<Transporteur, 'nom' | 'note' | 'tolerance' | 'penalite' | 'citernes' | 'aRegler'>(
    {
      nom: (t) => t.nom,
      note: (t) => t.notation.note,
      tolerance: (t) => t.toleranceAppliquee,
      penalite: (t) => t.penalite,
      citernes: (t) => t.citernes.filter((c) => c.actif).length,
      aRegler: (t) => t.penalitesARegler,
    },
    'nom',
  )
  const transporteurs = liste.data ?? []
  const visibles = tri.trier(transporteurs.filter((t) => correspond(recherche, t.nom, t.contratReference)))
  return (
    <>
      <Stack direction="row" sx={{ mb: 2, gap: 1.5, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
        <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un transporteur" />
        {peutGerer && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNouveau(true)}>
            Nouveau transporteur
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="info">Aucun transporteur enregistré.</Alert>}
      {transporteurs.length > 0 && visibles.length === 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          Aucun transporteur ne correspond à la recherche.
        </Alert>
      )}
      {visibles.length > 0 && (
        <CadreTableau sx={{ mb: 3 }}>
          <Table size="small" aria-label="Transporteurs">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="nom">
                  Transporteur
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="note">
                  Note
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="tolerance" align="right">
                  Tolérance
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="penalite" align="right">
                  Pénalité
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="citernes" align="right">
                  Citernes
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="aRegler" align="right">
                  À régler
                </EnTeteTri>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((t) => (
                <TableRow key={t.id} hover sx={{ cursor: 'pointer' }} onClick={() => setOuvert(t.id)}>
                  <TableCell>
                    {t.nom} {!t.actif && <PuceStatut actif={false} />}
                    {t.contratReference && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        Contrat {t.contratReference}
                        {t.contratFin ? ` jusqu'au ${formaterDate(t.contratFin)}` : ''}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <PuceNote note={t.notation.note} />
                  </TableCell>
                  <TableCell align="right">
                    {formaterPct(t.toleranceAppliquee)}
                    {t.tolerancePct === null && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        réseau
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">{t.penalite === null ? '—' : formaterPrix(t.penalite)}</TableCell>
                  <TableCell align="right">{t.citernes.filter((c) => c.actif).length}</TableCell>
                  <TableCell align="right" sx={{ color: t.penalitesARegler > 0 ? 'warning.dark' : undefined }}>
                    {t.penalitesARegler > 0 ? formaterPrix(t.penalitesARegler) : ''}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
      )}

      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="h6">Pénalités de transport</Typography>
        <TextField select size="small" label="Statut" value={statut} onChange={(e) => setStatut(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="A_REGLER">À régler</MenuItem>
          <MenuItem value="REGLE">Réglées</MenuItem>
          <MenuItem value="">Toutes</MenuItem>
        </TextField>
      </Stack>
      <AlerteErreur erreur={penalites.error} />
      {penalites.data?.length === 0 && <Alert severity="success">Aucune pénalité{statut === 'A_REGLER' ? ' à régler' : ''}.</Alert>}
      {penalites.data && penalites.data.length > 0 && <TablePenalites penalites={penalites.data} avecTransporteur />}

      {ouvert && <FicheTransporteur id={ouvert} onFermer={() => setOuvert(null)} />}
      {nouveau && (
        <DialogueTransporteur
          onFermer={(id) => {
            setNouveau(false)
            if (id) setOuvert(id)
          }}
        />
      )}
    </>
  )
}

// ---------------------------------------------------------------- Fournisseurs

function DialogueFournisseur({ fournisseur, onFermer }: { fournisseur?: FournisseurAppro; onFermer: () => void }) {
  const invalider = useInvaliderAppro()
  const f = fournisseur
  const [nom, setNom] = useState(f?.nom ?? '')
  const [telephone, setTelephone] = useState(f?.telephone ?? '')
  const [adresse, setAdresse] = useState(f?.adresse ?? '')
  const [delai, setDelai] = useState(String(f?.delaiLivraison ?? 1))
  const [principal, setPrincipal] = useState(f?.principal ?? false)
  const [a15, setA15] = useState(f?.factureA15 ?? true)
  const [actif, setActif] = useState(f?.actif ?? true)
  const enregistrement = useMutation({
    mutationFn: () => {
      const corps = {
        nom: nom.trim(),
        telephone: videVersNull(telephone),
        adresse: videVersNull(adresse),
        delaiLivraison: versNombre(delai),
        principal,
        factureA15: a15,
        actif,
      }
      return f ? api.put(`/appro/fournisseurs/${f.id}`, { ...corps, version: f.version }) : api.post('/appro/fournisseurs', corps)
    },
    onSuccess: () => {
      invalider()
      notifier('Fournisseur enregistré.')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>{f ? f.nom : 'Nouveau fournisseur'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Nom du dépôt" value={nom} onChange={(e) => setNom(e.target.value)} required autoFocus />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField fullWidth label="Téléphone" value={telephone} onChange={(e) => setTelephone(e.target.value)} />
            <TextField
              fullWidth
              label="Délai de livraison (jours)"
              value={delai}
              onChange={(e) => setDelai(e.target.value)}
              slotProps={{ htmlInput: { inputMode: 'numeric' } }}
            />
          </Stack>
          <TextField label="Adresse" value={adresse} onChange={(e) => setAdresse(e.target.value)} />
          <FormControlLabel
            control={<Switch checked={principal} onChange={(e) => setPrincipal(e.target.checked)} />}
            label="Dépôt principal (délai des propositions de commande)"
          />
          <FormControlLabel control={<Switch checked={a15} onChange={(e) => setA15(e.target.checked)} />} label="Facture les quantités à 15 °C" />
          <FormControlLabel control={<Switch checked={actif} onChange={(e) => setActif(e.target.checked)} />} label="Actif" />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={!nom.trim() || versNombre(delai) === null || enregistrement.isPending}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialoguePrix({ fournisseur, onFermer }: { fournisseur: FournisseurAppro; onFermer: () => void }) {
  const invalider = useInvaliderAppro()
  const produits = useProduits()
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [produitId, setProduitId] = useState('')
  const [debut, setDebut] = useState(() => new Date().toISOString().slice(0, 10))
  const [prix, setPrix] = useState('')
  const ajout = useMutation({
    mutationFn: () => api.post(`/appro/fournisseurs/${fournisseur.id}/prix`, { produitId, debut, prix: versNombre(prix) }),
    onSuccess: () => {
      invalider()
      setPrix('')
      notifier('Prix d’achat enregistré.')
    },
  })
  const suppression = useMutation({
    mutationFn: (id: string) => api.delete(`/appro/prix/${id}`),
    onSuccess: () => invalider(),
  })
  const carburants = (produits.data ?? []).filter((p) => p.actif && p.suiviJaugeage)
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Prix d'achat · {fournisseur.nom}</DialogTitle>
      <DialogContent>
        {fournisseur.prix.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Aucun prix convenu.
          </Typography>
        ) : (
          <Table size="small" sx={{ mb: 2 }}>
            <TableBody>
              {fournisseur.prix.map((p) => (
                <TableRow key={p.id}>
                  <TableCell>{p.produitLibelle}</TableCell>
                  <TableCell>à partir du {formaterDate(p.debut)}</TableCell>
                  <TableCell align="right">
                    {formaterPrix(p.prix)} {p.enVigueur && <Chip size="small" color="success" label="En vigueur" />}
                  </TableCell>
                  <TableCell padding="checkbox">
                    {peutGerer && (
                      <IconButton aria-label="Retirer le prix" size="small" onClick={() =>
                          confirmerPuis(
                            {
                              titre: 'Supprimer le prix ?',
                              message: `Le prix ${p.produitLibelle} de ${formaterPrix(p.prix)} convenu à partir du ${formaterDate(p.debut)} sera retiré.`,
                            },
                            () => suppression.mutate(p.id),
                          )
                        }
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {peutGerer && (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField select size="small" label="Produit" value={produitId} onChange={(e) => setProduitId(e.target.value)} sx={{ minWidth: 150 }}>
              {carburants.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.libelle}
                </MenuItem>
              ))}
            </TextField>
            <TextField type="date" size="small" label="À partir du" value={debut} onChange={(e) => setDebut(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField size="small" label="Prix / L" value={prix} onChange={(e) => setPrix(e.target.value)} slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ width: 110 }} />
            <Button onClick={() => ajout.mutate()} disabled={!produitId || !debut || !(versNombre(prix) ?? 0) || ajout.isPending}>
              Ajouter
            </Button>
          </Stack>
        )}
        <AlerteErreur erreur={ajout.error ?? suppression.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}

export function Fournisseurs() {
  const liste = useFournisseursAppro()
  const peutGerer = usePermission('RECEPTION_VALIDER')
  const [edition, setEdition] = useState<FournisseurAppro | 'nouveau' | null>(null)
  const [prixDe, setPrixDe] = useState<string | null>(null)
  const pourPrix = liste.data?.find((f) => f.id === prixDe)
  const [recherche, setRecherche] = useState('')
  const tri = useTri<FournisseurAppro, 'nom' | 'delai' | 'facturation'>(
    { nom: (f) => f.nom, delai: (f) => f.delaiLivraison, facturation: (f) => (f.factureA15 ? 'À 15 °C' : 'Ambiante') },
    'nom',
  )
  const fournisseurs = liste.data ?? []
  const visibles = tri.trier(fournisseurs.filter((f) => correspond(recherche, f.nom, f.telephone)))
  return (
    <>
      <Stack direction="row" sx={{ mb: 2, gap: 1.5, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
        <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un dépôt" />
        {peutGerer && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEdition('nouveau')}>
            Nouveau fournisseur
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="info">Aucun fournisseur enregistré.</Alert>}
      {fournisseurs.length > 0 && visibles.length === 0 && <Alert severity="info">Aucun dépôt ne correspond à la recherche.</Alert>}
      {visibles.length > 0 && (
        <CadreTableau>
          <Table size="small" aria-label="Fournisseurs">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="nom">
                  Dépôt
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="delai" align="right">
                  Délai
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="facturation">
                  Facturation
                </EnTeteTri>
                <TableCell>Prix d'achat en vigueur</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((f) => (
                <TableRow key={f.id} hover>
                  <TableCell>
                    {f.nom} {f.principal && <PuceEtat ton="primary" libelle="Principal" />} {!f.actif && <PuceStatut actif={false} />}
                    {f.telephone && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {f.telephone}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">{f.delaiLivraison} j</TableCell>
                  <TableCell>{f.factureA15 ? 'À 15 °C' : 'Ambiante'}</TableCell>
                  <TableCell>
                    {f.prix.filter((p) => p.enVigueur).map((p) => `${p.produitCode} ${formaterPrix(p.prix)}`).join(' · ') || '—'}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Button size="small" onClick={() => setPrixDe(f.id)}>
                      Prix
                    </Button>
                    {peutGerer && (
                      <Button size="small" onClick={() => setEdition(f)}>
                        Modifier
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
      )}
      {edition && <DialogueFournisseur fournisseur={edition === 'nouveau' ? undefined : edition} onFermer={() => setEdition(null)} />}
      {pourPrix && <DialoguePrix fournisseur={pourPrix} onFermer={() => setPrixDe(null)} />}
    </>
  )
}
