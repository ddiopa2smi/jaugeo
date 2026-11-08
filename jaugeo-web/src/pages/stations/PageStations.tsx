import AddIcon from '@mui/icons-material/Add'
import Button from '@mui/material/Button'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useStations } from '../../api/requetes'
import type { Station } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, EnTetePage, PuceStatut } from '../../shared/composants'
import { formaterDate, MODES_EXPLOITATION, TYPES_STATION } from '../../shared/libelles'
import { FormulaireStation } from './FormulaireStation'

const colonnes: GridColDef<Station>[] = [
  { field: 'code', headerName: 'Code', width: 100 },
  { field: 'nom', headerName: 'Nom', flex: 1, minWidth: 160 },
  { field: 'type', headerName: 'Type', width: 130, valueFormatter: (v: Station['type']) => TYPES_STATION[v] },
  { field: 'regionNom', headerName: 'Région', width: 140 },
  { field: 'localite', headerName: 'Localité', width: 140 },
  {
    field: 'modeExploitation',
    headerName: 'Exploitation',
    width: 170,
    valueFormatter: (v: Station['modeExploitation']) => MODES_EXPLOITATION[v],
  },
  {
    field: 'dateOuverture',
    headerName: 'Ouverture',
    width: 110,
    valueFormatter: (v: string | null) => formaterDate(v),
  },
  {
    field: 'actif',
    headerName: 'Statut',
    width: 110,
    renderCell: ({ value }) => <PuceStatut actif={!!value} libelleInactif="Fermée" />,
  },
]

export function PageStations() {
  const stations = useStations()
  const peutGerer = usePermission('REFERENTIEL_GERER')
  const [creation, setCreation] = useState(false)
  const navigate = useNavigate()

  return (
    <>
      <EnTetePage
        titre="Stations"
        sousTitre="Stations de votre périmètre. Cliquez sur une ligne pour voir ses cuves, pompes et pistolets."
        actions={
          peutGerer && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreation(true)}>
              Nouvelle station
            </Button>
          )
        }
      />
      <AlerteErreur erreur={stations.error} />
      <DataGrid
        rows={stations.data ?? []}
        columns={colonnes}
        loading={stations.isLoading}
        getRowId={(s) => s.id}
        onRowClick={({ row }) => navigate(`/stations/${row.id}`)}
        initialState={{ sorting: { sortModel: [{ field: 'code', sort: 'asc' }] } }}
        disableRowSelectionOnClick
        autoHeight
        hideFooter={(stations.data?.length ?? 0) <= 25}
        sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }}
      />
      {creation && (
        <FormulaireStation
          onFermer={(s) => {
            setCreation(false)
            if (s) navigate(`/stations/${s.id}`)
          }}
        />
      )}
    </>
  )
}
