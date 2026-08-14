// Renders a set of static, spec-inferred mockup HTML screens to PNGs, then
// composes them into a single serpentine (wrapping) storyboard image. No
// live app, server, or backend required — every screen is a static file
// built to look like a real, populated screen; its content is inferred
// directly from the spec, not produced by running code.
//
// Usage: node render.mjs <group-dir>   (e.g. docs/frontend/brand)
// <group-dir>/manifest.json shape: [{ "file": "step-1.html", "caption": "..." }, ...]
//
// Connector arrows point from the real element that was clicked to the real
// element it produced, not a generic centered glyph: mark the clicked
// element in step N with `data-trigger`, and (optionally) the thing it
// produced in step N+1 with `data-target` — both are invisible metadata,
// they never change what gets screenshotted. Unmarked transitions fall back
// to a sensible edge-to-edge default (still a straight line, so it's never
// forced to be purely horizontal — a wrapped row's connector is vertical).
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'

const groupDir = process.argv[2]
if (!groupDir) {
  console.error('Usage: node render.mjs <group-dir>')
  process.exit(1)
}

const manifestPath = path.join(groupDir, 'manifest.json')
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'))

const viewport = { width: 1440, height: 900 }
// 2x device pixel ratio — plain 1x screenshots look soft/blurry once embedded
// and viewed at any zoom; this doubles the linear resolution of every capture
// (step PNGs and the composed board) at no layout cost.
const deviceScaleFactor = 2
const cols = 3
const browser = await chromium.launch()

for (const step of manifest) {
  const page = await browser.newPage({ viewport, deviceScaleFactor })
  await page.goto(`file://${path.resolve(groupDir, step.file)}`)
  const pngName = step.file.replace(/\.html$/, '.png')
  await page.screenshot({ path: path.join(groupDir, pngName) })
  step.png = pngName
  step.anchors = await page.evaluate(() => {
    const rectOf = (el) => {
      if (!el) return null
      const r = el.getBoundingClientRect()
      return { left: r.left, top: r.top, width: r.width, height: r.height }
    }
    return {
      trigger: rectOf(document.querySelector('[data-trigger]')),
      target: rectOf(document.querySelector('[data-target]')),
    }
  })
  await page.close()
}

// Serpentine (boustrophedon) layout: chunk the sequence into rows of `cols`
// frames instead of one ever-widening horizontal strip. Odd rows render
// right-to-left (still the same left→right *reading* order once you follow
// the arrows) so the row-to-row turn is a short drop at whichever edge the
// previous row ended on, not a long diagonal jump across the page.
const rowOf = (i) => Math.floor(i / cols)
const rowReversed = (i) => rowOf(i) % 2 === 1
const rows = []
for (let i = 0; i < manifest.length; i += cols) rows.push(manifest.slice(i, i + cols))

const figureHtml = (m, i) => `
  <figure id="frame-${i}">
    <img src="${m.png}" />
    <figcaption>${m.caption}</figcaption>
  </figure>`

const buildRowsHtml = () =>
  rows
    .map((row, r) => {
      const startIdx = r * cols
      const indices = row.map((_, j) => startIdx + j)
      const ordered = r % 2 === 1 ? [...indices].reverse() : indices
      return `<div class="row">${ordered.map((i) => figureHtml(manifest[i], i)).join('')}</div>`
    })
    .join('')

const buildHtml = (overlaySvg = '') => `<!doctype html>
<html><head><meta charset="utf-8"><style>
  body { margin: 0; padding: 32px; background: #f4f5f7; font-family: -apple-system, "PingFang TC", "Noto Sans TC", sans-serif; }
  .board { position: relative; display: flex; flex-direction: column; gap: 56px; width: max-content; }
  .row { display: flex; align-items: flex-start; gap: 40px; }
  figure { margin: 0; background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 8px; }
  figure img { display: block; max-height: 480px; border-radius: 4px; }
  figcaption { margin-top: 8px; max-width: 360px; font-size: 14px; color: #333; text-align: center; }
  .arrows { position: absolute; top: 0; left: 0; pointer-events: none; overflow: visible; }
</style></head>
<body><div class="board">${buildRowsHtml()}${overlaySvg}</div></body></html>`

const htmlPath = path.join(groupDir, 'storyboard.html')
fs.writeFileSync(htmlPath, buildHtml())

// A fixed viewport is wrong in both directions: too narrow and the flex row
// shrinks/clips frames (flex-shrink kicks in), too narrow for the *element
// screenshot* specifically and Chromium can't capture past the viewport edge
// either. So: load at a viewport wide enough for any realistic step count to
// render unshrunk, measure the board's true natural size, then resize the
// viewport to exactly fit that before cropping — works for 3 steps or 12.
const page2 = await browser.newPage({ viewport: { width: 20000, height: 3000 }, deviceScaleFactor })
await page2.goto(`file://${path.resolve(htmlPath)}`)
let box = await page2.evaluate(() => {
  const el = document.querySelector('.board')
  return { width: Math.ceil(el.scrollWidth), height: Math.ceil(el.scrollHeight) }
})
await page2.setViewportSize({ width: box.width + 80, height: box.height + 80 })

// Now that layout is final, measure exactly where each frame landed on the
// board so arrows can be drawn between real coordinates, not guessed ones.
const frameRects = await page2.evaluate((n) => {
  const rects = []
  for (let i = 0; i < n; i++) {
    const img = document.querySelector(`#frame-${i} img`)
    const r = img.getBoundingClientRect()
    rects.push({ left: r.left, top: r.top, width: r.width, height: r.height })
  }
  return rects
}, manifest.length)

// Convert an element's rect (measured in step-local 1440x900 design space)
// into board coordinates, using the frame it belongs to as the anchor.
const toBoardRect = (elRect, frameRect) => {
  const scale = frameRect.height / 900
  return {
    left: frameRect.left + elRect.left * scale,
    top: frameRect.top + elRect.top * scale,
    width: elRect.width * scale,
    height: elRect.height * scale,
  }
}

// The point on a rect's own boundary for a given side — 'left'/'right' land
// mid-height on that side, 'top'/'bottom' land mid-width on that side.
const sideOf = (rect, side) => {
  const midX = rect.left + rect.width / 2
  const midY = rect.top + rect.height / 2
  if (side === 'left') return { x: rect.left, y: midY }
  if (side === 'right') return { x: rect.left + rect.width, y: midY }
  if (side === 'top') return { x: midX, y: rect.top }
  return { x: midX, y: rect.top + rect.height } // 'bottom'
}

const arrows = []
for (let i = 0; i < manifest.length - 1; i++) {
  const sameRow = rowOf(i) === rowOf(i + 1)

  // The arrow always clips to the boundary of whatever box is actually
  // relevant — the clicked element's own box on exit, the produced
  // element's own box on entry (a modal, a toast, a row) — never the
  // outer screenshot card. Falls back to the frame itself when a step
  // has no data-trigger/data-target, using the same directional rule:
  // same-row → the near left/right side; a row wrap → top/bottom.
  const triggerRect = manifest[i].anchors?.trigger
  const startBox = triggerRect ? toBoardRect(triggerRect, frameRects[i]) : frameRects[i]
  const startSide = sameRow ? (rowReversed(i) ? 'left' : 'right') : 'bottom'
  const start = sideOf(startBox, startSide)

  const targetRect = manifest[i + 1].anchors?.target
  const endBox = targetRect ? toBoardRect(targetRect, frameRects[i + 1]) : frameRects[i + 1]
  const endSide = sameRow ? (rowReversed(i + 1) ? 'right' : 'left') : 'top'
  const end = sideOf(endBox, endSide)

  arrows.push({ start, end })
}

const overlaySvg = `
  <svg class="arrows" width="${box.width}" height="${box.height}">
    <defs>
      <marker id="arrowhead" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M0,0 L10,5 L0,10 z" fill="#888" />
      </marker>
    </defs>
    ${arrows
      .map(
        (a) =>
          `<line x1="${a.start.x}" y1="${a.start.y}" x2="${a.end.x}" y2="${a.end.y}" stroke="#888" stroke-width="3" marker-end="url(#arrowhead)" />`,
      )
      .join('')}
  </svg>`

fs.writeFileSync(htmlPath, buildHtml(overlaySvg))
await page2.goto(`file://${path.resolve(htmlPath)}`)
box = await page2.evaluate(() => {
  const el = document.querySelector('.board')
  return { width: Math.ceil(el.scrollWidth), height: Math.ceil(el.scrollHeight) }
})
await page2.setViewportSize({ width: box.width + 80, height: box.height + 80 })
await page2.locator('.board').screenshot({ path: path.join(groupDir, 'storyboard.png') })

// storyboard.html/.pdf are the actually-comfortable ways to browse a wide
// board: open in a real browser (native pan/zoom) or a PDF viewer (text
// captions stay vector-crisp at any zoom, unlike the flat PNG above).
await page2.pdf({
  path: path.join(groupDir, 'storyboard.pdf'),
  width: `${box.width}px`,
  height: `${box.height}px`,
  printBackground: true,
  margin: { top: '0', bottom: '0', left: '0', right: '0' },
})
await browser.close()

console.log(`Storyboard written to ${groupDir}/storyboard.png (+ .html, .pdf)`)
