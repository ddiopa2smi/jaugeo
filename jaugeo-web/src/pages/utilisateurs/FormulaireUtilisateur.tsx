import { zodResolver } from '@hookform/resolvers/zod'
import Alert from '@mui/material/Alert'
import Autocomplete from '@mui/material/Autocomplete'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api, messageErreur } from '../../api/client'
import { cles, useRoles, useStations } from '../../api/requetes'
import type { ReponseMotDePasseProvisoire, Utilisateur } from '../../api/types'
import { ChampInterrupteur } from '../../shared/ChampInterrupteur'
import { ChampTexte } from '../../shared/composants'
import { reporterErreursServeur } from '../../shared/formulaires'
import { PERIMETRES, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

/** Rôles exercés dans une station : périmètre limité par défaut. */
const ROLES_DE_STATION = ['GERANT', 'POMPISTE']

const schema = z
  .object({
    identifiant: z
      .string()
      .trim()
      .regex(/^[a-zA-Z0-9._-]{3,50}$/, '3 à 50 caractères : lettres, chiffres, point, tiret ou souligné'),
    nomComplet: z.string().trim().min(1, 'Le nom est obligatoire').max(100),
    email: z.union([z.literal(''), z.email('Adresse e-mail invalide')]),
    telephone: z.string().max(30),
    role: z.string().min(1, 'Choisissez un rôle'),
    perimetre: z.enum(['RESEAU', 'STATIONS']),
    stationIds: z.array(z.number()),
    actif: z.boolean(),
  })
  .refine((v) => v.perimetre === 'RESEAU' || v.stationIds.length > 0, {
    path: ['stationIds'],
    message: 'Désignez au moins une station.',
  })
type Saisie = z.infer<typeof schema>

/**
 * Création ou modification d'un compte. À la création, le serveur renvoie un mot de passe
 * provisoire, transmis à onCree pour être affiché une seule fois.
 */
export function FormulaireUtilisateur({
  utilisateur,
  onFermer,
  onCree,
}: {
  utilisateur?: Utilisateur
  onFermer: () => void
  onCree: (r: ReponseMotDePasseProvisoire) => void
}) {
  const roles = useRoles()
  const stations = useStations()
  const queryClient = useQueryClient()
  const [erreur, setErreur] = useState<string | null>(null)
  const { control, handleSubmit, setError, setValue, subscribe } = useForm<Saisie>({
    resolver: zodResolver(schema),
    defaultValues: {
      identifiant: utilisateur?.identifiant ?? '',
      nomComplet: utilisateur?.nomComplet ?? '',
      email: utilisateur?.email ?? '',
      telephone: utilisateur?.telephone ?? '',
      role: utilisateur?.role ?? '',
      perimetre: utilisateur?.perimetre ?? 'STATIONS',
      stationIds: utilisateur?.stationIds ?? [],
      actif: utilisateur?.actif ?? true,
    },
  })
  const perimetre = useWatch({ control, name: 'perimetre' })
  const role = useWatch({ control, name: 'role' })
  const descriptionRole = roles.data?.find((r) => r.code === role)?.description

  // À la création, le choix du rôle propose le périmètre habituel (modifiable ensuite).
  useEffect(() => {
    if (utilisateur) return
    return subscribe({
      name: 'role',
      formState: { values: true },
      callback: ({ values }) =>
        setValue('perimetre', ROLES_DE_STATION.includes(values.role) ? 'STATIONS' : 'RESEAU', { shouldValidate: true }),
    })
  }, [utilisateur, subscribe, setValue])

  const mutation = useMutation({
    mutationFn: async (v: Saisie) => {
      const commun = {
        nomComplet: v.nomComplet.trim(),
        email: videVersNull(v.email),
        telephone: videVersNull(v.telephone),
        role: v.role,
        perimetre: v.perimetre,
        stationIds: v.perimetre === 'RESEAU' ? [] : v.stationIds,
      }
      if (utilisateur) {
        await api.put<Utilisateur>(`/utilisateurs/${utilisateur.id}`, { ...commun, actif: v.actif, version: utilisateur.version })
        return null
      }
      return api.post<ReponseMotDePasseProvisoire>('/utilisateurs', { ...commun, identifiant: v.identifiant.trim() })
    },
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: cles.utilisateurs })
      if (r) onCree(r)
      else notifier('Utilisateur mis à jour.')
      onFermer()
    },
    onError: (e) => {
      if (!reporterErreursServeur(e, setError)) setErreur(messageErreur(e))
    },
  })

  const listeStations = stations.data ?? []

  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="sm">
      <form
        noValidate
        onSubmit={handleSubmit((v) => {
          setErreur(null)
          mutation.mutate(v)
        })}
      >
        <DialogTitle>{utilisateur ? `Modifier ${utilisateur.identifiant}` : 'Nouvel utilisateur'}</DialogTitle>
        <DialogContent>
          {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, pt: 1 }}>
            <ChampTexte
              control={control}
              nom="identifiant"
              libelle="Identifiant de connexion"
              disabled={!!utilisateur}
              autoFocus={!utilisateur}
              aide={utilisateur ? 'Non modifiable' : 'Ex. : fdiop'}
            />
            <ChampTexte control={control} nom="nomComplet" libelle="Nom complet" autoFocus={!!utilisateur} />
            <ChampTexte control={control} nom="email" libelle="E-mail" type="email" />
            <ChampTexte control={control} nom="telephone" libelle="Téléphone" />
            <ChampTexte control={control} nom="role" libelle="Rôle" select aide={descriptionRole ?? undefined}>
              {(roles.data ?? []).map((r) => (
                <MenuItem key={r.code} value={r.code}>{r.libelle}</MenuItem>
              ))}
            </ChampTexte>
            <ChampTexte control={control} nom="perimetre" libelle="Périmètre" select>
              {Object.entries(PERIMETRES).map(([v, l]) => (
                <MenuItem key={v} value={v}>{l}</MenuItem>
              ))}
            </ChampTexte>
            {perimetre === 'STATIONS' && (
              <Box sx={{ gridColumn: '1 / -1' }}>
                <Controller
                  control={control}
                  name="stationIds"
                  render={({ field, fieldState }) => (
                    <Autocomplete
                      multiple
                      size="small"
                      options={listeStations}
                      loading={stations.isLoading}
                      getOptionLabel={(s) => `${s.code} — ${s.nom}`}
                      isOptionEqualToValue={(a, b) => a.numero === b.numero}
                      value={listeStations.filter((s) => field.value.includes(s.numero))}
                      onChange={(_, choix) => field.onChange(choix.map((s) => s.numero))}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          label="Stations accessibles"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? "L'utilisateur ne verra que ces stations."}
                        />
                      )}
                    />
                  )}
                />
              </Box>
            )}
            {utilisateur && (
              <Box sx={{ gridColumn: '1 / -1' }}>
                <ChampInterrupteur control={control} nom="actif" libelle="Compte actif" />
                <Typography variant="caption" color="text.secondary" component="p">
                  Un compte désactivé ne peut plus se connecter ; ses sessions ouvertes prennent fin sous 15 minutes au plus.
                </Typography>
              </Box>
            )}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onFermer}>Annuler</Button>
          <Button type="submit" variant="contained" loading={mutation.isPending}>
            {utilisateur ? 'Enregistrer' : 'Créer le compte'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
