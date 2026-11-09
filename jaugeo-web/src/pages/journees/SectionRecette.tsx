import AccountBalanceIcon from '@mui/icons-material/AccountBalance'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useRecette } from '../../api/requetes'
import type { RecetteJournee } from '../../api/types'
import { AlerteErreur } from '../../shared/composants'
import { formaterDate, formaterPrix } from '../../shared/libelles'
import { VoirPiece } from '../../shared/PieceJointe'
import { formaterEcart, STATUTS_VERSEMENT } from '../../shared/tresorerie'
import { DialogueVersement } from '../tresorerie/DialogueVersement'

function Ligne({ libelle, valeur, gras, testid }: { libelle: string; valeur: string; gras?: boolean; testid?: string }) {
  const sx = { fontWeight: gras ? 700 : undefined, borderBottom: gras ? undefined : 'none', py: 0.5, px: 0 }
  return (
    <TableRow>
      <TableCell sx={sx}>{libelle}</TableCell>
      <TableCell align="right" sx={{ ...sx, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }} data-testid={testid}>
        {valeur}
      </TableCell>
    </TableRow>
  )
}

const moins = (v: number | null) => (v === null ? '—' : v === 0 ? formaterPrix(0) : `− ${formaterPrix(v)}`)

/** Décomposition du montant à verser (feuille station : ventes carburant et boutique − bons − dépenses − compléments + créances recouvrées). */
export function DetailMontant({ recette }: { recette: RecetteJournee }) {
  return (
    <Table size="small">
      <TableBody>
        <Ligne libelle={recette.ventesArticles > 0 ? 'Ventes carburant' : 'Ventes'} valeur={formaterPrix(recette.ventes)} />
        {recette.ventesArticles > 0 && (
          <Ligne libelle="Lubrifiants et boutique" valeur={`+ ${formaterPrix(recette.ventesArticles)}`} testid="ventes-articles" />
        )}
        <Ligne libelle="Bons direction" valeur={moins(recette.bonsDirection)} />
        <Ligne libelle="Bons gérant" valeur={moins(recette.bonsGerant)} />
        <Ligne libelle="Dépenses autorisées" valeur={moins(recette.depenses)} />
        <Ligne libelle="Compléments citerne" valeur={moins(recette.complements)} />
        {recette.recouvrements > 0 && <Ligne libelle="Créances recouvrées" valeur={`+ ${formaterPrix(recette.recouvrements)}`} />}
        <Ligne libelle="Montant à verser" valeur={formaterPrix(recette.montantAVerser)} gras testid="montant-a-verser" />
      </TableBody>
    </Table>
  )
}

export function SectionRecette({ journeeId }: { journeeId: string }) {
  const requete = useRecette(journeeId)
  const [versement, setVersement] = useState(false)
  const r = requete.data
  if (!r) return <AlerteErreur erreur={requete.error} />

  return (
    <Paper variant="outlined" sx={{ p: 2 }} aria-label="Recette et versements">
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Recette à verser
        </Typography>
        <Chip size="small" label={r.fige ? 'Figée' : 'Provisoire'} color={r.fige ? 'success' : 'default'} />
      </Stack>
      <DetailMontant recette={r} />
      {!r.fige && (
        <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>
          Calculée sur la saisie enregistrée ; figée à la clôture.
        </Typography>
      )}

      <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>
        Caisse du gérant
      </Typography>
      <Table size="small">
        <TableBody>
          <Ligne libelle="Solde non versé la veille" valeur={formaterPrix(r.soldeOuverture)} />
          <Ligne libelle="+ Montant du jour" valeur={formaterPrix(r.montantAVerser)} />
          <Ligne libelle="− Versements du jour" valeur={formaterPrix(r.totalVersements)} />
          {r.arrete?.ecart != null && r.arrete.ecart !== 0 && (
            <Ligne libelle="Écart à l'arrêté de caisse" valeur={formaterEcart(r.arrete.ecart, formaterPrix)} />
          )}
          <Ligne libelle="Solde non versé" valeur={formaterPrix(r.soldeFin)} gras testid="solde-fin" />
        </TableBody>
      </Table>

      {r.versements.map((v) => (
        <Stack key={v.id} direction="row" spacing={1} sx={{ alignItems: 'center', mt: 1 }}>
          <Typography variant="body2" component="div" sx={{ flexGrow: 1 }}>
            {v.banque} · BV {v.referenceBv}
            <Typography variant="caption" color="text.secondary" component="div">
              {formaterDate(v.dateDepot)} · {formaterPrix(v.montant)}
            </Typography>
          </Typography>
          {v.piece && <VoirPiece chemin={`/tresorerie/versements/${v.id}/piece`} piece={v.piece} />}
          <Chip size="small" color={STATUTS_VERSEMENT[v.statut].couleur} label={STATUTS_VERSEMENT[v.statut].libelle} />
        </Stack>
      ))}

      {r.peutVerser && (
        <Button fullWidth variant="outlined" startIcon={<AccountBalanceIcon />} sx={{ mt: 2 }} onClick={() => setVersement(true)}>
          Enregistrer un versement
        </Button>
      )}
      {versement && (
        <DialogueVersement
          stationId={r.stationId}
          stationCode={r.stationCode}
          montantSuggere={r.soldeFin}
          dateSuggeree={r.date}
          onFermer={() => setVersement(false)}
        />
      )}
    </Paper>
  )
}
