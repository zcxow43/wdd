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

---

## 2026-08-26 — `/spec` 的成本在「讀太多」與「無條件重畫」，不在模型選擇

**What happened:** 使用者看到 weekly 額度 51%，`/usage` 顯示 99% 用量來自 subagent-heavy
sessions、86% 跑在 150k context 以上，其中 `/spec` 37%、`/doc-fronend` 12%、
`/doc-blue-print` 11%、`/doc-backend` 4%。我第一時間建議「把 doc-* subagent 改成 sonnet」——
但實際去看 `.claude/agents/*.md` 才發現**所有 agent 早就是 `model: sonnet`**（`demo` 甚至是
haiku）。建議完全無效，因為根本沒有可降的東西。

**How to apply:**
1. **先讀檔再開藥方。** 談到專案設定的優化時，先去看 `.claude/agents/` / `.claude/commands/`
   的實際內容，不要憑對「一般專案長怎樣」的印象給建議。
2. 真正的成本結構是：主 session 自己的 context（`/spec` 步驟一原本寫「Read the codebase」，
   無界限）＋ 每次 `/spec` 都無條件把改到的 domain 的 doc 全部重畫一遍。
3. 修法（已寫進 `.claude/commands/spec.md`）：新增 `## Reading Budget` 限制讀取範圍
   （specs frontmatter 掃描、只讀真的要改的 spec、預設不讀 `develop/` 與 `docs/`）；
   `## Doc Generation` 改成 need-based gate——spec 被編輯只是**前提**不是**理由**，
   要看這次改動有沒有真的改變那份文件畫的東西。

**關鍵界線:** 省 token 只能靠「少跑、跑得準」，**不能靠降低畫圖品質**。gate 通過時，
doc 命令仍完全照它自己的檔案跑，rendering / diagram style / dispatch prompt 一個字都沒動。

---

## 2026-08-26 — 全景圖只畫關係，細節下放到各章

**What happened:** `docs/blueprint/backend/1.png` 被畫成完整 DFD——7 個 process、8 個 data store、
2 個角色、1 個外部 API、約 30 條有標籤的邊，線交纏成一團。使用者反映「blue-print 有些複雜了，
可能全景圖做一個最簡單的關係連接，然後拆開來解釋的時候再畫詳細些」。

**Root cause:** `.claude/commands/doc-blue-print.md` 的 agent prompt 裡有一句
「任何橫跨兩個以上 entity 的規則，都要用**這張圖上的標籤邊**表示出來」——等於強迫把整個系統
所有跨實體規則全部塞進 Diagram 1。圖只會愈長愈滿，不可能收斂。

**How to apply（已寫進 doc-blue-print.md）:**
- **Diagram 1 = 純關係圖**：節點只放 entity/data store，不放 process 方框、不放操作/審核角色、
  不放外部系統。硬性上限 節點 ≤10、邊 ≤12、有標籤的邊 ≤5——超過表示還沒抽象夠，不准放寬上限。
- **§3 各章的詳細圖才用正規 DFD**，把角色、process、審核路徑、外部呼叫補回來，範圍限定
  「自己 + 直接往來的鄰居」，不要每章重畫整個系統。
- 驗收問句：讀者掃一眼能不能講出「這系統由哪幾塊組成、誰接誰」？要研究才看得懂就是失敗。

**通則:** 一張圖同時要「一眼看懂」又要「完整無遺漏」是矛盾需求。解法是分層——上層給結構，
下層給細節——而不是在同一張圖上調參數。
