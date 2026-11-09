import Box from '@mui/material/Box'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState } from 'react'
import type { JourDirection, ProduitDirection } from '../../api/types'
import { formaterNombre, formaterPrix } from '../../shared/libelles'
import { couleurProduit } from './couleursProduits'

const HAUTEUR = 220
const MARGE = { haut: 8, bas: 22, gauche: 44, droite: 4 }

/** Graduation « ronde » de l'axe des montants : 1, 2 ou 5 × 10^n. */
function pas(max: number, n = 4): number {
  if (max <= 0) return 1
  const brut = max / n
  const p = 10 ** Math.floor(Math.log10(brut))
  return [1, 2, 5, 10].map((m) => m * p).find((v) => v >= brut) ?? 10 * p
}

function montantCourt(v: number): string {
  if (v >= 1_000_000) return `${formaterNombre(Math.round(v / 100_000) / 10)} M`
  if (v >= 1_000) return `${formaterNombre(Math.round(v / 1_000))} k`
  return formaterNombre(v)
}

/** Ventes du mois jour par jour, en colonnes empilées par produit (le total de la colonne = ventes du jour). */
export function GraphiqueVentesJour({ jours, produits }: { jours: JourDirection[]; produits: ProduitDirection[] }) {
  const conteneur = useRef<HTMLDivElement>(null)
  const [largeur, setLargeur] = useState(640)
  const [survol, setSurvol] = useState<number | null>(null)

  useEffect(() => {
    const el = conteneur.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const obs = new ResizeObserver(([e]) => setLargeur(Math.max(280, Math.floor(e!.contentRect.width))))
    obs.observe(el)
    return () => obs.disconnect()
  }, [])

  const max = Math.max(0, ...jours.map((j) => j.ventesTotal))
  const unite = pas(max)
  const plafond = Math.max(unite, Math.ceil(max / unite) * unite)
  const graduations = Array.from({ length: Math.round(plafond / unite) + 1 }, (_, i) => i * unite)
  const zoneL = largeur - MARGE.gauche - MARGE.droite
  const zoneH = HAUTEUR - MARGE.haut - MARGE.bas
  const bande = zoneL / Math.max(1, jours.length)
  const barre = Math.max(3, Math.min(22, bande * 0.62))
  const y = (v: number) => MARGE.haut + zoneH - (v / plafond) * zoneH
  const etiquetteTous = bande >= 22 ? 1 : bande >= 11 ? 2 : 5

  const jourSurvole = survol === null ? null : jours[survol]
  // Info-bulle à côté de la colonne survolée (à droite dans la première moitié, à gauche ensuite), jamais dessus
  const centre = survol === null ? 0 : MARGE.gauche + survol * bande + bande / 2
  const infoGauche = Math.min(Math.max(centre < largeur / 2 ? centre + barre / 2 + 8 : centre - barre / 2 - 8 - 200, 0), largeur - 200)

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 0.5, mb: 1 }} aria-label="Légende">
        {produits.map((p) => (
          <Stack key={p.code} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
            <Box sx={{ width: 10, height: 10, borderRadius: '3px', bgcolor: couleurProduit(p.code, produits) }} />
            <Typography variant="caption" color="text.secondary">
              {p.libelle}
            </Typography>
          </Stack>
        ))}
      </Stack>
      <Box ref={conteneur} sx={{ position: 'relative', width: '100%' }} onMouseLeave={() => setSurvol(null)}>
        <svg
          width={largeur}
          height={HAUTEUR}
          role="img"
          aria-label={`Ventes par jour et par produit, ${jours.length} jours ; maximum ${formaterPrix(max)}`}
          style={{ display: 'block', touchAction: 'pan-y' }}
        >
          {graduations.map((g) => (
            <g key={g}>
              <line x1={MARGE.gauche} x2={largeur - MARGE.droite} y1={y(g)} y2={y(g)} stroke="#e3e7eb" strokeWidth={1} />
              <text x={MARGE.gauche - 6} y={y(g) + 4} textAnchor="end" fontSize={11} fill="#6b7280">
                {montantCourt(g)}
              </text>
            </g>
          ))}
          {jours.map((j, i) => {
            const x = MARGE.gauche + i * bande + (bande - barre) / 2
            let cumul = 0
            const segments = produits
              .map((p) => ({ code: p.code, v: j.ventes[p.code] ?? 0 }))
              .filter((s) => s.v > 0)
            return (
              <g key={j.date} opacity={survol === null || survol === i ? 1 : 0.45}>
                {segments.map((s, k) => {
                  const haut = y(cumul + s.v)
                  const bas = y(cumul)
                  cumul += s.v
                  // 2 px de séparation entre segments ; coin arrondi de 4 px sur le segment du haut seulement
                  const h = Math.max(0, bas - haut - (k > 0 ? 2 : 0))
                  const r = k === segments.length - 1 ? Math.min(4, barre / 2, h) : 0
                  const y0 = haut
                  const d = `M${x},${y0 + h} V${y0 + r} Q${x},${y0} ${x + r},${y0} H${x + barre - r} Q${x + barre},${y0} ${x + barre},${y0 + r} V${y0 + h} Z`
                  return <path key={s.code} d={d} fill={couleurProduit(s.code, produits)} />
                })}
              </g>
            )
          })}
          {jours.map((j, i) =>
            i % etiquetteTous === 0 ? (
              <text key={j.date} x={MARGE.gauche + i * bande + bande / 2} y={HAUTEUR - 6} textAnchor="middle" fontSize={11} fill="#6b7280">
                {Number(j.date.slice(8, 10))}
              </text>
            ) : null,
          )}
          {/* Zones de survol pleine hauteur, plus larges que les barres (souris et toucher) */}
          {jours.map((j, i) => (
            <rect
              key={j.date}
              x={MARGE.gauche + i * bande}
              y={MARGE.haut}
              width={bande}
              height={zoneH}
              fill="transparent"
              onMouseEnter={() => setSurvol(i)}
              onClick={() => setSurvol((s) => (s === i ? null : i))}
            />
          ))}
        </svg>
        {jourSurvole && (
          <Box
            role="tooltip"
            sx={{
              position: 'absolute',
              top: 0,
              left: infoGauche,
              width: 200,
              p: 1.25,
              bgcolor: 'background.paper',
              border: '1px solid #e3e7eb',
              borderRadius: 1,
              boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
              pointerEvents: 'none',
            }}
          >
            <Typography variant="caption" sx={{ fontWeight: 600, display: 'block', mb: 0.5 }}>
              {new Date(`${jourSurvole.date}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}
            </Typography>
            {produits.map((p) => (
              <Stack key={p.code} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: couleurProduit(p.code, produits), flexShrink: 0 }} />
                <Typography variant="caption" sx={{ flexGrow: 1 }}>
                  {p.libelle}
                </Typography>
                <Typography variant="caption" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                  {formaterPrix(jourSurvole.ventes[p.code] ?? 0)}
                </Typography>
              </Stack>
            ))}
            <Typography variant="caption" sx={{ display: 'block', mt: 0.5, fontWeight: 600, textAlign: 'right' }}>
              {jourSurvole.ventesTotal > 0 ? `Total ${formaterPrix(jourSurvole.ventesTotal)}` : 'Aucune journée clôturée'}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  )
}
