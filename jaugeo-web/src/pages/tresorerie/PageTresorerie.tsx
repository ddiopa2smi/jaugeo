import AccountBalanceIcon from '@mui/icons-material/AccountBalance'
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import HistoryToggleOffIcon from '@mui/icons-material/HistoryToggleOff'
import LockClockIcon from '@mui/icons-material/LockClock'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined'
import SavingsIcon from '@mui/icons-material/Savings'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
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
import { useNavigate, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import {
  cles,
  useDepensesAValider,
  useLivreCaisse,
  useManquants,
  useRafraichirTresorerie,
  useStations,
  useVersements,
} from '../../api/requetes'
import type { DepenseAValider, LigneCaisse, LivreCaisse, Manquant, StatutVersement, TypeArrete, Versement } from '../../api/types'
import { usePermission, useSession } from '../../auth/session'
import { CarteIndicateur } from '../../shared/CarteIndicateur'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { BarreFiltres, CadreTableau, ChampRecherche, EnTeteTri, FiltrePuces, PuceEtat } from '../../shared/tableaux'
import { correspond, useLigneChoisie, useTri } from '../../shared/tri'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterDate, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { BoutonPiece, VoirPiece } from '../../shared/PieceJointe'
import { moisCourant } from '../../shared/stock'
import { aujourdhui, formaterEcart, MODES_REGLEMENT, STATUTS_MANQUANT, STATUTS_VERSEMENT, TYPES_ARRETE } from '../../shared/tresorerie'
import { DialogueBanques } from './DialogueBanques'
import { DialogueManquant } from './DialogueManquant'
import { DialogueVersement } from './DialogueVersement'
import { OngletRapprochement } from './OngletRapprochement'

const prix = (v: number | null | undefined) => (v === null || v === undefined ? '' : formaterPrix(v))

// ------------------------------------------------------------------ Versements

function DialogueVerification({ versement, onFermer }: { versement: Versement; onFermer: () => void }) {
  const rafraichir = useRafraichirTresorerie()
  const [motif, setMotif] = useState('')
  const verification = useMutation({
    mutationFn: (valide: boolean) =>
      api.post<Versement>(`/tresorerie/versements/${versement.id}/verification`, {
        version: versement.version,
        valide,
        motif: videVersNull(motif),
      }),
    onSuccess: (_v, valide) => {
      rafraichir()
      notifier(valide ? 'Versement vérifié.' : 'Versement rejeté : il ne compte plus dans la caisse du gérant.', valide ? 'success' : 'warning')
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Vérifier le versement</DialogTitle>
      <DialogContent>
        <Typography>
          {versement.stationCode} · {versement.banque} · BV {versement.referenceBv}
        </Typography>
        <Typography variant="h6" sx={{ my: 1 }}>
          {formaterPrix(versement.montant)}{' '}
          <Typography component="span" color="text.secondary">
            déposé le {formaterDate(versement.dateDepot)}
          </Typography>
        </Typography>
        {versement.piece ? (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
            <VoirPiece chemin={`/tresorerie/versements/${versement.id}/piece`} piece={versement.piece} />
            <Typography variant="body2">Comparez avec le relevé bancaire avant de valider.</Typography>
          </Stack>
        ) : (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Pas de photo du bordereau : le versement ne peut pas être vérifié.
          </Alert>
        )}
        <TextField
          label="Motif (obligatoire pour un rejet)"
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
        <AlerteErreur erreur={verification.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button color="error" onClick={() => verification.mutate(false)} disabled={verification.isPending || !motif.trim()}>
          Rejeter
        </Button>
        <Button
          variant="contained"
          color="success"
          onClick={() => verification.mutate(true)}
          disabled={verification.isPending || !versement.piece}
        >
          Vérifié
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function TableVersements({ versements, avecStation }: { versements: Versement[]; avecStation?: boolean }) {
  const rafraichir = useRafraichirTresorerie()
  const [edition, setEdition] = useState<Versement | null>(null)
  const [verification, setVerification] = useState<Versement | null>(null)
  const suppression = useMutation({
    mutationFn: (v: Versement) => api.delete<void>(`/tresorerie/versements/${v.id}?version=${v.version}`),
    onSuccess: () => {
      rafraichir()
      notifier('Versement retiré.')
    },
  })
  const jointure = useMutation({
    mutationFn: ({ v, fichier }: { v: Versement; fichier: File }) =>
      api.fichier<Versement>(`/tresorerie/versements/${v.id}/piece?version=${v.version}`, fichier),
    onSuccess: () => {
      rafraichir()
      notifier('Photo du bordereau jointe.')
    },
  })

  // Filtres : recherche libre, statut, banque et (liste du siège) station ; tri par colonne
  const [ligneChoisie, marquer] = useLigneChoisie()
  const [recherche, setRecherche] = useState('')
  const [statut, setStatut] = useState<StatutVersement | 'TOUS'>('TOUS')
  const [banque, setBanque] = useState('')
  const [station, setStation] = useState('')
  const tri = useTri<Versement, 'date' | 'station' | 'bordereau' | 'montant' | 'statut'>(
    {
      date: (v) => v.dateDepot,
      station: (v) => v.stationCode,
      bordereau: (v) => `${v.banque} ${v.referenceBv}`,
      montant: (v) => v.montant,
      statut: (v) => STATUTS_VERSEMENT[v.statut].libelle,
    },
    'date',
  )
  const statuts = (Object.keys(STATUTS_VERSEMENT) as StatutVersement[]).filter((s) => versements.some((v) => v.statut === s))
  const banques = [...new Set(versements.map((v) => v.banque))].sort()
  const stations = [...new Set(versements.map((v) => v.stationCode))].sort()
  const visibles = tri.trier(
    versements.filter(
      (v) =>
        (statut === 'TOUS' || v.statut === statut) &&
        (banque === '' || v.banque === banque) &&
        (station === '' || v.stationCode === station) &&
        correspond(recherche, v.referenceBv, v.banque, v.deposant, v.creePar, v.stationCode),
    ),
  )
  const total = visibles.reduce((t, v) => t + v.montant, 0)

  return (
    <>
      <AlerteErreur erreur={suppression.error ?? jointure.error} />
      <BarreFiltres
        resume={
          <span data-testid="resume-versements">
            {visibles.length} versement(s) · {formaterPrix(total)}
          </span>
        }
      >
        <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher un bordereau, un déposant" />
        {avecStation && stations.length > 1 && (
          <TextField select label="Station" value={station} onChange={(e) => setStation(e.target.value)} sx={{ width: { xs: '100%', sm: 150 } }}>
            <MenuItem value="">Toutes</MenuItem>
            {stations.map((s) => (
              <MenuItem key={s} value={s}>
                {s}
              </MenuItem>
            ))}
          </TextField>
        )}
        {banques.length > 1 && (
          <TextField select label="Banque" value={banque} onChange={(e) => setBanque(e.target.value)} sx={{ width: { xs: '100%', sm: 150 } }}>
            <MenuItem value="">Toutes</MenuItem>
            {banques.map((b) => (
              <MenuItem key={b} value={b}>
                {b}
              </MenuItem>
            ))}
          </TextField>
        )}
        {statuts.length > 1 && (
          <FiltrePuces
            libelle="Filtrer par statut"
            valeur={statut}
            onChange={setStatut}
            options={[
              { valeur: 'TOUS' as const, libelle: 'Tous', nombre: versements.length },
              ...statuts.map((s) => ({ valeur: s, libelle: STATUTS_VERSEMENT[s].libelle, nombre: versements.filter((v) => v.statut === s).length })),
            ]}
          />
        )}
      </BarreFiltres>
      {visibles.length === 0 && <Alert severity="info">Aucun versement ne correspond à ces filtres.</Alert>}
      <CadreTableau compact sx={{ display: visibles.length === 0 ? 'none' : undefined }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <EnTeteTri tri={tri} cle="date">
                Dépôt
              </EnTeteTri>
              {avecStation && (
                <EnTeteTri tri={tri} cle="station">
                  Station
                </EnTeteTri>
              )}
              <EnTeteTri tri={tri} cle="bordereau">
                Bordereau
              </EnTeteTri>
              <EnTeteTri tri={tri} cle="montant" align="right">
                Montant
              </EnTeteTri>
              <TableCell>Pièce</TableCell>
              <EnTeteTri tri={tri} cle="statut">
                Statut
              </EnTeteTri>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {visibles.map((v) => (
              <TableRow
                key={v.id}
                hover
                selected={ligneChoisie === v.id}
                onClick={() => marquer(v.id)}
                sx={{ cursor: 'pointer' }}
                data-testid={`versement-${v.referenceBv}`}
              >
                <TableCell>{formaterDate(v.dateDepot)}</TableCell>
                {avecStation && <TableCell>{v.stationCode}</TableCell>}
                <TableCell>
                  {v.banque} · {v.referenceBv}
                  <Typography variant="caption" color="text.secondary" component="div">
                    {[v.deposant, `saisi par ${v.creePar}`].filter(Boolean).join(' · ')}
                  </Typography>
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                  {formaterPrix(v.montant)}
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {v.piece && <VoirPiece chemin={`/tresorerie/versements/${v.id}/piece`} piece={v.piece} />}
                  {v.modifiable && (
                    <BoutonPiece libelle="Joindre" piece={v.piece} disabled={jointure.isPending} onFichier={(fichier) => jointure.mutate({ v, fichier })} />
                  )}
                </TableCell>
                <TableCell>
                  <PuceEtat ton={STATUTS_VERSEMENT[v.statut].couleur} libelle={STATUTS_VERSEMENT[v.statut].libelle} />
                  {(v.verifiePar || v.motifRejet) && (
                    <Typography variant="caption" color="text.secondary" component="div">
                      {v.verifiePar}
                      {v.motifRejet ? ` : ${v.motifRejet}` : ''}
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {v.verifiable && (
                    <Button size="small" onClick={() => setVerification(v)}>
                      Vérifier
                    </Button>
                  )}
                  {v.modifiable && (
                    <>
                      <IconButton size="small" aria-label={`Corriger le BV ${v.referenceBv}`} onClick={() => setEdition(v)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        aria-label={`Retirer le BV ${v.referenceBv}`}
                        onClick={() =>
                          confirmerPuis(
                            {
                              titre: 'Supprimer le versement ?',
                              message: `Le versement BV ${v.referenceBv} de ${formaterPrix(v.montant)} (${v.banque}) sera supprimé.`,
                            },
                            () => suppression.mutate(v),
                          )
                        }
                        disabled={suppression.isPending}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CadreTableau>
      {edition && (
        <DialogueVersement stationId={edition.stationId} stationCode={edition.stationCode} versement={edition} onFermer={() => setEdition(null)} />
      )}
      {verification && <DialogueVerification versement={verification} onFermer={() => setVerification(null)} />}
    </>
  )
}

// ------------------------------------------------------------------ Caisse du gérant

function DialogueArrete({ livre, onFermer }: { livre: LivreCaisse; onFermer: () => void }) {
  const rafraichir = useRafraichirTresorerie()
  const derniere = [...livre.lignes].reverse().find((l) => l.statutJournee && l.statutJournee !== 'BROUILLON')
  const [date, setDate] = useState(derniere?.date ?? livre.lignes[livre.lignes.length - 1]?.date ?? aujourdhui())
  const [type, setType] = useState<TypeArrete>('CONTROLE')
  const [especes, setEspeces] = useState('')
  const [responsable, setResponsable] = useState('')
  const [observation, setObservation] = useState('')
  const ligne = livre.lignes.find((l) => l.date === date)
  const comptees = versNombre(especes)
  const theorique = type === 'CONTROLE' ? (ligne?.soldeTheorique ?? null) : null
  const ecart = comptees !== null && theorique !== null ? comptees - theorique : null

  const arrete = useMutation({
    mutationFn: () =>
      api.post(`/stations/${livre.stationId}/arretes`, {
        date,
        type,
        especesComptees: comptees,
        responsable: videVersNull(responsable),
        observation: videVersNull(observation),
      }),
    onSuccess: () => {
      rafraichir()
      notifier(
        ecart !== null && ecart < 0 ? `Caisse arrêtée. Manquant de ${formaterPrix(-ecart)} à la charge du gérant.` : 'Caisse arrêtée.',
        ecart !== null && ecart < 0 ? 'warning' : 'success',
      )
      onFermer()
    },
  })

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Arrêter la caisse · {livre.stationCode}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Les espèces comptées deviennent le solde du gérant. Les versements et journées antérieurs ne pourront plus être modifiés.
        </Typography>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select label="Type" value={type} onChange={(e) => setType(e.target.value as TypeArrete)} sx={{ minWidth: 200 }}>
              {Object.entries(TYPES_ARRETE).map(([v, l]) => (
                <MenuItem key={v} value={v}>
                  {l}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              type="date"
              label="En fin de journée du"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: aujourdhui() } }}
            />
          </Stack>
          <TextField
            label="Espèces comptées (F)"
            value={especes}
            onChange={(e) => setEspeces(e.target.value)}
            required
            autoFocus
            slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off' } }}
            helperText={
              type === 'REPRISE'
                ? 'Solde de départ de la caisse (reprise de l’existant).'
                : theorique !== null
                  ? `Solde théorique : ${formaterPrix(theorique)}`
                  : 'Solde théorique calculé à l’enregistrement.'
            }
          />
          {ecart !== null && (
            <Alert severity={ecart === 0 ? 'success' : ecart < 0 ? 'error' : 'warning'} data-testid="apercu-ecart">
              Écart {formaterEcart(ecart, formaterPrix)}
              {ecart < 0 ? ' : un manquant sera ouvert au nom du gérant.' : ecart > 0 ? ' : excédent de caisse.' : ' : caisse juste.'}
            </Alert>
          )}
          <TextField label="Gérant / responsable de caisse" value={responsable} onChange={(e) => setResponsable(e.target.value)} slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField
            label="Observation"
            value={observation}
            onChange={(e) => setObservation(e.target.value)}
            required={ecart !== null && ecart !== 0}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={arrete.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => arrete.mutate()}
          disabled={arrete.isPending || comptees === null || comptees < 0 || !date || (ecart !== null && ecart !== 0 && !observation.trim())}
        >
          Arrêter la caisse
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialoguePlafond({ livre, onFermer }: { livre: LivreCaisse; onFermer: () => void }) {
  const rafraichir = useRafraichirTresorerie()
  const [plafond, setPlafond] = useState(livre.plafondEspeces === null ? '' : String(livre.plafondEspeces))
  const valeur = versNombre(plafond)
  const enregistrement = useMutation({
    mutationFn: () => api.put(`/stations/${livre.stationId}/plafond-especes`, { plafond: valeur }),
    onSuccess: () => {
      rafraichir()
      notifier(valeur === null ? 'Plafond d’espèces retiré.' : `Plafond d’espèces fixé à ${formaterPrix(valeur)}.`)
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Plafond d’espèces · {livre.stationCode}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Au-delà de ce montant d’espèces non versées, une alerte s’affiche sur la journée, le tableau de bord et la vue Direction.
          Laissez vide pour retirer le plafond.
        </Typography>
        <TextField
          autoFocus
          fullWidth
          label="Plafond (F CFA)"
          value={plafond}
          onChange={(e) => setPlafond(e.target.value)}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
        />
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={enregistrement.isPending || (valeur !== null && valeur <= 0)}>
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}

type FiltreJours = 'TOUS' | 'VERSES' | 'ARRETES' | 'SAISIE'

function Caisse() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const peutVerser = usePermission('VERSEMENT_SAISIR')
  const peutControler = usePermission('CAISSE_CONTROLER')
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const mois = params.get('mois') ?? moisCourant()
  const livre = useLivreCaisse(stationId, mois)
  const [ligneChoisie, marquer] = useLigneChoisie()
  const versements = useVersements(stationId, mois)
  const [versement, setVersement] = useState(false)
  const [arrete, setArrete] = useState(false)
  const [plafond, setPlafond] = useState(false)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    if (stationId && !p.get('station')) p.set('station', stationId)
    setParams(p)
  }
  const l = livre.data
  const station = actives.find((s) => s.id === stationId)
  const depasse = l !== undefined && l.plafondEspeces !== null && l.soldeFin > l.plafondEspeces
  const avecEncaissements = (l?.totalEncaissements ?? 0) > 0
  const avecCartes = (l?.totalCartes ?? 0) > 0
  // Livre de caisse : filtre sur les jours et tri par colonne
  const [jours, setJours] = useState<FiltreJours>('TOUS')
  const tri = useTri<LigneCaisse, 'date' | 'ventes' | 'aVerser' | 'verse' | 'solde'>(
    { date: (x) => x.date, ventes: (x) => x.ventes, aVerser: (x) => x.montantAVerser, verse: (x) => x.versements, solde: (x) => x.solde },
    'date',
  )
  const filtresJours: Record<FiltreJours, { libelle: string; garde: (x: LigneCaisse) => boolean }> = {
    TOUS: { libelle: 'Tous les jours', garde: () => true },
    VERSES: { libelle: 'Avec versement', garde: (x) => x.versements > 0 },
    ARRETES: { libelle: 'Avec arrêté', garde: (x) => x.arrete !== null },
    SAISIE: { libelle: 'En saisie', garde: (x) => x.statutJournee === 'BROUILLON' },
  }
  const lignes = l ? tri.trier(l.lignes.filter(filtresJours[jours].garde)) : []

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        {actives.length > 1 && (
          <TextField select label="Station" value={stationId ?? ''} onChange={(e) => changer('station', e.target.value)} sx={{ minWidth: 280 }}>
            {actives.map((s) => (
              <MenuItem key={s.id} value={s.id}>
                {s.code} · {s.nom}
              </MenuItem>
            ))}
          </TextField>
        )}
        <TextField
          type="month"
          label="Mois"
          value={mois}
          onChange={(e) => e.target.value && changer('mois', e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Box sx={{ flexGrow: 1 }} />
        {station && peutVerser && (
          <Button variant="contained" startIcon={<AccountBalanceIcon />} onClick={() => setVersement(true)} sx={{ flexShrink: 0 }}>
            Enregistrer un versement
          </Button>
        )}
        {l?.peutArreter && (
          <Button variant="outlined" startIcon={<LockClockIcon />} onClick={() => setArrete(true)} sx={{ flexShrink: 0 }}>
            Arrêter la caisse
          </Button>
        )}
        {l && peutControler && (
          <Button variant="outlined" startIcon={<SavingsIcon />} onClick={() => setPlafond(true)} sx={{ flexShrink: 0 }}>
            Plafond d’espèces
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={stations.error ?? livre.error ?? versements.error} />
      {!stationId && !stations.isLoading && <Alert severity="info">Choisissez une station.</Alert>}
      {livre.isLoading && <Chargement />}
      {l && (
        <>
          <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(5, 1fr)' }, mb: 2.5 }}>
            <CarteIndicateur icone={<HistoryToggleOffIcon />} libelle="Solde au début du mois" valeur={formaterPrix(l.soldeDebut)} sous="espèces non versées reportées" />
            <CarteIndicateur icone={<PaymentsOutlinedIcon />} libelle="Montant à verser du mois" valeur={formaterPrix(l.totalMontants)} sous="journées clôturées" />
            <CarteIndicateur icone={<AccountBalanceIcon />} libelle="Versements" valeur={formaterPrix(l.totalVersements)} ton="success" sous="déposés en banque" />
            <CarteIndicateur
              icone={<BalanceOutlinedIcon />}
              libelle="Écarts aux arrêtés"
              valeur={formaterEcart(l.totalEcarts, formaterPrix)}
              ton={l.totalEcarts < 0 ? 'error' : 'primary'}
              sous={l.totalEcarts < 0 ? 'manquant à la charge du gérant' : 'caisse comptée'}
            />
            <Box sx={{ gridColumn: { xs: '1 / -1', md: 'auto' }, display: 'flex', '& > *': { flexGrow: 1 } }}>
              <CarteIndicateur
                icone={<AccountBalanceWalletOutlinedIcon />}
                libelle="Solde non versé"
                valeur={formaterPrix(l.soldeFin)}
                testid="solde-mois"
                accent
                ton={depasse ? 'warning' : 'primary'}
                sous={
                  l.plafondEspeces === null ? 'espèces détenues par le gérant' : `${depasse ? 'Au-delà du plafond' : 'Plafond'} : ${formaterPrix(l.plafondEspeces)}`
                }
              />
            </Box>
          </Box>
          {avecEncaissements && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }} data-testid="encaissements-mois">
              Encaissés par Wave, Orange Money et carte (hors espèces) : {formaterPrix(l.totalEncaissements)}
            </Typography>
          )}
          {avecCartes && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }} data-testid="cartes-mois">
              Pleins payés par carte carburant prépayée : {formaterPrix(l.totalCartes)}
            </Typography>
          )}
          {l.joursNonClotures > 0 && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {l.joursNonClotures} journée(s) du mois non clôturée(s) : leur recette n’entre dans la caisse qu’à la clôture.
            </Alert>
          )}
          <BarreFiltres resume={`${lignes.length} jour(s) sur ${l.lignes.length}`}>
            <Typography variant="h6" component="h2">
              Livre de caisse
            </Typography>
            <FiltrePuces
              libelle="Filtrer les jours"
              valeur={jours}
              onChange={setJours}
              options={(Object.keys(filtresJours) as FiltreJours[])
                .map((f) => ({ valeur: f, libelle: filtresJours[f].libelle, nombre: l.lignes.filter(filtresJours[f].garde).length }))
                .filter((o) => o.valeur === 'TOUS' || o.valeur === jours || o.nombre > 0)}
            />
          </BarreFiltres>
          {lignes.length === 0 && (
            <Alert severity="info" sx={{ mb: 3 }}>
              Aucun jour ne correspond à ce filtre.
            </Alert>
          )}
          <CadreTableau
            compact
            sx={{ mb: 3, display: lignes.length === 0 ? 'none' : undefined, '& .MuiTableCell-root': { fontVariantNumeric: 'tabular-nums' } }}
          >
            <Table size="small">
              <TableHead>
                <TableRow>
                  <EnTeteTri tri={tri} cle="date">
                    Date
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="ventes" align="right">
                    Ventes
                  </EnTeteTri>
                  <TableCell align="right">Bons</TableCell>
                  <TableCell align="right">Dépenses</TableCell>
                  <TableCell align="right">Compléments</TableCell>
                  {avecEncaissements && <TableCell align="right">Wave, OM, carte</TableCell>}
                  {avecCartes && <TableCell align="right">Cartes carburant</TableCell>}
                  <EnTeteTri tri={tri} cle="aVerser" align="right">
                    À verser
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="verse" align="right">
                    Versé
                  </EnTeteTri>
                  <TableCell align="right">Arrêté</TableCell>
                  <EnTeteTri tri={tri} cle="solde" align="right">
                    Solde non versé
                  </EnTeteTri>
                </TableRow>
              </TableHead>
              <TableBody>
                {lignes.map((x) => (
                  <TableRow
                    key={x.date}
                    hover
                    selected={ligneChoisie === x.date}
                    sx={{ cursor: 'pointer' }}
                    onClick={() => {
                      // Le jour cliqué reste marqué au retour de la journée ouverte
                      marquer(x.date)
                      if (x.journeeId) navigate(`/journees/${x.journeeId}`)
                    }}
                    data-testid={`caisse-${x.date}`}
                  >
                    <TableCell>
                      {formaterDate(x.date)}
                      {x.statutJournee === 'BROUILLON' && (
                        <Typography variant="caption" color="text.secondary" component="div">
                          en saisie
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">{prix(x.ventes)}</TableCell>
                    <TableCell align="right">{x.bons ? prix(x.bons) : ''}</TableCell>
                    <TableCell align="right">{x.depenses ? prix(x.depenses) : ''}</TableCell>
                    <TableCell align="right">{x.complements ? prix(x.complements) : ''}</TableCell>
                    {avecEncaissements && <TableCell align="right">{x.encaissements ? prix(x.encaissements) : ''}</TableCell>}
                    {avecCartes && <TableCell align="right">{x.cartes ? prix(x.cartes) : ''}</TableCell>}
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {prix(x.montantAVerser)}
                    </TableCell>
                    <TableCell align="right" sx={{ color: 'success.dark', fontWeight: x.versements ? 600 : undefined }}>
                      {x.versements ? prix(x.versements) : ''}
                    </TableCell>
                    <TableCell align="right">
                      {x.arrete && (
                        <PuceEtat
                          ton={x.ecart !== null && x.ecart < 0 ? 'error' : 'default'}
                          libelle={x.arrete.type === 'REPRISE' ? 'Reprise' : `Écart ${formaterEcart(x.ecart, formaterPrix)}`}
                        />
                      )}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                      {formaterPrix(x.solde)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CadreTableau>
        </>
      )}
      {stationId && versements.data && (
        <>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Versements du mois
          </Typography>
          {versements.data.length === 0 ? (
            <Alert severity="info">Aucun versement enregistré sur ce mois.</Alert>
          ) : (
            <TableVersements versements={versements.data} />
          )}
        </>
      )}
      {versement && station && (
        <DialogueVersement stationId={station.id} stationCode={station.code} montantSuggere={l?.soldeFin} onFermer={() => setVersement(false)} />
      )}
      {arrete && l && <DialogueArrete livre={l} onFermer={() => setArrete(false)} />}
      {plafond && l && <DialoguePlafond livre={l} onFermer={() => setPlafond(false)} />}
    </>
  )
}

function AVerifier() {
  const liste = useVersements(null, '')
  return (
    <>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="success">Aucun versement en attente de vérification.</Alert>}
      {liste.data && liste.data.length > 0 && <TableVersements versements={liste.data} avecStation />}
    </>
  )
}

// ------------------------------------------------------------------ Dépenses à valider

function DepensesAValider() {
  const liste = useDepensesAValider()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [cible, setCible] = useState<{ depense: DepenseAValider; autoriser: boolean } | null>(null)
  const [motif, setMotif] = useState('')
  const [recherche, setRecherche] = useState('')
  const [station, setStation] = useState('')
  const [piece, setPiece] = useState<'TOUS' | 'ABSENT'>('TOUS')
  const [ligneChoisie, marquer] = useLigneChoisie()
  const tri = useTri<DepenseAValider, 'journee' | 'depense' | 'montant' | 'plafond'>(
    { journee: (d) => `${d.date} ${d.stationCode}`, depense: (d) => d.libelle, montant: (d) => d.montant, plafond: (d) => d.plafond },
    'journee',
  )
  const stations = [...new Set((liste.data ?? []).map((d) => d.stationCode))].sort()
  const visibles = tri.trier(
    (liste.data ?? []).filter(
      (d) =>
        (station === '' || d.stationCode === station) &&
        (piece === 'TOUS' || !d.justificatif) &&
        correspond(recherche, d.libelle, d.natureLibelle, d.beneficiaire, d.creePar, d.stationCode),
    ),
  )
  const decision = useMutation({
    mutationFn: ({ depense, autoriser }: { depense: DepenseAValider; autoriser: boolean }) =>
      api.post(`/journees/${depense.journeeId}/depenses/${depense.id}/decision`, {
        version: depense.journeeVersion,
        autoriser,
        motif: videVersNull(motif),
      }),
    onSuccess: (_r, { depense, autoriser }) => {
      queryClient.invalidateQueries({ queryKey: cles.depensesAValider })
      queryClient.invalidateQueries({ queryKey: cles.journee(depense.journeeId) })
      notifier(autoriser ? 'Dépense autorisée.' : 'Dépense refusée.')
      setCible(null)
      setMotif('')
    },
  })

  return (
    <>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="success">Aucune dépense au-delà du plafond en attente.</Alert>}
      {liste.data && liste.data.length > 0 && (
        <BarreFiltres resume={`${visibles.length} dépense(s) · ${formaterPrix(visibles.reduce((t, d) => t + d.montant, 0))}`}>
          <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher une dépense, un bénéficiaire" />
          {stations.length > 1 && (
            <TextField select label="Station" value={station} onChange={(e) => setStation(e.target.value)} sx={{ width: { xs: '100%', sm: 150 } }}>
              <MenuItem value="">Toutes</MenuItem>
              {stations.map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
          )}
          <FiltrePuces
            libelle="Filtrer par justificatif"
            valeur={piece}
            onChange={setPiece}
            options={[
              { valeur: 'TOUS', libelle: 'Toutes' },
              { valeur: 'ABSENT', libelle: 'Sans justificatif', nombre: liste.data.filter((d) => !d.justificatif).length },
            ]}
          />
        </BarreFiltres>
      )}
      {liste.data && liste.data.length > 0 && visibles.length === 0 && <Alert severity="info">Aucune dépense ne correspond à ces filtres.</Alert>}
      {visibles.length > 0 && (
        <CadreTableau compact>
          <Table size="small">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="journee">
                  Journée
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="depense">
                  Dépense
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="montant" align="right">
                  Montant
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="plafond" align="right">
                  Plafond
                </EnTeteTri>
                <TableCell>Justificatif</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((d) => (
                <TableRow key={d.id} hover selected={ligneChoisie === d.id} onClick={() => marquer(d.id)} sx={{ cursor: 'pointer' }}>
                  <TableCell sx={{ cursor: 'pointer' }} onClick={() => navigate(`/journees/${d.journeeId}`)}>
                    {d.stationCode} · {formaterDate(d.date)}
                  </TableCell>
                  <TableCell>
                    {d.libelle}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {[d.natureLibelle, d.beneficiaire, `saisie par ${d.creePar}`].filter(Boolean).join(' · ')}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {formaterPrix(d.montant)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                    {prix(d.plafond)}
                  </TableCell>
                  <TableCell>{d.justificatif ? <PuceEtat ton="success" libelle="Joint" /> : <PuceEtat ton="warning" libelle="Absent" />}</TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {d.decidable && (
                      <>
                        <Button size="small" color="error" onClick={() => setCible({ depense: d, autoriser: false })}>
                          Refuser
                        </Button>
                        <Button size="small" color="success" onClick={() => setCible({ depense: d, autoriser: true })}>
                          Autoriser
                        </Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
      )}
      <Dialog open={!!cible} onClose={() => setCible(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{cible?.autoriser ? 'Autoriser la dépense' : 'Refuser la dépense'}</DialogTitle>
        <DialogContent>
          {cible && (
            <Typography sx={{ mb: 2 }}>
              {cible.depense.stationCode} · {cible.depense.libelle} : {formaterPrix(cible.depense.montant)} (plafond {prix(cible.depense.plafond)})
            </Typography>
          )}
          <TextField
            label={cible?.autoriser ? 'Motif (facultatif)' : 'Motif du refus'}
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            fullWidth
            multiline
            minRows={2}
            autoFocus
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <AlerteErreur erreur={decision.error} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCible(null)}>Annuler</Button>
          <Button
            variant="contained"
            color={cible?.autoriser ? 'success' : 'error'}
            onClick={() => cible && decision.mutate(cible)}
            disabled={decision.isPending || (!cible?.autoriser && !motif.trim())}
          >
            {cible?.autoriser ? 'Autoriser' : 'Refuser'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

// ------------------------------------------------------------------ Manquants

function Manquants() {
  const [statut, setStatut] = useState('OUVERT')
  const liste = useManquants(statut)
  const [aRegler, setARegler] = useState<Manquant | null>(null)
  const [recherche, setRecherche] = useState('')
  const [retard, setRetard] = useState<'TOUS' | 'RETARD'>('TOUS')
  const [ligneChoisie, marquer] = useLigneChoisie()
  const tri = useTri<Manquant, 'date' | 'station' | 'responsable' | 'montant' | 'reste'>(
    { date: (m) => m.date, station: (m) => m.stationCode, responsable: (m) => m.responsable, montant: (m) => m.montant, reste: (m) => m.reste },
    'date',
    'desc',
  )
  const tous = liste.data ?? []
  const enRetard = tous.filter((m) => m.retard > 0)
  const visibles = tri.trier(
    tous.filter((m) => (retard === 'TOUS' || m.retard > 0) && correspond(recherche, m.stationCode, m.responsable, m.observation)),
  )
  return (
    <>
      {tous.length > 0 && (
        <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)' }, mb: 2.5 }}>
          <CarteIndicateur
            icone={<ReportProblemOutlinedIcon />}
            libelle={statut === 'OUVERT' ? 'Manquants ouverts' : statut === 'SOLDE' ? 'Manquants soldés' : 'Manquants'}
            valeur={tous.length}
            sous={`pour ${formaterPrix(tous.reduce((t, m) => t + m.montant, 0))} constatés`}
            ton={statut === 'SOLDE' ? 'success' : 'primary'}
          />
          <CarteIndicateur
            icone={<AccountBalanceWalletOutlinedIcon />}
            libelle="Reste dû par les gérants"
            valeur={formaterPrix(tous.reduce((t, m) => t + m.reste, 0))}
            testid="reste-du-total"
            ton="error"
            accent={tous.some((m) => m.reste > 0)}
          />
          <Box sx={{ gridColumn: { xs: '1 / -1', md: 'auto' }, display: 'flex', '& > *': { flexGrow: 1 } }}>
            <CarteIndicateur
              icone={<ScheduleOutlinedIcon />}
              libelle="Échéances en retard"
              valeur={enRetard.length}
              sous={enRetard.length > 0 ? 'manquant(s) à relancer' : 'aucun retard'}
              ton={enRetard.length > 0 ? 'warning' : 'success'}
            />
          </Box>
        </Box>
      )}
      <BarreFiltres resume={tous.length > 0 ? `${visibles.length} manquant(s) sur ${tous.length}` : undefined}>
        <TextField select label="Statut" value={statut} onChange={(e) => setStatut(e.target.value)} sx={{ width: { xs: '100%', sm: 160 } }}>
          <MenuItem value="OUVERT">Ouverts</MenuItem>
          <MenuItem value="SOLDE">Soldés</MenuItem>
          <MenuItem value="">Tous</MenuItem>
        </TextField>
        {tous.length > 0 && <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher une station, un responsable" />}
        {enRetard.length > 0 && (
          <FiltrePuces
            libelle="Filtrer par retard"
            valeur={retard}
            onChange={setRetard}
            options={[
              { valeur: 'TOUS', libelle: 'Tous' },
              { valeur: 'RETARD', libelle: 'En retard', nombre: enRetard.length },
            ]}
          />
        )}
      </BarreFiltres>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="success">Aucun manquant {statut === 'OUVERT' ? 'ouvert' : ''}.</Alert>}
      {tous.length > 0 && visibles.length === 0 && <Alert severity="info">Aucun manquant ne correspond à ces filtres.</Alert>}
      {visibles.length > 0 && (
        <CadreTableau compact>
          <Table size="small">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="date">
                  Arrêté du
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="station">
                  Station
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="responsable">
                  Responsable
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="montant" align="right">
                  Montant
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="reste" align="right">
                  Reste dû
                </EnTeteTri>
                <TableCell>Statut</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((m) => (
                <TableRow key={m.id} hover selected={ligneChoisie === m.id} onClick={() => marquer(m.id)} sx={{ cursor: 'pointer' }}>
                  <TableCell>{formaterDate(m.date)}</TableCell>
                  <TableCell>{m.stationCode}</TableCell>
                  <TableCell>
                    {m.responsable ?? '—'}
                    {m.observation && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {m.observation}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {formaterPrix(m.montant)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', fontWeight: 600 }} data-testid={`reste-${m.id}`}>
                    {formaterPrix(m.reste)}
                    {m.prochaineEcheance && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        échéance du {formaterDate(m.prochaineEcheance)}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" sx={{ gap: 0.5, flexWrap: 'wrap' }}>
                      {m.retard > 0 && <PuceEtat ton="warning" libelle="Échéance en retard" />}
                      <PuceEtat
                        ton={STATUTS_MANQUANT[m.statut].couleur}
                        libelle={
                          m.statut === 'SOLDE' && m.modeReglement
                            ? `${MODES_REGLEMENT[m.modeReglement]} · ${formaterDate(m.regleLe)}`
                            : STATUTS_MANQUANT[m.statut].libelle
                        }
                      />
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => setARegler(m)}>
                      {m.reglable ? 'Gérer' : 'Détail'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
      )}
      {aRegler && <DialogueManquant manquant={aRegler} onFermer={() => setARegler(null)} />}
    </>
  )
}

// ------------------------------------------------------------------ Page

export function PageTresorerie() {
  const [params, setParams] = useSearchParams()
  const verifie = usePermission('VERSEMENT_VALIDER')
  const decide = usePermission('DEPENSE_VALIDER')
  const controle = usePermission('CAISSE_CONTROLER')
  const gereBanques = usePermission('REFERENTIEL_GERER')
  const [banques, setBanques] = useState(false)
  const reseau =useSession((e) => e.utilisateur?.perimetre === 'RESEAU')
  const onglets = [
    { valeur: 'caisse', libelle: 'Caisse du gérant' },
    ...(verifie ? [{ valeur: 'verification', libelle: 'Versements à vérifier' }] : []),
    ...(decide ? [{ valeur: 'depenses', libelle: 'Dépenses à valider' }] : []),
    { valeur: 'manquants', libelle: 'Manquants' },
    ...(reseau && (verifie || controle) ? [{ valeur: 'rapprochement', libelle: 'Rapprochement' }] : []),
  ]
  const demande = params.get('onglet')
  const onglet = onglets.some((o) => o.valeur === demande) ? demande! : 'caisse'

  return (
    <>
      <EnTetePage
        titre="Trésorerie"
        sousTitre="Montant à verser jour par jour, versements bancaires, solde non versé du gérant, arrêtés de caisse et manquants."
        actions={
          gereBanques ? (
            <Button variant="outlined" startIcon={<AccountBalanceIcon />} onClick={() => setBanques(true)}>
              Banques
            </Button>
          ) : undefined
        }
      />
      {banques && <DialogueBanques onFermer={() => setBanques(false)} />}
      <Tabs
        value={onglet}
        onChange={(_e, v) => {
          const p = new URLSearchParams(params)
          p.set('onglet', v)
          setParams(p)
        }}
        variant="scrollable"
        allowScrollButtonsMobile
        sx={{ mb: 2 }}
      >
        {onglets.map((o) => (
          <Tab key={o.valeur} value={o.valeur} label={o.libelle} />
        ))}
      </Tabs>
      {onglet === 'caisse' && <Caisse />}
      {onglet === 'verification' && <AVerifier />}
      {onglet === 'depenses' && <DepensesAValider />}
      {onglet === 'manquants' && <Manquants />}
      {onglet === 'rapprochement' && <OngletRapprochement />}
    </>
  )
}
