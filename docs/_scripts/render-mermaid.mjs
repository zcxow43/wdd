// Renders every Mermaid diagram in a job file to PNG using ONE headless
// browser process.
//
// Why this exists: the doc commands used to shell out to
// `npx --yes -p @mermaid-js/mermaid-cli mmdc ...` once per diagram. On
// Windows every one of those spawns its own npm/cmd shell plus its own
// Chromium — a dozen diagrams meant a dozen console windows flashing over
// whatever the user was doing. This script resolves Mermaid locally (no npx
// package resolution), launches Chromium exactly once, and renders the whole
// batch in that single process.
//
// Usage: node docs/_scripts/render-mermaid.mjs <jobs.json>
// jobs.json shape:
//   [{ "src": "path/to/a.mmd", "out": "docs/db/er-model/0.png",
//      "background": "white", "width": 1600, "scale": 2 }, ...]
// `background` defaults to white, `width` to 1400, `scale` to 2.
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const mermaidJs = path.join(here, 'node_modules', 'mermaid', 'dist', 'mermaid.min.js')

const jobsPath = process.argv[2]
if (!jobsPath) {
  console.error('Usage: node render-mermaid.mjs <jobs.json>')
  process.exit(1)
}
if (!fs.existsSync(mermaidJs)) {
  console.error(`mermaid not installed at ${mermaidJs} — run: npm --prefix docs/_scripts install`)
  process.exit(1)
}

const jobs = JSON.parse(fs.readFileSync(jobsPath, 'utf-8'))
const browser = await chromium.launch()
let failed = 0

for (const job of jobs) {
  const background = job.background ?? 'white'
  const width = job.width ?? 1400
  const scale = job.scale ?? 2
  const source = fs.readFileSync(job.src, 'utf-8')

  const page = await browser.newPage({
    viewport: { width, height: 900 },
    deviceScaleFactor: scale,
  })
  // Mermaid measures text against the real document, so the diagram has to be
  // laid out in a page rather than rendered to a detached string.
  await page.setContent(
    `<!doctype html><html><head><meta charset="utf-8">
     <style>html,body{margin:0;padding:0;background:${background};}
     #d{display:inline-block;}</style></head>
     <body><div id="d"></div></body></html>`,
  )
  await page.addScriptTag({ path: mermaidJs })

  const result = await page.evaluate(async (src) => {
    // eslint-disable-next-line no-undef
    const m = window.mermaid
    m.initialize({ startOnLoad: false })
    try {
      const { svg } = await m.render('graph', src)
      document.getElementById('d').innerHTML = svg
      // Mermaid emits a max-width style that can shrink the SVG below its
      // natural size; strip it so the capture is the diagram's real extent.
      const el = document.querySelector('#d svg')
      el.style.maxWidth = 'none'
      el.removeAttribute('width')
      el.removeAttribute('height')
      const vb = el.getAttribute('viewBox').split(/[\s,]+/).map(Number)
      el.style.width = `${vb[2]}px`
      el.style.height = `${vb[3]}px`
      return { ok: true }
    } catch (err) {
      return { ok: false, error: String(err && err.message ? err.message : err) }
    }
  }, source)

  if (!result.ok) {
    console.error(`FAIL ${job.src}: ${result.error}`)
    failed += 1
    await page.close()
    continue
  }

  fs.mkdirSync(path.dirname(job.out), { recursive: true })
  const svg = await page.locator('#d svg').first()
  await svg.screenshot({ path: job.out, omitBackground: false })
  console.log(`ok   ${job.out}`)
  await page.close()
}

await browser.close()
if (failed > 0) {
  console.error(`${failed} diagram(s) failed to render`)
  process.exit(1)
}
console.log(`rendered ${jobs.length} diagram(s) in one browser process`)
