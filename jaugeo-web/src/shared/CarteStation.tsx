import ChevronRight from '@mui/icons-material/ChevronRight'
import LocalGasStation from '@mui/icons-material/LocalGasStation'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Chip from '@mui/material/Chip'
import { alpha } from '@mui/material/styles'
import Typography from '@mui/material/Typography'
import type { Station } from '../api/types'
import { TYPES_STATION } from './libelles'

type Teinte = 'primary' | 'success' | 'warning' | 'error' | 'neutre'

/** Pompe sur fond teinté : le repère visuel d'une station, à la place de son code. */
export function PastilleStation({
  teinte = 'primary',
  taille = 44,
  icone: Icone = LocalGasStation,
}: {
  teinte?: Teinte
  taille?: number
  /** Autre équipement que la pompe (cuve…) */
  icone?: typeof LocalGasStation
}) {
  return (
    <Box
      aria-hidden
      sx={(t) => {
        const couleur = teinte === 'neutre' ? t.palette.text.secondary : t.palette[teinte].main
        return {
          width: taille,
          height: taille,
          flexShrink: 0,
          display: 'grid',
          placeItems: 'center',
          borderRadius: taille >= 40 ? 1.5 : 1,
          color: couleur,
          bgcolor: alpha(couleur, 0.1),
        }
      }}
    >
      <Icone sx={{ fontSize: Math.round(taille * 0.55) }} />
    </Box>
  )
}

/** Station dans une ligne de tableau : pompe puis nom, avec un détail facultatif dessous. */
export function NomStation({ nom, detail, teinte }: { nom: string; detail?: string; teinte?: Teinte }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
      <PastilleStation teinte={teinte} taille={30} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
          {nom}
        </Typography>
        {detail && (
          <Typography variant="caption" color="text.secondary" component="div" sx={{ whiteSpace: 'nowrap' }}>
            {detail}
          </Typography>
        )}
      </Box>
    </Box>
  )
}

/** Carte d'une station : toute la surface ouvre sa fiche, au doigt comme au clavier. */
export function CarteStation({ station, onOuvrir }: { station: Station; onOuvrir: () => void }) {
  const lieu = [station.localite, station.regionNom].filter((v, i, tous) => v && tous.indexOf(v) === i).join(' · ')
  return (
    <ButtonBase
      onClick={onOuvrir}
      aria-label={`Station ${station.nom}${station.actif ? '' : ' (fermée)'}`}
      sx={(t) => ({
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        width: '100%',
        minHeight: 76,
        p: 1.5,
        textAlign: 'left',
        bgcolor: 'background.paper',
        border: '1px solid #e3e7eb',
        borderRadius: 1.5,
        transition: 'border-color .15s, box-shadow .15s, transform .15s',
        '& .chevron': { color: 'text.disabled', transition: 'transform .15s, color .15s' },
        '@media (hover: hover)': {
          '&:hover': { borderColor: alpha(t.palette.primary.main, 0.45), boxShadow: `0 4px 14px ${alpha(t.palette.primary.main, 0.1)}` },
          '&:hover .chevron': { color: 'primary.main', transform: 'translateX(2px)' },
        },
        '&.Mui-focusVisible': { borderColor: 'primary.main', boxShadow: `0 0 0 3px ${alpha(t.palette.primary.main, 0.2)}` },
        '&:active': { transform: 'scale(0.99)' },
        '@media (prefers-reduced-motion: reduce)': { transition: 'none', '&:active': { transform: 'none' } },
      })}
    >
      <PastilleStation teinte={station.actif ? 'primary' : 'neutre'} />
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="subtitle1" noWrap sx={{ fontWeight: 600, lineHeight: 1.3, color: station.actif ? 'text.primary' : 'text.secondary' }}>
            {station.nom}
          </Typography>
          {!station.actif && <Chip size="small" variant="outlined" label="Fermée" sx={{ height: 20, flexShrink: 0 }} />}
        </Box>
        <Typography variant="body2" color="text.secondary" noWrap>
          {TYPES_STATION[station.type]}
          {lieu ? ` · ${lieu}` : ''}
        </Typography>
      </Box>
      <ChevronRight className="chevron" />
    </ButtonBase>
  )
}
