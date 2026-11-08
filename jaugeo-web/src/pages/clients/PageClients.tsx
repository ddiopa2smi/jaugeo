import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import PostAddIcon from '@mui/icons-material/PostAdd'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useClients, useFicheClient, useReleve, useStations } from '../../api/requetes'
import type { Client, FicheClient, ModeReglementClient, Recouvrement, SaisieClient, SaisieOperation } from '../../api/types'
import { usePermission } from '../../auth/session'
import { MODES_REGLEMENT_CLIENT, MODES_SIEGE, RECOUVREMENTS } from '../../shared/clients'
import { AlerteErreur, Chargement, EnTetePage, PuceStatut } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { aujourdhui } from '../../shared/tresorerie'

const TYPES_LIGNE: Record<string, string> = { BON: 'Bon', REPRISE: 'Reprise de créance', REGLEMENT: 'Règlement' }

function useRafraichirClients() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: cles.clients })
    queryClient.invalidateQueries({ queryKey: cles.syntheses })
  }
}

function DialogueClient({ client, onFermer }: { client: Client | null; onFermer: () => void }) {
  const rafraichir = useRafraichirClients()
  const [code, setCode] = useState(client?.code ?? '')
  const [nom, setNom] = useState(client?.nom ?? '')
  const [telephone, setTelephone] = useState(client?.telephone ?? '')
  const [recouvrement, setRecouvrement] = useState<Recouvrement>(client?.recouvrement ?? 'DIRECTION')
  const [plafond, setPlafond] = useState(client?.plafond != null ? String(client.plafond) : '')
  const [actif, setActif] = useState(client?.actif ?? true)
  const [observation, setObservation] = useState(client?.observation ?? '')
  const p = versNombre(plafond)
  const plafondValide = plafond.trim() === '' || (p !== null && p > 0 && Number.isInteger(p))

  const enregistrement = useMutation({
    mutationFn: () => {
      const corps: SaisieClient = {
        nom: nom.trim(),
        telephone: videVersNull(telephone),
        recouvrement,
        plafond: plafond.trim() === '' ? null : p,
        actif,
        observation: videVersNull(observation),
      }
      return client
        ? api.put<Client>(`/clients/${client.id}`, { ...corps, version: client.version })
        : api.post<Client>('/clients', { ...corps, code: code.trim() })
    },
    onSuccess: () => {
      rafraichir()
      notifier('Client enregistré.')
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>{client ? `Client ${client.code}` : 'Nouveau client en compte'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Stack direction="row" spacing={2}>
            <TextField
              label="Code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
              disabled={!!client}
              sx={{ width: 160 }}
              slotProps={{ htmlInput: { maxLength: 20 } }}
            />
            <TextField label="Nom" value={nom} onChange={(e) => setNom(e.target.value)} required sx={{ flex: 1 }} slotProps={{ htmlInput: { maxLength: 120 } }} />
          </Stack>
          <TextField label="Téléphone" value={telephone} onChange={(e) => setTelephone(e.target.value)} slotProps={{ htmlInput: { maxLength: 30 } }} />
          <TextField
            select
            label="Recouvrement des bons"
            value={recouvrement}
            onChange={(e) => setRecouvrement(e.target.value as Recouvrement)}
            helperText={
              recouvrement === 'DIRECTION'
                ? 'Bons déduits de la recette et recouvrés par le siège.'
                : 'Créance que le gérant recouvre lui-même à la station.'
            }
          >
            {(Object.keys(RECOUVREMENTS) as Recouvrement[]).map((r) => (
              <MenuItem key={r} value={r}>
                {RECOUVREMENTS[r]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Plafond d'encours (F)"
            value={plafond}
            onChange={(e) => setPlafond(e.target.value)}
            error={!plafondValide}
            helperText="Vide : sans plafond. Au-delà, le bon est refusé."
            slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
          />
          <TextField select label="État" value={actif ? 'oui' : 'non'} onChange={(e) => setActif(e.target.value === 'oui')}>
            <MenuItem value="oui">Actif</MenuItem>
            <MenuItem value="non">Inactif (plus de nouveaux bons)</MenuItem>
          </TextField>
          <TextField
            label="Observation"
            value={observation}
            onChange={(e) => setObservation(e.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !code.trim() || !nom.trim() || !plafondValide}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueOperation({ fiche, stationId, onFermer }: { fiche: FicheClient; stationId: string | null; onFermer: () => void }) {
  const rafraichir = useRafraichirClients()
  const queryClient = useQueryClient()
  const [station, setStation] = useState(stationId ?? '')
  const [type, setType] = useState<'REGLEMENT' | 'REPRISE'>('REGLEMENT')
  const [mode, setMode] = useState<ModeReglementClient>('VIREMENT')
  const [date, setDate] = useState(aujourdhui())
  const [montant, setMontant] = useState('')
  const [reference, setReference] = useState('')
  const [observation, setObservation] = useState('')
  const m = versNombre(montant)
  const { data: stations } = useStationsActives()

  const enregistrement = useMutation({
    mutationFn: () => {
      const corps: SaisieOperation = {
        stationId: station,
        type,
        mode: type === 'REGLEMENT' ? mode : null,
        date,
        montant: m!,
        reference: videVersNull(reference),
        observation: videVersNull(observation),
      }
      return api.post<FicheClient>(`/clients/${fiche.client.id}/operations`, corps)
    },
    onSuccess: (f) => {
      queryClient.setQueryData(cles.ficheClient(f.client.id), f)
      rafraichir()
      notifier(type === 'REPRISE' ? 'Créance reprise.' : 'Règlement enregistré.')
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Opération sur le compte de {fiche.client.nom}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField select label="Station" value={station} onChange={(e) => setStation(e.target.value)} required>
            {(stations ?? []).map((s) => (
              <MenuItem key={s.id} value={s.id}>
                {s.code} · {s.nom}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Opération" value={type} onChange={(e) => setType(e.target.value as 'REGLEMENT' | 'REPRISE')}>
            <MenuItem value="REGLEMENT">Règlement reçu par le siège</MenuItem>
            <MenuItem value="REPRISE">Reprise d'une créance existante (solde d'ouverture)</MenuItem>
          </TextField>
          {type === 'REGLEMENT' && (
            <TextField
              select
              label="Mode"
              value={mode}
              onChange={(e) => setMode(e.target.value as ModeReglementClient)}
              helperText="Les espèces remises au gérant se saisissent dans la journée de la station."
            >
              {MODES_SIEGE.map((x) => (
                <MenuItem key={x} value={x}>
                  {MODES_REGLEMENT_CLIENT[x]}
                </MenuItem>
              ))}
            </TextField>
          )}
          <Stack direction="row" spacing={2}>
            <TextField type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField
              label="Montant (F)"
              value={montant}
              onChange={(e) => setMontant(e.target.value)}
              required
              sx={{ flex: 1 }}
              slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
            />
          </Stack>
          <TextField label="Référence" value={reference} onChange={(e) => setReference(e.target.value)} slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField
            label="Observation"
            value={observation}
            onChange={(e) => setObservation(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => enregistrement.mutate()}
          disabled={enregistrement.isPending || !station || !date || m === null || m <= 0 || !Number.isInteger(m)}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Stations actives du périmètre, par code. */
function useStationsActives() {
  const r = useStations()
  return { ...r, data: (r.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code)) }
}

function Releve({ fiche, stationId }: { fiche: FicheClient; stationId: string }) {
  const [du, setDu] = useState('')
  const [au, setAu] = useState('')
  const releve = useReleve(fiche.client.id, stationId, du, au)
  const queryClient = useQueryClient()
  const rafraichir = useRafraichirClients()
  const suppression = useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => api.delete<FicheClient>(`/clients/operations/${id}?version=${version}`),
    onSuccess: (f) => {
      queryClient.setQueryData(cles.ficheClient(f.client.id), f)
      rafraichir()
      notifier('Opération retirée.')
    },
  })
  const r = releve.data
  return (
    <>
      <Stack direction="row" spacing={2} sx={{ my: 2 }}>
        <TextField type="date" label="Du" value={du} onChange={(e) => setDu(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField type="date" label="Au" value={au} onChange={(e) => setAu(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <AlerteErreur erreur={releve.error ?? suppression.error} />
      {releve.isLoading && <Chargement />}
      {r && (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Relevé de compte">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Opération</TableCell>
                <TableCell>Détail</TableCell>
                <TableCell align="right">Débit</TableCell>
                <TableCell align="right">Crédit</TableCell>
                <TableCell align="right">Solde</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              <TableRow>
                <TableCell colSpan={5}>Solde au début de la période</TableCell>
                <TableCell align="right">{formaterPrix(r.soldeDebut)}</TableCell>
                <TableCell />
              </TableRow>
              {r.lignes.map((l, i) => (
                <TableRow key={l.operationId ?? `${l.numero}-${i}`}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formaterDate(l.date)}</TableCell>
                  <TableCell>
                    {TYPES_LIGNE[l.type]}
                    {l.type === 'REGLEMENT' && l.mode && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {MODES_REGLEMENT_CLIENT[l.mode as ModeReglementClient] ?? l.mode}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {l.type === 'BON'
                      ? `n° ${l.numero} · ${formaterNombre(l.litres)} L ${l.produitCode ?? ''} à ${formaterPrix(l.prixUnitaire)}`
                      : [l.reference, l.observation].filter(Boolean).join(' · ')}
                    {l.type === 'BON' && l.reference && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {[l.reference, l.observation].filter(Boolean).join(' · ')}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">{l.debit !== null ? formaterPrix(l.debit) : ''}</TableCell>
                  <TableCell align="right">{l.credit !== null ? formaterPrix(l.credit) : ''}</TableCell>
                  <TableCell align="right">{formaterPrix(l.solde)}</TableCell>
                  <TableCell align="right">
                    {l.supprimable && l.operationId && (
                      <IconButton
                        size="small"
                        aria-label="Retirer l'opération"
                        disabled={suppression.isPending}
                        onClick={() => suppression.mutate({ id: l.operationId!, version: l.version! })}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              <TableRow>
                <TableCell colSpan={3} sx={{ fontWeight: 700 }}>
                  Solde dû en fin de période
                </TableCell>
                <TableCell align="right">{formaterPrix(r.totalDebit)}</TableCell>
                <TableCell align="right">{formaterPrix(r.totalCredit)}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }} data-testid="solde-releve">
                  {formaterPrix(r.soldeFin)}
                </TableCell>
                <TableCell />
              </TableRow>
            </TableBody>
          </Table>
        </Box>
      )}
    </>
  )
}

function Fiche({ id, onModifier }: { id: string; onModifier: (c: Client) => void }) {
  const requete = useFicheClient(id)
  const [stationId, setStationId] = useState<string | null>(null)
  const [operation, setOperation] = useState(false)
  const f = requete.data
  if (!f) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  const choisie = stationId ?? f.comptes[0]?.stationId ?? null
  return (
    <Paper variant="outlined" sx={{ p: 2 }} aria-label={`Fiche du client ${f.client.code}`}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, mb: 1 }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6">
            {f.client.nom} <PuceStatut actif={f.client.actif} />
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {f.client.code} · {RECOUVREMENTS[f.client.recouvrement]}
            {f.client.telephone ? ` · ${f.client.telephone}` : ''}
          </Typography>
        </Box>
        {f.peutGerer && (
          <>
            <Button startIcon={<EditIcon />} onClick={() => onModifier(f.client)}>
              Modifier
            </Button>
            <Button variant="outlined" startIcon={<PostAddIcon />} onClick={() => setOperation(true)}>
              Règlement ou reprise
            </Button>
          </>
        )}
      </Stack>
      <Typography variant="body2">
        Encours : <strong data-testid="encours-client">{formaterPrix(f.client.encours)}</strong>
        {f.client.plafond !== null && ` · plafond ${formaterPrix(f.client.plafond)} · disponible ${formaterPrix(f.client.disponible)}`}
      </Typography>
      {f.comptes.length === 0 ? (
        <Alert severity="info" sx={{ mt: 2 }}>
          Aucun mouvement dans les stations de votre périmètre.
        </Alert>
      ) : (
        <>
          <Table size="small" sx={{ mt: 2 }}>
            <TableHead>
              <TableRow>
                <TableCell>Station</TableCell>
                <TableCell align="right">Créance</TableCell>
                <TableCell>Dernier bon</TableCell>
                <TableCell>Dernier règlement</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {f.comptes.map((c) => (
                <TableRow
                  key={c.stationId}
                  hover
                  selected={c.stationId === choisie}
                  onClick={() => setStationId(c.stationId)}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell>
                    {c.stationCode} · {c.stationNom}
                  </TableCell>
                  <TableCell align="right">{formaterPrix(c.creance)}</TableCell>
                  <TableCell>{formaterDate(c.dernierBon)}</TableCell>
                  <TableCell>{formaterDate(c.dernierReglement)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {choisie && <Releve key={choisie} fiche={f} stationId={choisie} />}
        </>
      )}
      {operation && <DialogueOperation fiche={f} stationId={choisie} onFermer={() => setOperation(false)} />}
    </Paper>
  )
}

export function PageClients() {
  const clients = useClients()
  const peutGerer = usePermission('CLIENT_GERER')
  const [params, setParams] = useSearchParams()
  const [edition, setEdition] = useState<Client | 'nouveau' | null>(null)
  const [filtre, setFiltre] = useState('')
  const choisi = params.get('client')
  const liste = (clients.data ?? []).filter((c) => `${c.code} ${c.nom}`.toLowerCase().includes(filtre.trim().toLowerCase()))
  const total = (clients.data ?? []).reduce((s, c) => s + c.encours, 0)

  return (
    <>
      <EnTetePage
        titre="Clients en compte"
        sousTitre={`Encours total du réseau : ${formaterPrix(total)}`}
        actions={
          peutGerer && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEdition('nouveau')}>
              Nouveau client
            </Button>
          )
        }
      />
      <AlerteErreur erreur={clients.error} />
      {clients.isLoading && <Chargement />}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 2fr) minmax(0, 3fr)' } }}>
        <Paper variant="outlined">
          <Box sx={{ p: 1.5 }}>
            <TextField size="small" fullWidth label="Rechercher" value={filtre} onChange={(e) => setFiltre(e.target.value)} />
          </Box>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Clients">
              <TableHead>
                <TableRow>
                  <TableCell>Client</TableCell>
                  <TableCell>Recouvrement</TableCell>
                  <TableCell align="right">Encours</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {liste.map((c) => (
                  <TableRow
                    key={c.id}
                    hover
                    selected={c.id === choisi}
                    onClick={() => setParams({ client: c.id })}
                    sx={{ cursor: 'pointer', opacity: c.actif ? 1 : 0.6 }}
                    data-testid={`client-${c.code}`}
                  >
                    <TableCell>
                      {c.nom}
                      <Typography variant="caption" color="text.secondary" component="div">
                        {c.code}
                        {!c.actif && ' · inactif'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" variant="outlined" label={c.recouvrement === 'DIRECTION' ? 'Direction' : 'Gérant'} />
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {formaterPrix(c.encours)}
                      {c.plafond !== null && (
                        <Typography
                          variant="caption"
                          color={c.disponible === 0 ? 'error' : 'text.secondary'}
                          component="div"
                        >
                          plafond {formaterPrix(c.plafond)}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {liste.length === 0 && !clients.isLoading && (
                  <TableRow>
                    <TableCell colSpan={3}>
                      <Typography variant="body2" color="text.secondary">
                        Aucun client.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
        </Paper>
        {choisi ? (
          <Fiche key={choisi} id={choisi} onModifier={setEdition} />
        ) : (
          <Alert severity="info">Choisissez un client pour voir ses créances par station et son relevé.</Alert>
        )}
      </Box>
      {edition && <DialogueClient client={edition === 'nouveau' ? null : edition} onFermer={() => setEdition(null)} />}
    </>
  )
}
