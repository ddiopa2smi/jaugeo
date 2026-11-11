import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import EditIcon from '@mui/icons-material/Edit'
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation'
import LockResetIcon from '@mui/icons-material/LockReset'
import PublicIcon from '@mui/icons-material/Public'
import SearchIcon from '@mui/icons-material/Search'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import { alpha } from '@mui/material/styles'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api, messageErreur } from '../../api/client'
import { cles, useStations, useUtilisateurs } from '../../api/requetes'
import type { ReponseMotDePasseProvisoire, Utilisateur } from '../../api/types'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDateHeureCourte } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { FormulaireUtilisateur } from './FormulaireUtilisateur'

type Statut = 'ACTIF' | 'PROVISOIRE' | 'VERROUILLE' | 'DESACTIVE'

const STATUTS: Record<Statut, { libelle: string; pluriel: string; couleur: 'success' | 'warning' | 'error' | 'default' }> = {
  ACTIF: { libelle: 'Actif', pluriel: 'Actifs', couleur: 'success' },
  PROVISOIRE: { libelle: 'Mot de passe provisoire', pluriel: 'Mot de passe provisoire', couleur: 'warning' },
  VERROUILLE: { libelle: 'Verrouillé', pluriel: 'Verrouillés', couleur: 'error' },
  DESACTIVE: { libelle: 'Désactivé', pluriel: 'Désactivés', couleur: 'default' },
}

/** Un seul statut par compte, du plus bloquant au moins bloquant. */
function statut(u: Utilisateur): Statut {
  if (!u.actif) return 'DESACTIVE'
  if (u.verrouille) return 'VERROUILLE'
  if (u.doitChangerMotDePasse) return 'PROVISOIRE'
  return 'ACTIF'
}

function initiales(nom: string): string {
  const mots = nom.trim().split(/\s+/).filter(Boolean)
  return ((mots[0]?.[0] ?? '') + (mots.length > 1 ? (mots[mots.length - 1]?.[0] ?? '') : '')).toUpperCase() || '?'
}

/** Recherche sans tenir compte de la casse ni des accents. */
const normaliser = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Affiche une seule fois le mot de passe provisoire à transmettre à l'utilisateur. */
function DialogueMotDePasseProvisoire({ reponse, onFermer }: { reponse: ReponseMotDePasseProvisoire; onFermer: () => void }) {
  const copier = async () => {
    try {
      await navigator.clipboard.writeText(reponse.motDePasseProvisoire)
      notifier('Mot de passe copié.')
    } catch {
      notifier('Copie impossible : sélectionnez le mot de passe et copiez-le manuellement.', 'warning')
    }
  }
  return (
    <Dialog open onClose={(_, raison) => raison !== 'backdropClick' && onFermer()} maxWidth="xs" fullWidth>
      <DialogTitle>Mot de passe provisoire</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Transmettez ce mot de passe à <strong>{reponse.utilisateur.nomComplet}</strong> (identifiant{' '}
          <strong>{reponse.utilisateur.identifiant}</strong>). Il devra le changer à sa première connexion.
        </DialogContentText>
        <Stack
          direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', bgcolor: 'grey.100', borderRadius: 1, px: 2, py: 1 }}
        >
          <Typography sx={{ fontFamily: 'monospace', fontSize: 22, letterSpacing: 2, userSelect: 'all' }} data-testid="mdp-provisoire">
            {reponse.motDePasseProvisoire}
          </Typography>
          <Tooltip title="Copier">
            <IconButton onClick={copier} aria-label="Copier le mot de passe">
              <ContentCopyIcon />
            </IconButton>
          </Tooltip>
        </Stack>
        <Alert severity="warning" sx={{ mt: 2 }}>
          Il ne sera plus affiché après fermeture de cette fenêtre.
        </Alert>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="contained" onClick={onFermer}>
          J'ai transmis le mot de passe
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Carte d'un compte : qui, quel rôle, quel périmètre, quel état ; modifier et réinitialiser en bas. */
function CarteUtilisateur({
  u,
  stations,
  onModifier,
  onReinitialiser,
}: {
  u: Utilisateur
  stations: string[]
  onModifier: () => void
  onReinitialiser: () => void
}) {
  const s = statut(u)
  const reseau = u.perimetre === 'RESEAU'
  const visibles = stations.slice(0, 2).join(', ')
  const reste = stations.length - 2
  return (
    <Paper sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }} data-testid={`utilisateur-${u.identifiant}`}>
      <Stack direction="row" sx={{ gap: 1.5, p: 2, pb: 1.5, alignItems: 'flex-start' }}>
        <Avatar
          aria-hidden
          sx={(t) => ({
            width: 44,
            height: 44,
            fontSize: 16,
            fontWeight: 600,
            color: u.actif ? 'primary.main' : 'text.secondary',
            bgcolor: alpha(u.actif ? t.palette.primary.main : t.palette.text.secondary, 0.1),
          })}
        >
          {initiales(u.nomComplet)}
        </Avatar>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" noWrap sx={{ fontWeight: 600, lineHeight: 1.3, color: u.actif ? 'text.primary' : 'text.secondary' }}>
            {u.nomComplet}
          </Typography>
          <Typography variant="body2" color="text.secondary" noWrap>
            {u.identifiant} · {u.roleLibelle}
          </Typography>
        </Box>
        <Chip
          size="small"
          color={STATUTS[s].couleur}
          variant={s === 'ACTIF' || s === 'DESACTIVE' ? 'outlined' : 'filled'}
          label={s === 'PROVISOIRE' ? 'Provisoire' : STATUTS[s].libelle}
          title={STATUTS[s].libelle}
          sx={{ flexShrink: 0 }}
        />
      </Stack>

      <Stack spacing={0.75} sx={{ px: 2, pb: 1.5, flexGrow: 1, color: 'text.secondary' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
          {reseau ? <PublicIcon sx={{ fontSize: 18 }} /> : <LocalGasStationIcon sx={{ fontSize: 18 }} />}
          <Typography variant="body2" noWrap title={reseau ? undefined : stations.join(', ')} sx={{ color: 'text.primary' }}>
            {reseau ? 'Tout le réseau' : stations.length === 0 ? 'Aucune station' : `${visibles}${reste > 0 ? ` +${reste}` : ''}`}
          </Typography>
        </Stack>
        <Typography variant="caption" noWrap>
          {[u.email, u.telephone].filter(Boolean).join(' · ') || 'Aucune coordonnée'}
        </Typography>
        <Typography variant="caption">
          {u.derniereConnexion ? `Dernière connexion le ${formaterDateHeureCourte(u.derniereConnexion)}` : 'Jamais connecté'}
        </Typography>
      </Stack>

      <Divider />
      <Stack direction="row" sx={{ px: 1, py: 0.5, justifyContent: 'flex-end', gap: 0.5 }}>
        <Button size="small" startIcon={<LockResetIcon />} onClick={onReinitialiser} aria-label={`Réinitialiser le mot de passe de ${u.identifiant}`}>
          Réinitialiser
        </Button>
        <Button size="small" startIcon={<EditIcon />} onClick={onModifier} aria-label={`Modifier ${u.identifiant}`}>
          Modifier
        </Button>
      </Stack>
    </Paper>
  )
}

export function PageUtilisateurs() {
  const utilisateurs = useUtilisateurs()
  const stations = useStations()
  const queryClient = useQueryClient()
  const [edition, setEdition] = useState<{ utilisateur?: Utilisateur } | null>(null)
  const [aReinitialiser, setAReinitialiser] = useState<Utilisateur | null>(null)
  const [provisoire, setProvisoire] = useState<ReponseMotDePasseProvisoire | null>(null)
  const [recherche, setRecherche] = useState('')
  const [role, setRole] = useState('')
  const [filtre, setFiltre] = useState<Statut | null>(null)

  const nomsStations = useMemo(() => new Map((stations.data ?? []).map((s) => [s.numero, s.nom])), [stations.data])
  const tous = useMemo(
    () => [...(utilisateurs.data ?? [])].sort((a, b) => a.nomComplet.localeCompare(b.nomComplet, 'fr')),
    [utilisateurs.data],
  )
  const roles = useMemo(
    () => [...new Map(tous.map((u) => [u.role, u.roleLibelle])).entries()].sort((a, b) => a[1].localeCompare(b[1], 'fr')),
    [tous],
  )
  const parStatut = useMemo(() => {
    const n: Record<Statut, number> = { ACTIF: 0, PROVISOIRE: 0, VERROUILLE: 0, DESACTIVE: 0 }
    tous.forEach((u) => n[statut(u)]++)
    return n
  }, [tous])
  const cherche = normaliser(recherche.trim())
  const liste = tous.filter(
    (u) =>
      (filtre === null || statut(u) === filtre) &&
      (role === '' || u.role === role) &&
      (cherche === '' || normaliser(`${u.nomComplet} ${u.identifiant} ${u.email ?? ''} ${u.telephone ?? ''}`).includes(cherche)),
  )

  const reinitialisation = useMutation({
    mutationFn: (u: Utilisateur) => api.post<ReponseMotDePasseProvisoire>(`/utilisateurs/${u.id}/reinitialiser-mot-de-passe`),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: cles.utilisateurs })
      setAReinitialiser(null)
      setProvisoire(r)
    },
    onError: (e) => notifier(messageErreur(e), 'error'),
  })

  return (
    <>
      <EnTetePage
        titre="Utilisateurs"
        sousTitre="Comptes, rôles et périmètre d'accès aux stations."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEdition({})}>
            Nouvel utilisateur
          </Button>
        }
      />
      <AlerteErreur erreur={utilisateurs.error} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { md: 'center' } }}>
        <TextField
          placeholder="Rechercher un nom, un identifiant, un e-mail"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
          slotProps={{
            htmlInput: { 'aria-label': 'Rechercher un utilisateur' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ maxWidth: { md: 360 }, bgcolor: 'background.paper' }}
        />
        <TextField
          select
          label="Rôle"
          value={role}
          onChange={(e) => setRole(e.target.value)}
          sx={{ width: { xs: '100%', md: 230 }, flexShrink: 0, bgcolor: 'background.paper' }}
        >
          <MenuItem value="">Tous les rôles</MenuItem>
          {roles.map(([code, libelle]) => (
            <MenuItem key={code} value={code}>
              {libelle}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <Stack direction="row" role="group" aria-label="Filtrer par état" sx={{ mb: 2, flexWrap: 'wrap', gap: 1 }}>
        <Chip
          label={`Tous · ${tous.length}`}
          color={filtre === null ? 'primary' : 'default'}
          variant={filtre === null ? 'filled' : 'outlined'}
          onClick={() => setFiltre(null)}
        />
        {(Object.keys(STATUTS) as Statut[])
          .filter((s) => parStatut[s] > 0 || filtre === s)
          .map((s) => (
            <Chip
              key={s}
              label={`${STATUTS[s].pluriel} · ${parStatut[s]}`}
              color={filtre === s ? 'primary' : 'default'}
              variant={filtre === s ? 'filled' : 'outlined'}
              onClick={() => setFiltre(filtre === s ? null : s)}
              data-testid={`filtre-${s}`}
            />
          ))}
      </Stack>

      {utilisateurs.isLoading ? (
        <Chargement />
      ) : liste.length === 0 ? (
        <Alert severity="info">
          {tous.length === 0 ? 'Aucun utilisateur.' : 'Aucun utilisateur ne correspond à cette recherche.'}
        </Alert>
      ) : (
        <Box
          sx={{ display: 'grid', gap: { xs: 1.5, sm: 2 }, gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))' }}
          aria-label="Utilisateurs"
          role="list"
        >
          {liste.map((u) => (
            <Box key={u.id} role="listitem" sx={{ display: 'flex', minWidth: 0, '& > *': { flexGrow: 1 } }}>
              <CarteUtilisateur
                u={u}
                stations={u.stationIds.map((id) => nomsStations.get(id) ?? `Station n° ${id}`)}
                onModifier={() => setEdition({ utilisateur: u })}
                onReinitialiser={() => setAReinitialiser(u)}
              />
            </Box>
          ))}
        </Box>
      )}

      {edition && (
        <FormulaireUtilisateur utilisateur={edition.utilisateur} onFermer={() => setEdition(null)} onCree={setProvisoire} />
      )}

      {aReinitialiser && (
        <Dialog open onClose={() => setAReinitialiser(null)} maxWidth="xs" fullWidth>
          <DialogTitle>Réinitialiser le mot de passe ?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Un nouveau mot de passe provisoire sera généré pour <strong>{aReinitialiser.nomComplet}</strong>. Son compte sera
              déverrouillé et ses sessions fermées.
            </DialogContentText>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setAReinitialiser(null)}>Annuler</Button>
            <Button
              variant="contained"
              color="warning"
              loading={reinitialisation.isPending}
              onClick={() => reinitialisation.mutate(aReinitialiser)}
            >
              Réinitialiser
            </Button>
          </DialogActions>
        </Dialog>
      )}

      {provisoire && <DialogueMotDePasseProvisoire reponse={provisoire} onFermer={() => setProvisoire(null)} />}
    </>
  )
}
