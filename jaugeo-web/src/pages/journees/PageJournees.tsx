import EventAvailableIcon from '@mui/icons-material/EventAvailable'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useJourneesStation, useStations } from '../../api/requetes'
import type { DetailJournee, ListeJournees, ResumeJournee } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, EnTetePage } from '../../shared/composants'
import { STATUTS_JOURNEE } from '../../shared/journee'
import { formaterDate, formaterDateHeureCourte, formaterNombre, formaterPrix } from '../../shared/libelles'

const colonnes: GridColDef<ResumeJournee>[] = [
  { field: 'date', headerName: 'Journée', width: 120, valueFormatter: (v: string) => formaterDate(v) },
  {
    field: 'statut',
    headerName: 'Statut',
    width: 150,
    renderCell: ({ row }) => (
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
        <Chip size="small" variant="outlined" color={STATUTS_JOURNEE[row.statut].couleur} label={STATUTS_JOURNEE[row.statut].libelle} />
        {row.statut === 'BROUILLON' && row.motifRenvoi && <Chip size="small" color="error" label="Renvoyée" />}
      </Stack>
    ),
  },
  {
    field: 'litres',
    headerName: 'Litres vendus',
    width: 140,
    align: 'right',
    headerAlign: 'right',
    valueFormatter: (v: number | null) => formaterNombre(v),
  },
  {
    field: 'montant',
    headerName: 'Ventes',
    width: 150,
    align: 'right',
    headerAlign: 'right',
    valueFormatter: (v: number | null) => formaterPrix(v),
  },
  {
    field: 'clotureePar',
    headerName: 'Clôture',
    flex: 1,
    minWidth: 180,
    valueGetter: (_v, row) => (row.clotureePar ? `${row.clotureePar} · ${formaterDateHeureCourte(row.clotureeLe)}` : ''),
  },
  { field: 'valideePar', headerName: 'Validation', width: 150, valueFormatter: (v: string | null) => v ?? '' },
]

function OuvertureJournee({ liste }: { liste: ListeJournees }) {
  const p = liste.prochaine
  const [date, setDate] = useState(p.date ?? p.dateMax)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const ouverture = useMutation({
    mutationFn: () => api.post<DetailJournee>(`/stations/${liste.stationId}/journees`, { date: p.date ?? date }),
    onSuccess: (j) => {
      queryClient.invalidateQueries({ queryKey: cles.journees })
      queryClient.setQueryData(cles.journee(j.id), j)
      navigate(`/journees/${j.id}`)
    },
  })

  if (!p.possible) {
    return p.motif ? (
      <Alert severity="info" sx={{ mb: 2 }}>
        {p.motif}
      </Alert>
    ) : null
  }
  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <AlerteErreur erreur={ouverture.error} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
        {p.date ? (
          <Typography sx={{ flexGrow: 1 }}>
            Prochaine journée : <strong>{formaterDate(p.date)}</strong>. Les index de départ sont repris de la clôture précédente.
          </Typography>
        ) : (
          <>
            <Typography sx={{ flexGrow: 1 }}>
              Première journée de la station : choisissez sa date. Les index de départ sont ceux de mise en service des pistolets.
            </Typography>
            <TextField
              type="date"
              label="Date de la journée"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: p.dateMax } }}
            />
          </>
        )}
        <Button
          variant="contained"
          size="large"
          startIcon={<EventAvailableIcon />}
          onClick={() => ouverture.mutate()}
          disabled={ouverture.isPending || (!p.date && !date)}
        >
          Ouvrir la journée
        </Button>
      </Stack>
    </Paper>
  )
}

export function PageJournees() {
  const stations = useStations()
  const [params, setParams] = useSearchParams()
  const actives = (stations.data ?? []).filter((s) => s.actif).sort((a, b) => a.code.localeCompare(b.code))
  // Un gérant n'a qu'une station : elle est choisie d'office.
  const stationId = params.get('station') ?? (actives.length === 1 ? actives[0]!.id : null)
  const liste = useJourneesStation(stationId)
  const peutOuvrir = usePermission('JOURNEE_SAISIR')
  const navigate = useNavigate()

  return (
    <>
      <EnTetePage titre="Journées" sousTitre="Ouverture, saisie des index et clôture des journées de station." />
      <AlerteErreur erreur={stations.error ?? liste.error} />
      {actives.length > 1 && (
        <TextField
          select
          label="Station"
          value={stationId ?? ''}
          onChange={(e) => setParams({ station: e.target.value })}
          sx={{ mb: 2, minWidth: 280 }}
        >
          {actives.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.code} · {s.nom}
            </MenuItem>
          ))}
        </TextField>
      )}
      {!stationId && !stations.isLoading && <Alert severity="info">Choisissez une station.</Alert>}
      {liste.data && (
        <>
          {peutOuvrir && <OuvertureJournee key={liste.data.stationId + (liste.data.prochaine.date ?? '')} liste={liste.data} />}
          <DataGrid
            rows={liste.data.journees}
            columns={colonnes}
            getRowId={(j) => j.id}
            onRowClick={({ row }) => navigate(`/journees/${row.id}`)}
            disableRowSelectionOnClick
            autoHeight
            hideFooter={liste.data.journees.length <= 25}
            localeText={{ noRowsLabel: 'Aucune journée sur les deux derniers mois.' }}
            sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }}
          />
        </>
      )}
    </>
  )
}
