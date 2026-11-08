import { zodResolver } from '@hookform/resolvers/zod'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, messageErreur } from '../../api/client'
import { cles, useRegions } from '../../api/requetes'
import type { Equipements, Station } from '../../api/types'
import { ChampInterrupteur } from '../../shared/ChampInterrupteur'
import { ChampTexte } from '../../shared/composants'
import { reporterErreursServeur } from '../../shared/formulaires'
import { MODES_EXPLOITATION, TYPES_STATION, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const coordonnee = (max: number) =>
  z.string().refine((v) => {
    if (v.trim() === '') return true
    const n = versNombre(v)
    return n !== null && Math.abs(n) <= max
  }, `Valeur entre -${max} et ${max}`)

const schema = z.object({
  code: z.string().trim().regex(/^[A-Za-z0-9_]{2,10}$/, '2 à 10 caractères : lettres, chiffres ou _'),
  nom: z.string().trim().min(1, 'Le nom est obligatoire').max(100),
  type: z.enum(['ROUTIERE', 'PECHE', 'MIXTE']),
  regionCode: z.string().min(1, 'Choisissez une région'),
  localite: z.string().max(100),
  latitude: coordonnee(90),
  longitude: coordonnee(180),
  modeExploitation: z.enum(['PROPRE', 'GERANCE_LIBRE', 'LOCATION_GERANCE']),
  dateOuverture: z.string(),
  actif: z.boolean(),
})
type Saisie = z.infer<typeof schema>

function valeursInitiales(s?: Station): Saisie {
  return {
    code: s?.code ?? '',
    nom: s?.nom ?? '',
    type: s?.type ?? 'ROUTIERE',
    regionCode: s?.regionCode ?? '',
    localite: s?.localite ?? '',
    latitude: s?.latitude?.toString() ?? '',
    longitude: s?.longitude?.toString() ?? '',
    modeExploitation: s?.modeExploitation ?? 'PROPRE',
    dateOuverture: s?.dateOuverture ?? '',
    actif: s?.actif ?? true,
  }
}

/** Création (station absente) ou modification d'une station. */
export function FormulaireStation({ station, onFermer }: { station?: Station; onFermer: (s?: Station) => void }) {
  const regions = useRegions()
  const queryClient = useQueryClient()
  const [erreur, setErreur] = useState<string | null>(null)
  const { control, handleSubmit, setError } = useForm<Saisie>({
    resolver: zodResolver(schema),
    defaultValues: valeursInitiales(station),
  })

  const enregistrement = useMutation({
    mutationFn: (v: Saisie) => {
      const corps = {
        code: v.code.toUpperCase(),
        nom: v.nom.trim(),
        type: v.type,
        regionCode: v.regionCode,
        localite: videVersNull(v.localite),
        latitude: versNombre(v.latitude),
        longitude: versNombre(v.longitude),
        modeExploitation: v.modeExploitation,
        dateOuverture: videVersNull(v.dateOuverture),
        actif: v.actif,
        version: station?.version,
      }
      return station ? api.put<Station>(`/stations/${station.id}`, corps) : api.post<Station>('/stations', corps)
    },
    onSuccess: (s) => {
      // Affichage immédiat de la version renvoyée par le serveur, puis rechargement en arrière-plan.
      queryClient.setQueryData<Equipements>(cles.equipements(s.id), (avant) => (avant ? { ...avant, station: s } : avant))
      queryClient.invalidateQueries({ queryKey: cles.stations })
      notifier(station ? `${s.nom} : modifications enregistrées.` : `${s.nom} (${s.code}) créée.`)
      onFermer(s)
    },
    onError: (e) => {
      if (!reporterErreursServeur(e, setError)) setErreur(messageErreur(e))
    },
  })

  const soumettre = handleSubmit((v) => {
    setErreur(null)
    enregistrement.mutate(v)
  })

  return (
    <Dialog open onClose={() => onFermer()} fullWidth maxWidth="sm">
      <form onSubmit={soumettre} noValidate>
        <DialogTitle>{station ? `Modifier la station ${station.code}` : 'Nouvelle station'}</DialogTitle>
        <DialogContent>
          {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' }, pt: 1 }}>
            <ChampTexte control={control} nom="code" libelle="Code" disabled={!!station} autoFocus={!station} aide={station ? 'Non modifiable' : 'Ex. : NIA'} />
            <ChampTexte control={control} nom="nom" libelle="Nom" autoFocus={!!station} />
            <ChampTexte control={control} nom="type" libelle="Type" select>
              {Object.entries(TYPES_STATION).map(([v, l]) => (
                <MenuItem key={v} value={v}>{l}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="regionCode" libelle="Région" select>
              {(regions.data ?? []).map((r) => (
                <MenuItem key={r.code} value={r.code}>{r.nom}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="modeExploitation" libelle="Mode d'exploitation" select>
              {Object.entries(MODES_EXPLOITATION).map(([v, l]) => (
                <MenuItem key={v} value={v}>{l}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="localite" libelle="Localité" />
            <ChampTexte control={control} nom="latitude" libelle="Latitude" aide="Ex. : 14.7167" />
            <ChampTexte control={control} nom="longitude" libelle="Longitude" aide="Ex. : -17.4677" />
            <ChampTexte
              control={control}
              nom="dateOuverture"
              libelle="Date d'ouverture"
              type="date"
              slotProps={{ inputLabel: { shrink: true } }}
            />
            {station && <ChampInterrupteur control={control} nom="actif" libelle="Station active" />}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => onFermer()}>Annuler</Button>
          <Button type="submit" variant="contained" loading={enregistrement.isPending}>
            Enregistrer
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
