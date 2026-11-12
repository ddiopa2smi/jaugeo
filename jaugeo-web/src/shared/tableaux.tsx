import SearchIcon from '@mui/icons-material/Search'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import InputAdornment from '@mui/material/InputAdornment'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import { alpha, type SxProps, type Theme } from '@mui/material/styles'
import TableCell from '@mui/material/TableCell'
import TableSortLabel from '@mui/material/TableSortLabel'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import type { Tri } from './tri'

// Composants communs des tableaux : cadre, en-tête triable, recherche, filtres en puces, puce d'état.
// Le tri et la recherche eux-mêmes sont dans tri.ts.

/** En-tête de colonne triable. */
export function EnTeteTri<T, C extends string>({
  tri,
  cle,
  align,
  sx,
  children,
}: {
  tri: Tri<T, C>
  cle: C
  align?: 'right'
  sx?: SxProps<Theme>
  children: ReactNode
}) {
  const actif = tri.cle === cle
  return (
    <TableCell align={align} sx={sx} sortDirection={actif ? tri.sens : false}>
      <TableSortLabel active={actif} direction={actif ? tri.sens : 'asc'} onClick={() => tri.basculer(cle)}>
        {children}
      </TableSortLabel>
    </TableCell>
  )
}

/**
 * Style compact d'un tableau : lignes serrées au minimum, survol bien visible, et ligne choisie
 * (TableRow `selected`) marquée d'un fond soutenu et d'un liseré à gauche.
 */
const styleCompact = (t: Theme) => ({
  '& .MuiTableCell-root.MuiTableCell-head, & .MuiTableBody-root .MuiTableCell-root': {
    py: 0.25,
    px: 0.75,
    lineHeight: 1.25,
    fontSize: 13,
    whiteSpace: 'nowrap',
  },
  '& .MuiTableCell-root.MuiTableCell-head': { fontSize: 11 },
  '& .MuiChip-root': { height: 18, fontSize: 11 },
  '& .MuiChip-label': { px: 0.75 },
  '& .MuiButton-sizeSmall': { py: 0, minHeight: 0, lineHeight: 1.6 },
  '& .MuiIconButton-sizeSmall': { p: 0.25 },
  '& .MuiTableBody-root .MuiTableRow-root:hover': { bgcolor: alpha(t.palette.primary.main, 0.12) },
  '& .MuiTableBody-root .MuiTableRow-root.Mui-selected': {
    bgcolor: alpha(t.palette.primary.main, 0.22),
    '& .MuiTableCell-root:first-of-type': { boxShadow: `inset 3px 0 0 ${t.palette.primary.main}` },
  },
  '& .MuiTableBody-root .MuiTableRow-root.Mui-selected:hover': { bgcolor: alpha(t.palette.primary.main, 0.28) },
})

/** Cadre arrondi d'un tableau : en-tête discret, lignes survolées, défilement horizontal sur petit écran. */
export function CadreTableau({ children, sx, compact }: { children: ReactNode; sx?: SxProps<Theme>; compact?: boolean }) {
  return (
    <Paper
      variant="outlined"
      sx={[
        (t) => ({
          overflowX: 'auto',
          borderRadius: '12px',
          '& .MuiTableCell-head': {
            bgcolor: alpha(t.palette.primary.main, 0.05),
            color: 'text.secondary',
            fontSize: 12,
            fontWeight: 600,
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
            whiteSpace: 'nowrap',
            py: 1.25,
          },
          '& .MuiTableSortLabel-root.Mui-active': { color: 'primary.main' },
          '& .MuiTableBody-root .MuiTableCell-root': { py: 1.25 },
          '& .MuiTableBody-root .MuiTableRow-root:last-of-type .MuiTableCell-root': { borderBottom: 0 },
          '& .MuiTableBody-root .MuiTableRow-root:hover': { bgcolor: alpha(t.palette.primary.main, 0.03) },
        }),
        // Le style compact passe après le style de base pour en remplacer les marges et le survol
        ...(compact ? [styleCompact] : []),
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {children}
    </Paper>
  )
}

export function ChampRecherche({ valeur, onChange, libelle }: { valeur: string; onChange: (v: string) => void; libelle: string }) {
  return (
    <TextField
      placeholder={libelle}
      value={valeur}
      onChange={(e) => onChange(e.target.value)}
      slotProps={{
        htmlInput: { 'aria-label': libelle },
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" />
            </InputAdornment>
          ),
        },
      }}
      sx={{ maxWidth: { sm: 300 }, bgcolor: 'background.paper', borderRadius: 1 }}
    />
  )
}

/** Filtre à choix unique présenté en puces, avec le nombre de lignes de chaque choix. */
export function FiltrePuces<V extends string>({
  libelle,
  options,
  valeur,
  onChange,
}: {
  libelle: string
  options: { valeur: V; libelle: string; nombre?: number }[]
  valeur: V
  onChange: (v: V) => void
}) {
  return (
    <Stack direction="row" role="group" aria-label={libelle} sx={{ flexWrap: 'wrap', gap: 0.75 }}>
      {options.map((o) => (
        <Chip
          key={o.valeur}
          size="small"
          label={o.nombre === undefined ? o.libelle : `${o.libelle} · ${o.nombre}`}
          color={valeur === o.valeur ? 'primary' : 'default'}
          variant={valeur === o.valeur ? 'filled' : 'outlined'}
          onClick={() => onChange(o.valeur)}
          aria-pressed={valeur === o.valeur}
        />
      ))}
    </Stack>
  )
}

/** Ligne de filtres au-dessus d'un tableau ; le résumé (nombre de lignes, total) se cale à droite. */
export function BarreFiltres({ children, resume }: { children: ReactNode; resume?: ReactNode }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1.5, mb: 1.5, alignItems: { sm: 'center' }, flexWrap: 'wrap' }}>
      {children}
      {resume && (
        <>
          <Box sx={{ flexGrow: 1 }} />
          <Typography variant="body2" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
            {resume}
          </Typography>
        </>
      )}
    </Stack>
  )
}

type Ton = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'

/** Puce d'état en aplat doux : fond teinté et texte de la couleur de l'état. */
export function PuceEtat({ ton, libelle }: { ton: Ton; libelle: string }) {
  return (
    <Chip
      size="small"
      label={libelle}
      sx={(t) => {
        const couleur = ton === 'default' ? t.palette.text.secondary : t.palette[ton].dark
        return { fontWeight: 600, color: couleur, bgcolor: alpha(ton === 'default' ? t.palette.text.secondary : t.palette[ton].main, 0.12) }
      }}
    />
  )
}
