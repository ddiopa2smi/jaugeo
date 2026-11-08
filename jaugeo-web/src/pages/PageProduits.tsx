import { zodResolver } from '@hookform/resolvers/zod'
import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, messageErreur } from '../api/client'
import { cles, useProduits } from '../api/requetes'
import type { Produit } from '../api/types'
import { usePermission } from '../auth/session'
import { ChampInterrupteur } from '../shared/ChampInterrupteur'
import { AlerteErreur, ChampTexte, EnTetePage, PuceStatut } from '../shared/composants'
import { reporterErreursServeur } from '../shared/formulaires'
import { FAMILLES, formaterNombre, UNITES, versNombre } from '../shared/libelles'
import { notifier } from '../shared/notifications'

const schema = z
  .object({
    code: z.string().trim().regex(/^[A-Za-z0-9_]{2,20}$/, '2 à 20 caractères : lettres, chiffres ou _'),
    libelle: z.string().trim().min(1, 'Le libellé est obligatoire').max(100),
    famille: z.enum(['CARBURANT', 'LUBRIFIANT', 'GAZ', 'BOUTIQUE']),
    unite: z.enum(['LITRE', 'UNITE', 'KG']),
    suiviJaugeage: z.boolean(),
    tauxTva: z.string().refine((v) => {
      if (v.trim() === '') return true
      const n = versNombre(v)
      return n !== null && n >= 0 && n <= 100
    }, 'Taux entre 0 et 100'),
    actif: z.boolean(),
  })
  .refine((v) => !v.suiviJaugeage || v.unite === 'LITRE', {
    path: ['suiviJaugeage'],
    message: 'Le jaugeage ne concerne que les produits vendus au litre.',
  })
type Saisie = z.infer<typeof schema>

function FormulaireProduit({ produit, onFermer }: { produit?: Produit; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [erreur, setErreur] = useState<string | null>(null)
  const { control, handleSubmit, setError, formState } = useForm<Saisie>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: produit?.code ?? '',
      libelle: produit?.libelle ?? '',
      famille: produit?.famille ?? 'CARBURANT',
      unite: produit?.unite ?? 'LITRE',
      suiviJaugeage: produit?.suiviJaugeage ?? true,
      tauxTva: produit?.tauxTva?.toString() ?? '',
      actif: produit?.actif ?? true,
    },
  })
  const mutation = useMutation({
    mutationFn: (v: Saisie) => {
      const corps = {
        code: v.code.toUpperCase(),
        libelle: v.libelle.trim(),
        famille: v.famille,
        unite: v.unite,
        suiviJaugeage: v.suiviJaugeage,
        tauxTva: versNombre(v.tauxTva),
        actif: v.actif,
        version: produit?.version,
      }
      return produit ? api.put<Produit>(`/produits/${produit.id}`, corps) : api.post<Produit>('/produits', corps)
    },
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: cles.produits })
      notifier(produit ? `${p.libelle} : modifications enregistrées.` : `Produit « ${p.libelle} » créé.`)
      onFermer()
    },
    onError: (e) => {
      if (!reporterErreursServeur(e, setError)) setErreur(messageErreur(e))
    },
  })
  const erreurJaugeage = formState.errors.suiviJaugeage?.message

  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="sm">
      <form
        noValidate
        onSubmit={handleSubmit((v) => {
          setErreur(null)
          mutation.mutate(v)
        })}
      >
        <DialogTitle>{produit ? `Modifier le produit ${produit.code}` : 'Nouveau produit'}</DialogTitle>
        <DialogContent>
          {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' }, pt: 1 }}>
            <ChampTexte control={control} nom="code" libelle="Code" disabled={!!produit} autoFocus={!produit} aide={produit ? 'Non modifiable' : 'Ex. : HUILE_15W40'} />
            <ChampTexte control={control} nom="libelle" libelle="Libellé" autoFocus={!!produit} />
            <ChampTexte control={control} nom="famille" libelle="Famille" select>
              {Object.entries(FAMILLES).map(([v, l]) => (
                <MenuItem key={v} value={v}>{l}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="unite" libelle="Unité de vente" select>
              {Object.entries(UNITES).map(([v, l]) => (
                <MenuItem key={v} value={v}>{l}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="tauxTva" libelle="Taux de TVA (%)" aide="Laisser vide si non applicable" />
            <Box>
              <ChampInterrupteur control={control} nom="suiviJaugeage" libelle="Suivi par jaugeage de cuve" />
              {erreurJaugeage && <Alert severity="warning" sx={{ mt: 1 }}>{erreurJaugeage}</Alert>}
              {produit && <ChampInterrupteur control={control} nom="actif" libelle="Produit actif" />}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onFermer}>Annuler</Button>
          <Button type="submit" variant="contained" loading={mutation.isPending}>
            Enregistrer
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

export function PageProduits() {
  const produits = useProduits()
  const peutGerer = usePermission('REFERENTIEL_GERER')
  const [edition, setEdition] = useState<{ produit?: Produit } | null>(null)

  const colonnes: GridColDef<Produit>[] = [
    { field: 'code', headerName: 'Code', width: 170 },
    { field: 'libelle', headerName: 'Libellé', flex: 1, minWidth: 160 },
    { field: 'famille', headerName: 'Famille', width: 130, valueFormatter: (v: Produit['famille']) => FAMILLES[v] },
    { field: 'unite', headerName: 'Unité', width: 120, valueFormatter: (v: Produit['unite']) => UNITES[v] },
    { field: 'suiviJaugeage', headerName: 'Jaugeage', width: 100, type: 'boolean' },
    {
      field: 'tauxTva',
      headerName: 'TVA',
      width: 90,
      valueFormatter: (v: number | null) => (v === null ? '—' : `${formaterNombre(v)} %`),
    },
    { field: 'actif', headerName: 'Statut', width: 110, renderCell: ({ value }) => <PuceStatut actif={!!value} /> },
  ]

  return (
    <>
      <EnTetePage
        titre="Produits"
        sousTitre="Carburants, lubrifiants et autres articles vendus dans le réseau."
        actions={
          peutGerer && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEdition({})}>
              Nouveau produit
            </Button>
          )
        }
      />
      <AlerteErreur erreur={produits.error} />
      <DataGrid
        rows={produits.data ?? []}
        columns={colonnes}
        loading={produits.isLoading}
        getRowId={(p) => p.id}
        onRowClick={peutGerer ? ({ row }) => setEdition({ produit: row }) : undefined}
        disableRowSelectionOnClick
        autoHeight
        hideFooter={(produits.data?.length ?? 0) <= 25}
        sx={peutGerer ? { '& .MuiDataGrid-row': { cursor: 'pointer' } } : undefined}
      />
      {edition && <FormulaireProduit produit={edition.produit} onFermer={() => setEdition(null)} />}
    </>
  )
}
