import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/client'
import { cles, useFactureFournisseur, useFacturesFournisseur, useFournisseursAppro, useProduits } from '../../api/requetes'
import type { FactureFournisseur, FactureFournisseurDetail, SaisieFactureFournisseur, StatutFactureFournisseur } from '../../api/types'
import { usePermission } from '../../auth/session'
import { AlerteErreur, Chargement } from '../../shared/composants'
import { formaterDate, formaterDateHeure, formaterNombre, formaterPrix, versNombre, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { BarreFiltres, CadreTableau, ChampRecherche, EnTeteTri, FiltrePuces, PuceEtat } from '../../shared/tableaux'
import { correspond, useTri } from '../../shared/tri'

const STATUTS_FACTURE: Record<StatutFactureFournisseur, { libelle: string; couleur: 'default' | 'success' | 'error' | 'warning' }> = {
  SAISIE: { libelle: 'À contrôler', couleur: 'warning' },
  BON_A_PAYER: { libelle: 'Bon à payer', couleur: 'success' },
  EN_LITIGE: { libelle: 'En litige', couleur: 'error' },
  ANNULEE: { libelle: 'Annulée', couleur: 'default' },
}

function usePeutFacturer() {
  const logistique = usePermission('RECEPTION_VALIDER')
  const comptable = usePermission('VERSEMENT_VALIDER')
  return logistique || comptable
}

interface LigneSaisie {
  numeroBl: string
  produitId: string
  quantite: string
  prixUnitaire: string
}

const LIGNE_VIDE: LigneSaisie = { numeroBl: '', produitId: '', quantite: '', prixUnitaire: '' }

function DialogueSaisieFacture({ detail, onFermer }: { detail?: FactureFournisseurDetail; onFermer: (id?: string) => void }) {
  const queryClient = useQueryClient()
  const fournisseurs = useFournisseursAppro()
  const produits = useProduits()
  const f = detail?.facture
  const [fournisseurId, setFournisseurId] = useState(f?.fournisseurId ?? '')
  const [numero, setNumero] = useState(f?.numero ?? '')
  const [date, setDate] = useState(() => f?.dateFacture ?? new Date().toISOString().slice(0, 10))
  const [observation, setObservation] = useState(f?.observation ?? '')
  const [lignes, setLignes] = useState<LigneSaisie[]>(
    detail
      ? detail.lignes.map((l) => ({
          numeroBl: l.numeroBl,
          produitId: l.produitId,
          quantite: String(l.quantite),
          prixUnitaire: String(l.prixUnitaire),
        }))
      : [{ ...LIGNE_VIDE }],
  )
  const carburants = (produits.data ?? []).filter((p) => p.actif && p.suiviJaugeage)
  const changer = (i: number, champ: keyof LigneSaisie, v: string) => setLignes((ls) => ls.map((l, j) => (j === i ? { ...l, [champ]: v } : l)))
  const total = lignes.reduce((s, l) => s + Math.round((versNombre(l.quantite) ?? 0) * (versNombre(l.prixUnitaire) ?? 0)), 0)

  const enregistrement = useMutation({
    mutationFn: () => {
      const corps: SaisieFactureFournisseur = {
        fournisseurId,
        numero: numero.trim(),
        dateFacture: date,
        observation: videVersNull(observation),
        lignes: lignes.map((l) => ({
          numeroBl: l.numeroBl.trim(),
          produitId: l.produitId,
          quantite: versNombre(l.quantite) ?? 0,
          prixUnitaire: versNombre(l.prixUnitaire) ?? 0,
        })),
      }
      return f
        ? api.put<FactureFournisseurDetail>(`/appro/factures/${f.id}`, { ...corps, version: f.version })
        : api.post<FactureFournisseurDetail>('/appro/factures', corps)
    },
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: cles.appro })
      notifier(`Facture ${d.facture.numero} enregistrée : ${d.facture.anomalies} anomalie(s).`)
      onFermer(d.facture.id)
    },
  })
  const complet =
    !!fournisseurId &&
    !!numero.trim() &&
    !!date &&
    lignes.length > 0 &&
    lignes.every((l) => l.numeroBl.trim() && l.produitId && (versNombre(l.quantite) ?? 0) > 0 && (versNombre(l.prixUnitaire) ?? 0) > 0)

  return (
    <Dialog open onClose={() => onFermer()} maxWidth="md" fullWidth>
      <DialogTitle>{f ? `Facture ${f.numero}` : 'Nouvelle facture fournisseur'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select fullWidth label="Fournisseur" value={fournisseurId} onChange={(e) => setFournisseurId(e.target.value)} required>
              {(fournisseurs.data ?? []).map((x) => (
                <MenuItem key={x.id} value={x.id}>
                  {x.nom}
                </MenuItem>
              ))}
            </TextField>
            <TextField fullWidth label="N° de facture" value={numero} onChange={(e) => setNumero(e.target.value)} required />
            <TextField type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
          </Stack>
          <Typography variant="subtitle2">Lignes (une par BL)</Typography>
          {lignes.map((l, i) => (
            <Stack key={i} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
              <TextField size="small" label="N° BL" value={l.numeroBl} onChange={(e) => changer(i, 'numeroBl', e.target.value)} />
              <TextField select size="small" label="Produit" value={l.produitId} onChange={(e) => changer(i, 'produitId', e.target.value)} sx={{ minWidth: 150 }}>
                {carburants.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.libelle}
                  </MenuItem>
                ))}
              </TextField>
              <TextField size="small" label="Quantité (L)" value={l.quantite} onChange={(e) => changer(i, 'quantite', e.target.value)}
                slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ width: 130 }} />
              <TextField size="small" label="Prix unitaire" value={l.prixUnitaire} onChange={(e) => changer(i, 'prixUnitaire', e.target.value)}
                slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ width: 120 }} />
              {lignes.length > 1 && (
                <IconButton aria-label="Retirer la ligne" onClick={() => setLignes((ls) => ls.filter((_x, j) => j !== i))}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
          <Button startIcon={<AddIcon />} onClick={() => setLignes((ls) => [...ls, { ...LIGNE_VIDE }])} sx={{ alignSelf: 'flex-start' }}>
            Ajouter un BL
          </Button>
          <Typography variant="body2">Total facturé : {formaterPrix(total)}</Typography>
          <TextField label="Observation" value={observation} onChange={(e) => setObservation(e.target.value)} multiline minRows={2} />
        </Stack>
        <AlerteErreur erreur={enregistrement.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onFermer()}>Annuler</Button>
        <Button variant="contained" onClick={() => enregistrement.mutate()} disabled={!complet || enregistrement.isPending}>
          Enregistrer et contrôler
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogueDecision({ detail, bonAPayer, onFermer }: { detail: FactureFournisseurDetail; bonAPayer: boolean | null; onFermer: () => void }) {
  const queryClient = useQueryClient()
  const [motif, setMotif] = useState('')
  const f = detail.facture
  const annulation = bonAPayer === null
  const decision = useMutation({
    mutationFn: () =>
      annulation
        ? api.post<FactureFournisseurDetail>(`/appro/factures/${f.id}/annulation`, { version: f.version, motif: motif.trim() })
        : api.post<FactureFournisseurDetail>(`/appro/factures/${f.id}/decision`, { version: f.version, bonAPayer, motif: videVersNull(motif) }),
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: cles.appro })
      notifier(`Facture ${d.facture.numero} : ${STATUTS_FACTURE[d.facture.statut].libelle.toLowerCase()}.`)
      onFermer()
    },
  })
  const motifExige = annulation || !bonAPayer || f.anomalies > 0
  const titre = annulation ? 'Annuler la facture' : bonAPayer ? 'Bon à payer' : 'Mettre en litige'
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>
        {titre} · {f.numero}
      </DialogTitle>
      <DialogContent>
        {bonAPayer && f.anomalies > 0 && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {f.anomalies} anomalie(s) relevée(s) : justifiez le bon à payer.
          </Alert>
        )}
        <Typography variant="body2" sx={{ mb: 2 }}>
          Facturé {formaterPrix(f.montant)}, attendu {formaterPrix(f.montantAttendu)} (écart {formaterPrix(f.ecart)}).
        </Typography>
        <TextField label={motifExige ? 'Motif' : 'Motif (facultatif)'} value={motif} onChange={(e) => setMotif(e.target.value)} fullWidth multiline minRows={2} autoFocus />
        <AlerteErreur erreur={decision.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Retour</Button>
        <Button
          variant="contained"
          color={bonAPayer ? 'success' : 'error'}
          onClick={() => decision.mutate()}
          disabled={(motifExige && !motif.trim()) || decision.isPending}
        >
          {titre}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DetailFacture({ id, onFermer }: { id: string; onFermer: () => void }) {
  const detail = useFactureFournisseur(id)
  const peutFacturer = usePeutFacturer()
  const [dialogue, setDialogue] = useState<'modifier' | 'bon' | 'litige' | 'annuler' | null>(null)
  const d = detail.data
  const f = d?.facture
  const modifiable = f && (f.statut === 'SAISIE' || f.statut === 'EN_LITIGE')
  return (
    <Dialog open onClose={onFermer} maxWidth="lg" fullWidth>
      <DialogTitle>
        Facture {f?.numero ?? ''} {f && <Chip size="small" color={STATUTS_FACTURE[f.statut].couleur} label={STATUTS_FACTURE[f.statut].libelle} />}
      </DialogTitle>
      <DialogContent>
        <AlerteErreur erreur={detail.error} />
        {detail.isLoading && <Chargement />}
        {d && f && (
          <>
            <Typography variant="body2" sx={{ mb: 1 }}>
              {f.fournisseurNom} · facture du {formaterDate(f.dateFacture)} · facturé {formaterPrix(f.montant)}, attendu{' '}
              {formaterPrix(f.montantAttendu)}
              {f.ecart !== 0 && (
                <Typography component="span" color="error" variant="body2">
                  {' '}
                  (écart {formaterPrix(f.ecart)})
                </Typography>
              )}
            </Typography>
            {f.motifDecision && (
              <Alert severity={f.statut === 'BON_A_PAYER' ? 'success' : 'error'} sx={{ mb: 2 }}>
                {f.motifDecision} — {f.decidePar}, {formaterDateHeure(f.decideLe)}
              </Alert>
            )}
            <CadreTableau>
              <Table size="small" aria-label="Contrôle des lignes">
                <TableHead>
                  <TableRow>
                    <TableCell>BL</TableCell>
                    <TableCell align="right">Facturé</TableCell>
                    <TableCell align="right">BL / reçu</TableCell>
                    <TableCell align="right">Prix convenu</TableCell>
                    <TableCell align="right">Attendu</TableCell>
                    <TableCell>Contrôle</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.lignes.map((l) => (
                    <TableRow key={l.numeroBl}>
                      <TableCell>
                        {l.numeroBl} · {l.produitCode}
                        {l.dateReception && (
                          <Typography variant="caption" color="text.secondary" component="div">
                            reçu le {formaterDate(l.dateReception)} à {l.stationCode}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {formaterNombre(l.quantite)} L × {formaterPrix(l.prixUnitaire)}
                        <Typography variant="caption" color="text.secondary" component="div">
                          {formaterPrix(l.montant)}
                        </Typography>
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {l.quantiteBl === null ? '—' : `${formaterNombre(l.quantiteBl)} / ${formaterNombre(l.quantiteRecue)} L`}
                        {l.base && (
                          <Typography variant="caption" color="text.secondary" component="div">
                            base {l.base}
                            {l.manquantFournisseur ? ` · manquant fournisseur ${formaterNombre(l.manquantFournisseur)} L` : ''}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">{formaterPrix(l.prixConvenu)}</TableCell>
                      <TableCell align="right">{formaterPrix(l.montantAttendu)}</TableCell>
                      <TableCell>
                        {l.anomalies.length === 0 ? (
                          <Chip size="small" color="success" label="Conforme" />
                        ) : (
                          l.anomalies.map((a) => (
                            <Typography key={a} variant="body2" color="error">
                              {a}
                            </Typography>
                          ))
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CadreTableau>
          </>
        )}
      </DialogContent>
      <DialogActions>
        {d && peutFacturer && modifiable && (
          <>
            <Button color="inherit" onClick={() => setDialogue('annuler')}>
              Annuler la facture
            </Button>
            <Button onClick={() => setDialogue('modifier')}>Corriger</Button>
            {f.statut === 'SAISIE' && (
              <Button color="error" onClick={() => setDialogue('litige')}>
                Litige
              </Button>
            )}
            <Button variant="contained" color="success" onClick={() => setDialogue('bon')}>
              Bon à payer
            </Button>
          </>
        )}
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
      {d && dialogue === 'modifier' && <DialogueSaisieFacture detail={d} onFermer={() => setDialogue(null)} />}
      {d && (dialogue === 'bon' || dialogue === 'litige' || dialogue === 'annuler') && (
        <DialogueDecision detail={d} bonAPayer={dialogue === 'annuler' ? null : dialogue === 'bon'} onFermer={() => setDialogue(null)} />
      )}
    </Dialog>
  )
}

/** Factures fournisseur : contrôle contre BL, quantités reçues et prix convenus ; bon à payer ou litige. */
export function FacturesFournisseur() {
  const liste = useFacturesFournisseur()
  const peutFacturer = usePeutFacturer()
  const [ouverte, setOuverte] = useState<string | null>(null)
  const [nouvelle, setNouvelle] = useState(false)
  const [recherche, setRecherche] = useState('')
  const [statut, setStatut] = useState<StatutFactureFournisseur | 'TOUS'>('TOUS')
  const tri = useTri<FactureFournisseur, 'facture' | 'fournisseur' | 'montant' | 'ecart' | 'controle' | 'statut'>(
    {
      facture: (f) => f.dateFacture,
      fournisseur: (f) => f.fournisseurNom,
      montant: (f) => f.montant,
      ecart: (f) => f.ecart,
      controle: (f) => f.anomalies,
      statut: (f) => STATUTS_FACTURE[f.statut].libelle,
    },
    'facture',
    'desc',
  )
  const factures = liste.data ?? []
  const statuts = (Object.keys(STATUTS_FACTURE) as StatutFactureFournisseur[]).filter((s) => factures.some((f) => f.statut === s))
  const visibles = tri.trier(factures.filter((f) => (statut === 'TOUS' || f.statut === statut) && correspond(recherche, f.numero, f.fournisseurNom)))
  return (
    <>
      <Stack direction="row" sx={{ mb: 2, justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Chaque ligne est rapprochée du BL chargé, de la quantité réceptionnée et du prix d'achat convenu.
        </Typography>
        {peutFacturer && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setNouvelle(true)}>
            Saisir une facture
          </Button>
        )}
      </Stack>
      <AlerteErreur erreur={liste.error} />
      {liste.isLoading && <Chargement />}
      {liste.data?.length === 0 && <Alert severity="info">Aucune facture fournisseur.</Alert>}
      {factures.length > 0 && (
        <BarreFiltres resume={`${visibles.length} facture(s) · ${formaterPrix(visibles.reduce((t, f) => t + f.montant, 0))}`}>
          <ChampRecherche valeur={recherche} onChange={setRecherche} libelle="Rechercher une facture, un fournisseur" />
          {statuts.length > 1 && (
            <FiltrePuces
              libelle="Filtrer par statut"
              valeur={statut}
              onChange={setStatut}
              options={[
                { valeur: 'TOUS' as const, libelle: 'Toutes', nombre: factures.length },
                ...statuts.map((s) => ({ valeur: s, libelle: STATUTS_FACTURE[s].libelle, nombre: factures.filter((f) => f.statut === s).length })),
              ]}
            />
          )}
        </BarreFiltres>
      )}
      {factures.length > 0 && visibles.length === 0 && <Alert severity="info">Aucune facture ne correspond à ces filtres.</Alert>}
      {visibles.length > 0 && (
        <CadreTableau>
          <Table size="small" aria-label="Factures fournisseur">
            <TableHead>
              <TableRow>
                <EnTeteTri tri={tri} cle="facture">
                  Facture
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="fournisseur">
                  Fournisseur
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="montant" align="right">
                  Facturé
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="ecart" align="right">
                  Écart
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="controle">
                  Contrôle
                </EnTeteTri>
                <EnTeteTri tri={tri} cle="statut">
                  Statut
                </EnTeteTri>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibles.map((f) => (
                <TableRow key={f.id} hover sx={{ cursor: 'pointer' }} onClick={() => setOuverte(f.id)}>
                  <TableCell>
                    {f.numero}
                    <Typography variant="caption" color="text.secondary" component="div">
                      {formaterDate(f.dateFacture)} · {f.lignes} BL
                    </Typography>
                  </TableCell>
                  <TableCell>{f.fournisseurNom}</TableCell>
                  <TableCell align="right">{formaterPrix(f.montant)}</TableCell>
                  <TableCell align="right" sx={{ color: f.ecart !== 0 ? 'error.main' : undefined }}>
                    {formaterPrix(f.ecart)}
                  </TableCell>
                  <TableCell>
                    {f.anomalies === 0 ? <PuceEtat ton="success" libelle="Conforme" /> : <PuceEtat ton="error" libelle={`${f.anomalies} anomalie(s)`} />}
                  </TableCell>
                  <TableCell>
                    <PuceEtat ton={STATUTS_FACTURE[f.statut].couleur} libelle={STATUTS_FACTURE[f.statut].libelle} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CadreTableau>
      )}
      {ouverte && <DetailFacture id={ouverte} onFermer={() => setOuverte(null)} />}
      {nouvelle && (
        <DialogueSaisieFacture
          onFermer={(id) => {
            setNouvelle(false)
            if (id) setOuverte(id)
          }}
        />
      )}
    </>
  )
}
