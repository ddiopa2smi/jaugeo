import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import AddCardIcon from '@mui/icons-material/AddCard'
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined'
import CreditCardOutlinedIcon from '@mui/icons-material/CreditCardOutlined'
import PrintIcon from '@mui/icons-material/Print'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
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
import { api } from '../../api/client'
import { cles, useCarte, useCartes } from '../../api/requetes'
import type { CarteCarburant, CarteDetail, ModeRecharge, StatutCarte } from '../../api/types'
import { usePermission } from '../../auth/session'
import { CarteIndicateur } from '../../shared/CarteIndicateur'
import { MODES_RECHARGE, STATUTS_CARTE } from '../../shared/clients'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterDate, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { imprimerCarte } from '../../shared/impressionCarte'
import { notifier } from '../../shared/notifications'
import { BarreFiltres, CadreTableau, ChampRecherche, EnTeteTri, FiltrePuces, PuceEtat } from '../../shared/tableaux'
import { correspond, useTri } from '../../shared/tri'
import { aujourdhui } from '../../shared/tresorerie'

const COULEUR_STATUT: Record<StatutCarte, 'success' | 'warning' | 'default'> = { ACTIVE: 'success', BLOQUEE: 'warning', ANNULEE: 'default' }

function useMajCarte() {
  const queryClient = useQueryClient()
  return (d: CarteDetail) => {
    queryClient.setQueryData(cles.carte(d.carte.id), d)
    queryClient.invalidateQueries({ queryKey: cles.cartes })
    queryClient.invalidateQueries({ queryKey: cles.ficheClient(d.carte.clientId) })
  }
}

export function DialogueEmissionCarte({ clientId, clientNom, onFermer }: { clientId: string; clientNom: string; onFermer: () => void }) {
  const maj = useMajCarte()
  const [porteur, setPorteur] = useState('')
  const [immatriculation, setImmatriculation] = useState('')
  const emission = useMutation({
    mutationFn: () =>
      api.post<CarteDetail>(`/clients/${clientId}/cartes`, { porteur: porteur.trim(), immatriculation: videVersNull(immatriculation) }),
    onSuccess: (d) => {
      maj(d)
      notifier(`Carte ${d.carte.numero} émise. Rechargez-la avant de la remettre.`)
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Nouvelle carte pour {clientNom}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField label="Porteur" value={porteur} onChange={(e) => setPorteur(e.target.value)} required slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField
            label="Véhicule ou pirogue"
            value={immatriculation}
            onChange={(e) => setImmatriculation(e.target.value)}
            helperText="Facultatif : imprimé sur la carte."
            slotProps={{ htmlInput: { maxLength: 30 } }}
          />
        </Stack>
        <AlerteErreur erreur={emission.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => emission.mutate()} disabled={emission.isPending || !porteur.trim()}>
          Émettre
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueRecharge({ carte, onFermer }: { carte: CarteCarburant; onFermer: () => void }) {
  const maj = useMajCarte()
  const [date, setDate] = useState(aujourdhui())
  const [mode, setMode] = useState<ModeRecharge>('VIREMENT')
  const [montant, setMontant] = useState('')
  const [reference, setReference] = useState('')
  const m = versNombre(montant)
  const recharge = useMutation({
    mutationFn: () =>
      api.post<CarteDetail>(`/clients/cartes/${carte.id}/recharges`, {
        version: carte.version,
        date,
        mode,
        montant: m,
        reference: videVersNull(reference),
      }),
    onSuccess: (d) => {
      maj(d)
      notifier(`Carte rechargée : solde ${formaterPrix(d.carte.solde)}.`)
      onFermer()
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>Recharger la carte {carte.numero}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField select label="Paiement reçu par le siège" value={mode} onChange={(e) => setMode(e.target.value as ModeRecharge)}>
            {(Object.keys(MODES_RECHARGE) as ModeRecharge[]).map((x) => (
              <MenuItem key={x} value={x}>
                {MODES_RECHARGE[x]}
              </MenuItem>
            ))}
          </TextField>
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
        </Stack>
        <AlerteErreur erreur={recharge.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          onClick={() => recharge.mutate()}
          disabled={recharge.isPending || !date || m === null || m <= 0 || !Number.isInteger(m)}
        >
          Recharger
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Détail d'une carte : solde, mouvements, recharge, blocage ou annulation, impression. */
export function DialogueCarte({ id, onFermer }: { id: string; onFermer: () => void }) {
  const requete = useCarte(id)
  const maj = useMajCarte()
  const [recharge, setRecharge] = useState(false)
  const d = requete.data
  const changement = useMutation({
    mutationFn: (statut: StatutCarte) =>
      api.put<CarteDetail>(`/clients/cartes/${id}`, {
        porteur: d!.carte.porteur,
        immatriculation: d!.carte.immatriculation,
        statut,
        version: d!.carte.version,
      }),
    onSuccess: (r) => {
      maj(r)
      notifier(`Carte ${STATUTS_CARTE[r.carte.statut].toLowerCase()}.`)
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>{d ? `Carte ${d.carte.numero}` : 'Carte'}</DialogTitle>
      <DialogContent>
        {!d ? (
          requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
        ) : (
          <>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' }, mb: 2 }}>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h6">
                  {d.carte.porteur} <Chip size="small" color={COULEUR_STATUT[d.carte.statut]} label={STATUTS_CARTE[d.carte.statut]} />
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {d.carte.clientNom} ({d.carte.clientCode}){d.carte.immatriculation ? ` · ${d.carte.immatriculation}` : ''}
                </Typography>
              </Box>
              <Typography variant="h5" data-testid="solde-carte">
                {formaterPrix(d.carte.solde)}
              </Typography>
            </Stack>
            <AlerteErreur erreur={changement.error} />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Mouvements de la carte">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Mouvement</TableCell>
                    <TableCell>Détail</TableCell>
                    <TableCell align="right">Montant</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.mouvements.map((m) => (
                    <TableRow key={m.id}>
                      <TableCell>{formaterDate(m.date)}</TableCell>
                      <TableCell>{m.type === 'RECHARGE' ? 'Recharge' : 'Plein'}</TableCell>
                      <TableCell>
                        {m.type === 'RECHARGE'
                          ? [m.mode && MODES_RECHARGE[m.mode], m.reference].filter(Boolean).join(' · ')
                          : `${m.stationCode} · ${formaterNombre(m.litres)} L ${m.produitCode} à ${formaterPrix(m.prixUnitaire)}`}
                      </TableCell>
                      <TableCell align="right" sx={{ color: m.type === 'DEBIT' ? 'error.main' : undefined }}>
                        {m.type === 'DEBIT' ? '−' : '+'}
                        {formaterPrix(m.montant)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {d.mouvements.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4}>Aucun mouvement : la carte n'a pas encore été rechargée.</TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ flexWrap: 'wrap', gap: 1 }}>
        {d && (
          <Button startIcon={<PrintIcon />} onClick={() => void imprimerCarte(d.carte)}>
            Imprimer
          </Button>
        )}
        {d?.peutGerer && d.carte.statut !== 'ANNULEE' && (
          <>
            {d.carte.statut === 'ACTIVE' ? (
              <Button color="warning" onClick={() => changement.mutate('BLOQUEE')} disabled={changement.isPending}>
                Bloquer (perte, vol)
              </Button>
            ) : (
              <Button onClick={() => changement.mutate('ACTIVE')} disabled={changement.isPending}>
                Réactiver
              </Button>
            )}
            {d.carte.solde === 0 && (
              <Button color="error" disabled={changement.isPending}
                onClick={() =>
                  confirmerPuis(
                    {
                      titre: 'Annuler la carte ?',
                      message: `La carte ${d.carte.numero} de ${d.carte.porteur} sera annulée définitivement : elle ne pourra plus être rechargée ni utilisée.`,
                      action: 'Annuler la carte',
                    },
                    () => changement.mutate('ANNULEE'),
                  )
                }
              >
                Annuler la carte
              </Button>
            )}
            <Button variant="contained" startIcon={<AddCardIcon />} onClick={() => setRecharge(true)}>
              Recharger
            </Button>
          </>
        )}
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
      {recharge && d && <DialogueRecharge carte={d.carte} onFermer={() => setRecharge(false)} />}
    </Dialog>
  )
}

/** Tableau des cartes (toutes ou celles d'un client). */
export function TableCartes({ cartes, onChoisir }: { cartes: CarteCarburant[]; onChoisir: (id: string) => void }) {
  const tri = useTri<CarteCarburant, 'numero' | 'porteur' | 'client' | 'etat' | 'solde'>(
    { numero: (c) => c.numero, porteur: (c) => c.porteur, client: (c) => c.clientNom, etat: (c) => STATUTS_CARTE[c.statut], solde: (c) => c.solde },
    'numero',
  )
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table size="small" aria-label="Cartes prépayées">
        <TableHead>
          <TableRow>
            <EnTeteTri tri={tri} cle="numero">
              Numéro
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="porteur">
              Porteur
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="client">
              Client
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="etat">
              État
            </EnTeteTri>
            <EnTeteTri tri={tri} cle="solde" align="right">
              Solde
            </EnTeteTri>
          </TableRow>
        </TableHead>
        <TableBody>
          {tri.trier(cartes).map((c) => (
            <TableRow key={c.id} hover onClick={() => onChoisir(c.id)} sx={{ cursor: 'pointer' }} data-testid={`carte-${c.numero}`}>
              <TableCell sx={{ fontFamily: 'monospace' }}>{c.numero}</TableCell>
              <TableCell>
                {c.porteur}
                {c.immatriculation && (
                  <Typography variant="caption" color="text.secondary" component="div">
                    {c.immatriculation}
                  </Typography>
                )}
              </TableCell>
              <TableCell>{c.clientNom}</TableCell>
              <TableCell>
                <PuceEtat ton={COULEUR_STATUT[c.statut]} libelle={STATUTS_CARTE[c.statut]} />
              </TableCell>
              <TableCell align="right">{formaterPrix(c.solde)}</TableCell>
            </TableRow>
          ))}
          {cartes.length === 0 && (
            <TableRow>
              <TableCell colSpan={5}>
                <Typography variant="body2" color="text.secondary">
                  Aucune carte.
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </Box>
  )
}

export function PageCartes() {
  const cartes = useCartes()
  const peutGerer = usePermission('CLIENT_GERER')
  const [choisie, setChoisie] = useState<string | null>(null)
  const [filtre, setFiltre] = useState('')
  const [statut, setStatut] = useState<StatutCarte | 'TOUTES'>('TOUTES')
  const toutes = cartes.data ?? []
  const statuts = (Object.keys(STATUTS_CARTE) as StatutCarte[]).filter((s) => toutes.some((c) => c.statut === s))
  const liste = toutes.filter(
    (c) => (statut === 'TOUTES' || c.statut === statut) && correspond(filtre, c.numero, c.porteur, c.clientNom, c.clientCode, c.immatriculation),
  )
  const total = toutes.reduce((s, c) => s + c.solde, 0)
  const actives = toutes.filter((c) => c.statut === 'ACTIVE')
  const bloquees = toutes.filter((c) => c.statut === 'BLOQUEE').length
  return (
    <>
      <EnTetePage titre="Cartes carburant prépayées" sousTitre={`Soldes prépayés en circulation : ${formaterPrix(total)}`} />
      {peutGerer && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Une carte s'émet depuis la fiche du client (onglet Cartes). Chaque plein se saisit dans la journée de la station, en
          scannant le QR code.
        </Alert>
      )}
      <AlerteErreur erreur={cartes.error} />
      {cartes.isLoading && <Chargement />}
      {cartes.data && (
        <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)' }, mb: 2.5 }}>
          <CarteIndicateur
            icone={<AccountBalanceWalletOutlinedIcon />}
            libelle="Soldes en circulation"
            valeur={formaterPrix(total)}
            accent
            sous="prépayés, non encore consommés"
          />
          <CarteIndicateur
            icone={<CreditCardOutlinedIcon />}
            libelle="Cartes actives"
            valeur={actives.length}
            ton="success"
            sous={`sur ${toutes.length} carte(s) émise(s)`}
          />
          <CarteIndicateur
            icone={<BlockOutlinedIcon />}
            libelle="Cartes bloquées"
            valeur={bloquees}
            ton={bloquees > 0 ? 'warning' : 'primary'}
            sous="pleins refusés tant qu’elles le restent"
          />
        </Box>
      )}
      <BarreFiltres resume={`${liste.length} carte(s) · ${formaterPrix(liste.reduce((s, c) => s + c.solde, 0))}`}>
        <ChampRecherche valeur={filtre} onChange={setFiltre} libelle="Rechercher un numéro, un porteur, un client" />
        {statuts.length > 1 && (
          <FiltrePuces
            libelle="Filtrer par état"
            valeur={statut}
            onChange={setStatut}
            options={[
              { valeur: 'TOUTES' as const, libelle: 'Toutes', nombre: toutes.length },
              ...statuts.map((s) => ({ valeur: s, libelle: STATUTS_CARTE[s], nombre: toutes.filter((c) => c.statut === s).length })),
            ]}
          />
        )}
      </BarreFiltres>
      <CadreTableau>
        <TableCartes cartes={liste} onChoisir={setChoisie} />
      </CadreTableau>
      {choisie && <DialogueCarte id={choisie} onFermer={() => setChoisie(null)} />}
    </>
  )
}
