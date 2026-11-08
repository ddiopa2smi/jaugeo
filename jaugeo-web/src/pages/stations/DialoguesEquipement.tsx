import { zodResolver } from '@hookform/resolvers/zod'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useForm, type DefaultValues, type FieldValues, type UseFormReturn } from 'react-hook-form'
import { z } from 'zod'
import { api, messageErreur } from '../../api/client'
import { cles, useProduits } from '../../api/requetes'
import type { Cuve, Pistolet, Pompe } from '../../api/types'
import { ChampInterrupteur } from '../../shared/ChampInterrupteur'
import { ChampTexte } from '../../shared/composants'
import { reporterErreursServeur } from '../../shared/formulaires'
import { versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const code = z.string().trim().regex(/^[A-Za-z0-9_]{1,10}$/, '1 à 10 caractères : lettres, chiffres ou _')
const quantite = (obligatoire: boolean) =>
  z.string().refine((v) => {
    if (v.trim() === '') return !obligatoire
    const n = versNombre(v)
    return n !== null && n >= 0
  }, obligatoire ? 'Nombre positif obligatoire' : 'Nombre positif attendu')

/** Squelette commun : formulaire dans un dialogue, mutation, erreurs serveur reportées sur les champs. */
function DialogueFormulaire<T extends FieldValues, R>({
  titre,
  schema,
  valeurs,
  envoyer,
  succes,
  stationId,
  onFermer,
  children,
}: {
  titre: string
  schema: z.ZodType<T, T>
  valeurs: DefaultValues<T>
  envoyer: (v: T) => Promise<R>
  succes: string
  stationId: string
  onFermer: () => void
  children: (form: UseFormReturn<T>) => ReactNode
}) {
  const queryClient = useQueryClient()
  const [erreur, setErreur] = useState<string | null>(null)
  const form = useForm<T>({ resolver: zodResolver(schema), defaultValues: valeurs })
  const mutation = useMutation({
    mutationFn: envoyer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cles.equipements(stationId) })
      notifier(succes)
      onFermer()
    },
    onError: (e) => {
      if (!reporterErreursServeur(e, form.setError)) setErreur(messageErreur(e))
    },
  })
  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="xs">
      <form
        noValidate
        onSubmit={form.handleSubmit((v) => {
          setErreur(null)
          mutation.mutate(v)
        })}
      >
        <DialogTitle>{titre}</DialogTitle>
        <DialogContent>
          {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
          <Stack spacing={2} sx={{ pt: 1 }}>
            {children(form)}
          </Stack>
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

const schemaCuve = z.object({
  code,
  produitCode: z.string().min(1, 'Choisissez un produit'),
  capaciteLitres: quantite(false),
  creuxSecuriteLitres: quantite(false),
  actif: z.boolean(),
})

export function DialogueCuve({ stationId, cuve, onFermer }: { stationId: string; cuve?: Cuve; onFermer: () => void }) {
  const produits = useProduits()
  const produitsCuve = (produits.data ?? []).filter((p) => p.unite === 'LITRE' && (p.actif || p.code === cuve?.produitCode))
  return (
    <DialogueFormulaire
      titre={cuve ? `Modifier la cuve ${cuve.code}` : 'Nouvelle cuve'}
      schema={schemaCuve}
      stationId={stationId}
      onFermer={onFermer}
      succes={cuve ? 'Cuve mise à jour.' : 'Cuve ajoutée.'}
      valeurs={{
        code: cuve?.code ?? '',
        produitCode: cuve?.produitCode ?? '',
        capaciteLitres: cuve?.capaciteLitres?.toString() ?? '',
        creuxSecuriteLitres: cuve?.creuxSecuriteLitres?.toString() ?? '',
        actif: cuve?.actif ?? true,
      }}
      envoyer={(v) =>
        cuve
          ? api.put<Cuve>(`/cuves/${cuve.id}`, {
              capaciteLitres: versNombre(v.capaciteLitres),
              creuxSecuriteLitres: versNombre(v.creuxSecuriteLitres),
              actif: v.actif,
              version: cuve.version,
            })
          : api.post<Cuve>(`/stations/${stationId}/cuves`, {
              code: v.code.toUpperCase(),
              produitCode: v.produitCode,
              capaciteLitres: versNombre(v.capaciteLitres),
              creuxSecuriteLitres: versNombre(v.creuxSecuriteLitres),
            })
      }
    >
      {({ control }) => (
        <>
          <ChampTexte control={control} nom="code" libelle="Code" disabled={!!cuve} autoFocus={!cuve} aide={cuve ? undefined : 'Ex. : C3'} />
          <ChampTexte control={control} nom="produitCode" libelle="Produit stocké" select disabled={!!cuve}>
            {produitsCuve.map((p) => (
              <MenuItem key={p.code} value={p.code}>{p.libelle}</MenuItem>
            ))}
          </ChampTexte>
          <ChampTexte control={control} nom="capaciteLitres" libelle="Capacité (litres)" aide="Volume utile de la cuve" />
          <ChampTexte control={control} nom="creuxSecuriteLitres" libelle="Creux de sécurité (litres)" aide="Volume minimal à ne pas pomper" />
          {cuve && <ChampInterrupteur control={control} nom="actif" libelle="Cuve en service" />}
        </>
      )}
    </DialogueFormulaire>
  )
}

const schemaPompe = z.object({ code, marque: z.string().max(50), numeroSerie: z.string().max(50) })

export function DialoguePompe({ stationId, onFermer }: { stationId: string; onFermer: () => void }) {
  return (
    <DialogueFormulaire
      titre="Nouvelle pompe"
      schema={schemaPompe}
      stationId={stationId}
      onFermer={onFermer}
      succes="Pompe ajoutée."
      valeurs={{ code: '', marque: '', numeroSerie: '' }}
      envoyer={(v) =>
        api.post<Pompe>(`/stations/${stationId}/pompes`, {
          code: v.code.toUpperCase(),
          marque: videVersNull(v.marque),
          numeroSerie: videVersNull(v.numeroSerie),
        })
      }
    >
      {({ control }) => (
        <>
          <ChampTexte control={control} nom="code" libelle="Code" autoFocus aide="Ex. : P3" />
          <ChampTexte control={control} nom="marque" libelle="Marque" />
          <ChampTexte control={control} nom="numeroSerie" libelle="Numéro de série" />
        </>
      )}
    </DialogueFormulaire>
  )
}

const schemaPistolet = z.object({
  code,
  libelle: z.string().trim().min(1, 'Le libellé est obligatoire').max(50),
  cuveId: z.string().min(1, 'Choisissez la cuve qui alimente ce pistolet'),
  indexMiseEnService: quantite(true),
  actif: z.boolean(),
})

export function DialoguePistolet({
  stationId,
  pompe,
  pistolet,
  cuves,
  onFermer,
}: {
  stationId: string
  pompe: Pompe
  pistolet?: Pistolet
  cuves: Cuve[]
  onFermer: () => void
}) {
  return (
    <DialogueFormulaire
      titre={pistolet ? `Modifier le pistolet ${pistolet.code}` : `Nouveau pistolet sur ${pompe.code}`}
      schema={schemaPistolet}
      stationId={stationId}
      onFermer={onFermer}
      succes={pistolet ? 'Pistolet mis à jour.' : 'Pistolet ajouté.'}
      valeurs={{
        code: pistolet?.code ?? '',
        libelle: pistolet?.libelle ?? '',
        cuveId: pistolet?.cuveId ?? '',
        indexMiseEnService: pistolet?.indexMiseEnService?.toString() ?? '',
        actif: pistolet?.actif ?? true,
      }}
      envoyer={(v) =>
        pistolet
          ? api.put<Pistolet>(`/pistolets/${pistolet.id}`, { libelle: v.libelle.trim(), actif: v.actif, version: pistolet.version })
          : api.post<Pistolet>(`/pompes/${pompe.id}/pistolets`, {
              code: v.code.toUpperCase(),
              libelle: v.libelle.trim(),
              cuveId: v.cuveId,
              indexMiseEnService: versNombre(v.indexMiseEnService),
            })
      }
    >
      {({ control }) => (
        <>
          <ChampTexte control={control} nom="code" libelle="Code" disabled={!!pistolet} autoFocus={!pistolet} aide={pistolet ? undefined : 'Ex. : G3'} />
          <ChampTexte control={control} nom="libelle" libelle="Libellé" autoFocus={!!pistolet} aide="Ex. : Gasoil côté route" />
          <ChampTexte control={control} nom="cuveId" libelle="Cuve d'alimentation" select disabled={!!pistolet}>
            {cuves
              .filter((c) => c.actif || c.id === pistolet?.cuveId)
              .map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.code} — {c.produitLibelle}
                </MenuItem>
              ))}
          </ChampTexte>
          <ChampTexte
            control={control}
            nom="indexMiseEnService"
            libelle="Index à la mise en service"
            disabled={!!pistolet}
            aide={pistolet ? 'Non modifiable : sert de base au calcul des ventes' : 'Lecture du compteur totalisateur'}
          />
          {pistolet && <ChampInterrupteur control={control} nom="actif" libelle="Pistolet en service" />}
        </>
      )}
    </DialogueFormulaire>
  )
}
