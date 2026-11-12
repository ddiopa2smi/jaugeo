import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type ReactNode } from 'react'
import { useCompteExploitation } from '../../api/requetes'
import type { CompteExploitation } from '../../api/types'
import { NomStation } from '../../shared/CarteStation'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { formaterPct } from '../../shared/stock'
import { CadreTableau, EnTeteTri } from '../../shared/tableaux'
import { useTri } from '../../shared/tri'

const chiffres = { whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' } as const
const RESEAU = 'RESEAU'

/** Montant signé à l'affichage : « − 12 000 F », « + 3 000 F ». */
function signe(v: number): string {
  if (v === 0) return formaterPrix(0)
  return `${v < 0 ? '−' : '+'} ${formaterPrix(Math.abs(v))}`
}

/** Une ligne du compte : opérateur, libellé (et détail), montant. Les sous-totaux sont en gras sur fond teinté. */
function Ligne({
  operateur,
  libelle,
  detail,
  valeur,
  fort,
  couleur,
  testid,
}: {
  operateur?: string
  libelle: string
  detail?: ReactNode
  valeur: string
  fort?: boolean
  couleur?: string
  testid?: string
}) {
  return (
    <Stack
      direction="row"
      sx={{ alignItems: 'baseline', gap: 1, py: fort ? 1.25 : 0.9, px: { xs: 1.5, sm: 2 }, bgcolor: fort ? 'action.hover' : undefined }}
    >
      <Typography sx={{ width: 14, flexShrink: 0, color: 'text.secondary', fontWeight: 600 }} aria-hidden>
        {operateur}
      </Typography>
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: fort ? 700 : 400 }}>
          {libelle}
        </Typography>
        {detail && (
          <Typography variant="caption" color="text.secondary" component="div">
            {detail}
          </Typography>
        )}
      </Box>
      <Typography variant={fort ? 'body1' : 'body2'} sx={{ ...chiffres, fontWeight: fort ? 700 : 500, color: couleur }} data-testid={testid}>
        {valeur}
      </Typography>
    </Stack>
  )
}

function Etat({ c }: { c: CompteExploitation }) {
  return c.clos ? (
    <Chip size="small" color="success" label={c.clotureLe ? `Clôturé le ${formaterDate(c.clotureLe.slice(0, 10))}` : 'Clôturé'} />
  ) : (
    <Chip size="small" color="warning" variant="outlined" label="Provisoire" />
  )
}

/** Compte d'exploitation en lecture verticale, du chiffre d'affaires au résultat. */
function Compte({ c }: { c: CompteExploitation }) {
  const montant = (v: number | null) => (v === null ? '—' : formaterPrix(v))
  return (
    <Paper sx={{ overflow: 'hidden' }} aria-label={`Compte d'exploitation ${c.stationNom}`}>
      <Stack direction="row" sx={{ alignItems: 'center', gap: 1, px: { xs: 1.5, sm: 2 }, py: 1.5, flexWrap: 'wrap' }}>
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, flexGrow: 1 }}>
          {c.stationNom}
        </Typography>
        <Etat c={c} />
      </Stack>
      <Divider />
      {c.produits
        .filter((p) => p.chiffreAffaires !== 0)
        .map((p) => (
          <Ligne
            key={p.code}
            libelle={`Ventes ${p.libelle}`}
            detail={`${formaterNombre(p.litres)} L · coût d'achat ${montant(p.coutAchat)} · marge ${montant(p.marge)}`}
            valeur={formaterPrix(p.chiffreAffaires)}
          />
        ))}
      <Ligne libelle="Chiffre d'affaires carburants" valeur={formaterPrix(c.chiffreAffaires)} fort testid="compte-ca" />
      <Ligne operateur="−" libelle="Coût d'achat des carburants vendus" detail="Litres vendus × prix d'achat convenu du jour" valeur={formaterPrix(c.coutAchat)} />
      <Ligne operateur="=" libelle="Marge sur carburants" valeur={formaterPrix(c.margeCarburants)} fort />
      {(c.ventesArticles !== 0 || c.coutArticles !== 0) && (
        <Ligne
          operateur="+"
          libelle="Marge sur lubrifiants et boutique"
          detail={`Ventes ${formaterPrix(c.ventesArticles)} − coût au CMUP ${formaterPrix(c.coutArticles)}`}
          valeur={formaterPrix(c.margeArticles)}
        />
      )}
      {c.remises !== 0 && <Ligne operateur="−" libelle="Remises accordées aux clients" valeur={formaterPrix(c.remises)} />}
      <Ligne
        operateur="="
        libelle="Marge brute"
        detail={c.tauxMarge === null ? undefined : `${formaterPct(c.tauxMarge)} du chiffre d'affaires`}
        valeur={formaterPrix(c.margeBrute)}
        fort
        testid="compte-marge"
      />
      <Ligne
        operateur="±"
        libelle="Écarts de stock"
        detail="Jaugé moins théorique, au coût d'achat (négatif = perte)"
        valeur={signe(c.ecartsStock)}
        couleur={c.ecartsStock < 0 ? 'error.main' : undefined}
      />
      <Ligne operateur="−" libelle="Dépenses d'exploitation" valeur={formaterPrix(c.depenses)} />
      {c.regularisations !== 0 && (
        <Ligne
          operateur="±"
          libelle="Régularisations sur mois clos"
          valeur={signe(c.regularisations)}
          couleur={c.regularisations < 0 ? 'error.main' : undefined}
          testid="compte-regularisations"
        />
      )}
      <Ligne
        operateur="="
        libelle="Résultat"
        valeur={formaterPrix(c.resultat)}
        fort
        couleur={c.resultat < 0 ? 'error.main' : 'success.main'}
        testid="compte-resultat"
      />
    </Paper>
  )
}

export function OngletCompte({ mois }: { mois: string }) {
  const requete = useCompteExploitation(mois)
  const [choix, setChoix] = useState(RESEAU)
  const tri = useTri<CompteExploitation, 'station' | 'ca' | 'marge' | 'depenses' | 'resultat'>(
    {
      station: (s) => s.stationNom,
      ca: (s) => s.chiffreAffaires + s.ventesArticles,
      marge: (s) => s.margeBrute,
      depenses: (s) => s.depenses,
      resultat: (s) => s.resultat,
    },
    'station',
  )
  const c = requete.data
  if (!c) return requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
  if (c.stations.length === 0) return <Alert severity="info">Aucune station dans votre périmètre pour ce mois.</Alert>
  const compte = c.stations.find((s) => s.stationCode === choix) ?? c.total

  return (
    <>
      {!compte.coutComplet && (
        <Alert severity="warning" sx={{ mb: 2 }} data-testid="prix-manquants">
          Prix d'achat convenu manquant pour {compte.prixManquants.join(', ')} : la marge ne couvre que les produits dont le coût est connu.
          Renseignez le prix dans Approvisionnement, onglet des fournisseurs.
        </Alert>
      )}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 5fr) minmax(0, 7fr)' }, alignItems: 'start' }}>
        <Box>
          {c.stations.length > 1 && (
            <TextField select label="Périmètre" value={compte === c.total ? RESEAU : choix} onChange={(e) => setChoix(e.target.value)} sx={{ mb: 2 }}>
              <MenuItem value={RESEAU}>Réseau ({c.stations.length} stations)</MenuItem>
              {c.stations.map((s) => (
                <MenuItem key={s.stationCode} value={s.stationCode}>
                  {s.stationNom}
                </MenuItem>
              ))}
            </TextField>
          )}
          <Compte c={compte} />
        </Box>

        <CadreTableau>
          <Table size="small" aria-label="Comparaison des stations">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="station">
                  Station
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="ca" align="right">
                  Chiffre d'affaires
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="marge" align="right">
                  Marge brute
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="depenses" align="right">
                  Dépenses
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="resultat" align="right">
                  Résultat
                </EnTeteTri>
              </TableRow>
            </TableHead>
            <TableBody>
              {tri.trier(c.stations).map((s) => (
                <TableRow
                  key={s.stationCode}
                  hover
                  selected={s === compte}
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setChoix(s.stationCode)}
                  data-testid={`compte-${s.stationCode}`}
                >
                  <TableCell>
                    <NomStation nom={s.stationNom} teinte={s.clos ? 'success' : 'primary'} detail={s.clos ? 'Clôturé' : 'Provisoire'} />
                  </TableCell>
                  <TableCell align="right" sx={chiffres}>
                    {formaterPrix(s.chiffreAffaires + s.ventesArticles)}
                  </TableCell>
                  <TableCell align="right" sx={chiffres}>
                    {formaterPrix(s.margeBrute)}
                    <Typography variant="caption" color={s.coutComplet ? 'text.secondary' : 'warning.main'} component="div">
                      {s.coutComplet ? formaterPct(s.tauxMarge) : 'coût incomplet'}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={chiffres}>
                    {formaterPrix(s.depenses)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 600, color: s.resultat < 0 ? 'error.main' : undefined }}>
                    {formaterPrix(s.resultat)}
                  </TableCell>
                </TableRow>
              ))}
              {c.stations.length > 1 && (
                <TableRow hover selected={compte === c.total} sx={{ cursor: 'pointer' }} onClick={() => setChoix(RESEAU)}>
                  <TableCell sx={{ fontWeight: 700 }}>Réseau</TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 700 }}>
                    {formaterPrix(c.total.chiffreAffaires + c.total.ventesArticles)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 700 }}>
                    {formaterPrix(c.total.margeBrute)}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {formaterPct(c.total.tauxMarge)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 700 }}>
                    {formaterPrix(c.total.depenses)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...chiffres, fontWeight: 700, color: c.total.resultat < 0 ? 'error.main' : undefined }}>
                    {formaterPrix(c.total.resultat)}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CadreTableau>
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
        Calculé sur les journées clôturées. Un mois clôturé est figé : ses chiffres ne changent plus, une correction passe par une
        régularisation comptée dans le mois en cours.
      </Typography>
    </>
  )
}
