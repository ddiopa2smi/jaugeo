import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, messageErreur } from '../../api/client'
import { cles } from '../../api/requetes'
import type { CibleEvenement, Evenement, TypeEvenement } from '../../api/types'
import { dakarVersIso, EVENEMENTS_PAR_CIBLE, formaterNombre, isoVersDakar, TYPES_EVENEMENT, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

export interface CibleDialogue {
  cible: CibleEvenement
  id: string
  libelle: string
  /** Pistolet : index attendu avant changement (mise en service ou dernier changement de compteur). */
  indexReference?: number
}

const CHEMINS: Record<CibleEvenement, string> = { PISTOLET: 'pistolets', POMPE: 'pompes', CUVE: 'cuves' }
const changeCompteur = (t: TypeEvenement) => t === 'REMPLACEMENT_TOTALISATEUR' || t === 'REMISE_A_ZERO'

export function DialogueEvenement({ stationId, cible, onFermer }: { stationId: string; cible: CibleDialogue; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const types = EVENEMENTS_PAR_CIBLE[cible.cible]
  const [type, setType] = useState<TypeEvenement>(types[0]!)
  const [date, setDate] = useState(() => isoVersDakar(new Date()))
  const [indexAvant, setIndexAvant] = useState('')
  const [indexApres, setIndexApres] = useState('0')
  const [scelles, setScelles] = useState('')
  const [intervenant, setIntervenant] = useState('')
  const [prochaine, setProchaine] = useState('')
  const [commentaire, setCommentaire] = useState('')
  const [tente, setTente] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  const compteur = changeCompteur(type)
  const avant = versNombre(indexAvant)
  const apres = versNombre(indexApres)
  const erreurs: Record<string, string | null> = {
    date: date ? null : 'Date obligatoire',
    indexAvant: !compteur
      ? null
      : avant === null || avant < 0
        ? 'Index relevé avant l’intervention obligatoire'
        : cible.indexReference !== undefined && avant < cible.indexReference
          ? `Inférieur au dernier index connu (${formaterNombre(cible.indexReference)})`
          : null,
    indexApres: !compteur
      ? null
      : apres === null || apres < 0
        ? 'Index du nouveau compteur obligatoire'
        : type === 'REMISE_A_ZERO' && avant !== null && apres >= avant
          ? 'Après une remise à zéro, l’index doit être inférieur'
          : null,
    scelles: type === 'POSE_SCELLES' && !scelles.trim() ? 'Références des scellés obligatoires' : null,
    intervenant: type === 'ETALONNAGE' && !intervenant.trim() ? 'Organisme ou technicien obligatoire' : null,
    commentaire: compteur && !commentaire.trim() ? 'Motif obligatoire pour un changement de compteur' : null,
  }
  const valide = Object.values(erreurs).every((e) => !e)
  const montrer = (champ: string) => (tente ? erreurs[champ] : null)

  const mutation = useMutation({
    mutationFn: () =>
      api.post<Evenement>(`/${CHEMINS[cible.cible]}/${cible.id}/evenements`, {
        type,
        date: dakarVersIso(date),
        indexAvant: compteur ? avant : null,
        indexApres: compteur ? apres : null,
        referenceScelles: scelles.trim() || null,
        intervenant: intervenant.trim() || null,
        prochaineVerification: prochaine || null,
        commentaire: commentaire.trim() || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cles.evenements(stationId) })
      notifier(`${TYPES_EVENEMENT[type]} consigné : ${cible.libelle}.`)
      onFermer()
    },
    onError: (e) => setErreur(messageErreur(e)),
  })

  return (
    <Dialog open onClose={onFermer} fullWidth maxWidth="sm">
      <DialogTitle>Consigner un événement — {cible.libelle}</DialogTitle>
      <DialogContent>
        {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField id="champ-type" select label="Type" value={type} onChange={(e) => setType(e.target.value as TypeEvenement)} sx={{ flex: 1 }}>
              {types.map((t) => (
                <MenuItem key={t} value={t}>
                  {TYPES_EVENEMENT[t]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              id="champ-date"
              label="Date et heure (Dakar)"
              type="datetime-local"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              error={!!montrer('date')}
              helperText={montrer('date')}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ flex: 1 }}
            />
          </Stack>
          {compteur && (
            <>
              <Alert severity="info">
                Relevez l'index de l'ancien compteur juste avant l'intervention, puis celui du nouveau. Les ventes de la journée seront
                calculées sur les deux segments.
              </Alert>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                <TextField
                  id="champ-index-avant"
                  label="Index avant"
                  value={indexAvant}
                  onChange={(e) => setIndexAvant(e.target.value)}
                  error={!!montrer('indexAvant')}
                  helperText={
                    montrer('indexAvant') ??
                    (cible.indexReference !== undefined ? `Dernier index connu : ${formaterNombre(cible.indexReference)}` : ' ')
                  }
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                  sx={{ flex: 1 }}
                />
                <TextField
                  id="champ-index-apres"
                  label="Index après"
                  value={indexApres}
                  onChange={(e) => setIndexApres(e.target.value)}
                  error={!!montrer('indexApres')}
                  helperText={montrer('indexApres') ?? ' '}
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                  sx={{ flex: 1 }}
                />
              </Stack>
            </>
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              id="champ-scelles"
              label="Références des scellés"
              value={scelles}
              onChange={(e) => setScelles(e.target.value)}
              required={type === 'POSE_SCELLES'}
              error={!!montrer('scelles')}
              helperText={montrer('scelles')}
              sx={{ flex: 1 }}
            />
            <TextField
              id="champ-intervenant"
              label="Intervenant"
              value={intervenant}
              onChange={(e) => setIntervenant(e.target.value)}
              required={type === 'ETALONNAGE'}
              error={!!montrer('intervenant')}
              helperText={montrer('intervenant')}
              sx={{ flex: 1 }}
            />
          </Stack>
          <TextField
            id="champ-prochaine"
            label="Prochaine vérification"
            type="date"
            value={prochaine}
            onChange={(e) => setProchaine(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Facultatif : une alerte apparaîtra à l'approche de l'échéance."
          />
          <TextField
            id="champ-commentaire"
            label={compteur ? 'Motif' : 'Commentaire'}
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            required={compteur}
            multiline
            minRows={2}
            error={!!montrer('commentaire')}
            helperText={montrer('commentaire') ?? 'L’historique est définitif : un événement ne peut plus être modifié.'}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onFermer}>Annuler</Button>
        <Button
          variant="contained"
          loading={mutation.isPending}
          onClick={() => {
            setTente(true)
            setErreur(null)
            if (valide) mutation.mutate()
          }}
        >
          Enregistrer
        </Button>
      </DialogActions>
    </Dialog>
  )
}
