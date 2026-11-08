import { zodResolver } from '@hookform/resolvers/zod'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { messageErreur } from '../api/client'
import { changerMotDePasse, deconnecter, problemesMotDePasse } from '../auth/authApi'
import { useSession } from '../auth/session'
import { ChampTexte } from '../shared/composants'
import { notifier } from '../shared/notifications'

function creerSchema(identifiant: string) {
  return z
    .object({
      ancienMotDePasse: z.string().min(1, 'Saisissez votre mot de passe actuel'),
      nouveauMotDePasse: z.string(),
      confirmation: z.string(),
    })
    .superRefine((v, ctx) => {
      const problemes = problemesMotDePasse(v.nouveauMotDePasse, identifiant)
      if (problemes.length > 0) {
        ctx.addIssue({ code: 'custom', path: ['nouveauMotDePasse'], message: `Il faut : ${problemes.join(', ')}.` })
      } else if (v.nouveauMotDePasse === v.ancienMotDePasse) {
        ctx.addIssue({ code: 'custom', path: ['nouveauMotDePasse'], message: "Choisissez un mot de passe différent de l'actuel." })
      }
      if (v.confirmation !== v.nouveauMotDePasse) {
        ctx.addIssue({ code: 'custom', path: ['confirmation'], message: 'Les deux saisies ne correspondent pas.' })
      }
    })
}

type Saisie = z.infer<ReturnType<typeof creerSchema>>

/** Formulaire de changement de mot de passe (réutilisé dans « Mon compte »). */
export function FormulaireMotDePasse({ apresSucces }: { apresSucces: () => void }) {
  const identifiant = useSession((e) => e.utilisateur?.identifiant ?? '')
  const [erreur, setErreur] = useState<string | null>(null)
  const { control, handleSubmit, formState, reset } = useForm<Saisie>({
    resolver: zodResolver(creerSchema(identifiant)),
    defaultValues: { ancienMotDePasse: '', nouveauMotDePasse: '', confirmation: '' },
  })

  const soumettre = handleSubmit(async (v) => {
    setErreur(null)
    try {
      await changerMotDePasse(v.ancienMotDePasse, v.nouveauMotDePasse)
      reset()
      notifier('Mot de passe modifié. Vos autres sessions ont été fermées.')
      apresSucces()
    } catch (e) {
      setErreur(messageErreur(e))
    }
  })

  return (
    <Stack component="form" onSubmit={soumettre} noValidate spacing={2}>
      {erreur && <Alert severity="error">{erreur}</Alert>}
      <ChampTexte control={control} nom="ancienMotDePasse" libelle="Mot de passe actuel" type="password" autoComplete="current-password" />
      <ChampTexte
        control={control}
        nom="nouveauMotDePasse"
        libelle="Nouveau mot de passe"
        type="password"
        autoComplete="new-password"
        aide="10 caractères minimum, avec majuscule, minuscule et chiffre."
      />
      <ChampTexte control={control} nom="confirmation" libelle="Confirmer le nouveau mot de passe" type="password" autoComplete="new-password" />
      <Box>
        <Button type="submit" variant="contained" loading={formState.isSubmitting}>
          Enregistrer le nouveau mot de passe
        </Button>
      </Box>
    </Stack>
  )
}

/** Page imposée tant que le mot de passe initial ou provisoire n'a pas été changé. */
export function PageChangementMotDePasse() {
  const navigate = useNavigate()
  const nom = useSession((e) => e.utilisateur?.nomComplet)
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Paper sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 460 }}>
        <Stack spacing={2}>
          <Typography variant="h5" component="h1">
            Choisissez votre mot de passe
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Bonjour {nom}. Votre mot de passe actuel est provisoire : remplacez-le avant de continuer.
          </Typography>
          <FormulaireMotDePasse apresSucces={() => navigate('/', { replace: true })} />
          <Button
            variant="text"
            color="inherit"
            onClick={async () => {
              await deconnecter().catch(() => undefined)
              navigate('/connexion', { replace: true })
            }}
          >
            Se déconnecter
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
