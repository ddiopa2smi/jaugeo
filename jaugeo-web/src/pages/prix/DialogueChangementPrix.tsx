import Alert from '@mui/material/Alert'
import Autocomplete from '@mui/material/Autocomplete'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import InputAdornment from '@mui/material/InputAdornment'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api, ErreurApi, messageErreur } from '../../api/client'
import { cles, useStations } from '../../api/requetes'
import type { ChangementPrix, DemandeChangementPrix, LigneGrille, Station } from '../../api/types'
import { dakarVersIso, formaterPrix, isoVersDakar, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { useMaintenant } from '../../shared/temps'

interface ProduitPrixable {
  code: string
  libelle: string
  min: number | null
  max: number | null
}

/** Produits présents dans la grille, avec la fourchette des prix actuels du réseau. */
function produitsDeLaGrille(grille: LigneGrille[]): ProduitPrixable[] {
  const parCode = new Map<string, ProduitPrixable>()
  for (const l of grille) {
    const p = parCode.get(l.produitCode) ?? { code: l.produitCode, libelle: l.produitLibelle, min: null, max: null }
    if (l.prix !== null) {
      p.min = p.min === null ? l.prix : Math.min(p.min, l.prix)
      p.max = p.max === null ? l.prix : Math.max(p.max, l.prix)
    }
    parCode.set(l.produitCode, p)
  }
  return [...parCode.values()]
}

function prixActuel(p: ProduitPrixable): string {
  if (p.min === null || p.max === null) return 'Aucun prix actuel'
  return p.min === p.max ? `Actuel : ${formaterPrix(p.min)}` : `Actuel : ${formaterPrix(p.min)} à ${formaterPrix(p.max)}`
}

/** Demain 00:00 à Dakar : l'usage est d'appliquer les nouveaux prix à l'ouverture. */
function demainMinuit(): string {
  const d = new Date()
  d.setUTCDate(d.getUTCDate() + 1)
  d.setUTCHours(0, 0, 0, 0)
  return isoVersDakar(d)
}

export function DialogueChangementPrix({ grille, onFermer }: { grille: LigneGrille[]; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const maintenant = useMaintenant()
  const stations = useStations()
  const produits = useMemo(() => produitsDeLaGrille(grille), [grille])
  const stationsActives = (stations.data ?? []).filter((s) => s.actif)

  const [debut, setDebut] = useState(demainMinuit)
  const [motif, setMotif] = useState('')
  const [reference, setReference] = useState('')
  const [cibles, setCibles] = useState<Station[]>([])
  const [prix, setPrix] = useState<Record<string, string>>({})
  const [tente, setTente] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  /** Message du serveur demandant de confirmer une variation de plus de 20 %. */
  const [aConfirmer, setAConfirmer] = useState<string | null>(null)

  // Toute modification annule une confirmation en attente : on confirme ce qu'on a relu.
  const modifier = <T,>(maj: (v: T) => void) => (v: T) => {
    setAConfirmer(null)
    maj(v)
  }

  const lignes = produits
    .map((p) => ({ produitCode: p.code, texte: prix[p.code] ?? '' }))
    .filter((l) => l.texte.trim() !== '')
  const erreursPrix = Object.fromEntries(
    lignes.map((l) => {
      const n = versNombre(l.texte)
      return [l.produitCode, n === null || n <= 0 ? 'Prix positif attendu' : null]
    }),
  )
  const erreurs = {
    debut: debut ? null : 'Date d’effet obligatoire',
    motif: motif.trim() ? null : 'Motif obligatoire (arrêté, note de la direction…)',
    prix: lignes.length === 0 ? 'Saisissez au moins un nouveau prix.' : null,
  }
  const valide = !erreurs.debut && !erreurs.motif && !erreurs.prix && Object.values(erreursPrix).every((e) => !e)

  const mutation = useMutation({
    mutationFn: (confirmer: boolean) =>
      api.post<ChangementPrix>('/prix/changements', {
        debut: dakarVersIso(debut),
        motif: motif.trim(),
        reference: reference.trim() || null,
        stations: cibles.map((s) => s.id),
        prix: lignes.map((l) => ({ produitCode: l.produitCode, prixUnitaire: versNombre(l.texte)! })),
        confirmerVariation: confirmer,
      } satisfies DemandeChangementPrix),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: cles.prix })
      const nb = new Set(c.lignes.map((l) => l.stationId)).size
      // Sur tout le réseau, ignorer les stations qui ne vendent pas le produit est normal ;
      // sur des stations désignées, c'est probablement une erreur de sélection : on le signale.
      if (c.ignores.length > 0 && cibles.length > 0) {
        notifier(`Changement enregistré pour ${nb} station(s). Non appliqué : ${c.ignores.join(' ; ')}.`, 'warning')
      } else {
        notifier(`Changement enregistré pour ${nb} station(s).`)
      }
      onFermer()
    },
    onError: (e) => {
      if (e instanceof ErreurApi && e.code === 'VARIATION_A_CONFIRMER') setAConfirmer(e.message)
      else setErreur(messageErreur(e))
    },
  })

  const envoyer = () => {
    setTente(true)
    setErreur(null)
    if (!valide) return
    mutation.mutate(aConfirmer !== null)
  }

  const passe = debut !== '' && Date.parse(dakarVersIso(debut)) < maintenant

  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="sm">
      <DialogTitle>Changement de prix</DialogTitle>
      <DialogContent>
        {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              id="champ-debut"
              label="Date et heure d'effet (Dakar)"
              type="datetime-local"
              value={debut}
              onChange={(e) => modifier(setDebut)(e.target.value)}
              error={tente && !!erreurs.debut}
              helperText={(tente && erreurs.debut) || (passe ? 'Date passée : le prix s’applique rétroactivement.' : ' ')}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ flex: 1 }}
            />
            <TextField
              id="champ-reference"
              label="Référence (arrêté, note…)"
              value={reference}
              onChange={(e) => modifier(setReference)(e.target.value)}
              helperText=" "
              sx={{ flex: 1 }}
            />
          </Stack>
          <TextField
            id="champ-motif"
            label="Motif"
            value={motif}
            onChange={(e) => modifier(setMotif)(e.target.value)}
            error={tente && !!erreurs.motif}
            helperText={tente && erreurs.motif}
            required
          />
          <Autocomplete
            multiple
            options={stationsActives}
            value={cibles}
            onChange={(_, v) => modifier(setCibles)(v)}
            getOptionLabel={(s) => `${s.code} — ${s.nom}`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => (
              <TextField
                {...params}
                id="champ-stations"
                label="Stations concernées"
                placeholder={cibles.length === 0 ? 'Tout le réseau' : undefined}
                helperText="Laissez vide pour appliquer à toutes les stations actives."
              />
            )}
          />
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Nouveaux prix (laissez vide les produits inchangés)
            </Typography>
            {tente && erreurs.prix && <Alert severity="error" sx={{ mb: 1 }}>{erreurs.prix}</Alert>}
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              {produits.map((p) => (
                <TextField
                  key={p.code}
                  id={`champ-prix-${p.code}`}
                  label={p.libelle}
                  value={prix[p.code] ?? ''}
                  onChange={(e) => modifier(setPrix)({ ...prix, [p.code]: e.target.value })}
                  error={tente && !!erreursPrix[p.code]}
                  helperText={(tente && erreursPrix[p.code]) || prixActuel(p)}
                  slotProps={{
                    htmlInput: { inputMode: 'decimal' },
                    input: { endAdornment: <InputAdornment position="end">F / unité</InputAdornment> },
                  }}
                />
              ))}
            </Box>
          </Box>
          {aConfirmer && (
            <Alert severity="warning" data-testid="variation-a-confirmer">
              {aConfirmer}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" color={aConfirmer ? 'warning' : 'primary'} loading={mutation.isPending} onClick={envoyer}>
          {aConfirmer ? 'Confirmer ces montants' : 'Enregistrer'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
