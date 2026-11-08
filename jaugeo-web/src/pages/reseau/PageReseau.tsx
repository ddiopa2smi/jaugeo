import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { lazy, Suspense, useState } from 'react'
import { useNavigate } from 'react-router'
import { useTableauReseau } from '../../api/requetes'
import type { NiveauEcart, StationReseau } from '../../api/types'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { NIVEAUX_ECART } from '../../shared/stock'

// Leaflet n'est téléchargé qu'à l'ouverture de la page
const CarteReseau = lazy(async () => ({ default: (await import('./CarteReseau')).CarteReseau }))

const LIBELLES_NIVEAU: Record<NiveauEcart, string> = { NORMAL: 'Normal', ALERTE: 'Alerte', ENQUETE: 'Enquête' }
const ORDRE: Record<NiveauEcart, number> = { ENQUETE: 0, ALERTE: 1, NORMAL: 2 }

const aujourdhui = () => new Date().toISOString().slice(0, 10)

function Indicateur({ libelle, valeur, detail }: { libelle: string; valeur: string; detail?: string }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="body2" color="text.secondary">
        {libelle}
      </Typography>
      <Typography variant="h5" component="p" sx={{ my: 0.5, fontVariantNumeric: 'tabular-nums' }}>
        {valeur}
      </Typography>
      {detail && (
        <Typography variant="caption" color="text.secondary">
          {detail}
        </Typography>
      )}
    </Paper>
  )
}

function Cloture({ s }: { s: StationReseau }) {
  if (!s.derniereCloture) return <>—</>
  return (
    <>
      {formaterDate(s.derniereCloture)}
      {s.joursSansCloture ? (
        <Typography component="span" variant="caption" color={s.joursSansCloture >= 2 ? 'error' : 'text.secondary'} sx={{ ml: 0.5 }}>
          ({s.joursSansCloture} j de retard)
        </Typography>
      ) : null}
    </>
  )
}

/** Tableau de bord du réseau : niveau d'alerte, volumes, écarts de stock et caisse de chaque station. */
export function PageReseau() {
  const [date, setDate] = useState(aujourdhui)
  const tableau = useTableauReseau(date)
  const navigate = useNavigate()
  const t = tableau.data

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3, alignItems: { sm: 'center' } }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">
            Réseau
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Situation de chaque station au jour choisi : journées clôturées jusqu’à la veille, cumuls du mois.
          </Typography>
        </Box>
        <TextField
          type="date"
          label="Situation au"
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
          size="small"
          slotProps={{ inputLabel: { shrink: true } }}
          sx={{ width: { xs: '100%', sm: 200 }, flexShrink: 0 }}
        />
      </Stack>

      {tableau.isLoading && <Chargement />}
      <AlerteErreur erreur={tableau.error} />
      {t && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(5, 1fr)' }, mb: 3 }}>
            <Paper sx={{ p: 2 }} aria-label="Stations par niveau">
              <Typography variant="body2" color="text.secondary">
                Stations
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
                {(['ENQUETE', 'ALERTE', 'NORMAL'] as const).map((n) => (
                  <Chip
                    key={n}
                    size="small"
                    color={NIVEAUX_ECART[n].couleur}
                    variant={t.parNiveau[n] > 0 ? 'filled' : 'outlined'}
                    label={`${t.parNiveau[n]} ${LIBELLES_NIVEAU[n].toLowerCase()}`}
                    data-testid={`niveau-${n}`}
                  />
                ))}
              </Stack>
            </Paper>
            <Indicateur libelle="Litres vendus la veille" valeur={`${formaterNombre(t.litresVeille)} L`} />
            <Indicateur libelle="Litres vendus dans le mois" valeur={`${formaterNombre(t.litresMois)} L`} />
            <Indicateur libelle="Ventes du mois" valeur={formaterPrix(t.ventesMois)} detail={`versé : ${formaterPrix(t.versementsMois)}`} />
            <Indicateur
              libelle="Écarts de stock du mois"
              valeur={formaterPrix(t.ecartStockValeurMois)}
              detail="valeur des écarts (négatif = manquant)"
            />
          </Box>

          <Paper sx={{ p: 1, mb: 3 }}>
            <Suspense fallback={<Chargement />}>
              <CarteReseau stations={t.stations} />
            </Suspense>
          </Paper>

          <Paper sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Stations du réseau">
              <TableHead>
                <TableRow>
                  <TableCell>Station</TableCell>
                  <TableCell>Niveau</TableCell>
                  <TableCell>Motifs</TableCell>
                  <TableCell>Dernière clôture</TableCell>
                  <TableCell align="right">Litres veille</TableCell>
                  <TableCell align="right">Litres du mois</TableCell>
                  <TableCell align="right">Ventes du mois</TableCell>
                  <TableCell align="right">Écart de stock</TableCell>
                  <TableCell align="right">Caisse du gérant</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {[...t.stations]
                  .sort((a, b) => ORDRE[a.niveau] - ORDRE[b.niveau] || a.code.localeCompare(b.code))
                  .map((s) => (
                    <TableRow
                      key={s.stationId}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/journees?station=${s.stationId}`)}
                      data-testid={`station-${s.code}`}
                    >
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {s.code}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {s.nom}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color={NIVEAUX_ECART[s.niveau].couleur} label={LIBELLES_NIVEAU[s.niveau]} />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        {s.motifs.length === 0 ? (
                          <Typography variant="body2" color="text.secondary">
                            —
                          </Typography>
                        ) : (
                          s.motifs.map((m) => (
                            <Typography key={m} variant="body2">
                              {m}
                            </Typography>
                          ))
                        )}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        <Cloture s={s} />
                      </TableCell>
                      <TableCell align="right">{formaterNombre(s.litresVeille)}</TableCell>
                      <TableCell align="right">{formaterNombre(s.litresMoisTotal)}</TableCell>
                      <TableCell align="right">{formaterPrix(s.ventesMois)}</TableCell>
                      <TableCell align="right" sx={{ color: s.ecartStockValeurMois < 0 ? 'error.main' : undefined }}>
                        {formaterPrix(s.ecartStockValeurMois)}
                      </TableCell>
                      <TableCell align="right">{formaterPrix(s.soldeCaisse)}</TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
    </>
  )
}
