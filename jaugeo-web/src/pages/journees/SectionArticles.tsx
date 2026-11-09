import SaveIcon from '@mui/icons-material/Save'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, rafraichirJournee, useVentesArticles } from '../../api/requetes'
import type { VenteArticle, VentesArticlesJournee } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterNombre, formaterPrix, versNombre } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'

const texte = (v: number | null) => (v === null ? '' : String(v))

/** Ventes de lubrifiants et d'articles boutique de la journée : elles s'ajoutent au montant à verser. */
export function SectionArticles({ journeeId }: { journeeId: string }) {
  const requete = useVentesArticles(journeeId)
  const queryClient = useQueryClient()
  const [saisies, setSaisies] = useState<Record<string, string>>({})
  const [toutAfficher, setToutAfficher] = useState(false)
  const v = requete.data

  const enregistrement = useMutation({
    mutationFn: (corps: { version: number; ventes: { produitId: string; quantite: number | null }[] }) =>
      api.put<VentesArticlesJournee>(`/journees/${journeeId}/articles`, corps),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.ventesArticles(journeeId), r)
      rafraichirJournee(queryClient, journeeId, cles.ventesArticles(journeeId))
      queryClient.invalidateQueries({ queryKey: cles.articles })
      setSaisies({})
      notifier('Ventes boutique enregistrées.')
    },
  })

  if (!v) return <AlerteErreur erreur={requete.error} />
  if (v.articles.length === 0 && !v.modifiable) return null

  const valeur = (a: VenteArticle) => saisies[a.produitId] ?? texte(a.quantite)
  const modifiees = v.articles.filter((a) => saisies[a.produitId] !== undefined && versNombre(saisies[a.produitId]!) !== a.quantite)
  const visibles = v.articles.filter((a) => toutAfficher || !v.modifiable || a.disponible > 0 || a.quantite !== null || saisies[a.produitId])
  const masques = v.articles.length - visibles.length
  const total = v.articles.reduce((t, a) => {
    const q = versNombre(valeur(a)) ?? 0
    return t + (a.prixUnitaire === null ? 0 : Math.round(q * a.prixUnitaire))
  }, 0)
  const enregistrer = () =>
    enregistrement.mutate({
      version: v.version,
      ventes: modifiees.map((a) => ({ produitId: a.produitId, quantite: versNombre(saisies[a.produitId]!) })),
    })

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }} aria-label="Lubrifiants et boutique">
      <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Lubrifiants et boutique
        </Typography>
        <Typography variant="body2" color="text.secondary" data-testid="total-boutique">
          {formaterPrix(modifiees.length ? total : v.total)}
        </Typography>
      </Stack>
      {v.articles.length === 0 ? (
        <Alert severity="info">Aucun lubrifiant ni article boutique au référentiel.</Alert>
      ) : (
        <Table size="small" sx={{ '& td, & th': { px: 1 }, '& td:first-of-type, & th:first-of-type': { pl: 0 } }}>
          <TableHead>
            <TableRow>
              <TableCell>Article</TableCell>
              <TableCell align="right" sx={{ width: 104 }}>
                Vendus
              </TableCell>
              <TableCell align="right" sx={{ width: 110, pr: '0 !important' }}>
                Montant
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {visibles.map((a) => {
              const q = versNombre(valeur(a))
              const auDela = q !== null && q > a.disponible
              const montant = q !== null && a.prixUnitaire !== null ? Math.round(q * a.prixUnitaire) : null
              return (
                <TableRow key={a.produitId}>
                  <TableCell>
                    {a.libelle}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {a.prixUnitaire === null ? 'sans prix de vente' : `${formaterPrix(a.prixUnitaire)} l'unité`}
                      {v.modifiable && ` · stock ${formaterNombre(a.disponible)}`}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    {v.modifiable ? (
                      <TextField
                        size="small"
                        value={valeur(a)}
                        onChange={(e) => setSaisies((s) => ({ ...s, [a.produitId]: e.target.value }))}
                        error={auDela}
                        disabled={a.prixUnitaire === null}
                        slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `Quantité vendue ${a.libelle}`, style: { textAlign: 'right' } } }}
                        sx={{ width: 88 }}
                      />
                    ) : (
                      formaterNombre(a.quantite)
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap', pr: '0 !important', fontVariantNumeric: 'tabular-nums' }}>
                    {montant === null ? '—' : formaterPrix(montant)}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
      {masques > 0 && (
        <Button size="small" onClick={() => setToutAfficher(true)} sx={{ mt: 0.5 }}>
          {masques > 1 ? `Afficher les ${masques} articles sans stock` : 'Afficher l’article sans stock'}
        </Button>
      )}
      <AlerteErreur erreur={enregistrement.error} />
      {v.modifiable && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1, alignItems: { sm: 'center' }, justifyContent: 'flex-end' }}>
          {modifiees.length > 0 && (
            <Typography variant="body2" color="warning.main" sx={{ flexGrow: 1 }}>
              Ventes non enregistrées.
            </Typography>
          )}
          <Button variant="outlined" startIcon={<SaveIcon />} onClick={enregistrer} disabled={enregistrement.isPending || modifiees.length === 0}>
            Enregistrer les ventes
          </Button>
        </Stack>
      )}
    </Paper>
  )
}
