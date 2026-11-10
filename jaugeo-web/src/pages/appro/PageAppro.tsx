import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import { useSearchParams } from 'react-router'
import { EnTetePage } from '../../shared/composants'
import { FacturesFournisseur } from './Factures'
import { Fournisseurs, Transporteurs } from './Partenaires'
import { Propositions, Tournees } from './Tournees'

const ONGLETS = ['propositions', 'tournees', 'factures', 'transporteurs', 'fournisseurs'] as const
type Onglet = (typeof ONGLETS)[number]

export function PageAppro() {
  const [params, setParams] = useSearchParams()
  const o = params.get('onglet') as Onglet | null
  const onglet: Onglet = o && ONGLETS.includes(o) ? o : 'propositions'
  const changer = (v: Onglet) => {
    const p = new URLSearchParams(params)
    p.set('onglet', v)
    setParams(p)
  }
  return (
    <>
      <EnTetePage
        titre="Approvisionnement"
        sousTitre="Propositions de commande, tournées citernes, factures fournisseur, transporteurs et dépôts."
      />
      <Tabs value={onglet} onChange={(_e, v: Onglet) => changer(v)} variant="scrollable" allowScrollButtonsMobile sx={{ mb: 2 }}>
        <Tab value="propositions" label="Propositions" />
        <Tab value="tournees" label="Tournées" />
        <Tab value="factures" label="Factures fournisseur" />
        <Tab value="transporteurs" label="Transporteurs" />
        <Tab value="fournisseurs" label="Fournisseurs" />
      </Tabs>
      {onglet === 'propositions' && <Propositions versTournees={() => changer('tournees')} />}
      {onglet === 'tournees' && <Tournees />}
      {onglet === 'factures' && <FacturesFournisseur />}
      {onglet === 'transporteurs' && <Transporteurs />}
      {onglet === 'fournisseurs' && <Fournisseurs />}
    </>
  )
}
