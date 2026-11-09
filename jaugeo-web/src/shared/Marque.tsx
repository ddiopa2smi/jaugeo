import Box from '@mui/material/Box'
import Typography, { type TypographyProps } from '@mui/material/Typography'

// Lu par les lecteurs d'écran, invisible à l'écran. En ligne (pas de position absolue) pour que le nom
// se lise « Jaugeo » d'un seul mot.
const visuallyHidden = {
  display: 'inline-block',
  width: '1px',
  height: '1px',
  marginRight: '-1px',
  overflow: 'hidden',
  clipPath: 'inset(50%)',
  whiteSpace: 'nowrap',
  verticalAlign: 'baseline',
} as const

type Props = Pick<TypographyProps, 'variant' | 'component' | 'sx'> & {
  /** Couleur de la goutte (le « o ») ; orange de la marque par défaut */
  couleurGoutte?: string
}

/**
 * Nom de la plateforme : « Jauge » suivi d'une goutte de carburant à la place du « o ».
 * Le texte accessible reste « Jaugeo » (lecteurs d'écran, recherche dans la page, tests).
 */
export function Marque({ variant = 'h6', component = 'span', sx, couleurGoutte = '#f5a623' }: Props) {
  return (
    <Typography variant={variant} component={component} sx={{ letterSpacing: 0.5, whiteSpace: 'nowrap', ...sx }}>
      Jauge
      <Box
        component="svg"
        viewBox="17 9 30 43"
        aria-hidden="true"
        focusable="false"
        sx={{ display: 'inline-block', height: '0.92em', width: 'auto', ml: '0.05em', verticalAlign: '-0.02em' }}
      >
        {/* Goutte du premier logo ; ses deux traits de jauge prennent la couleur du texte */}
        <path d="M32 10c-8 11-14 19-14 27a14 14 0 0 0 28 0c0-8-6-16-14-27z" fill={couleurGoutte} />
        <rect x="24" y="37" width="16" height="3.4" rx="1.7" fill="currentColor" />
        <rect x="24" y="43.5" width="10" height="3.4" rx="1.7" fill="currentColor" />
      </Box>
      <Box component="span" sx={visuallyHidden}>
        o
      </Box>
    </Typography>
  )
}
