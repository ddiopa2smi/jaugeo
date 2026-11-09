import QRCode from 'qrcode'
import type { CarteCarburant } from '../api/types'
import { notifier } from './notifications'

/** Contenu du QR code : préfixe lisible par l'application, puis le numéro. */
export const contenuQr = (numero: string) => `JAUGEO:CARTE:${numero}`

/** Ouvre la carte au format bancaire (85,6 × 54 mm) avec son QR code, prête à imprimer. */
export async function imprimerCarte(c: CarteCarburant) {
  const qr = await QRCode.toDataURL(contenuQr(c.numero), { errorCorrectionLevel: 'M', margin: 1, width: 300 })
  const fenetre = window.open('', '_blank')
  if (!fenetre) {
    notifier("Autorisez l'ouverture de fenêtres pour imprimer la carte.", 'warning')
    return
  }
  const echapper = (s: string) => s.replace(/[&<>"]/g, (x) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[x]!)
  const numero = c.numero.replace(/^(JC)(\d{4})(\d{3})(\d{3})$/, '$1 $2 $3 $4')
  fenetre.document.write(`<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Carte ${echapper(c.numero)}</title>
<style>
  @page { size: 85.6mm 54mm; margin: 0 }
  body { margin: 0; font-family: Roboto, Arial, sans-serif }
  .carte { box-sizing: border-box; width: 85.6mm; height: 54mm; padding: 4mm; display: flex; gap: 3mm;
           border: 0.3mm solid #999; border-radius: 3mm; background: #fff; color: #1a1a1a }
  .texte { flex: 1; display: flex; flex-direction: column; justify-content: space-between }
  .marque { font-weight: 700; font-size: 11pt; color: #e8590c }
  .titre { font-size: 7pt; text-transform: uppercase; letter-spacing: .05em; color: #555 }
  .porteur { font-size: 10pt; font-weight: 700 }
  .client, .immat { font-size: 7.5pt }
  .numero { font-family: monospace; font-size: 9pt; letter-spacing: .05em }
  img { width: 30mm; height: 30mm; align-self: center }
  @media screen { body { padding: 16px; background: #eee } }
</style></head><body>
<div class="carte"><div class="texte">
  <div><div class="marque">Jaugeo</div><div class="titre">Carte carburant prépayée</div></div>
  <div><div class="porteur">${echapper(c.porteur)}</div><div class="client">${echapper(c.clientNom)}</div>
  ${c.immatriculation ? `<div class="immat">${echapper(c.immatriculation)}</div>` : ''}</div>
  <div class="numero">${echapper(numero)}</div>
</div><img src="${qr}" alt="QR code"></div>
<script>window.onload = () => window.print()</script>
</body></html>`)
  fenetre.document.close()
}
