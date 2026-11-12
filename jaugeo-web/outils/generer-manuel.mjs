// Génère le PDF du manuel des fonctionnalités à partir de docs/manuel/manuel.html.
// Usage (depuis jaugeo-web) : npm run manuel
// Utilise Microsoft Edge déjà installé sur le poste, comme le parcours e2e : aucun téléchargement de navigateur.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright-core'

const dossier = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'manuel')
const source = path.join(dossier, 'manuel.html')
const sortie = path.join(dossier, 'Jaugeo — Manuel des fonctionnalités.pdf')

if (!existsSync(source)) {
  console.error(`Source introuvable : ${source}`)
  process.exit(1)
}

const navigateur = await chromium.launch({ channel: 'msedge', headless: true })
try {
  const page = await navigateur.newPage()
  await page.goto(pathToFileURL(source).href, { waitUntil: 'load' })
  const { version, date } = await page.evaluate(() => ({ ...document.body.dataset }))
  const pied = `<div style="width:100%;font-family:'Segoe UI',Arial,sans-serif;font-size:7.5pt;color:#55666d;padding:0 16mm;display:flex;justify-content:space-between;">
    <span>Jaugeo — Manuel des fonctionnalités · version ${version} · ${date}</span>
    <span>page <span class="pageNumber"></span> / <span class="totalPages"></span></span></div>`
  await page.pdf({
    path: sortie,
    format: 'A4',
    printBackground: true,
    preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: pied,
  })
  console.log(`Manuel généré (version ${version}, ${date}) : ${sortie}`)
} finally {
  await navigateur.close()
}
