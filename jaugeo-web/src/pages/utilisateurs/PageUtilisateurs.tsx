import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import EditIcon from '@mui/icons-material/Edit'
import LockResetIcon from '@mui/icons-material/LockReset'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { DataGrid, GridActionsCellItem, type GridColDef } from '@mui/x-data-grid'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api, messageErreur } from '../../api/client'
import { cles, useStations, useUtilisateurs } from '../../api/requetes'
import type { ReponseMotDePasseProvisoire, Utilisateur } from '../../api/types'
import { AlerteErreur, EnTetePage } from '../../shared/composants'
import { formaterDateHeure, PERIMETRES } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { FormulaireUtilisateur } from './FormulaireUtilisateur'

function StatutUtilisateur({ u }: { u: Utilisateur }) {
  if (!u.actif) return <Chip size="small" label="Désactivé" />
  if (u.verrouille) return <Chip size="small" color="error" variant="outlined" label="Verrouillé" />
  if (u.doitChangerMotDePasse) return <Chip size="small" color="warning" variant="outlined" label="Mot de passe provisoire" />
  return <Chip size="small" color="success" variant="outlined" label="Actif" />
}

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

export function PageUtilisateurs() {
  const utilisateurs = useUtilisateurs()
  const stations = useStations()
  const queryClient = useQueryClient()
  const [edition, setEdition] = useState<{ utilisateur?: Utilisateur } | null>(null)
  const [aReinitialiser, setAReinitialiser] = useState<Utilisateur | null>(null)
  const [provisoire, setProvisoire] = useState<ReponseMotDePasseProvisoire | null>(null)

  const codesStations = useMemo(
    () => new Map((stations.data ?? []).map((s) => [s.numero, s.code])),
    [stations.data],
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

  const colonnes: GridColDef<Utilisateur>[] = [
    { field: 'identifiant', headerName: 'Identifiant', width: 150 },
    { field: 'nomComplet', headerName: 'Nom', flex: 1, minWidth: 170 },
    { field: 'roleLibelle', headerName: 'Rôle', width: 180 },
    {
      field: 'perimetre',
      headerName: 'Périmètre',
      width: 200,
      valueGetter: (_, u) =>
        u.perimetre === 'RESEAU'
          ? PERIMETRES.RESEAU
          : u.stationIds.map((id) => codesStations.get(id) ?? `#${id}`).join(', '),
    },
    {
      field: 'statut',
      headerName: 'Statut',
      width: 190,
      sortable: false,
      renderCell: ({ row }) => <StatutUtilisateur u={row} />,
    },
    {
      field: 'derniereConnexion',
      headerName: 'Dernière connexion',
      width: 170,
      valueFormatter: (v: string | null) => formaterDateHeure(v),
    },
    {
      field: 'actions',
      type: 'actions',
      width: 90,
      getActions: ({ row }) => [
        <GridActionsCellItem key="modifier" icon={<EditIcon />} label="Modifier" onClick={() => setEdition({ utilisateur: row })} />,
        <GridActionsCellItem
          key="reinitialiser"
          icon={<LockResetIcon />}
          label="Réinitialiser le mot de passe"
          onClick={() => setAReinitialiser(row)}
          showInMenu={false}
        />,
      ],
    },
  ]

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
      <Box>
        <DataGrid
          rows={utilisateurs.data ?? []}
          columns={colonnes}
          loading={utilisateurs.isLoading}
          getRowId={(u) => u.id}
          disableRowSelectionOnClick
          autoHeight
          initialState={{ sorting: { sortModel: [{ field: 'identifiant', sort: 'asc' }] } }}
          hideFooter={(utilisateurs.data?.length ?? 0) <= 25}
        />
      </Box>

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
