import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import { useEffect, useRef, useState } from 'react'
import { constructeurDetecteur, lectureCameraDisponible } from './camera'

/**
 * Lecture d'un QR code par la caméra arrière ; s'arrête au premier code lu. Sans BarcodeDetector, le bouton n'apparaît
 * pas : le numéro se tape, ou une douchette le saisit comme un clavier.
 */
export function LecteurQr({ onLu }: { onLu: (texte: string) => void }) {
  const [actif, setActif] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const video = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    if (!actif) return
    const Detecteur = constructeurDetecteur()
    if (!Detecteur) return
    let flux: MediaStream | null = null
    let arret = false
    let minuterie: number | undefined
    const detecteur = new Detecteur({ formats: ['qr_code'] })
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' } })
      .then((f) => {
        flux = f
        if (arret || !video.current) return
        video.current.srcObject = f
        void video.current.play()
        const lire = () => {
          if (arret || !video.current) return
          detecteur
            .detect(video.current)
            .then((codes) => {
              const code = codes[0]?.rawValue
              if (code) {
                setActif(false)
                onLu(code)
              } else {
                minuterie = window.setTimeout(lire, 250)
              }
            })
            .catch(() => {
              minuterie = window.setTimeout(lire, 500)
            })
        }
        lire()
      })
      .catch(() => setErreur("Caméra inaccessible : autorisez-la dans le navigateur, ou saisissez le numéro."))
    return () => {
      arret = true
      window.clearTimeout(minuterie)
      flux?.getTracks().forEach((t) => t.stop())
    }
  }, [actif, onLu])

  if (!lectureCameraDisponible()) return null
  return (
    <Box>
      <Button
        startIcon={<QrCodeScannerIcon />}
        onClick={() => {
          setErreur(null)
          setActif((a) => !a)
        }}
      >
        {actif ? 'Arrêter la caméra' : 'Scanner le QR code'}
      </Button>
      {actif && (
        <Box
          component="video"
          ref={video}
          muted
          playsInline
          sx={{ display: 'block', width: '100%', maxHeight: 240, mt: 1, borderRadius: 1, bgcolor: 'common.black' }}
        />
      )}
      {erreur && (
        <Alert severity="warning" sx={{ mt: 1 }}>
          {erreur}
        </Alert>
      )}
    </Box>
  )
}
