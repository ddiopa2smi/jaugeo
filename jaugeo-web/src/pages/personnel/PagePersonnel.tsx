import AddIcon from '@mui/icons-material/Add'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import EditIcon from '@mui/icons-material/Edit'
import SaveIcon from '@mui/icons-material/Save'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
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
import { Link as LienRouteur, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useEcartsPersonnel, useEmploye, usePersonnelStation, usePlanning, useStations } from '../../api/requetes'
import type { Employe, FonctionEmploye, PersonnelStation, PlanningSemaine, SaisieEmploye, Station, TypePoste } from '../../api/types'
import { AlerteErreur, Chargement, EnTetePage, PuceStatut } from '../../shared/composants'
import { formaterDate, formaterPrix, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { ajouterJours, COULEUR_ECART, FONCTIONS, formaterEcart, LIBELLES_POSTE, lundi, POSTES } from '../../shared/personnel'

const CACHE_TELEPHONE = { display: { xs: 'none', sm: 'table-cell' } }
const aujourdhui = () => new Date().toISOString().slice(0, 10)
const jourCourt = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'numeric', timeZone: 'UTC' })

// ---------------------------------------------------------------- Employés

function DialogueEmploye({
  stationId,
  stations,
  employe,
  onFermer,
}: {
  stationId: string
  stations: Station[]
  employe: Employe | null
  onFermer: () => void
}) {
  const queryClient = useQueryClient()
  const [f, setF] = useState({
    prenom: employe?.prenom ?? '',
    nom: employe?.nom ?? '',
    matricule: employe?.matricule ?? '',
    telephone: employe?.telephone ?? '',
    fonction: (employe?.fonction ?? 'POMPISTE') as FonctionEmploye,
    dateEntree: employe?.dateEntree ?? '',
    dateSortie: employe?.dateSortie ?? '',
    identifiant: employe?.identifiant ?? '',
    stationId: employe?.stationId ?? stationId,
    actif: employe?.actif ?? true,
  })
  const enregistrement = useMutation({
    mutationFn: (corps: SaisieEmploye) =>
      employe ? api.put<Employe>(`/personnel/employes/${employe.id}`, corps) : api.post<Employe>(`/personnel/stations/${stationId}/employes`, corps),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cles.personnel })
      notifier(employe ? 'Employé modifié.' : 'Employé créé.')
      onFermer()
    },
  })
  const champ = (cle: keyof typeof f) => ({
    value: f[cle] as string,
    onChange: (e: { target: { value: string } }) => setF((x) => ({ ...x, [cle]: e.target.value })),
  })
  const complet = f.prenom.trim() !== '' && f.nom.trim() !== ''

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>{employe ? `Modifier ${employe.nomComplet}` : 'Nouvel employé'}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, pt: 1 }}>
          <TextField label="Prénom" required autoFocus {...champ('prenom')} slotProps={{ htmlInput: { maxLength: 60 } }} />
          <TextField label="Nom" required {...champ('nom')} slotProps={{ htmlInput: { maxLength: 60 } }} />
          <TextField select label="Fonction" {...champ('fonction')}>
            {(Object.keys(FONCTIONS) as FonctionEmploye[]).map((k) => (
              <MenuItem key={k} value={k}>
                {FONCTIONS[k]}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Matricule" {...champ('matricule')} slotProps={{ htmlInput: { maxLength: 20 } }} />
          <TextField label="Téléphone" {...champ('telephone')} slotProps={{ htmlInput: { maxLength: 30 } }} />
          <TextField
            label="Compte Jaugeo"
            helperText="Identifiant de connexion, si l'employé saisit lui-même"
            {...champ('identifiant')}
            slotProps={{ htmlInput: { maxLength: 50 } }}
          />
          <TextField type="date" label="Date d'entrée" {...champ('dateEntree')} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField
            type="date"
            label="Date de sortie"
            helperText="Un employé sorti n'est plus actif"
            {...champ('dateSortie')}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          {employe && stations.length > 1 && (
            <TextField select label="Station" helperText="Changer de station ouvre une nouvelle affectation" {...champ('stationId')}>
              {stations.map((s) => (
                <MenuItem key={s.id} value={s.id}>
                  {s.code} · {s.nom}
                </MenuItem>
              ))}
            </TextField>
          )}
          <FormControlLabel
            control={<Switch checked={f.actif && !f.dateSortie} disabled={!!f.dateSortie} onChange={(e) => setF((x) => ({ ...x, actif: e.target.checked }))} />}
            label="Actif"
          />
        </Box>
        <Box sx={{ mt: 2 }}>
          <AlerteErreur erreur={enregistrement.error} />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          disabled={!complet || enregistrement.isPending}
          onClick={() =>
            enregistrement.mutate({
              prenom: f.prenom.trim(),
              nom: f.nom.trim(),
              matricule: videVersNull(f.matricule),
              telephone: videVersNull(f.telephone),
              fonction: f.fonction,
              dateEntree: videVersNull(f.dateEntree),
              dateSortie: videVersNull(f.dateSortie),
              identifiant: videVersNull(f.identifiant),
              actif: f.actif,
              ...(employe ? { stationId: f.stationId, version: employe.version } : {}),
            })
          }
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Fiche d'un employé : affectations et feuilles de caisse du mois. */
function FicheEmploye({ id, moisInitial, onFermer }: { id: string; moisInitial: string; onFermer: () => void }) {
  const [mois, setMois] = useState(moisInitial)
  const requete = useEmploye(id, mois)
  const d = requete.data

  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>{d ? d.employe.nomComplet : 'Employé'}</DialogTitle>
      <DialogContent>
        <AlerteErreur erreur={requete.error} />
        {requete.isLoading && <Chargement />}
        {d && (
          <>
            <Typography variant="body2" color="text.secondary">
              {FONCTIONS[d.employe.fonction]} · {d.employe.stationCode}
              {d.employe.matricule ? ` · matricule ${d.employe.matricule}` : ''}
              {d.employe.telephone ? ` · ${d.employe.telephone}` : ''}
            </Typography>
            <Typography variant="subtitle2" sx={{ mt: 2 }}>
              Affectations
            </Typography>
            {d.affectations.map((a, i) => (
              <Typography key={i} variant="body2">
                {a.stationCode} · {a.stationNom} : depuis le {formaterDate(a.debut)}
                {a.fin ? ` jusqu'au ${formaterDate(a.fin)}` : ''}
              </Typography>
            ))}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 2, mb: 1, alignItems: { sm: 'center' } }}>
              <Typography variant="subtitle2" sx={{ flexGrow: 1 }}>
                Feuilles de caisse
              </Typography>
              <TextField
                type="month"
                size="small"
                label="Mois"
                value={mois}
                onChange={(e) => e.target.value && setMois(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>
            {d.feuilles.length === 0 ? (
              <Alert severity="info">Aucune feuille de caisse clôturée ce mois-ci.</Alert>
            ) : (
              <>
                <Typography variant="body2" sx={{ mb: 1 }} data-testid="cumul-employe">
                  Manquants {formaterPrix(d.manquants)} · excédents {formaterPrix(d.excedents)}
                </Typography>
                <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Poste</TableCell>
                        <TableCell align="right" sx={CACHE_TELEPHONE}>
                          Dû
                        </TableCell>
                        <TableCell align="right" sx={CACHE_TELEPHONE}>
                          Remis
                        </TableCell>
                        <TableCell align="right">Écart</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {d.feuilles.map((x, i) => (
                        <TableRow key={i}>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>
                            {x.journeeId ? <LienRouteur to={`/journees/${x.journeeId}`}>{formaterDate(x.date)}</LienRouteur> : formaterDate(x.date)}
                          </TableCell>
                          <TableCell>
                            {LIBELLES_POSTE[x.poste]}
                            {x.observation ? ` · ${x.observation}` : ''}
                          </TableCell>
                          <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                            {formaterPrix(x.du)}
                          </TableCell>
                          <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                            {formaterPrix(x.remis)}
                          </TableCell>
                          <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontWeight: 600, color: COULEUR_ECART(x.ecart) }}>
                            {formaterEcart(x.ecart)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Paper>
              </>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}

function OngletEmployes({ personnel, stations }: { personnel: PersonnelStation; stations: Station[] }) {
  const queryClient = useQueryClient()
  const [edition, setEdition] = useState<Employe | 'nouveau' | null>(null)
  const [fiche, setFiche] = useState<string | null>(null)
  const reglage = useMutation({
    mutationFn: (actif: boolean) => api.put<PersonnelStation>(`/personnel/stations/${personnel.stationId}/feuilles-caisse`, { actif }),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.personnelStation(personnel.stationId), r)
      queryClient.invalidateQueries({ queryKey: cles.journees })
      notifier(r.feuillesCaisse ? 'Feuilles de caisse activées.' : 'Feuilles de caisse désactivées.')
    },
  })

  return (
    <>
      <Paper variant="outlined" sx={{ px: 2, py: 1.5, mb: 2 }}>
        <FormControlLabel
          control={
            <Switch
              checked={personnel.feuillesCaisse}
              disabled={!personnel.reglerFeuilles || reglage.isPending}
              onChange={(e) => reglage.mutate(e.target.checked)}
            />
          }
          label="Feuilles de caisse par pompiste"
        />
        <Typography variant="body2" color="text.secondary">
          {personnel.feuillesCaisse
            ? 'La journée ne se clôture que si les postes totalisent les litres du relevé. Les écarts sont suivis par pompiste.'
            : 'Cette station ne tient pas de feuilles de caisse : la journée se clôture sans relevé par poste.'}
          {!personnel.reglerFeuilles && ' Ce réglage appartient au superviseur ou à l’administrateur.'}
        </Typography>
        <AlerteErreur erreur={reglage.error} />
      </Paper>
      {personnel.gerer && (
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEdition('nouveau')} sx={{ mb: 2 }}>
          Nouvel employé
        </Button>
      )}
      {personnel.employes.length === 0 ? (
        <Alert severity="info">Aucun employé enregistré dans cette station.</Alert>
      ) : (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
            <TableHead>
              <TableRow>
                <TableCell>Employé</TableCell>
                <TableCell>Fonction</TableCell>
                <TableCell sx={CACHE_TELEPHONE}>Matricule</TableCell>
                <TableCell sx={CACHE_TELEPHONE}>Téléphone</TableCell>
                <TableCell sx={CACHE_TELEPHONE}>Entrée</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {personnel.employes.map((e) => (
                <TableRow key={e.id} hover>
                  <TableCell>
                    <Button size="small" onClick={() => setFiche(e.id)} sx={{ textTransform: 'none', px: 0, minWidth: 0, textAlign: 'left' }}>
                      {e.nomComplet}
                    </Button>
                  </TableCell>
                  <TableCell>{FONCTIONS[e.fonction]}</TableCell>
                  <TableCell sx={CACHE_TELEPHONE}>{e.matricule ?? '—'}</TableCell>
                  <TableCell sx={CACHE_TELEPHONE}>{e.telephone ?? '—'}</TableCell>
                  <TableCell sx={CACHE_TELEPHONE}>{formaterDate(e.dateEntree)}</TableCell>
                  <TableCell>
                    <PuceStatut actif={e.actif} libelleInactif={e.dateSortie ? 'Sorti' : 'Inactif'} />
                  </TableCell>
                  <TableCell align="right">
                    {personnel.gerer && (
                      <IconButton size="small" aria-label={`Modifier ${e.nomComplet}`} onClick={() => setEdition(e)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {edition && (
        <DialogueEmploye
          stationId={personnel.stationId}
          stations={stations}
          employe={edition === 'nouveau' ? null : edition}
          onFermer={() => setEdition(null)}
        />
      )}
      {fiche && <FicheEmploye id={fiche} moisInitial={aujourdhui().slice(0, 7)} onFermer={() => setFiche(null)} />}
    </>
  )
}

// ---------------------------------------------------------------- Planning

const cleCreneau = (employeId: string, date: string) => `${employeId}|${date}`

function GrillePlanning({ planning }: { planning: PlanningSemaine }) {
  const queryClient = useQueryClient()
  const jours = Array.from({ length: 7 }, (_x, i) => ajouterJours(planning.du, i))
  const initial = (): Record<string, TypePoste | ''> => {
    const r: Record<string, TypePoste | ''> = {}
    for (const c of planning.creneaux) r[cleCreneau(c.employeId, c.date)] ??= c.poste
    return r
  }
  const [grille, setGrille] = useState(initial)
  const [modifie, setModifie] = useState(false)
  const [reconduction, setReconduction] = useState<{ semaines: string; rotation: boolean } | null>(null)
  const base = `/personnel/stations/${planning.stationId}/planning`

  const enregistrement = useMutation({
    mutationFn: () =>
      api.put<PlanningSemaine>(base, {
        du: planning.du,
        creneaux: Object.entries(grille)
          .filter(([, poste]) => poste !== '')
          .map(([cle, poste]) => ({ employeId: cle.split('|')[0], date: cle.split('|')[1], poste })),
      }),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.planning(planning.stationId, planning.du), r)
      setModifie(false)
      notifier('Planning enregistré.')
    },
  })
  const reconduire = useMutation({
    mutationFn: (corps: { du: string; semaines: number; rotation: boolean }) => api.post<PlanningSemaine>(`${base}/reconduction`, corps),
    onSuccess: (_r, corps) => {
      queryClient.invalidateQueries({ queryKey: cles.personnel })
      setReconduction(null)
      notifier(`Planning reconduit sur ${corps.semaines} semaine${corps.semaines > 1 ? 's' : ''}.`)
    },
  })
  const vide = Object.values(grille).every((p) => p === '')

  if (planning.employes.length === 0) return <Alert severity="info">Aucun employé actif à planifier dans cette station.</Alert>

  return (
    <>
      <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small" sx={{ '& td, & th': { px: 1 } }}>
          <TableHead>
            <TableRow>
              <TableCell>Employé</TableCell>
              {jours.map((j) => (
                <TableCell key={j} align="center" sx={{ whiteSpace: 'nowrap' }}>
                  {jourCourt.format(new Date(`${j}T00:00:00Z`))}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {planning.employes.map((e) => (
              <TableRow key={e.id}>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{e.nomComplet}</TableCell>
                {jours.map((j) => (
                  <TableCell key={j} align="center" sx={{ minWidth: 92 }}>
                    <TextField
                      select
                      size="small"
                      variant="standard"
                      value={grille[cleCreneau(e.id, j)] ?? ''}
                      disabled={!planning.modifiable}
                      onChange={(ev) => {
                        setGrille((g) => ({ ...g, [cleCreneau(e.id, j)]: ev.target.value as TypePoste | '' }))
                        setModifie(true)
                      }}
                      slotProps={{ htmlInput: { 'aria-label': `${e.nomComplet} le ${formaterDate(j)}` }, select: { displayEmpty: true } }}
                      sx={{ minWidth: 80 }}
                    >
                      <MenuItem value="">—</MenuItem>
                      {POSTES.map((p) => (
                        <MenuItem key={p} value={p}>
                          {LIBELLES_POSTE[p]}
                        </MenuItem>
                      ))}
                    </TextField>
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <AlerteErreur erreur={enregistrement.error} />
      {planning.modifiable && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 2, alignItems: { sm: 'center' }, justifyContent: 'flex-end' }}>
          {modifie && (
            <Typography variant="body2" color="warning.main" sx={{ flexGrow: 1 }}>
              Planning non enregistré.
            </Typography>
          )}
          <Button disabled={modifie || vide} onClick={() => setReconduction({ semaines: '1', rotation: false })}>
            Reconduire
          </Button>
          <Button variant="contained" startIcon={<SaveIcon />} disabled={!modifie || enregistrement.isPending} onClick={() => enregistrement.mutate()}>
            Enregistrer le planning
          </Button>
        </Stack>
      )}
      <Dialog open={reconduction !== null} onClose={() => setReconduction(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Reconduire cette semaine</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Le planning des semaines suivantes est remplacé par celui de la semaine du {formaterDate(planning.du)}.
          </Typography>
          <TextField
            select
            label="Nombre de semaines"
            value={reconduction?.semaines ?? '1'}
            onChange={(e) => setReconduction((r) => r && { ...r, semaines: e.target.value })}
            fullWidth
          >
            {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
              <MenuItem key={n} value={String(n)}>
                {n}
              </MenuItem>
            ))}
          </TextField>
          <FormControlLabel
            sx={{ mt: 1 }}
            control={<Checkbox checked={reconduction?.rotation ?? false} onChange={(e) => setReconduction((r) => r && { ...r, rotation: e.target.checked })} />}
            label="Rotation : chaque semaine, chacun passe au poste suivant"
          />
          <AlerteErreur erreur={reconduire.error} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReconduction(null)}>Annuler</Button>
          <Button
            variant="contained"
            disabled={reconduire.isPending}
            onClick={() => reconduire.mutate({ du: planning.du, semaines: Number(reconduction!.semaines), rotation: reconduction!.rotation })}
          >
            Reconduire
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function OngletPlanning({ stationId }: { stationId: string }) {
  const [du, setDu] = useState(() => lundi(aujourdhui()))
  const requete = usePlanning(stationId, du)

  return (
    <>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
        <IconButton aria-label="Semaine précédente" onClick={() => setDu(ajouterJours(du, -7))}>
          <ChevronLeftIcon />
        </IconButton>
        <Typography variant="subtitle1" data-testid="semaine">
          Semaine du {formaterDate(du)}
        </Typography>
        <IconButton aria-label="Semaine suivante" onClick={() => setDu(ajouterJours(du, 7))}>
          <ChevronRightIcon />
        </IconButton>
      </Stack>
      <AlerteErreur erreur={requete.error} />
      {requete.isLoading && <Chargement />}
      {/* Remontée à chaque réponse du serveur : la grille repart du planning enregistré */}
      {requete.data && <GrillePlanning key={`${du}-${requete.dataUpdatedAt}`} planning={requete.data} />}
    </>
  )
}

// ---------------------------------------------------------------- Écarts de caisse

function OngletEcarts({ stationId }: { stationId: string }) {
  const [mois, setMois] = useState(() => aujourdhui().slice(0, 7))
  const [fiche, setFiche] = useState<string | null>(null)
  const requete = useEcartsPersonnel(stationId, mois)
  const e = requete.data

  return (
    <>
      <TextField
        type="month"
        label="Mois"
        value={mois}
        onChange={(ev) => ev.target.value && setMois(ev.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
        sx={{ mb: 2 }}
      />
      <AlerteErreur erreur={requete.error} />
      {requete.isLoading && <Chargement />}
      {e && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, mb: 2 }}>
            {(
              [
                ['Feuilles clôturées', String(e.feuilles), undefined],
                ['Manquants', formaterPrix(e.manquants), e.manquants > 0 ? 'error.main' : undefined],
                ['Excédents', formaterPrix(e.excedents), undefined],
                ['Écart net', formaterEcart(e.net), COULEUR_ECART(e.net)],
              ] as const
            ).map(([l, v, couleur]) => (
              <Paper key={l} variant="outlined" sx={{ px: 2, py: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {l}
                </Typography>
                <Typography variant="h6" sx={{ fontVariantNumeric: 'tabular-nums', color: couleur }}>
                  {v}
                </Typography>
              </Paper>
            ))}
          </Box>
          {e.lignes.length === 0 ? (
            <Alert severity="info">Aucune feuille de caisse clôturée ce mois-ci.</Alert>
          ) : (
            <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
              <Table size="small" sx={{ '& td, & th': { px: { xs: 1, sm: 2 } } }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Employé</TableCell>
                    <TableCell align="right">Feuilles</TableCell>
                    <TableCell align="right" sx={CACHE_TELEPHONE}>
                      Dû
                    </TableCell>
                    <TableCell align="right" sx={CACHE_TELEPHONE}>
                      Remis
                    </TableCell>
                    <TableCell align="right">Manquants</TableCell>
                    <TableCell align="right" sx={CACHE_TELEPHONE}>
                      Excédents
                    </TableCell>
                    <TableCell align="right">Écart net</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {e.lignes.map((l) => (
                    <TableRow key={l.employeId} hover data-testid={`ecart-${l.employe}`}>
                      <TableCell>
                        <Button size="small" onClick={() => setFiche(l.employeId)} sx={{ textTransform: 'none', px: 0, minWidth: 0, textAlign: 'left' }}>
                          {l.employe}
                        </Button>
                      </TableCell>
                      <TableCell align="right">{l.feuilles}</TableCell>
                      <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                        {formaterPrix(l.du)}
                      </TableCell>
                      <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                        {formaterPrix(l.remis)}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap', color: l.manquants > 0 ? 'error.main' : undefined }}>
                        {formaterPrix(l.manquants)}
                      </TableCell>
                      <TableCell align="right" sx={{ ...CACHE_TELEPHONE, whiteSpace: 'nowrap' }}>
                        {formaterPrix(l.excedents)}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontWeight: 600, color: COULEUR_ECART(l.net) }}>
                        {formaterEcart(l.net)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          )}
          <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
            Écart = caisse remise − ventes des pistolets tenus. C'est un suivi : le gérant reste redevable du montant à verser.
          </Typography>
        </>
      )}
      {fiche && <FicheEmploye id={fiche} moisInitial={mois} onFermer={() => setFiche(null)} />}
    </>
  )
}

// ---------------------------------------------------------------- Page

export function PagePersonnel() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const o = params.get('onglet')
  const onglet = o === 'planning' || o === 'ecarts' ? o : 'employes'
  const personnel = usePersonnelStation(stationId)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    if (stationId && !p.get('station')) p.set('station', stationId)
    setParams(p)
  }

  return (
    <>
      <EnTetePage titre="Personnel" sousTitre="Employés, planning des postes et écarts de caisse par pompiste." />
      {actives.length > 1 && (
        <TextField select label="Station" value={stationId ?? ''} onChange={(e) => changer('station', e.target.value)} sx={{ minWidth: 280, mb: 2 }}>
          {actives.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.code} · {s.nom}
            </MenuItem>
          ))}
        </TextField>
      )}
      <AlerteErreur erreur={stations.error ?? personnel.error} />
      {!stationId && !stations.isLoading && <Alert severity="info">Choisissez une station.</Alert>}
      {stationId && (
        <>
          <Tabs value={onglet} onChange={(_e, v) => changer('onglet', v)} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
            <Tab value="employes" label="Employés" />
            <Tab value="planning" label="Planning" />
            <Tab value="ecarts" label="Écarts de caisse" />
          </Tabs>
          {onglet === 'planning' ? (
            <OngletPlanning key={stationId} stationId={stationId} />
          ) : onglet === 'ecarts' ? (
            <OngletEcarts key={stationId} stationId={stationId} />
          ) : personnel.isLoading ? (
            <Chargement />
          ) : personnel.data ? (
            <OngletEmployes personnel={personnel.data} stations={actives} />
          ) : null}
        </>
      )}
    </>
  )
}
