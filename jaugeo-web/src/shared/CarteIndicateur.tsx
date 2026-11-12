import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import { alpha } from '@mui/material/styles'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'

type Ton = 'primary' | 'success' | 'warning' | 'error' | 'info'

/**
 * Chiffre clé d'une page : pictogramme teinté, libellé, valeur et précision.
 * `accent` remplit la carte de la couleur du ton, pour le chiffre que l'on vient chercher.
 */
export function CarteIndicateur({
  libelle,
  valeur,
  sous,
  icone,
  ton = 'primary',
  accent = false,
  testid,
  onClick,
  children,
}: {
  libelle: string
  valeur?: ReactNode
  sous?: ReactNode
  icone: ReactNode
  ton?: Ton
  accent?: boolean
  testid?: string
  /** Carte cliquable : elle se soulève au survol et se commande au clavier. */
  onClick?: () => void
  /** Contenu libre sous l'en-tête, à la place de la valeur (ex. plusieurs chiffres côte à côte). */
  children?: ReactNode
}) {
  return (
    <Paper
      variant="outlined"
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-label={onClick ? libelle : undefined}
      onKeyDown={onClick ? (e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), onClick()) : undefined}
      sx={(t) => ({
        p: { xs: 1.75, md: 2 },
        borderRadius: '14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        minWidth: 0,
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        transition: 'transform .15s ease, box-shadow .15s ease, border-color .15s ease',
        // Halo discret de la couleur du ton dans le coin de la carte
        backgroundImage: accent
          ? 'none'
          : `radial-gradient(circle at 100% 0%, ${alpha(t.palette[ton].main, 0.1)} 0, transparent 55%)`,
        ...(accent && { bgcolor: t.palette[ton].main, color: t.palette[ton].contrastText, borderColor: t.palette[ton].main }),
        ...(onClick && {
          cursor: 'pointer',
          '&:hover, &:focus-visible': {
            transform: 'translateY(-2px)',
            boxShadow: `0 6px 18px ${alpha(t.palette[ton].main, 0.18)}`,
            borderColor: alpha(t.palette[ton].main, 0.5),
          },
          '&:focus-visible': { outline: `2px solid ${t.palette[ton].main}`, outlineOffset: 2 },
        }),
      })}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
        <Box
          aria-hidden
          sx={(t) => ({
            width: 36,
            height: 36,
            flexShrink: 0,
            borderRadius: '10px',
            display: 'grid',
            placeItems: 'center',
            color: accent ? 'inherit' : t.palette[ton].main,
            bgcolor: accent ? alpha(t.palette[ton].contrastText, 0.18) : alpha(t.palette[ton].main, 0.12),
            '& svg': { fontSize: 20 },
          })}
        >
          {icone}
        </Box>
        <Typography variant="body2" sx={{ color: accent ? 'inherit' : 'text.secondary', lineHeight: 1.25, opacity: accent ? 0.9 : 1 }}>
          {libelle}
        </Typography>
      </Box>
      {valeur !== undefined && (
        <Typography
          sx={{ fontSize: { xs: 22, md: 26 }, fontWeight: 700, lineHeight: 1.1, fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}
          data-testid={testid}
        >
          {valeur}
        </Typography>
      )}
      {children}
      {sous && (
        <Typography variant="caption" component="div" sx={{ color: accent ? 'inherit' : 'text.secondary', opacity: accent ? 0.9 : 1 }}>
          {sous}
        </Typography>
      )}
    </Paper>
  )
}
