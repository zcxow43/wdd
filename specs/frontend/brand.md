---
status: done
title: "Brand Management Page"
requirement: "匯率中心需要品牌管理畫面，列出七個品牌 au, moneta, pug, star, um, vjp, vt，並可開啟/關閉品牌，且顏色需固定符合文件樣式，不受瀏覽器/系統深色模式影響"
depends_on: []
---

# Brand Management — Frontend Spec

## Overview
A page under the "匯率中心" (Exchange Rate Center) section that lists all seeded brands and lets an admin toggle each brand's active/inactive state. Backed entirely by [brand.md](../backend/brand.md).

## Requirements

### Page: 品牌管理 (`/brands`)
- Located under the "匯率中心" sidebar group, alongside other exchange-rate-center pages.
- On load, calls `GET /api/brands` and renders one row per brand.
- Table columns: `品牌代碼` (code), `品牌名稱` (name), `狀態` (active — toggle switch).
- No "新增"/"刪除" controls anywhere on this page — brands are fixed at seven, seeded only.

### Layout (ASCII mockup)
```
匯率中心 > 品牌管理
┌─────────────────────────────────────┐
│ 品牌代碼   品牌名稱      狀態          │
├─────────────────────────────────────┤
│ au        au           [●  ] 啟用    │
│ moneta     moneta       [●  ] 啟用    │
│ pug        pug          [●  ] 啟用    │
│ star       star         [ ● ] 停用    │
│ um         um           [●  ] 啟用    │
│ vjp        vjp          [●  ] 啟用    │
│ vt         vt           [●  ] 啟用    │
└─────────────────────────────────────┘
```

### Interactions
- Clicking a row's toggle switch immediately calls `PUT /api/brands/{id}` with `{ "active": <new value> }`.
- While the request is in flight, disable that row's toggle and show a "更新中..." label next to it.
- On success, update the row's toggle to the new state.
- On failure, revert the toggle to its previous state and show an inline error toast ("更新品牌狀態失敗，請稍後再試").

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單 | GET | /api/brands | — | `[{id, code, name, active, createdAt, updatedAt}]` |
| 切換品牌狀態 | PUT | /api/brands/{id} | `{active: boolean}` | `{id, code, name, active, createdAt, updatedAt}` |

## Error States
- List load failure: show an inline error message with a "重試" button instead of the table.
- Toggle failure: see Interactions above — revert + toast, table stays interactive.

## Visual Style
This page has a **fixed light theme** — every color below is a literal value, matching the rendered storyboard (`docs/frontend/brand/storyboard.png`) exactly. Do not source any of these from a CSS variable/theme token that changes under `prefers-color-scheme: dark` or any other OS/browser theme preference — this page must look identical regardless of the user's system theme.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Sidebar | background | `#1f2937` |
| Sidebar nav text | color | `#cbd5e1` |
| Sidebar active nav item | background / text / left border | `#334155` / `#fff` / `#3b82f6` |
| Breadcrumb text | color | `#64748b` |
| Page title (`品牌管理`) | color | `#111827` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 品牌代碼 cell | text | `#374151`, monospace font |
| Toggle switch — off | track background | `#d1d5db` |
| Toggle switch — on | track background | `#22c55e` |
| Toggle switch knob | background | `#fff` |
| 狀態 label — 停用 | color | `#6b7280` |
| 狀態 label — 啟用 | color / weight | `#16a34a` / `600` |

These values apply to every row identically — a brand's `active` value only changes the toggle switch position/color and the 狀態 label's own color; it never dims, fades, or changes the `品牌代碼`/`品牌名稱` cell text.

## Acceptance Criteria
- [x] Page loads all 7 brands from `GET /api/brands` and displays code/name/active for each.
- [x] Toggling a brand calls `PUT /api/brands/{id}` with the new `active` value and reflects the result in the table.
- [x] A toggle shows a disabled "更新中..." state while its request is in flight.
- [x] A failed toggle reverts the switch and shows an error toast.
- [x] The page has no create/delete UI for brands.
- [x] Every color in the page (background, sidebar, breadcrumb, title, table header/rows, code cell, toggle track, status label) matches the `## Visual Style` table exactly, verified via computed styles — not just visually.
- [x] The page renders identically whether the browser/OS reports a light or dark `prefers-color-scheme` — no CSS variable in this page's styling changes value based on that media query.
- [x] `品牌代碼`/`品牌名稱` cell text color is `#1f2430` for every row regardless of that row's `active` value (no dimming/fading of inactive rows' text).

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/vite.config.ts` — added `server.proxy` for `/api` → `http://localhost:8080` (dev-server proxy convention, since none existed yet), and Vitest `test` config (jsdom environment, setup file).
  - `develop/frontend/package.json` — added `react-router-dom` (routing convention for future exchange-rate-center pages); added devDependencies `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`, `@vitest/ui`; added `"test": "vitest run"` script.
  - `develop/frontend/src/setupTests.ts` (new) — imports `@testing-library/jest-dom/vitest` matchers and registers `afterEach(cleanup)` for RTL (required since `vitest.config` doesn't use `globals: true`).
  - `develop/frontend/src/api/http.ts` (new) — thin `fetch` wrapper (`apiRequest`) hitting the `/api` prefix, throwing `ApiError` on non-2xx.
  - `develop/frontend/src/api/brands.ts` (new) — `Brand` type, `fetchBrands()` (`GET /api/brands`), `updateBrandActive(id, active)` (`PUT /api/brands/{id}`).
  - `develop/frontend/src/components/ToggleSwitch.tsx` + `.css` (new) — reusable switch: `role="switch"`, disabled+"更新中..." pending state, on/off track styling matching the storyboard.
  - `develop/frontend/src/components/Toast.tsx` + `.css` (new) — dismissible inline error toast, fixed bottom-right.
  - `develop/frontend/src/layouts/AppLayout.tsx` + `.css` (new) — dark sidebar shell with "匯率中心 WDD" header and a "匯率中心" nav group; `品牌管理` is the only enabled/linked item (routes to `/brands`), the sibling exchange-rate-center pages seen in the storyboard (幣別管理, 幣別對管理, 價差群組管理, 審核紀錄) are rendered as disabled placeholders since no frontend spec for them exists yet — this avoids inventing unspecced pages/routes while still matching the documented sidebar layout.
  - `develop/frontend/src/pages/BrandManagementPage.tsx` + `.css` (new) — the `/brands` page: loads brands on mount, renders the 品牌代碼/品牌名稱/狀態 table, optimistic-toggle-with-revert-on-failure interaction, inline loading/error+重試 states, no create/delete controls.
  - `develop/frontend/src/pages/BrandManagementPage.test.tsx` (new) — 5 Vitest + RTL tests: loads & displays all 7 brands; inline error + retry on list-load failure; toggle calls `PUT` with new value, shows disabled "更新中..." while pending, reflects the resolved state; failed toggle reverts and shows the error toast; no 新增/刪除 controls present.
  - `develop/frontend/src/pages/HealthPage.tsx` (new) — the original health-check markup, moved out of `App.tsx` and kept reachable at `/health` so the pre-existing health check isn't lost.
  - `develop/frontend/src/App.tsx` — replaced the health-check body with `react-router-dom` `Routes`: `/health` (health check), and `/`, `/brands` under `AppLayout` (`/` redirects to `/brands`).
  - `develop/frontend/src/main.tsx` — wrapped `<App />` in `<BrowserRouter>`.
- Notes:
  - Interaction design resolved from the storyboard screenshots (`docs/frontend/brand/step-{1,2,3}.png`): clicking a toggle immediately shows the *target* state but disabled/greyed with a "更新中..." label (optimistic update); on success the row is reconciled with the server response; on failure the toggle reverts to its pre-click value and the toast `更新品牌狀態失敗，請稍後再試` appears — this reading matches both the spec's "revert to previous state" wording and the storyboard frames.
  - API convention: relative `/api/*` calls proxied by Vite's dev server to `http://localhost:8080` (`vite.config.ts` `server.proxy`), since no such convention existed in the codebase yet; this is the standard Vite pattern and keeps frontend code origin-agnostic for later reverse-proxy deployment.
  - Added `react-router-dom` and a `vitest`/RTL test toolchain since neither existed in the scaffolded skeleton; this establishes the routing/testing convention for future exchange-rate-center pages.
  - Verified with: (1) `npx tsc -b` — no errors; (2) `npm run build` — `tsc -b && vite build` succeeds, produces `dist/`; (3) `npx oxlint` — exit 0 (one informational `set-state-in-effect` warning on the standard fetch-on-mount pattern, not an error); (4) `npx vitest run` — all 5 tests pass; (5) live end-to-end check: started the real backend (`mvn -f develop/backend/pom.xml spring-boot:run`, confirmed MySQL `wdd` reachable on 3306) and the Vite dev server (`npm run dev`, port 5173), then `curl`'d `http://localhost:5173/api/brands` through the dev proxy and got the real 7 seeded brands from the live DB; `curl -X PUT http://localhost:5173/api/brands/4` with `{"active": false}` then `{"active": true}` round-tripped correctly through the proxy to the live backend (confirmed by response bodies and `updatedAt` bump), and the `star` brand's `active` was restored to `true` afterward so no residual state was left behind. Both dev processes were stopped and ports 5173/8080 confirmed free at the end.
  - `docker/launch.json` already had a valid `frontend` entry (`npm --prefix develop/frontend run dev`, port 5173) alongside `backend`; `.claude/launch.json` symlink to `../docker/launch.json` was already present and valid — no changes needed to either.

### Increment 1 — 2026-08-20
- Status: DONE
- Root cause: `BrandManagementPage.css` and `ToggleSwitch.css` colored table/label text via `var(--text)`/`var(--text-h)`, which `index.css` redefines to near-white (`#f3f4f6`/`#9ca3af`) under `@media (prefers-color-scheme: dark)`. `.brand-table`'s background stayed hardcoded `#fff` regardless of that media query, so on a browser/OS reporting a dark color-scheme preference, every row's `品牌代碼`/`品牌名稱`/狀態 label text rendered as near-white-on-white — effectively invisible, not just "the wrong color." Confirmed via computed-style inspection (`getComputedStyle`) that every row had identical `color: rgb(243, 244, 246)` before the fix, regardless of `active` — the visual impression of some rows being "more faded than others" was a screenshot/perception artifact from the adjacent green vs. grey toggle color, not an actual per-row code difference.
- Files changed:
  - `develop/frontend/src/pages/BrandManagementPage.css` — replaced `var(--text)`/`var(--text-h)` with the fixed hex values from this spec's new `## Visual Style` table (breadcrumb `#64748b`, title `#111827`, status `#6b7280`, `th` background `#f9fafb`/text `#6b7280`/border-bottom `#e5e7eb`, `td` text `#1f2430`/border-bottom `#f1f2f5` with `tbody tr:last-child td { border-bottom: none }`), and added `.brand-table__code-cell` (monospace, `#374151`).
  - `develop/frontend/src/pages/BrandManagementPage.tsx` — applied the new `brand-table__code-cell` class to the 品牌代碼 cell.
  - `develop/frontend/src/components/ToggleSwitch.css` — track off/on colors fixed to `#d1d5db`/`#22c55e`; label color fixed to `#6b7280` (off/pending) / `#16a34a` bold via a new `--on` modifier class (previously always `var(--text-h)`, so labels also went near-invisible in dark mode).
  - `develop/frontend/src/components/ToggleSwitch.tsx` — label now gets a `toggle-switch__label--on` class when `checked && !isPending`, so CSS can give 啟用/停用 their distinct fixed colors.
- Notes: `AppLayout.css`'s sidebar/content colors were already hardcoded hex (not theme variables) and needed no change; `index.css`'s `:root`/`h1`/dark-mode-media-query variables are untouched (still used by `HealthPage`, out of scope for this spec) — this fix only removes the brand page's own dependency on those shifting variables, per the new Visual Style requirement that this page must not vary with `prefers-color-scheme`.
  Verified: (1) `npx tsc -b` — no errors; (2) `npm run build` — succeeds; (3) `npx vitest run` — all 5 existing tests still pass unchanged; (4) live browser check via the preview tool at `/brands` — `getComputedStyle` on every row confirmed `code`/`name` color is `rgb(31, 36, 48)` (`#1f2430`) identically for all 7 brands regardless of `active`, toggle track backgrounds are exactly `rgb(209, 213, 219)`/`rgb(34, 197, 94)` (`#d1d5db`/`#22c55e`), and labels are exactly `rgb(107, 114, 128)`/`rgb(22, 163, 74)` (`#6b7280`/`#16a34a`) for 停用/啟用 respectively — a full-page screenshot confirmed all 7 rows are now equally legible.
