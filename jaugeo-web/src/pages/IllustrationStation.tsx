import Box from '@mui/material/Box'
import { keyframes } from '@mui/material/styles'

const flotte = keyframes`
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
`
const remplit = keyframes`
  0% { transform: scaleY(0.25); }
  100% { transform: scaleY(1); }
`
const vague = keyframes`
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(8px); }
`

const clair = '#e8f1f5'
const pale = '#cfe0e8'
const bleu = '#0b4f6c'
const orange = '#f5a623'

/**
 * Illustration de la page de connexion (grand écran seulement) : une station routière avec ses pompes,
 * une cuve et sa jauge, la synthèse de la journée et une pirogue pour les stations de pêche.
 * Purement décorative ; animations coupées si l'utilisateur demande moins de mouvement.
 */
export function IllustrationStation() {
  return (
    <Box
      component="svg"
      viewBox="0 0 480 370"
      aria-hidden="true"
      focusable="false"
      sx={{
        width: '100%',
        maxWidth: 520,
        height: 'auto',
        '& .flotte': { animation: `${flotte} 5s ease-in-out infinite` },
        '& .niveau': { transformOrigin: '380px 269px', transformBox: 'view-box', animation: `${remplit} 1.6s ease-out both` },
        '& .vague': { animation: `${vague} 6s ease-in-out infinite` },
        '@media (prefers-reduced-motion: reduce)': { '& .flotte, & .niveau, & .vague': { animation: 'none' } },
      }}
    >
      {/* Fond */}
      <circle cx="362" cy="122" r="110" fill="#ffffff" opacity="0.05" />
      <circle cx="140" cy="250" r="110" fill="#ffffff" opacity="0.04" />
      <circle cx="74" cy="58" r="22" fill={orange} opacity="0.9" />

      {/* Sol et canalisation vers la cuve */}
      <rect x="40" y="286" width="420" height="4" rx="2" fill="#ffffff" opacity="0.25" />
      <path d="M268 279H340" stroke="#ffffff" strokeOpacity="0.3" strokeWidth="3" strokeDasharray="6 6" />

      {/* Auvent */}
      <rect x="166" y="76" width="44" height="22" rx="5" fill={orange} />
      <path d="M188 80c-3.5 4.6-6 8-6 11.4a6 6 0 0 0 12 0c0-3.4-2.5-6.8-6-11.4z" fill="#ffffff" />
      <rect x="60" y="98" width="250" height="20" rx="6" fill={clair} />
      <rect x="60" y="114" width="250" height="10" rx="3" fill={orange} />
      <rect x="78" y="124" width="10" height="162" fill={pale} opacity="0.85" />
      <rect x="282" y="124" width="10" height="162" fill={pale} opacity="0.85" />

      {/* Pompe principale */}
      <rect x="144" y="280" width="68" height="8" rx="3" fill={pale} />
      <rect x="150" y="176" width="56" height="106" rx="8" fill="#ffffff" />
      <rect x="160" y="188" width="36" height="26" rx="3" fill={bleu} />
      <rect x="165" y="195" width="26" height="4" rx="2" fill={orange} />
      <rect x="165" y="203" width="16" height="4" rx="2" fill={orange} opacity="0.7" />
      <rect x="150" y="240" width="56" height="6" fill={orange} />
      <rect x="206" y="214" width="8" height="22" rx="3" fill={orange} />
      <path d="M214 232q22 10 18 38q-2 12-14 14" fill="none" stroke={pale} strokeWidth="4" strokeLinecap="round" />

      {/* Seconde pompe */}
      <rect x="226" y="281" width="48" height="7" rx="3" fill={pale} />
      <rect x="230" y="200" width="40" height="82" rx="7" fill="#ffffff" opacity="0.92" />
      <rect x="237" y="210" width="26" height="18" rx="3" fill={bleu} />
      <rect x="241" y="216" width="18" height="3.5" rx="1.75" fill={orange} />
      <rect x="230" y="246" width="40" height="5" fill={orange} opacity="0.85" />

      {/* Cuve et jauge */}
      <rect x="340" y="168" width="80" height="118" rx="14" fill={pale} />
      <rect x="340" y="168" width="80" height="14" rx="7" fill={clair} />
      <rect x="368" y="186" width="24" height="88" rx="6" fill={bleu} />
      <rect className="niveau" x="371" y="214" width="18" height="55" rx="4" fill={orange} />
      {[194, 210, 226, 242, 258].map((y) => (
        <rect key={y} x="350" y={y} width={y % 32 === 2 ? 12 : 8} height="2.5" rx="1.25" fill={bleu} opacity="0.55" />
      ))}

      {/* Synthèse de la journée */}
      <g className="flotte">
        <rect x="304" y="46" width="150" height="94" rx="10" fill="#000000" opacity="0.15" />
        <rect x="300" y="40" width="150" height="94" rx="10" fill="#ffffff" />
        <rect x="314" y="54" width="64" height="6" rx="3" fill={bleu} opacity="0.85" />
        <rect x="314" y="64" width="40" height="4" rx="2" fill={bleu} opacity="0.35" />
        <circle cx="430" cy="60" r="9" fill="#2e7d32" />
        <path d="M425.5 60l3 3 5.5-6.5" fill="none" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
        <rect x="314" y="120" width="124" height="2" rx="1" fill={bleu} opacity="0.2" />
        {[
          [318, 26],
          [340, 38],
          [362, 30],
          [384, 46],
          [406, 40],
        ].map(([x, h], i) => (
          <rect key={x} x={x} y={120 - h} width="14" height={h} rx="3" fill={i === 4 ? orange : bleu} opacity={i === 4 ? 1 : 0.8} />
        ))}
      </g>

      {/* Pirogue des stations de pêche */}
      <g className="vague">
        <path d="M30 330q15-8 30 0t30 0t30 0t30 0t30 0" fill="none" stroke="#ffffff" strokeOpacity="0.25" strokeWidth="3" strokeLinecap="round" />
        <path d="M70 348q15-8 30 0t30 0t30 0t30 0" fill="none" stroke="#ffffff" strokeOpacity="0.15" strokeWidth="3" strokeLinecap="round" />
      </g>
      <path d="M38 304h124q-10 20-32 20H70q-22 0-32-20z" fill={orange} />
      <path d="M50 311h100" stroke="#ffffff" strokeOpacity="0.75" strokeWidth="3" strokeLinecap="round" />
      <path d="M100 304V276l18 22" fill="none" stroke={clair} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </Box>
  )
}
