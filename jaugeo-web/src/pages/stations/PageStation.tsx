import AddIcon from '@mui/icons-material/Add'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import BuildIcon from '@mui/icons-material/Build'
import EditIcon from '@mui/icons-material/Edit'
import StraightenIcon from '@mui/icons-material/Straighten'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useState, type ReactNode } from 'react'
import { Link as LienRouteur, useParams } from 'react-router'
import { useEquipements, useEvenements } from '../../api/requetes'
import type { Cuve, Evenement, Pistolet, Pompe } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, Chargement, PuceStatut } from '../../shared/composants'
import { formaterDate, formaterNombre, MODES_EXPLOITATION, TYPES_STATION } from '../../shared/libelles'
import { DialogueBareme } from './DialogueBareme'
import { DialogueEvenement, type CibleDialogue } from './DialogueEvenement'
import { DialogueCuve, DialoguePistolet, DialoguePompe } from './DialoguesEquipement'
import { HistoriqueEquipements, PrixDeLaStation } from './SectionsStation'
import { FormulaireStation } from './FormulaireStation'

type Dialogue =
  | { type: 'station' }
  | { type: 'cuve'; cuve?: Cuve }
  | { type: 'pompe' }
  | { type: 'pistolet'; pompe: Pompe; pistolet?: Pistolet }
  | { type: 'bareme'; cuve: Cuve }
  | { type: 'evenement'; cible: CibleDialogue }

/** Index attendu avant un changement de compteur : dernier « index après », sinon index de mise en service. */
function indexReference(pistolet: Pistolet, evenements: Evenement[]): number {
  const dernier = evenements
    .filter((e) => e.equipementId === pistolet.id && e.indexApres !== null)
    .sort((a, b) => b.date.localeCompare(a.date))[0]
  return dernier?.indexApres ?? pistolet.indexMiseEnService
}

function Info({ libelle, valeur }: { libelle: string; valeur: ReactNode }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {libelle}
      </Typography>
      <Typography variant="body2">{valeur || '—'}</Typography>
    </Box>
  )
}

function Section({ titre, action, children }: { titre: string; action?: ReactNode; children: ReactNode }) {
  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Typography variant="h6" component="h2">
          {titre}
        </Typography>
        {action}
      </Stack>
      {children}
    </Paper>
  )
}

function BoutonIcone({ libelle, icone, onClick }: { libelle: string; icone: ReactNode; onClick: () => void }) {
  return (
    <Tooltip title={libelle}>
      <IconButton size="small" aria-label={libelle} onClick={onClick}>
        {icone}
      </IconButton>
    </Tooltip>
  )
}

function BoutonModifier({ libelle, onClick }: { libelle: string; onClick: () => void }) {
  return (
    <Tooltip title={libelle}>
      <IconButton size="small" aria-label={libelle} onClick={onClick}>
        <EditIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  )
}

export function PageStation() {
  const { id = '' } = useParams()
  const equipements = useEquipements(id)
  const peutGerer = usePermission('REFERENTIEL_GERER')
  const evenements = useEvenements(id)
  const [dialogue, setDialogue] = useState<Dialogue | null>(null)
  const fermer = () => setDialogue(null)

  if (equipements.isLoading) return <Chargement />
  if (!equipements.data) {
    return (
      <>
        <Button component={LienRouteur} to="/stations" startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
          Stations
        </Button>
        <AlerteErreur erreur={equipements.error ?? new Error('Station introuvable.')} />
      </>
    )
  }

  const { station, cuves, pompes } = equipements.data
  const nbPistolets = pompes.reduce((n, p) => n + p.pistolets.length, 0)
  const listeEvenements = evenements.data ?? []
  const consigner = (cible: CibleDialogue) => setDialogue({ type: 'evenement', cible })

  return (
    <>
      <Button component={LienRouteur} to="/stations" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Stations
      </Button>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3, justifyContent: 'space-between' }}>
        <Box>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Typography variant="h5" component="h1">
              {station.nom}
            </Typography>
            <Chip size="small" label={station.code} color="primary" />
            <PuceStatut actif={station.actif} libelleInactif="Fermée" />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {cuves.length} cuve(s) · {pompes.length} pompe(s) · {nbPistolets} pistolet(s)
          </Typography>
        </Box>
        {peutGerer && (
          <Box>
            <Button variant="outlined" startIcon={<EditIcon />} onClick={() => setDialogue({ type: 'station' })}>
              Modifier la station
            </Button>
          </Box>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(6, 1fr)' } }}>
          <Info libelle="Type" valeur={TYPES_STATION[station.type]} />
          <Info libelle="Région" valeur={station.regionNom} />
          <Info libelle="Localité" valeur={station.localite} />
          <Info libelle="Exploitation" valeur={MODES_EXPLOITATION[station.modeExploitation]} />
          <Info libelle="Ouverture" valeur={formaterDate(station.dateOuverture)} />
          <Info
            libelle="Coordonnées GPS"
            valeur={station.latitude !== null && station.longitude !== null ? `${station.latitude}, ${station.longitude}` : null}
          />
        </Box>
      </Paper>

      <Section
        titre="Cuves"
        action={
          peutGerer && (
            <Button size="small" startIcon={<AddIcon />} onClick={() => setDialogue({ type: 'cuve' })}>
              Ajouter une cuve
            </Button>
          )
        }
      >
        {cuves.length === 0 ? (
          <Typography color="text.secondary">Aucune cuve déclarée.</Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Code</TableCell>
                  <TableCell>Produit</TableCell>
                  <TableCell align="right">Capacité (L)</TableCell>
                  <TableCell align="right">Creux de sécurité (L)</TableCell>
                  <TableCell>Barème</TableCell>
                  <TableCell>Statut</TableCell>
                  {peutGerer && <TableCell width={96} />}
                </TableRow>
              </TableHead>
              <TableBody>
                {cuves.map((c) => (
                  <TableRow key={c.id} hover>
                    <TableCell>{c.code}</TableCell>
                    <TableCell>{c.produitLibelle}</TableCell>
                    <TableCell align="right">{formaterNombre(c.capaciteLitres)}</TableCell>
                    <TableCell align="right">{formaterNombre(c.creuxSecuriteLitres)}</TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        startIcon={<StraightenIcon />}
                        color={c.baremePoints > 0 ? 'primary' : 'warning'}
                        onClick={() => setDialogue({ type: 'bareme', cuve: c })}
                        aria-label={`Barème de la cuve ${c.code}`}
                      >
                        {c.baremePoints > 0 ? `${c.baremePoints} points` : 'À saisir'}
                      </Button>
                    </TableCell>
                    <TableCell>
                      <PuceStatut actif={c.actif} libelleInactif="Hors service" />
                    </TableCell>
                    {peutGerer && (
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        <BoutonIcone
                          libelle={`Événement sur la cuve ${c.code}`}
                          icone={<BuildIcon fontSize="small" />}
                          onClick={() => consigner({ cible: 'CUVE', id: c.id, libelle: `Cuve ${c.code}` })}
                        />
                        <BoutonModifier libelle={`Modifier la cuve ${c.code}`} onClick={() => setDialogue({ type: 'cuve', cuve: c })} />
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Section>

      <Section
        titre="Pompes et pistolets"
        action={
          peutGerer && (
            <Button size="small" startIcon={<AddIcon />} onClick={() => setDialogue({ type: 'pompe' })}>
              Ajouter une pompe
            </Button>
          )
        }
      >
        {pompes.length === 0 && <Typography color="text.secondary">Aucune pompe déclarée.</Typography>}
        <Stack spacing={2}>
          {pompes.map((p) => (
            <Box key={p.id} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  Pompe {p.code}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
                  {[p.marque, p.numeroSerie && `n° ${p.numeroSerie}`].filter(Boolean).join(' · ')}
                </Typography>
                {peutGerer && (
                  <>
                    <Button
                      size="small"
                      startIcon={<BuildIcon />}
                      onClick={() => consigner({ cible: 'POMPE', id: p.id, libelle: `Pompe ${p.code}` })}
                    >
                      Événement
                    </Button>
                    <Button size="small" startIcon={<AddIcon />} onClick={() => setDialogue({ type: 'pistolet', pompe: p })}>
                      Pistolet
                    </Button>
                  </>
                )}
              </Stack>
              {p.pistolets.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Aucun pistolet.
                </Typography>
              ) : (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Pistolet</TableCell>
                        <TableCell>Libellé</TableCell>
                        <TableCell>Cuve · produit</TableCell>
                        <TableCell align="right">Index de mise en service</TableCell>
                        <TableCell>Statut</TableCell>
                        {peutGerer && <TableCell width={96} />}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {p.pistolets.map((pi) => (
                        <TableRow key={pi.id} hover>
                          <TableCell>{pi.code}</TableCell>
                          <TableCell>{pi.libelle}</TableCell>
                          <TableCell>
                            {pi.cuveCode} · {pi.produitLibelle}
                          </TableCell>
                          <TableCell align="right">{formaterNombre(pi.indexMiseEnService)}</TableCell>
                          <TableCell>
                            <PuceStatut actif={pi.actif} libelleInactif="Hors service" />
                          </TableCell>
                          {peutGerer && (
                            <TableCell sx={{ whiteSpace: 'nowrap' }}>
                              <BoutonIcone
                                libelle={`Événement sur le pistolet ${pi.code}`}
                                icone={<BuildIcon fontSize="small" />}
                                onClick={() =>
                                  consigner({
                                    cible: 'PISTOLET',
                                    id: pi.id,
                                    libelle: `Pistolet ${pi.code} · pompe ${p.code}`,
                                    indexReference: indexReference(pi, listeEvenements),
                                  })
                                }
                              />
                              <BoutonModifier
                                libelle={`Modifier le pistolet ${pi.code}`}
                                onClick={() => setDialogue({ type: 'pistolet', pompe: p, pistolet: pi })}
                              />
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          ))}
        </Stack>
      </Section>

      <Section titre="Historique des équipements">
        {evenements.isLoading ? (
          <Chargement />
        ) : evenements.error ? (
          <AlerteErreur erreur={evenements.error} />
        ) : (
          <HistoriqueEquipements evenements={listeEvenements} />
        )}
      </Section>

      <Section titre="Prix">
        <PrixDeLaStation stationId={station.id} />
      </Section>

      {dialogue?.type === 'station' && (
        <FormulaireStation
          station={station}
          onFermer={fermer}
        />
      )}
      {dialogue?.type === 'cuve' && <DialogueCuve stationId={station.id} cuve={dialogue.cuve} onFermer={fermer} />}
      {dialogue?.type === 'pompe' && <DialoguePompe stationId={station.id} onFermer={fermer} />}
      {dialogue?.type === 'pistolet' && (
        <DialoguePistolet stationId={station.id} pompe={dialogue.pompe} pistolet={dialogue.pistolet} cuves={cuves} onFermer={fermer} />
      )}
      {dialogue?.type === 'bareme' && (
        <DialogueBareme stationId={station.id} cuve={dialogue.cuve} peutGerer={peutGerer} onFermer={fermer} />
      )}
      {dialogue?.type === 'evenement' && <DialogueEvenement stationId={station.id} cible={dialogue.cible} onFermer={fermer} />}
    </>
  )
}
