import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import { Link } from 'react-router'

export function PageIntrouvable() {
  return (
    <Alert severity="info" action={<Button component={Link} to="/">Accueil</Button>}>
      Cette page n'existe pas.
    </Alert>
  )
}
