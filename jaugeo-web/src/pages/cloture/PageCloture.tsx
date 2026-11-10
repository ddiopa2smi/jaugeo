import DownloadIcon from '@mui/icons-material/Download'
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { moisPrecedent } from '../../shared/cloture'
import { AlerteErreur, EnTetePage } from '../../shared/composants'
import { moisCourant } from '../../shared/stock'
import { telecharger } from '../../shared/telechargement'
import { OngletBudget } from './OngletBudget'
import { OngletCompte } from './OngletCompte'
import { OngletEtat } from './OngletEtat'
import { OngletRegularisations } from './OngletRegularisations'

const ONGLETS = ['compte', 'cloture', 'regularisations', 'budget'] as const
type Onglet = (typeof ONGLETS)[number]

/** Clôture mensuelle : compte d'exploitation, clôture des stations, régularisations des mois clos, budget. */
export function PageCloture() {
  const [params, setParams] = useSearchParams()
  // Le mois qui se clôture est le dernier mois terminé
  const mois = params.get('mois') ?? moisPrecedent(moisCourant())
  const demande = params.get('onglet') as Onglet | null
  const onglet: Onglet = demande && ONGLETS.includes(demande) ? demande : 'compte'
  const [erreur, setErreur] = useState<unknown>(null)
  const [enCours, setEnCours] = useState(false)
  const changer = (cle: string, valeur: string) => {
    const p = new URLSearchParams(params)
    p.set(cle, valeur)
    setParams(p)
  }
  const exporter = async (format: 'xlsx' | 'pdf') => {
    setErreur(null)
    setEnCours(true)
    try {
      await telecharger(`/cloture/export.${format}?mois=${mois}`, `compte-exploitation-${mois}.${format}`)
    } catch (e) {
      setErreur(e)
    } finally {
      setEnCours(false)
    }
  }

  return (
    <>
      <EnTetePage
        titre="Clôture mensuelle"
        sousTitre="Compte d'exploitation, clôture des mois par station, régularisations et budget"
        actions={
          <>
            <Button variant="outlined" startIcon={<DownloadIcon />} disabled={enCours} onClick={() => exporter('xlsx')}>
              Excel
            </Button>
            <Button variant="outlined" startIcon={<PictureAsPdfIcon />} disabled={enCours} onClick={() => exporter('pdf')}>
              PDF
            </Button>
          </>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 1, alignItems: { sm: 'center' } }}>
        <TextField
          type="month"
          label="Mois"
          value={mois}
          onChange={(e) => e.target.value && changer('mois', e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          sx={{ width: { xs: '100%', sm: 200 } }}
        />
      </Stack>
      <AlerteErreur erreur={erreur} />
      <Tabs
        value={onglet}
        onChange={(_e, v: string) => changer('onglet', v)}
        variant="scrollable"
        allowScrollButtonsMobile
        sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab value="compte" label="Compte d'exploitation" />
        <Tab value="cloture" label="Clôture" />
        <Tab value="regularisations" label="Régularisations" />
        <Tab value="budget" label="Budget" />
      </Tabs>
      {onglet === 'compte' && <OngletCompte mois={mois} />}
      {onglet === 'cloture' && <OngletEtat mois={mois} />}
      {onglet === 'regularisations' && <OngletRegularisations mois={mois} />}
      {onglet === 'budget' && <OngletBudget mois={mois} />}
    </>
  )
}
