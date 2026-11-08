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
import { DataGrid, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid'
import { useDeferredValue, useState } from 'react'
import { useAudit, useTablesAudit } from '../api/requetes'
import type { EntreeAudit } from '../api/types'
import { AlerteErreur, EnTetePage } from '../shared/composants'
import { formaterDateHeure, OPERATIONS_AUDIT } from '../shared/libelles'

const COULEURS: Record<string, 'success' | 'info' | 'error'> = { INSERT: 'success', UPDATE: 'info', DELETE: 'error' }

function lireJson(texte: string | null): Record<string, unknown> {
  if (!texte) return {}
  try {
    return JSON.parse(texte) as Record<string, unknown>
  } catch {
    return {}
  }
}

const afficher = (v: unknown) => (v === undefined ? '' : v === null ? '∅' : typeof v === 'object' ? JSON.stringify(v) : String(v))

/** Détail d'une entrée : valeurs avant / après, champs modifiés en évidence. */
export function DetailAudit({ entree, onFermer }: { entree: EntreeAudit; onFermer: () => void }) {
  const avant = lireJson(entree.ancienneValeur)
  const apres = lireJson(entree.nouvelleValeur)
  const champs = [...new Set([...Object.keys(avant), ...Object.keys(apres)])].sort()
  const modifies = champs.filter((c) => JSON.stringify(avant[c]) !== JSON.stringify(apres[c]))
  const [tout, setTout] = useState(entree.operation !== 'UPDATE')
  const affiches = tout ? champs : modifies

  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>
        {OPERATIONS_AUDIT[entree.operation] ?? entree.operation} · {entree.schema}.{entree.table}
        {entree.cle && ` #${entree.cle}`}
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Par <strong>{entree.utilisateur ?? 'inconnu'}</strong> le {formaterDateHeure(entree.horodatage)}
        </Typography>
        {entree.operation === 'UPDATE' && (
          <Button size="small" onClick={() => setTout((t) => !t)} sx={{ mb: 1 }}>
            {tout ? 'Afficher seulement les champs modifiés' : 'Afficher tous les champs'}
          </Button>
        )}
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Champ</TableCell>
              {entree.operation !== 'INSERT' && <TableCell>Avant</TableCell>}
              {entree.operation !== 'DELETE' && <TableCell>Après</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {affiches.map((c) => (
              <TableRow key={c} sx={modifies.includes(c) && entree.operation === 'UPDATE' ? { bgcolor: '#fff8e1' } : undefined}>
                <TableCell sx={{ fontFamily: 'monospace' }}>{c}</TableCell>
                {entree.operation !== 'INSERT' && <TableCell sx={{ wordBreak: 'break-all' }}>{afficher(avant[c])}</TableCell>}
                {entree.operation !== 'DELETE' && <TableCell sx={{ wordBreak: 'break-all' }}>{afficher(apres[c])}</TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}

const colonnes: GridColDef<EntreeAudit>[] = [
  { field: 'horodatage', headerName: 'Date', width: 170, sortable: false, valueFormatter: (v: string) => formaterDateHeure(v) },
  { field: 'utilisateur', headerName: 'Utilisateur', width: 150, sortable: false },
  {
    field: 'operation',
    headerName: 'Opération',
    width: 130,
    sortable: false,
    renderCell: ({ value }) => (
      <Chip size="small" variant="outlined" color={COULEURS[value as string] ?? 'default'} label={OPERATIONS_AUDIT[value as string] ?? value} />
    ),
  },
  { field: 'table', headerName: 'Table', width: 180, sortable: false, valueGetter: (_, e) => `${e.schema}.${e.table}` },
  { field: 'cle', headerName: 'Clé', width: 90, sortable: false },
  {
    field: 'resume',
    headerName: 'Champs modifiés',
    flex: 1,
    minWidth: 200,
    sortable: false,
    valueGetter: (_, e) => {
      if (e.operation !== 'UPDATE') return ''
      const avant = lireJson(e.ancienneValeur)
      const apres = lireJson(e.nouvelleValeur)
      return Object.keys(apres)
        .filter((c) => JSON.stringify(avant[c]) !== JSON.stringify(apres[c]))
        .join(', ')
    },
  },
]

export function PageAudit() {
  const [table, setTable] = useState('')
  const [utilisateur, setUtilisateur] = useState('')
  const [pagination, setPagination] = useState<GridPaginationModel>({ page: 0, pageSize: 50 })
  const [detail, setDetail] = useState<EntreeAudit | null>(null)
  const utilisateurDiffere = useDeferredValue(utilisateur)
  const tables = useTablesAudit()
  const audit = useAudit({ table, utilisateur: utilisateurDiffere, page: pagination.page, taille: pagination.pageSize })

  return (
    <>
      <EnTetePage
        titre="Journal d'audit"
        sousTitre="Toute création, modification ou suppression est tracée en base, sans possibilité d'effacement."
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Table"
          value={table}
          onChange={(e) => {
            setTable(e.target.value)
            setPagination((p) => ({ ...p, page: 0 }))
          }}
          sx={{ maxWidth: { sm: 260 } }}
        >
          <MenuItem value="">Toutes les tables</MenuItem>
          {(tables.data ?? []).map((t) => (
            <MenuItem key={t} value={t}>{t}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="Utilisateur"
          value={utilisateur}
          onChange={(e) => {
            setUtilisateur(e.target.value)
            setPagination((p) => ({ ...p, page: 0 }))
          }}
          sx={{ maxWidth: { sm: 260 } }}
        />
      </Stack>
      <AlerteErreur erreur={audit.error} />
      <Box>
        <DataGrid
          rows={audit.data?.contenu ?? []}
          columns={colonnes}
          getRowId={(e) => `${e.id}-${e.horodatage}`}
          loading={audit.isFetching}
          paginationMode="server"
          rowCount={audit.data?.total ?? 0}
          paginationModel={pagination}
          onPaginationModelChange={setPagination}
          pageSizeOptions={[25, 50, 100]}
          onRowClick={({ row }) => setDetail(row)}
          disableRowSelectionOnClick
          disableColumnFilter
          autoHeight
          sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Box>
      {detail && <DetailAudit entree={detail} onFermer={() => setDetail(null)} />}
    </>
  )
}
