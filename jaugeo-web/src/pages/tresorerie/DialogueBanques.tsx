import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemText from '@mui/material/ListItemText'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { useBanques } from '../../api/requetes'
import type { Banque } from '../../api/types'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { notifier } from '../../shared/notifications'

/** Recherche sans tenir compte de la casse ni des accents. */
const normaliser = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/**
 * Banques de la compagnie : la liste est pré-remplie avec les banques du Sénégal ; seules celles qui sont
 * activées ici sont proposées à la saisie d'un versement.
 */
export function DialogueBanques({ onFermer }: { onFermer: () => void }) {
  const queryClient = useQueryClient()
  const banques = useBanques(true)
  const [recherche, setRecherche] = useState('')
  const [code, setCode] = useState('')
  const [nom, setNom] = useState('')
  const rafraichir = () => queryClient.invalidateQueries({ queryKey: ['tresorerie', 'banques'] })

  const activation = useMutation({
    mutationFn: (b: Banque) => api.put<Banque>(`/tresorerie/banques/${b.id}`, { version: b.version, actif: !b.actif }),
    onSuccess: (b) => {
      rafraichir()
      notifier(b.actif ? `${b.nom} est proposée à la saisie des versements.` : `${b.nom} n’est plus proposée.`)
    },
    onError: rafraichir,
  })
  const ajout = useMutation({
    mutationFn: () => api.post<Banque>('/tresorerie/banques', { code: code.trim(), nom: nom.trim() }),
    onSuccess: (b) => {
      rafraichir()
      notifier(`${b.nom} ajoutée.`)
      setCode('')
      setNom('')
    },
  })

  const cherche = normaliser(recherche.trim())
  const liste = (banques.data ?? []).filter((b) => cherche === '' || normaliser(`${b.nom} ${b.code}`).includes(cherche))
  const actives = (banques.data ?? []).filter((b) => b.actif).length

  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Banques de la compagnie</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Seules les banques activées sont proposées à la saisie d’un versement. Désactiver une banque ne modifie pas les versements déjà
          enregistrés.
        </Typography>
        <TextField
          placeholder="Rechercher une banque"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
          fullWidth
          size="small"
          slotProps={{ htmlInput: { 'aria-label': 'Rechercher une banque' } }}
        />
        {banques.isLoading && <Chargement />}
        {banques.data && (
          <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }} data-testid="banques-actives">
            {actives} banque(s) active(s) sur {banques.data.length}
          </Typography>
        )}
        <List dense sx={{ maxHeight: 340, overflowY: 'auto' }}>
          {liste.map((b) => (
            <ListItem
              key={b.id}
              disableGutters
              data-testid={`banque-${b.code}`}
              secondaryAction={
                <Switch
                  edge="end"
                  checked={b.actif}
                  disabled={activation.isPending}
                  onChange={() => activation.mutate(b)}
                  slotProps={{ input: { 'aria-label': `${b.nom} : banque active` } }}
                />
              }
            >
              <ListItemText primary={b.nom} secondary={b.nom === b.code ? undefined : b.code} sx={{ pr: 4 }} />
            </ListItem>
          ))}
        </List>
        {banques.data && liste.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Aucune banque ne correspond à cette recherche.
          </Typography>
        )}
        <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>
          Ajouter une banque absente de la liste
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <TextField
            label="Sigle"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            size="small"
            sx={{ width: { sm: 140 }, flexShrink: 0 }}
            slotProps={{ htmlInput: { maxLength: 60 } }}
          />
          <TextField label="Nom de la banque" value={nom} onChange={(e) => setNom(e.target.value)} size="small" fullWidth slotProps={{ htmlInput: { maxLength: 120 } }} />
          <Button variant="outlined" onClick={() => ajout.mutate()} disabled={ajout.isPending || !code.trim() || !nom.trim()} sx={{ flexShrink: 0 }}>
            Ajouter
          </Button>
        </Stack>
        <AlerteErreur erreur={banques.error ?? activation.error ?? ajout.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}
