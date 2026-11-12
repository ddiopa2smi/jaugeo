import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined'
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined'
import LocalGasStationOutlinedIcon from '@mui/icons-material/LocalGasStationOutlined'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
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
import { CarteIndicateur } from '../../shared/CarteIndicateur'
import { NomStation } from '../../shared/CarteStation'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix } from '../../shared/libelles'
import { NIVEAUX_ECART } from '../../shared/stock'
import { CadreTableau, EnTeteTri, PuceEtat } from '../../shared/tableaux'
import { useTri } from '../../shared/tri'

// Leaflet n'est téléchargé qu'à l'ouverture de la page
const CarteReseau = lazy(async () => ({ default: (await import('./CarteReseau')).CarteReseau }))

const LIBELLES_NIVEAU: Record<NiveauEcart, string> = { NORMAL: 'Normal', ALERTE: 'Alerte', ENQUETE: 'Enquête' }
const ORDRE: Record<NiveauEcart, number> = { ENQUETE: 0, ALERTE: 1, NORMAL: 2 }

const aujourdhui = () => new Date().toISOString().slice(0, 10)

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
  // Tri initial : les stations à surveiller d'abord
  const tri = useTri<StationReseau, 'station' | 'niveau' | 'cloture' | 'veille' | 'litres' | 'ventes' | 'ecart' | 'caisse'>(
    {
      station: (s) => s.nom,
      niveau: (s) => `${ORDRE[s.niveau]} ${s.code}`,
      cloture: (s) => s.derniereCloture,
      veille: (s) => s.litresVeille,
      litres: (s) => s.litresMoisTotal,
      ventes: (s) => s.ventesMois,
      ecart: (s) => s.ecartStockValeurMois,
      caisse: (s) => s.soldeCaisse,
    },
    'niveau',
  )

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
          <Box sx={{ display: 'grid', gap: { xs: 1.5, md: 2 }, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(5, 1fr)' }, mb: 3 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: '12px' }} aria-label="Stations par niveau">
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
            <CarteIndicateur icone={<LocalGasStationOutlinedIcon />} libelle="Litres vendus la veille" valeur={`${formaterNombre(t.litresVeille)} L`} />
            <CarteIndicateur icone={<CalendarMonthOutlinedIcon />} libelle="Litres vendus dans le mois" valeur={`${formaterNombre(t.litresMois)} L`} />
            <CarteIndicateur
              icone={<PaymentsOutlinedIcon />}
              libelle="Ventes du mois"
              valeur={formaterPrix(t.ventesMois)}
              ton="success"
              sous={`versé : ${formaterPrix(t.versementsMois)}`}
            />
            <CarteIndicateur
              icone={<BalanceOutlinedIcon />}
              libelle="Écarts de stock du mois"
              valeur={formaterPrix(t.ecartStockValeurMois)}
              ton={t.ecartStockValeurMois < 0 ? 'error' : 'primary'}
              sous="valeur des écarts (négatif = manquant)"
            />
          </Box>

          <Paper sx={{ p: 1, mb: 3 }}>
            <Suspense fallback={<Chargement />}>
              <CarteReseau stations={t.stations} />
            </Suspense>
          </Paper>

          <CadreTableau>
            <Table size="small" aria-label="Stations du réseau">
              <TableHead>
                <TableRow>
                  <EnTeteTri tri={tri} cle="station">
                    Station
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="niveau">
                    Niveau
                  </EnTeteTri>
                  <TableCell>Motifs</TableCell>
                  <EnTeteTri tri={tri} cle="cloture">
                    Dernière clôture
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="veille" align="right">
                    Litres veille
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="litres" align="right">
                    Litres du mois
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="ventes" align="right">
                    Ventes du mois
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="ecart" align="right">
                    Écart de stock
                  </EnTeteTri>
                  <EnTeteTri tri={tri} cle="caisse" align="right">
                    Caisse du gérant
                  </EnTeteTri>
                </TableRow>
              </TableHead>
              <TableBody>
                {tri.trier(t.stations).map((s) => (
                    <TableRow
                      key={s.stationId}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/journees?station=${s.stationId}`)}
                      data-testid={`station-${s.code}`}
                    >
                      <TableCell>
                        <NomStation nom={s.nom} teinte={NIVEAUX_ECART[s.niveau].couleur} />
                      </TableCell>
                      <TableCell>
                        <PuceEtat ton={NIVEAUX_ECART[s.niveau].couleur} libelle={LIBELLES_NIVEAU[s.niveau]} />
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
          </CadreTableau>
        </>
      )}
    </>
  )
}
