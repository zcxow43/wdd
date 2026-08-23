# Lessons

Patterns to avoid repeating. Append a new section after any user correction.

---

## 2026-08-23 — 渲染 PNG 時不要噴出一堆 cmd 視窗

**User correction:** 「造 png 的時候多了很多 cmd 談框, 我不要顯示他們」，接著「can you run spec without black box ?」

**What happened:** `/spec` 鏈進 `/doc-db` 與 `/doc-blue-print` 時，我對每一張圖各跑一次
`npx --yes -p @mermaid-js/mermaid-cli mmdc ...`（這次共 12 次）。在 Windows 上每一次 `npx`
都會另外開一個 npm/cmd shell、再各自啟一個 Chromium，結果使用者桌面上被灌了十幾個黑框。

**Why it matters:** 這不影響產出正確性，但會一直打斷使用者當下正在做的事——文件產生是背景工作，
不該霸佔前景。

**Fix that shipped (已實作，不是待辦):**
- 新增 `docs/_scripts/render-mermaid.mjs`：用本地 Playwright + 本地 mermaid，**一次**啟動
  Chromium 把整批圖渲染完，輸出與 `mmdc` 等價（同樣吃 `%%{init}%%`、背景色、`--scale 2`）。
- 用法：寫好全部 `.mmd` 後組一個 `jobs.json`（`[{src, out, background, width, scale}, ...]`），
  然後 **一次** 呼叫：
  ```bash
  node docs/_scripts/render-mermaid.mjs <scratch>/jobs.json
  ```
- `.claude/commands/doc-db.md` 與 `doc-blue-print.md` 的 Rendering 段落已改寫成這個流程，並明文
  禁止再用「一張圖一次 `npx mmdc`」。

**How to apply (下次照做):**
1. 先把所有圖的 `.mmd` 都寫完，再組 `jobs.json`，**一次**渲染完——不要邊渲染邊看邊補。
2. 渲染指令一律用 Bash tool 的 `run_in_background: true`，不要前景同步呼叫。
3. 全新 clone 第一次需要 `npm --prefix docs/_scripts install`（同樣背景執行）；
   `docs/_scripts/node_modules/mermaid` 已存在就跳過，不要每次重裝。
4. 同原則適用 `docs/frontend/_scripts/render.mjs`（Playwright storyboard）：一個 group 一次呼叫。

**Caveat（要誠實告知）:** 視窗數已從「每張圖一個」降到「整批一個」，但 Windows 上 Chromium
無頭行程仍可能短暫閃一次；無法保證絕對為零。

---

## 2026-08-23 — `erDiagram` 的 classDef 不要用白色文字

**What happened:** ER 明細圖第一次渲染出來，每隔一列的欄位文字整列消失。原因是 Mermaid 會把
attribute 列做斑馬條紋，偶數列維持白底，而 `classDef ... color:#ffffff` 讓所有文字變白 →
白底白字，等於看不見。跟 `specs/frontend/brand.md` 當初 dark-mode 白字白底是同一類錯誤。

**How to apply:** `erDiagram` 的 `classDef` 文字色用深色（例如 `color:#1f2430`），在有色列與
白色列上都讀得到。只有 `flowchart` 的實心節點可以放心用白字。此規則已寫進
`.claude/commands/doc-db.md` 的 Rendering 段。

**更通用的一條:** 圖渲染完一定要把 PNG 讀回來看過再引用——這個 bug 光看 Mermaid 原始碼是看不出來的。
