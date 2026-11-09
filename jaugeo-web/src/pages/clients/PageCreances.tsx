import GavelIcon from '@mui/icons-material/Gavel'
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tabs from '@mui/material/Tabs'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { cles, useBalanceAgee, useBonsEnAttente, useClients, useFacture, useFactures } from '../../api/requetes'
import type { BonEnAttente, Facture, FactureDetail, LigneBalance, ModeRelance, Relance, ResultatFacturation } from '../../api/types'
import { usePermission } from '../../auth/session'
import { MODES_RELANCE, NIVEAUX_RELANCE } from '../../shared/clients'
import { AlerteErreur, Chargement, EnTetePage } from '../../shared/composants'
import { formaterDate, formaterNombre, formaterPrix, videVersNull } from '../../shared/libelles'
import { notifier } from '../../shared/notifications'
import { telecharger } from '../../shared/telechargement'
import { aujourdhui } from '../../shared/tresorerie'
import { DialogueDecisionBon } from './DecisionBon'

const nombres = { whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' } as const

function useRafraichirCreances() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: cles.clients })
    queryClient.invalidateQueries({ queryKey: cles.syntheses })
  }
}

/** Premier et dernier jour du mois précédent (période de facturation par défaut). */
function moisPrecedent(): { du: string; au: string } {
  const [a, m] = aujourdhui().split('-').map(Number) as [number, number]
  const debut = new Date(Date.UTC(m === 1 ? a - 1 : a, m === 1 ? 11 : m - 2, 1))
  const fin = new Date(Date.UTC(a, m - 1, 0))
  return { du: debut.toISOString().slice(0, 10), au: fin.toISOString().slice(0, 10) }
}

// ---------------------------------------------------------------- Balance âgée et relances

function DialogueRelance({ ligne, onFermer }: { ligne: LigneBalance; onFermer: () => void }) {
  const rafraichir = useRafraichirCreances()
  const [mode, setMode] = useState<ModeRelance>('COURRIER')
  const [commentaire, setCommentaire] = useState('')
  const relance = useMutation({
    mutationFn: () => api.post<Relance>(`/clients/${ligne.clientId}/relances`, { mode, commentaire: videVersNull(commentaire) }),
    onSuccess: async (r) => {
      rafraichir()
      notifier(`${NIVEAUX_RELANCE[r.niveau]} enregistré pour ${formaterPrix(r.montantEchu)} échus.`)
      onFermer()
      if (r.mode === 'COURRIER' || r.mode === 'EMAIL') {
        await telecharger(`/clients/relances/${r.id}/lettre`, `relance-${ligne.code}-${r.date}.pdf`)
      }
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="xs" fullWidth>
      <DialogTitle>
        {NIVEAUX_RELANCE[ligne.niveauSuggere]} · {ligne.nom}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2">
            Échu non réglé : <strong>{formaterPrix(ligne.echu)}</strong>
            {ligne.plusAncienneEcheance && ` depuis le ${formaterDate(ligne.plusAncienneEcheance)}`}.
            {ligne.derniereRelance && ` Dernière relance le ${formaterDate(ligne.derniereRelance)}.`}
          </Typography>
          <TextField select label="Mode" value={mode} onChange={(e) => setMode(e.target.value as ModeRelance)}>
            {(Object.keys(MODES_RELANCE) as ModeRelance[]).map((m) => (
              <MenuItem key={m} value={m}>
                {MODES_RELANCE[m]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Commentaire"
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            multiline
            minRows={2}
            helperText="Par courrier ou e-mail, la lettre PDF est téléchargée après l'enregistrement."
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
        <AlerteErreur erreur={relance.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>Annuler</Button>
        <Button variant="contained" onClick={() => relance.mutate()} disabled={relance.isPending}>
          Enregistrer la relance
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function OngletBalance() {
  const [au, setAu] = useState(aujourdhui())
  const balance = useBalanceAgee(au)
  const peutGerer = usePermission('CLIENT_GERER')
  const [relance, setRelance] = useState<LigneBalance | null>(null)
  const b = balance.data
  const total = (cle: keyof LigneBalance, v: number) => (
    <TableCell align="right" sx={{ ...nombres, fontWeight: 700 }} key={cle}>
      {formaterPrix(v)}
    </TableCell>
  )
  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <TextField type="date" label="Au" value={au} onChange={(e) => setAu(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <Typography variant="body2" color="text.secondary">
          Tranches selon l'ancienneté de la facture ou de la reprise ; les règlements soldent d'abord les dettes les plus anciennes.
          « Échu » : échéance passée et non réglée.
        </Typography>
      </Stack>
      <AlerteErreur erreur={balance.error} />
      {balance.isLoading && <Chargement />}
      {b && (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Balance âgée">
            <TableHead>
              <TableRow>
                <TableCell>Client</TableCell>
                <TableCell align="right">Non facturé</TableCell>
                <TableCell align="right">0-30 j</TableCell>
                <TableCell align="right">31-60 j</TableCell>
                <TableCell align="right">61-90 j</TableCell>
                <TableCell align="right">+ 90 j</TableCell>
                <TableCell align="right">Total dû</TableCell>
                <TableCell align="right">Échu</TableCell>
                <TableCell>Relances</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {b.lignes.map((l) => (
                <TableRow key={l.clientId} data-testid={`balance-${l.code}`}>
                  <TableCell>
                    <RouterLink to={`/clients?client=${l.clientId}`}>{l.nom}</RouterLink>
                    <Typography variant="caption" color="text.secondary" component="div">
                      {l.code}
                      {l.delaiPaiement !== null ? ` · ${l.delaiPaiement} j` : ' · sans délai'}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={nombres}>
                    {formaterPrix(l.nonFacture)}
                  </TableCell>
                  <TableCell align="right" sx={nombres}>
                    {formaterPrix(l.jours0a30)}
                  </TableCell>
                  <TableCell align="right" sx={nombres}>
                    {formaterPrix(l.jours31a60)}
                  </TableCell>
                  <TableCell align="right" sx={nombres}>
                    {formaterPrix(l.jours61a90)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...nombres, color: l.plusDe90 > 0 ? 'error.main' : undefined }}>
                    {formaterPrix(l.plusDe90)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...nombres, fontWeight: 700 }}>
                    {formaterPrix(l.total)}
                  </TableCell>
                  <TableCell align="right" sx={{ ...nombres, color: l.echu > 0 ? 'error.main' : undefined }}>
                    {formaterPrix(l.echu)}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {l.derniereRelance && (
                      <Typography variant="caption" component="div">
                        {NIVEAUX_RELANCE[l.dernierNiveau ?? 1]} le {formaterDate(l.derniereRelance)}
                      </Typography>
                    )}
                    {peutGerer && l.niveauSuggere > 0 && (
                      <Button size="small" startIcon={<GavelIcon />} onClick={() => setRelance(l)}>
                        {NIVEAUX_RELANCE[l.niveauSuggere]}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {b.lignes.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9}>Aucune créance à cette date.</TableCell>
                </TableRow>
              )}
              {b.lignes.length > 0 && (
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Total</TableCell>
                  {total('nonFacture', b.nonFacture)}
                  {total('jours0a30', b.jours0a30)}
                  {total('jours31a60', b.jours31a60)}
                  {total('jours61a90', b.jours61a90)}
                  {total('plusDe90', b.plusDe90)}
                  {total('total', b.total)}
                  {total('echu', b.echu)}
                  <TableCell />
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      {relance && <DialogueRelance ligne={relance} onFermer={() => setRelance(null)} />}
    </>
  )
}

// ---------------------------------------------------------------- Factures

function DialogueFacturation({ onFermer }: { onFermer: () => void }) {
  const rafraichir = useRafraichirCreances()
  const clients = useClients()
  const defaut = moisPrecedent()
  const [du, setDu] = useState(defaut.du)
  const [au, setAu] = useState(defaut.au)
  const [dateFacture, setDateFacture] = useState(aujourdhui())
  const [clientId, setClientId] = useState('')
  const [resultat, setResultat] = useState<ResultatFacturation | null>(null)
  const facturation = useMutation({
    mutationFn: () => api.post<ResultatFacturation>('/clients/factures', { du, au, dateFacture, clientId: clientId || null }),
    onSuccess: (r) => {
      rafraichir()
      setResultat(r)
      if (r.factures.length > 0) notifier(`${r.factures.length} facture(s) émise(s), ${r.bonsFactures} bon(s).`)
    },
  })
  return (
    <Dialog open onClose={onFermer} maxWidth="sm" fullWidth>
      <DialogTitle>Facturer une période</DialogTitle>
      <DialogContent>
        {resultat ? (
          <Stack spacing={1} sx={{ pt: 1 }}>
            {resultat.remarques.map((r) => (
              <Alert key={r} severity="info">
                {r}
              </Alert>
            ))}
            {resultat.factures.map((f) => (
              <Typography key={f.id} variant="body2">
                {f.numero} · {f.clientNom} · {f.bons} bon(s) · <strong>{formaterPrix(f.montant)}</strong> · échéance{' '}
                {formaterDate(f.dateEcheance)}
              </Typography>
            ))}
          </Stack>
        ) : (
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Stack direction="row" spacing={2}>
              <TextField type="date" label="Du" value={du} onChange={(e) => setDu(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ flex: 1 }} />
              <TextField type="date" label="Au" value={au} onChange={(e) => setAu(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ flex: 1 }} />
            </Stack>
            <TextField
              type="date"
              label="Date de facture"
              value={dateFacture}
              onChange={(e) => setDateFacture(e.target.value)}
              helperText="L'échéance de chaque facture = date de facture + délai de paiement du client."
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField select label="Client" value={clientId} onChange={(e) => setClientId(e.target.value)}>
              <MenuItem value="">Tous les clients ayant des bons</MenuItem>
              {(clients.data ?? []).map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.code} · {c.nom}
                </MenuItem>
              ))}
            </TextField>
            <Alert severity="info">
              Une facture par client, toutes stations. Les bons en attente de dérogation et ceux d'une journée encore en saisie
              attendent la facturation suivante.
            </Alert>
          </Stack>
        )}
        <AlerteErreur erreur={facturation.error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onFermer}>{resultat ? 'Fermer' : 'Annuler'}</Button>
        {!resultat && (
          <Button variant="contained" onClick={() => facturation.mutate()} disabled={facturation.isPending || !du || !au || !dateFacture}>
            Facturer
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}

export function DialogueFacture({ id, onFermer }: { id: string; onFermer: () => void }) {
  const requete = useFacture(id)
  const queryClient = useQueryClient()
  const rafraichir = useRafraichirCreances()
  const peutGerer = usePermission('CLIENT_GERER')
  const [motif, setMotif] = useState('')
  const [annulation, setAnnulation] = useState(false)
  const d = requete.data
  const annuler = useMutation({
    mutationFn: () => api.post<FactureDetail>(`/clients/factures/${id}/annulation`, { version: d!.facture.version, motif: motif.trim() }),
    onSuccess: (r) => {
      queryClient.setQueryData(cles.facture(id), r)
      rafraichir()
      notifier('Facture annulée : ses bons sont de nouveau à facturer.')
      setAnnulation(false)
    },
  })
  const f = d?.facture
  return (
    <Dialog open onClose={onFermer} maxWidth="md" fullWidth>
      <DialogTitle>{f ? `Facture ${f.numero}` : 'Facture'}</DialogTitle>
      <DialogContent>
        {!d || !f ? (
          requete.isLoading ? <Chargement /> : <AlerteErreur erreur={requete.error} />
        ) : (
          <>
            <Typography variant="body2" sx={{ mb: 1 }}>
              {f.clientNom} · du {formaterDate(f.periodeDebut)} au {formaterDate(f.periodeFin)} · facture du {formaterDate(f.dateFacture)} ·
              échéance {formaterDate(f.dateEcheance)}
            </Typography>
            {f.statut === 'ANNULEE' ? (
              <Alert severity="warning" sx={{ mb: 1 }}>
                Facture annulée : {f.motifAnnulation}
              </Alert>
            ) : (
              <Typography variant="body2" sx={{ mb: 1 }}>
                Montant <strong>{formaterPrix(f.montant)}</strong> · reste dû <strong>{formaterPrix(f.reste)}</strong>
              </Typography>
            )}
            {d.lignes.length > 0 && (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="Bons de la facture">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Station</TableCell>
                      <TableCell>Bon</TableCell>
                      <TableCell align="right">Litres</TableCell>
                      <TableCell align="right">Prix net</TableCell>
                      <TableCell align="right">Montant</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {d.lignes.map((l) => (
                      <TableRow key={`${l.stationCode}-${l.numero}`}>
                        <TableCell>{formaterDate(l.date)}</TableCell>
                        <TableCell>{l.stationCode}</TableCell>
                        <TableCell>
                          {l.numero}
                          {l.immatriculation && (
                            <Typography variant="caption" color="text.secondary" component="div">
                              {l.immatriculation}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={nombres}>
                          {formaterNombre(l.litres)} {l.produitCode}
                        </TableCell>
                        <TableCell align="right" sx={nombres}>
                          {formaterPrix(l.prixUnitaire)}
                          {l.remise > 0 && (
                            <Typography variant="caption" color="text.secondary" component="div">
                              remise {formaterPrix(l.remise)}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={nombres}>
                          {formaterPrix(l.montant)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
            {annulation && (
              <TextField
                label="Motif de l'annulation"
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                fullWidth
                required
                sx={{ mt: 2 }}
                slotProps={{ htmlInput: { maxLength: 500 } }}
              />
            )}
            <AlerteErreur erreur={annuler.error} />
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ flexWrap: 'wrap', gap: 1 }}>
        {f && (
          <Button startIcon={<PictureAsPdfIcon />} onClick={() => void telecharger(`/clients/factures/${f.id}/pdf`, `facture-${f.numero}.pdf`)}>
            PDF
          </Button>
        )}
        {f && peutGerer && f.statut === 'EMISE' && (
          annulation ? (
            <Button color="error" variant="contained" onClick={() => annuler.mutate()} disabled={annuler.isPending || !motif.trim()}>
              Confirmer l'annulation
            </Button>
          ) : (
            <Button color="error" onClick={() => setAnnulation(true)}>
              Annuler la facture
            </Button>
          )
        )}
        <Button onClick={onFermer}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}

/** Tableau de factures (toutes, ou celles d'un client). */
export function TableFactures({ factures, onChoisir, avecClient = true }: { factures: Facture[]; onChoisir: (id: string) => void; avecClient?: boolean }) {
  const jour = aujourdhui()
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table size="small" aria-label="Factures">
        <TableHead>
          <TableRow>
            <TableCell>Facture</TableCell>
            {avecClient && <TableCell>Client</TableCell>}
            <TableCell>Période</TableCell>
            <TableCell>Échéance</TableCell>
            <TableCell align="right">Montant</TableCell>
            <TableCell align="right">Reste dû</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {factures.map((f) => {
            const echue = f.statut === 'EMISE' && f.reste > 0 && f.dateEcheance < jour
            return (
              <TableRow key={f.id} hover onClick={() => onChoisir(f.id)} sx={{ cursor: 'pointer', opacity: f.statut === 'ANNULEE' ? 0.6 : 1 }}>
                <TableCell>
                  {f.numero}
                  <Typography variant="caption" color="text.secondary" component="div">
                    {formaterDate(f.dateFacture)} · {f.bons} bon(s)
                  </Typography>
                </TableCell>
                {avecClient && <TableCell>{f.clientNom}</TableCell>}
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {formaterDate(f.periodeDebut)} – {formaterDate(f.periodeFin)}
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {formaterDate(f.dateEcheance)}
                  {echue && <Chip size="small" color="error" label="Échue" sx={{ ml: 1 }} />}
                  {f.statut === 'ANNULEE' && <Chip size="small" label="Annulée" sx={{ ml: 1 }} />}
                </TableCell>
                <TableCell align="right" sx={nombres}>
                  {formaterPrix(f.montant)}
                </TableCell>
                <TableCell align="right" sx={nombres}>
                  {f.statut === 'EMISE' ? formaterPrix(f.reste) : '—'}
                </TableCell>
              </TableRow>
            )
          })}
          {factures.length === 0 && (
            <TableRow>
              <TableCell colSpan={avecClient ? 6 : 5}>
                <Typography variant="body2" color="text.secondary">
                  Aucune facture.
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </Box>
  )
}

function OngletFactures() {
  const factures = useFactures()
  const peutGerer = usePermission('CLIENT_GERER')
  const [facturation, setFacturation] = useState(false)
  const [choisie, setChoisie] = useState<string | null>(null)
  return (
    <>
      {peutGerer && (
        <Button variant="contained" startIcon={<ReceiptLongIcon />} onClick={() => setFacturation(true)} sx={{ mb: 2 }}>
          Facturer une période
        </Button>
      )}
      <AlerteErreur erreur={factures.error} />
      {factures.isLoading && <Chargement />}
      <Paper variant="outlined">
        <TableFactures factures={factures.data ?? []} onChoisir={setChoisie} />
      </Paper>
      {facturation && <DialogueFacturation onFermer={() => setFacturation(false)} />}
      {choisie && <DialogueFacture id={choisie} onFermer={() => setChoisie(null)} />}
    </>
  )
}

// ---------------------------------------------------------------- Bons en attente de dérogation

function OngletAttente() {
  const bons = useBonsEnAttente()
  const peutDeroger = usePermission('CREDIT_DEROGER')
  const [choisi, setChoisi] = useState<BonEnAttente | null>(null)
  return (
    <>
      <Alert severity="info" sx={{ mb: 2 }}>
        Bons enregistrés hors règle (plafond, échéance, engin non autorisé, contrat). La journée de la station ne peut pas être
        clôturée tant que la Direction n'a pas accordé la dérogation ou refusé le bon.
      </Alert>
      <AlerteErreur erreur={bons.error} />
      {bons.isLoading && <Chargement />}
      <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label="Bons en attente">
          <TableHead>
            <TableRow>
              <TableCell>Journée</TableCell>
              <TableCell>Bon</TableCell>
              <TableCell>Client</TableCell>
              <TableCell align="right">Montant</TableCell>
              <TableCell>Motif</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {(bons.data ?? []).map((b) => (
              <TableRow key={b.id} data-testid={`attente-${b.numero}`}>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  <RouterLink to={`/journees/${b.journeeId}`}>
                    {b.stationCode} · {formaterDate(b.date)}
                  </RouterLink>
                </TableCell>
                <TableCell>
                  {b.numero}
                  <Typography variant="caption" color="text.secondary" component="div">
                    {formaterNombre(b.litres)} L {b.produitCode}
                    {b.immatriculation ? ` · ${b.immatriculation}` : ''}
                  </Typography>
                </TableCell>
                <TableCell>{b.clientNom}</TableCell>
                <TableCell align="right" sx={nombres}>
                  {formaterPrix(b.montant)}
                </TableCell>
                <TableCell sx={{ maxWidth: 360 }}>
                  <Typography variant="body2">{b.motifBlocage}</Typography>
                </TableCell>
                <TableCell align="right">
                  {peutDeroger && (
                    <IconButton aria-label={`Décider du bon ${b.numero}`} onClick={() => setChoisi(b)}>
                      <GavelIcon fontSize="small" />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {bons.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>Aucun bon en attente.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
      {choisi && <DialogueDecisionBon bon={choisi} journeeId={choisi.journeeId} onFermer={() => setChoisi(null)} />}
    </>
  )
}

export function PageCreances() {
  const [params, setParams] = useSearchParams()
  const attente = useBonsEnAttente()
  const onglet = params.get('onglet') ?? 'balance'
  const nbAttente = attente.data?.length ?? 0
  return (
    <>
      <EnTetePage titre="Créances et factures" sousTitre="Balance âgée, relances, facturation périodique et dérogations" />
      <Tabs
        value={onglet}
        onChange={(_e, v: string) => setParams({ onglet: v })}
        variant="scrollable"
        allowScrollButtonsMobile
        sx={{ mb: 2 }}
      >
        <Tab value="balance" label="Balance âgée" />
        <Tab value="factures" label="Factures" />
        <Tab value="attente" label={nbAttente > 0 ? `Bons en attente (${nbAttente})` : 'Bons en attente'} />
      </Tabs>
      {onglet === 'balance' && <OngletBalance />}
      {onglet === 'factures' && <OngletFactures />}
      {onglet === 'attente' && <OngletAttente />}
    </>
  )
}
