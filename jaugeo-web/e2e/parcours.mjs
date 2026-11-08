// Parcours de bout en bout dans Edge (headless) contre l'application en cours d'execution.
// Prerequis : API sur :8080, front sur :5173. Cree un utilisateur de test a chaque execution.
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const BASE = process.env.JAUGEO_WEB_URL ?? 'http://localhost:5173'
const ADMIN_MDP = process.env.JAUGEO_ADMIN_MDP ?? process.argv[2]
if (!ADMIN_MDP) {
  console.error('Indiquez le mot de passe admin : JAUGEO_ADMIN_MDP=... npm run e2e')
  process.exit(2)
}
const SORTIE = fileURLToPath(new URL('./captures/', import.meta.url))
mkdirSync(SORTIE, { recursive: true })
let ok = 0
let ko = 0
const verifier = (nom, cond) => {
  if (cond) ok++
  else ko++
  console.log(`${cond ? '[OK]' : '[KO]'} ${nom}`)
}

const navigateur = await chromium.launch({ channel: 'msedge', headless: true })
const erreursConsole = []
async function nouveauContexte() {
  const ctx = await navigateur.newContext({ viewport: { width: 1366, height: 860 }, locale: 'fr-FR' })
  const page = await ctx.newPage()
  page.on('console', (m) => m.type() === 'error' && erreursConsole.push(m.text()))
  page.on('pageerror', (e) => erreursConsole.push(String(e)))
  return { ctx, page }
}
const capture = (page, nom) => page.screenshot({ path: `${SORTIE}${nom}.png`, fullPage: true })

try {
  const { ctx, page } = await nouveauContexte()

  // 1. Connexion
  await page.goto(`${BASE}/stations`)
  await page.getByRole('button', { name: 'Se connecter' }).waitFor()
  verifier('Sans session : redirection vers /connexion', page.url().endsWith('/connexion'))
  await page.getByLabel('Identifiant').fill('admin')
  await page.getByLabel('Mot de passe', { exact: true }).fill('mauvais-mdp')
  await page.getByRole('button', { name: 'Se connecter' }).click()
  await page.getByText('Identifiant ou mot de passe incorrect').waitFor()
  verifier('Mauvais mot de passe : message affiché', true)
  await capture(page, '01-connexion')
  await page.getByLabel('Mot de passe', { exact: true }).fill(ADMIN_MDP)
  await page.getByRole('button', { name: 'Se connecter' }).click()

  // 2. Stations (retour à la page demandée)
  await page.getByRole('heading', { name: 'Stations' }).waitFor()
  verifier('Après connexion : retour sur /stations', page.url().endsWith('/stations'))
  await page.getByRole('gridcell', { name: 'Niandane' }).first().waitFor()
  const lignes = await page.locator('.MuiDataGrid-row').count()
  verifier(`Grille des stations remplie (${lignes} lignes)`, lignes >= 6)
  await capture(page, '02-stations')

  // 3. Fiche station NIA
  await page.getByRole('gridcell', { name: 'Niandane' }).first().click()
  await page.getByRole('heading', { name: 'Cuves' }).waitFor()
  const texteFiche = await page.locator('main').innerText()
  verifier('Fiche Niandane : pistolet G1 à 88 916', /G1[\s\S]*88\s?916/.test(texteFiche))
  verifier('Fiche Niandane : 2 cuves (Gasoil, Super)', /C1[\s\S]*Gasoil/.test(texteFiche) && /C2[\s\S]*Super/.test(texteFiche))
  await capture(page, '03-fiche-station')

  // 4. Rechargement : la session est restaurée par le cookie
  await page.reload()
  await page.getByRole('heading', { name: 'Cuves' }).waitFor()
  verifier('Rechargement de la page : session conservée', true)

  // 5. Modification d'une station (verrou optimiste) puis retour
  await page.getByRole('button', { name: 'Modifier la station' }).click()
  const localite = page.getByLabel('Localité')
  const ancienneLocalite = await localite.inputValue()
  await localite.fill('Niandane E2E')
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await page.getByText('Station Niandane : modifications enregistrées.').waitFor()
  verifier('Modification de station enregistrée', (await page.locator('main').innerText()).includes('Niandane E2E'))
  await page.getByRole('button', { name: 'Modifier la station' }).click()
  await page.getByLabel('Localité').fill(ancienneLocalite)
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await page.getByText('Station Niandane : modifications enregistrées.').first().waitFor()

  // 5b. Sprint 1 : barème collé depuis Excel (contrôlé puis abandonné : la base de dev garde ses vraies valeurs)
  await page.getByRole('button', { name: 'Barème de la cuve C1' }).click()
  const dlgBareme = page.getByRole('dialog')
  await dlgBareme.getByText(/Aucun barème : les jaugeages/).waitFor()
  await dlgBareme.getByLabel('Tableau hauteur → volume (L)').fill('Hauteur (cm)\tVolume (L)\n0\t0\n10\t500\n200\t20 000')
  await dlgBareme.getByRole('button', { name: 'cm' }).click()
  await dlgBareme.getByText('Barème cohérent.').waitFor()
  verifier('Barème : 3 points lus, en-tête ignoré', (await page.getByTestId('resume-bareme').innerText()).includes('3 point(s)'))
  await dlgBareme.getByLabel('Hauteur jaugée').fill('5')
  verifier('Barème : 5 cm → 250 L', /250\s?L/.test(await page.getByTestId('volume-calcule').innerText()))
  await dlgBareme.getByLabel('Tableau hauteur → volume (L)').fill('0\t0\n10\t500\n20\t400')
  await dlgBareme.getByText(/Le volume doit croître/).waitFor()
  verifier('Barème : volumes décroissants signalés, enregistrement bloqué', await dlgBareme.getByRole('button', { name: 'Enregistrer le barème' }).isDisabled())
  await capture(page, '03b-bareme')
  await dlgBareme.getByRole('button', { name: 'Annuler' }).click()

  // 5c. Événement d'équipement : index incohérent refusé avant envoi
  await page.getByRole('button', { name: 'Événement sur le pistolet G1' }).click()
  const dlgEv = page.getByRole('dialog')
  await dlgEv.getByLabel('Index avant').fill('1000')
  await dlgEv.getByRole('button', { name: 'Enregistrer' }).click()
  await dlgEv.getByText(/Inférieur au dernier index connu \(88\s?916\)/).waitFor()
  verifier('Événement : index avant inférieur au compteur refusé', true)
  await dlgEv.getByText('Motif obligatoire pour un changement de compteur').waitFor()
  await capture(page, '03c-evenement')
  await dlgEv.getByRole('button', { name: 'Annuler' }).click()
  const fichePrix = await page.locator('main').innerText()
  verifier('Fiche Niandane : historique des prix (680 F puis 755 F)', fichePrix.includes('680 F') && fichePrix.includes('755 F'))

  // 6. Produits
  await page.getByRole('link', { name: 'Produits' }).click()
  await page.getByRole('gridcell', { name: 'Gasoil' }).first().waitFor()
  verifier('Liste des produits affichée', true)
  await capture(page, '04-produits')

  // 7. Création d'un utilisateur limité à Niandane
  const ident = `e2e.${Date.now().toString(36)}`
  await page.getByRole('link', { name: 'Utilisateurs' }).click()
  await page.getByRole('button', { name: 'Nouvel utilisateur' }).click()
  const dlg = page.getByRole('dialog')
  await dlg.getByLabel('Identifiant de connexion').fill(ident)
  await dlg.getByLabel('Nom complet').fill('Fatou Sarr')
  await dlg.getByLabel('Rôle', { exact: true }).click()
  await page.getByRole('option', { name: 'Gérant de station' }).click()
  await page.getByRole('button', { name: 'Créer le compte' }).click()
  await page.getByText('Désignez au moins une station.').waitFor()
  verifier('Périmètre « stations » sans station : refusé', true)
  await dlg.getByLabel('Stations accessibles').click()
  await page.getByRole('option', { name: /NIA/ }).click()
  await page.getByRole('button', { name: 'Créer le compte' }).click()
  const mdp = (await page.getByTestId('mdp-provisoire').innerText()).trim()
  verifier(`Mot de passe provisoire affiché (${mdp.length} caractères)`, mdp.length === 12)
  await capture(page, '05-mot-de-passe-provisoire')
  await page.getByRole('button', { name: "J'ai transmis le mot de passe" }).click()
  await page.getByRole('gridcell', { name: ident }).waitFor()
  verifier('Nouvel utilisateur visible dans la liste', true)
  await capture(page, '06-utilisateurs')

  // 7b. Un membre de la Direction (seul rôle habilité à changer les prix)
  const identDg = `e2e.dg.${Date.now().toString(36)}`
  await page.getByRole('button', { name: 'Nouvel utilisateur' }).click()
  await dlg.getByLabel('Identifiant de connexion').fill(identDg)
  await dlg.getByLabel('Nom complet').fill('Moussa Diop')
  await dlg.getByLabel('Rôle', { exact: true }).click()
  await page.getByRole('option', { name: 'Direction générale' }).click()
  await page.getByRole('button', { name: 'Créer le compte' }).click()
  const mdpDg = (await page.getByTestId('mdp-provisoire').innerText()).trim()
  await page.getByRole('button', { name: "J'ai transmis le mot de passe" }).click()
  await page.getByRole('gridcell', { name: identDg }).waitFor()

  // 8. Journal d'audit
  await page.getByRole('link', { name: "Journal d'audit" }).click()
  await page.locator('.MuiDataGrid-row').first().waitFor()
  await page.waitForLoadState('networkidle')
  await page.locator('.MuiDataGrid-overlay').waitFor({ state: 'detached' }).catch(() => undefined)
  await page.getByRole('gridcell', { name: 'referentiel.station' }).first().click()
  await page.getByText(/Par .* le /).waitFor()
  verifier("Journal d'audit : détail d'une entrée", true)
  await capture(page, '07-audit')
  await page.getByRole('button', { name: 'Fermer' }).click()

  // 9. Déconnexion
  await page.getByRole('button', { name: 'Mon compte' }).click()
  await page.getByRole('menuitem', { name: 'Se déconnecter' }).click()
  await page.getByRole('button', { name: 'Se connecter' }).waitFor()
  await page.goto(`${BASE}/`)
  await page.getByRole('button', { name: 'Se connecter' }).waitFor()
  verifier('Après déconnexion : la session ne revient pas au rechargement', page.url().endsWith('/connexion'))
  await ctx.close()

  // 10. Première connexion du gérant : changement de mot de passe imposé, périmètre restreint
  const g = await nouveauContexte()
  await g.page.goto(`${BASE}/utilisateurs`)
  await g.page.getByLabel('Identifiant').fill(ident)
  await g.page.getByLabel('Mot de passe', { exact: true }).fill(mdp)
  await g.page.getByRole('button', { name: 'Se connecter' }).click()
  await g.page.getByRole('heading', { name: 'Choisissez votre mot de passe' }).waitFor()
  verifier('Gérant : changement de mot de passe imposé', g.page.url().endsWith('/changer-mot-de-passe'))
  await g.page.getByLabel('Mot de passe actuel').fill(mdp)
  await g.page.getByLabel('Nouveau mot de passe', { exact: true }).fill('Gerant-Nia-2026')
  await g.page.getByLabel('Confirmer le nouveau mot de passe').fill('Gerant-Nia-2026')
  await g.page.getByRole('button', { name: 'Enregistrer le nouveau mot de passe' }).click()
  await g.page.getByRole('heading', { name: 'Bonjour Fatou' }).waitFor()
  verifier('Gérant : arrivée sur le tableau de bord', true)
  const menu = await g.page.getByRole('navigation').innerText()
  verifier('Gérant : pas de rubrique Utilisateurs ni Audit', !menu.includes('Utilisateurs') && !menu.includes("Journal d'audit"))
  const cartes = await g.page.locator('main').innerText()
  verifier('Gérant : ne voit que Niandane', cartes.includes('Niandane') && !cartes.includes('Hydrobase') && !cartes.includes('Kayar'))
  await capture(g.page, '08-tableau-de-bord-gerant')
  await g.page.goto(`${BASE}/utilisateurs`)
  await g.page.getByText("Vous n'avez pas accès à cette page").waitFor()
  verifier('Gérant : accès direct à /utilisateurs bloqué', true)
  await g.page.goto(`${BASE}/stations`)
  await g.page.getByRole('gridcell', { name: 'Niandane' }).first().waitFor()
  verifier('Gérant : pas de bouton « Nouvelle station »', (await g.page.getByRole('button', { name: 'Nouvelle station' }).count()) === 0)
  await g.page.getByRole('link', { name: 'Prix' }).click()
  await g.page.getByRole('table', { name: 'Prix en vigueur' }).waitFor()
  const grilleGerant = await g.page.getByRole('table', { name: 'Prix en vigueur' }).innerText()
  verifier('Gérant : prix de Niandane seulement, sans bouton de changement', grilleGerant.includes('Niandane') && !grilleGerant.includes('Kayar')
    && (await g.page.getByRole('button', { name: 'Changement de prix' }).count()) === 0)

  // 11. Mobile
  await g.page.setViewportSize({ width: 390, height: 800 })
  await g.page.goto(`${BASE}/`)
  await g.page.getByRole('button', { name: 'Ouvrir le menu' }).click()
  await g.page.getByRole('link', { name: 'Stations' }).waitFor()
  verifier('Mobile : menu repliable', true)
  await capture(g.page, '09-mobile')
  await g.ctx.close()

  // 12. Direction : changement de prix programmé, variation à confirmer, puis annulation (base laissée intacte)
  const d = await nouveauContexte()
  const motifE2e = `Parcours e2e ${identDg}`
  await d.page.goto(`${BASE}/prix`)
  await d.page.getByLabel('Identifiant').fill(identDg)
  await d.page.getByLabel('Mot de passe', { exact: true }).fill(mdpDg)
  await d.page.getByRole('button', { name: 'Se connecter' }).click()
  await d.page.getByLabel('Mot de passe actuel').fill(mdpDg)
  await d.page.getByLabel('Nouveau mot de passe', { exact: true }).fill('Direction-Dg-2026')
  await d.page.getByLabel('Confirmer le nouveau mot de passe').fill('Direction-Dg-2026')
  await d.page.getByRole('button', { name: 'Enregistrer le nouveau mot de passe' }).click()
  await d.page.getByRole('link', { name: 'Prix' }).click()
  await d.page.getByRole('table', { name: 'Prix en vigueur' }).waitFor()
  const grille = await d.page.getByRole('table', { name: 'Prix en vigueur' }).innerText()
  verifier('Direction : grille réseau (Niandane 755 F, Kayar 497 F)', grille.includes('755 F') && grille.includes('497 F') && grille.includes('Kayar'))
  await capture(d.page, '10-prix')
  await d.page.getByRole('button', { name: 'Changement de prix' }).click()
  const dlgPrix = d.page.getByRole('dialog')
  await dlgPrix.getByLabel("Date et heure d'effet (Dakar)").fill('2030-01-01T00:00')
  await dlgPrix.getByLabel(/Motif/).fill(motifE2e)
  await dlgPrix.getByLabel('Gasoil').fill('7600')
  await dlgPrix.getByRole('button', { name: 'Enregistrer' }).click()
  await d.page.getByTestId('variation-a-confirmer').waitFor()
  verifier('Direction : variation de +900 % à confirmer', true)
  await dlgPrix.getByLabel('Gasoil').fill('760')
  await dlgPrix.getByRole('button', { name: 'Enregistrer' }).click()
  await d.page.getByText(/Changement enregistré pour 1 station/).waitFor()
  await d.page.getByText('→ 760 F le 01/01/2030 00:00').waitFor()
  verifier('Direction : prix programmé visible dans la grille', true)
  const ligneE2e = d.page.getByRole('row', { name: motifE2e })
  verifier('Historique : changement « Programmé »', (await ligneE2e.innerText()).includes('Programmé'))
  await capture(d.page, '11-prix-programme')
  await ligneE2e.getByRole('button', { name: 'Annuler' }).click()
  await d.page.getByRole('button', { name: 'Annuler le changement' }).click()
  await d.page.getByText(/Changement de prix annulé/).waitFor()
  await d.page.getByRole('row', { name: motifE2e }).getByText('Annulé', { exact: true }).waitFor()
  verifier('Direction : annulation, le prix programmé disparaît', (await d.page.getByText('→ 760 F').count()) === 0)
  await d.ctx.close()
} catch (e) {
  ko++
  console.log(`[KO] Exception : ${e.message.split('\n')[0]}`)
  console.log(e.stack.split('\n').find((l) => l.includes('parcours.mjs')))
  for (const c of navigateur.contexts()) for (const p of c.pages()) await p.screenshot({ path: `${SORTIE}echec.png` })
} finally {
  await navigateur.close()
}

const pertinentes = erreursConsole.filter((t) => !/status of 4\d\d/.test(t))
verifier(`Aucune erreur JavaScript dans la console (${pertinentes.length})`, pertinentes.length === 0)
pertinentes.slice(0, 10).forEach((t) => console.log('   console:', t.slice(0, 200)))
console.log(`\n${ok} OK, ${ko} KO`)
process.exit(ko ? 1 : 0)
