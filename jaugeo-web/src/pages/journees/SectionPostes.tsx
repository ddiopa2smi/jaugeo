import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as LienRouteur } from 'react-router'
import { api } from '../../api/client'
import { cles, rafraichirJournee, usePostes } from '../../api/requetes'
import type { PosteJournee, PostesJournee, SaisiePoste, TypePoste } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { confirmerPuis } from '../../shared/confirmation'
import { formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { COULEUR_ECART, formaterEcart, LIBELLES_POSTE, POSTES } from '../../shared/personnel'

interface FeuilleSaisie {
  employeId: string
  /** Index de fin de poste par pistolet ; vide = pistolet non tenu par ce pompiste */
  index: Record<string, string>
  remises: Record<string, string>
  especes: string
  electronique: string
  bons: string
  cartes: string
  observation: string
}

const texte = (v: number | null | undefined) => (v === null || v === undefined || v === 0 ? '' : String(v))

const feuilleVide = (employeId = ''): FeuilleSaisie => ({
  employeId,
  index: {},
  remises: {},
  especes: '',
  electronique: '',
  bons: '',
  cartes: '',
  observation: '',
})

/** Index de début d'un pistolet pour un poste : fin du poste précédent qui l'a relevé, sinon départ de la journée. */
function indexDebut(p: PostesJournee, type: TypePoste, pistoletId: string): number {
  let debut = p.pistolets.find((x) => x.pistoletId === pistoletId)?.indexDepart ?? 0
  for (const poste of p.postes) {
    if (POSTES.indexOf(poste.poste) >= POSTES.indexOf(type)) break
    for (const f of poste.feuilles) {
      const l = f.lignes.find((x) => x.pistoletId === pistoletId)
      if (l) debut = l.indexFin
    }
  }
  return debut
}

function saisieInitiale(p: PostesJournee, type: TypePoste): FeuilleSaisie[] {
  const existant = p.postes.find((x) => x.poste === type)
  if (existant) {
    return existant.feuilles.map((f) => ({
      employeId: f.employeId,
      index: Object.fromEntries(f.lignes.map((l) => [l.pistoletId, String(l.indexFin)])),
      remises: Object.fromEntries(f.lignes.filter((l) => l.remiseCuve > 0).map((l) => [l.pistoletId, String(l.remiseCuve)])),
      especes: texte(f.especes),
      electronique: texte(f.electronique),
      bons: texte(f.bons),
      cartes: texte(f.cartes),
      observation: f.observation ?? '',
    }))
  }
  // Nouveau poste : les pompistes prévus au planning, sinon une feuille vide
  const prevus = p.planning.filter((c) => c.poste === type && p.employes.some((e) => e.id === c.employeId))
  return prevus.length > 0 ? prevus.map((c) => feuilleVide(c.employeId)) : [feuilleVide()]
}

function DialoguePoste({ postes, type, onFermer }: { postes: PostesJournee; type: TypePoste; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const existant = postes.postes.find((x) => x.poste === type)
  const [feuilles, setFeuilles] = useState<FeuilleSaisie[]>(() => saisieInitiale(postes, type))
  const [observation, setObservation] = useState(existant?.observation ?? '')
  const [erreur, setErreur] = useState<string | null>(null)

  const enregistrement = useMutation({
    mutationFn: (corps: SaisiePoste) => api.put<PostesJournee>(`/journees/${postes.journeeId}/postes/${type}`, corps),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.postes(postes.journeeId), r)
      rafraichirJournee(queryClient, postes.journeeId, cles.postes(postes.journeeId))
      notifier(`Poste du ${LIBELLES_POSTE[type].toLowerCase()} enregistré.`)
      onFermer()
    },
  })

  const modifier = (i: number, f: Partial<FeuilleSaisie>) => setFeuilles((liste) => liste.map((x, k) => (k === i ? { ...x, ...f } : x)))

  const calcul = (f: FeuilleSaisie) => {
    let du = 0
    let litres = 0
    for (const pi of postes.pistolets) {
      const fin = versNombre(f.index[pi.pistoletId])
      if (fin === null) continue
      const l = fin - indexDebut(postes, type, pi.pistoletId) - (versNombre(f.remises[pi.pistoletId]) ?? 0)
      litres += l
      du += Math.round(l * (pi.prixUnitaire ?? 0))
    }
    const remis = [f.especes, f.electronique, f.bons, f.cartes].reduce((t, v) => t + (versNombre(v) ?? 0), 0)
    return { litres, du, remis, ecart: remis - du }
  }

  const valider = () => {
    const tenus = new Set<string>()
    const pompistes = new Set<string>()
    const corps: SaisiePoste = { version: postes.version, observation: videVersNull(observation), feuilles: [] }
    for (const f of feuilles) {
      if (!f.employeId) return setErreur('Choisissez le pompiste de chaque feuille de caisse.')
      if (pompistes.has(f.employeId)) return setErreur('Un pompiste a deux feuilles pour ce poste : regroupez ses pistolets.')
      pompistes.add(f.employeId)
      const lignes = []
      for (const pi of postes.pistolets) {
        const fin = versNombre(f.index[pi.pistoletId])
        if (fin === null) continue
        if (tenus.has(pi.pistoletId)) return setErreur(`Le pistolet ${pi.code} est tenu par deux pompistes : un seul pompiste par pistolet.`)
        tenus.add(pi.pistoletId)
        lignes.push({ pistoletId: pi.pistoletId, indexFin: fin, remiseCuve: versNombre(f.remises[pi.pistoletId]) })
      }
      if (lignes.length === 0) return setErreur("Saisissez l'index de fin d'au moins un pistolet par pompiste.")
      corps.feuilles.push({
        employeId: f.employeId,
        especes: versNombre(f.especes) ?? 0,
        electronique: versNombre(f.electronique) ?? 0,
        bons: versNombre(f.bons) ?? 0,
        cartes: versNombre(f.cartes) ?? 0,
        observation: videVersNull(f.observation),
        lignes,
      })
    }
    setErreur(null)
    enregistrement.mutate(corps)
  }

  const champNombre = { inputMode: 'decimal' as const, autoComplete: 'off', style: { textAlign: 'right' as const } }

  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>Poste du {LIBELLES_POSTE[type].toLowerCase()} : feuilles de caisse</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Relevez l'index de chaque pistolet à la fin du poste, sur la feuille du pompiste qui l'a tenu. Un pistolet n'a qu'un
          pompiste par poste.
        </Typography>
        <Stack spacing={2} divider={<Divider flexItem />}>
          {feuilles.map((f, i) => {
            const c = calcul(f)
            const n = i + 1
            return (
              <Box key={i} data-testid={`feuille-${n}`}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                  <TextField
                    select
                    size="small"
                    label="Pompiste"
                    value={f.employeId}
                    onChange={(e) => modifier(i, { employeId: e.target.value })}
                    sx={{ minWidth: 240, flexGrow: 1 }}
                    slotProps={{ htmlInput: { 'aria-label': `Pompiste ${n}` } }}
                  >
                    {postes.employes.map((e) => (
                      <MenuItem key={e.id} value={e.id}>
                        {e.nomComplet}
                      </MenuItem>
                    ))}
                  </TextField>
                  {feuilles.length > 1 && (
                    <IconButton aria-label={`Retirer la feuille ${n}`} onClick={() => setFeuilles((l) => l.filter((_x, k) => k !== i))}>
                      <DeleteOutlineIcon />
                    </IconButton>
                  )}
                </Stack>
                <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, mb: 1.5 }}>
                  {postes.pistolets.map((pi) => (
                    <Box key={pi.pistoletId} sx={{ display: 'contents' }}>
                      <TextField
                        size="small"
                        label={`Index de fin ${pi.code}`}
                        helperText={`${pi.produitCode} · début ${formaterNombre(indexDebut(postes, type, pi.pistoletId))}`}
                        value={f.index[pi.pistoletId] ?? ''}
                        onChange={(e) => modifier(i, { index: { ...f.index, [pi.pistoletId]: e.target.value } })}
                        slotProps={{ htmlInput: { ...champNombre, 'aria-label': `Index de fin ${pi.code} (pompiste ${n})` } }}
                      />
                      <TextField
                        size="small"
                        label={`Remise ${pi.code} (L)`}
                        value={f.remises[pi.pistoletId] ?? ''}
                        disabled={!f.index[pi.pistoletId]}
                        onChange={(e) => modifier(i, { remises: { ...f.remises, [pi.pistoletId]: e.target.value } })}
                        slotProps={{ htmlInput: { ...champNombre, 'aria-label': `Remise en cuve ${pi.code} (pompiste ${n})` } }}
                      />
                    </Box>
                  ))}
                </Box>
                <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' } }}>
                  {(
                    [
                      ['especes', 'Espèces remises'],
                      ['electronique', 'Wave, OM, carte'],
                      ['bons', 'Bons clients'],
                      ['cartes', 'Cartes carburant'],
                    ] as const
                  ).map(([cle, libelle]) => (
                    <TextField
                      key={cle}
                      size="small"
                      label={libelle}
                      value={f[cle]}
                      onChange={(e) => modifier(i, { [cle]: e.target.value })}
                      slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off', style: { textAlign: 'right' }, 'aria-label': `${libelle} (pompiste ${n})` } }}
                    />
                  ))}
                </Box>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0.5, sm: 3 }} sx={{ mt: 1.5 }} data-testid={`calcul-${n}`}>
                  <Typography variant="body2">
                    {formaterNombre(c.litres)} L · dû {formaterPrix(c.du)}
                  </Typography>
                  <Typography variant="body2">Remis {formaterPrix(c.remis)}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: COULEUR_ECART(c.ecart) }}>
                    Écart {formaterEcart(c.ecart)}
                  </Typography>
                </Stack>
              </Box>
            )
          })}
        </Stack>
        <Button startIcon={<AddIcon />} onClick={() => setFeuilles((l) => [...l, feuilleVide()])} sx={{ mt: 2 }}>
          Ajouter un pompiste
        </Button>
        <TextField
          label="Observation du poste"
          value={observation}
          onChange={(e) => setObservation(e.target.value)}
          fullWidth
          size="small"
          sx={{ mt: 2 }}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
        {erreur && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {erreur}
          </Alert>
        )}
        <Box sx={{ mt: 2 }}>
          <AlerteErreur erreur={enregistrement.error} />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={valider} disabled={enregistrement.isPending}>
          Enregistrer le poste
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function CartePoste({ poste, modifiable, onModifier, onRetirer }: { poste: PosteJournee; modifiable: boolean; onModifier: () => void; onRetirer: () => void }) {
  return (
    <Box data-testid={`poste-${poste.poste}`}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="subtitle2">
          {poste.libelle} · dû {formaterPrix(poste.du)} · remis {formaterPrix(poste.remis)}
        </Typography>
        {modifiable && (
          <Stack direction="row">
            <IconButton size="small" aria-label={`Modifier le poste du ${poste.libelle.toLowerCase()}`} onClick={onModifier}>
              <EditIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" aria-label={`Retirer le poste du ${poste.libelle.toLowerCase()}`} onClick={onRetirer}>
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </Stack>
        )}
      </Stack>
      {poste.feuilles.map((f) => (
        <Stack key={f.id} direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0, sm: 2 }} sx={{ py: 0.5, alignItems: { sm: 'baseline' } }}>
          <Typography variant="body2" sx={{ minWidth: 160, fontWeight: 500 }}>
            {f.employe}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
            {f.lignes.map((l) => `${l.pistoletCode} ${formaterNombre(l.indexDebut)} → ${formaterNombre(l.indexFin)}`).join(' · ')} ·{' '}
            {formaterNombre(f.litres)} L
          </Typography>
          <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
            dû {formaterPrix(f.du)} · remis {formaterPrix(f.remis)}
          </Typography>
          <Typography
            variant="body2"
            sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', fontWeight: 600, minWidth: 90, textAlign: { sm: 'right' }, color: COULEUR_ECART(f.ecart) }}
            data-testid={`ecart-${poste.poste}-${f.employe}`}
          >
            {formaterEcart(f.ecart)}
          </Typography>
        </Stack>
      ))}
      {poste.observation && (
        <Typography variant="caption" color="text.secondary">
          {poste.observation}
        </Typography>
      )}
    </Box>
  )
}

/**
 * Feuilles de caisse par pompiste : à chaque changement de poste, index de fin par pistolet et caisse remise.
 * Section affichée seulement dans les stations qui tiennent ces feuilles.
 */
export function SectionPostes({ journeeId }: { journeeId: string }) {
  const requete = usePostes(journeeId)
  const queryClient = useQueryClient()
  const [edition, setEdition] = useState<TypePoste | null>(null)
  const p = requete.data

  const retrait = useMutation({
    mutationFn: (type: TypePoste) => api.delete<PostesJournee>(`/journees/${journeeId}/postes/${type}?version=${p!.version}`),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.postes(journeeId), r)
      rafraichirJournee(queryClient, journeeId, cles.postes(journeeId))
      notifier('Poste retiré.')
    },
  })

  if (!p) return <AlerteErreur erreur={requete.error} />
  if (!p.actif && p.postes.length === 0) return null

  const manquants = POSTES.filter((t) => !p.postes.some((x) => x.poste === t))

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }} aria-label="Feuilles de caisse par pompiste">
      <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Feuilles de caisse par pompiste
        </Typography>
        {p.postes.length > 0 && (
          <Typography variant="body2" sx={{ fontWeight: 600, color: COULEUR_ECART(p.totaux.ecart) }} data-testid="ecart-postes">
            Écart {formaterEcart(p.totaux.ecart)}
          </Typography>
        )}
      </Stack>
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1 }}>
        Suivi individuel : il ne change pas le montant à verser par le gérant.
      </Typography>
      {p.modifiable && p.employes.length === 0 && (
        <Alert severity="info" sx={{ mb: 1 }}>
          Aucun employé actif dans cette station : créez les pompistes dans la page{' '}
          <LienRouteur to="/personnel">Personnel</LienRouteur>.
        </Alert>
      )}
      <Stack spacing={1.5} divider={<Divider flexItem />}>
        {p.postes.map((poste) => (
          <CartePoste
            key={poste.poste}
            poste={poste}
            modifiable={p.modifiable}
            onModifier={() => setEdition(poste.poste)}
            onRetirer={() =>
              confirmerPuis(
                {
                  titre: 'Supprimer le poste ?',
                  message: `Le poste du ${poste.libelle.toLowerCase()} et ses ${poste.feuilles.length} feuille(s) de caisse seront retirés de la journée.`,
                },
                () => retrait.mutate(poste.poste),
              )
            }
          />
        ))}
      </Stack>
      {p.problemes.length > 0 && (
        <Alert severity="warning" sx={{ mt: 1.5 }} data-testid="problemes-postes">
          {p.problemes.map((x) => (
            <div key={x}>{x}</div>
          ))}
        </Alert>
      )}
      <AlerteErreur erreur={retrait.error} />
      {p.modifiable && manquants.length > 0 && p.employes.length > 0 && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.5 }}>
          {manquants.map((t) => (
            <Button key={t} variant="outlined" startIcon={<AddIcon />} onClick={() => setEdition(t)}>
              Poste du {LIBELLES_POSTE[t].toLowerCase()}
            </Button>
          ))}
        </Stack>
      )}
      {edition && <DialoguePoste key={`${edition}-${p.version}`} postes={p} type={edition} onFermer={() => setEdition(null)} />}
    </Paper>
  )
}
