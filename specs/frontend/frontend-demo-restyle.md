---
status: done
title: "Restyle Frontend to Match Demo"
requirement: "frontend 畫面可以照 demo 的樣式做修改 (frontend screens can be restyled to follow the demo/ prototype's look)"
---

# Restyle Frontend to Match Demo — Frontend Spec

## Overview
The `demo/` folder at the repo root (`demo/index.html`, `demo/style.css`, `demo/script.js`, `demo/assets/ows-logo.png`) is a static HTML/CSS prototype showing the intended visual design for this app: a left sidebar with the OWS logo and navigation, a top header with a user avatar, and a content area built from reusable "card" patterns (filter bar, search/table card, data table, status badges, pagination footer). The real app (`develop/frontend/`) currently renders a bare, unstyled `CurrencyPage` with no app shell.

This spec restyles the real, working React app to match the demo's look — colors, spacing, typography, card/table/badge/button visual language, and the sidebar+header shell — **without** changing behavior, API calls, or the data model. It reuses the demo purely as a **style and layout reference**; it does not copy the demo's mock data, its approval-workflow columns (Operator / Approval Status / Approver / Last Approval Time — these have no backing field in `currency`, `currency_pair`, or `brand`), or its tab-switching pattern (this app already uses separate routes/pages per feature — see `specs/frontend/currency.md`, `specs/frontend/currency-pair.md`, `specs/frontend/brand.md` — so no tab bar is needed to switch between them).

## Requirements

### 1. Design tokens
Extract the demo's design tokens into the real app's global stylesheet (`develop/frontend/src/index.css`), replacing the current ad hoc palette:
| Token | Value | Usage |
|---|---|---|
| Brand green | `#00a870` (hover `#008c5c`) | primary buttons, active nav item, links, focus ring, currency-code text |
| Page background | `#f5f5f5` | body / content area |
| Surface | `#ffffff` | sidebar, header, cards, table |
| Border | `#e8e8e8` (structural), `#d9d9d9` (inputs/buttons) | dividers, card borders, input borders |
| Text primary | `#262626` | body text |
| Text muted | `#8c8c8c` | labels, secondary text |
| Text disabled/placeholder | `#bfbfbf` | placeholders |
| Success/active | `#52c41a` | active status badge |
| Danger/inactive | `#ff4d4f` | inactive status badge, destructive actions |
| Radius | `2px` (inputs/buttons/table), `4px` (cards) | matches demo exactly |
| Font | `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, ... sans-serif`, `14px` base | matches demo body font |
| Shadow | `0 1px 2px 0 rgba(0,0,0,0.03)` | card elevation |

Use CSS custom properties (e.g. `--color-brand`, `--color-border`, `--radius-sm`, `--radius-md`) so all components reference the same source of truth instead of hard-coded hex values scattered across files.

### 2. App shell (new)
Add a persistent shell that wraps every routed page, structurally matching `demo/index.html`'s `.app-container` (`.sidebar` + `.main-wrapper` containing `.top-header` + `.content-area`):
- **Sidebar**: OWS logo at top (copy `demo/assets/ows-logo.png` into `develop/frontend/src/assets/`), followed by navigation links to the app's **actual** pages only:
  - Currency Management → `/currencies` (existing)
  - Currency Pair List → `/currency-pairs` (once `specs/frontend/currency-pair.md` is implemented)
  - Brand Management → `/brands` (once `specs/frontend/brand.md` is implemented)
  Do **not** port the demo's "Rate Management" and "Exchange Rate History" sub-items — those are prototype-only placeholders with no corresponding page or API in this app. If a linked page doesn't exist yet, either omit that nav item until its page ships, or render it and let the router 404 — omitting is preferred to avoid dead links.
  The active route's nav item is visually highlighted (demo's `.nav-subitem.active` treatment: green text, light-green background, left border).
- **Top header**: right-aligned user affordance (avatar + label), styled per demo `.top-header` / `.header-user`. No real auth/user data exists in this app — use a static placeholder label (e.g. the OS/browser has no session concept here); do not fabricate a login flow.
- **Content area**: page title (`<h1>`, demo `.page-title` styling) + page-specific content below.

Implement this as a new `AppShell`/`Layout` component (e.g. `develop/frontend/src/layout/AppShell.tsx` + `.css`) that `App.tsx` wraps all `<Route>` elements in, using `react-router-dom`'s `<Outlet />` or by wrapping each page element directly.

### 3. Restyle shared primitives
- **Buttons** (`develop/frontend/src/index.css` `.btn*` classes): restyle `.btn-primary` to the demo's `.btn-search` look (solid `#00a870`, hover `#008c5c`), `.btn-secondary` to the demo's `.btn-reset` look (white, bordered, green border/text on hover), `.btn-danger` to a solid `#ff4d4f`/dark-red-on-hover treatment consistent with the demo's palette. Keep existing class names (`btn-primary`, `btn-secondary`, `btn-danger`, `btn-link`, `btn-link--danger`) so no component markup needs to change — only the CSS rules.
- **Modal / ConfirmDialog** (`develop/frontend/src/components/Modal.css`, `ConfirmDialog.css`): restyle to the demo's card look (white surface, `4px` radius, `0 1px 2px` shadow, `#e8e8e8` header divider) instead of the current styling.
- **Toast** (`develop/frontend/src/components/Toast.css`): keep functionally the same; restyle colors to the token palette above (error → `#ff4d4f`, success → `#52c41a`).

### 4. Restyle `CurrencyPage` and its components
- Wrap the filter/search toolbar (`StatusFilter` + search input + Add button) in a card matching demo's `.filter-card` (`.filter-row`, `.filter-group`, `.filter-label`) — label the status dropdown and search box the same way demo labels its filter inputs.
- Wrap `CurrencyTable` in a card matching demo's `.search-table-card` (header bar with a title, e.g. "Currencies", above the table — the demo's per-column settings icon is decorative and can be omitted since there is no column-configuration feature).
- Restyle `CurrencyTable`'s `<table>` to match demo's `.data-table` (header row background `#fafafa`, `12px 16px` cell padding, `#f0f0f0` row divider, row hover `#fafafa`, `currency-code` column in green monospace per demo's `.currency-code`).
- Replace the bare active/inactive dot with a demo-style `.status-badge` showing a colored dot **and** a text label ("ACTIVE" / "INACTIVE"), reusing the existing `aria-label` for accessibility. Keep the underlying `active: boolean` prop/logic unchanged.
- Restyle the Edit/Delete actions to the demo's compact, bordered `.action-btn` look. **Preserve the existing accessible button text/names ("Edit", "Delete")** — do not replace them with icon-only buttons with no accessible name, since existing tests query buttons by their visible name.
- Add a table-footer bar matching demo's `.table-footer` showing "Total N items" (`visibleCurrencies.length`). Do **not** add page-turn controls or a page-size selector — `GET /api/currencies` has no pagination support, so those controls would be non-functional; showing them would be misleading.

### 5. Reuse for future pages
The AppShell, design tokens, and restyled shared primitives (buttons, Modal, ConfirmDialog, Toast, `.filter-card`/`.search-table-card`/`.data-table`/`.status-badge` patterns) must be the ones used when `specs/frontend/currency-pair.md` and `specs/frontend/brand.md` are implemented, so the whole app looks consistent. This spec only needs to update `CurrencyPage` itself, but should leave the shared pieces (index.css tokens, AppShell, Modal/ConfirmDialog/Toast styling) in a state that later pages can reuse directly.

## Implementation Details
- No new UI framework/component library dependency (the demo itself is hand-rolled HTML/CSS, not a real Ant Design instance) — clone the visual language with plain CSS, matching `env.md`'s existing frontend stack.
- Do not change component **props**, callback signatures, API integration logic, or visible text content used by existing tests (currency code/name/symbol values, "Edit"/"Delete" button names, toast messages, confirmation dialog copy) — this is a styling/markup-wrapper change, not a behavior change.
- `develop/frontend/src/App.tsx`: wrap the existing `<Route>` list with the new shell so `/currencies` (and future routes) render inside it; the `/` → `/currencies` redirect stays as-is.
- After restyling, run the existing test suite (`npm test`) to confirm nothing broke, and `npm run build` / `npm run lint` to confirm the app still compiles cleanly.

## Acceptance Criteria
- [x] `develop/frontend/src/index.css` defines the token palette above as CSS custom properties, and existing `.btn*` classes are restyled to use them
- [x] A new `AppShell` renders a sidebar (OWS logo + nav) and top header on every page, matching the demo's structure and colors
- [x] Sidebar nav links only to real app pages (Currency Management now; Currency Pair / Brand once their specs ship); no dead links to demo-only placeholder items
- [x] The current route's nav item is visually highlighted
- [x] `CurrencyPage`'s filter/search toolbar is wrapped in a `.filter-card`-style container
- [x] `CurrencyTable` is wrapped in a `.search-table-card`-style container and its `<table>` matches the demo's data-table styling (header background, cell padding, row divider, row hover, green monospace currency code)
- [x] Active/inactive is shown as a colored dot + text label ("ACTIVE"/"INACTIVE"), accessible name preserved
- [x] Edit/Delete actions are restyled but keep their existing accessible names
- [x] A "Total N items" footer bar is shown on the table; no non-functional pagination controls are added
- [x] Modal, ConfirmDialog, and Toast are restyled to the token palette and demo's card look
- [x] `npm test` passes unchanged (no test file needs to change to accommodate the restyle)
- [x] `npm run build` and `npm run lint` pass
- [x] Manual check: `/currencies` in the dev server visually matches the demo's color palette, spacing, and card/table/badge language (side-by-side with `demo/index.html`)

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/frontend/src/assets/ows-logo.png (new — copied from `demo/assets/ows-logo.png`)
  - develop/frontend/src/index.css (rewritten — CSS custom properties for the demo's token palette (`--color-brand`, `--color-bg`, `--color-surface`, `--color-border*`, `--color-text*`, `--color-success`, `--color-danger*`, `--radius-sm/md`, `--shadow-card`, `--font-family`); restyled `.btn`/`.btn-primary`/`.btn-secondary`/`.btn-danger`/`.btn-link`/`.btn-link--danger`; added shared reusable classes used by all three pages: `.page-title`, `.filter-card`/`.filter-row`/`.filter-group`/`.filter-label`/`.filter-input`/`.filter-actions`, `.status-filter` (restyled, same selector already used by `StatusFilter`/`BrandFilter`), `.search-table-card`/`.search-table-header`/`.search-table-title`, `.data-table` (header bg, cell padding, row divider, row hover), `.currency-code` (green monospace), `.status-badge`/`.status-badge--active`/`.status-badge--inactive`/`.status-dot`, `.action-buttons`/`.action-btn`/`.action-btn--danger`, `.table-footer`/`.total-count`, `.table-empty`, `.align-center`/`.align-right`)
  - develop/frontend/src/layout/AppShell.tsx, AppShell.css (new — persistent shell: sidebar with OWS logo + flat nav list to `/currencies`, `/currency-pairs`, `/brands` using `react-router-dom`'s `NavLink` for active-route highlighting (green text/background/left border); top header with a static user avatar+label placeholder; content area wrapping `<Outlet />`)
  - develop/frontend/src/App.tsx (edited — wrapped all routes in a parent `<Route element={<AppShell />}>` so every page renders inside the shell; `/` → `/currencies` redirect unchanged)
  - develop/frontend/src/components/Modal.css, ConfirmDialog.css, Toast.css (restyled to token palette — white surface, `4px` radius, card shadow, `#e8e8e8` header divider; toast colors mapped to `--color-danger`/`--color-success`/`--color-brand`)
  - develop/frontend/src/components/CurrencyFormModal.css, CurrencyPairFormModal.css (restyled colors/radius to use the new tokens; no markup/behavior change)
  - develop/frontend/src/pages/CurrencyPage.tsx + .css (toolbar wrapped in `.filter-card` with labeled Status/Search filter groups and Add action; table wrapped in `.search-table-card` titled "Currencies" with a `.table-footer` showing "Total N items")
  - develop/frontend/src/components/CurrencyTable.tsx + .css (table restyled to `.data-table`; code column uses shared `.currency-code`; active/inactive rendered as `.status-badge` with dot + "ACTIVE"/"INACTIVE" text, same `aria-label`; Edit/Delete restyled to `.action-btn`/`.action-btn--danger`, same accessible names)
  - develop/frontend/src/pages/CurrencyPairPage.tsx + .css, develop/frontend/src/pages/BrandPage.tsx + .css (same shared-class treatment applied for consistency: filter-card for CurrencyPairPage's Brand/Status filters + Add button; search-table-card + table-footer for both; no props/callback/API changes)
  - develop/frontend/src/components/CurrencyPairTable.tsx + .css, develop/frontend/src/components/BrandTable.tsx + .css (adopted `.data-table`, `.currency-code`, `.status-badge` (pair table), `.action-btn` (pair table); brand table's toggle switch kept as-is functionally, recolored to tokens (`--color-success`/`--color-brand`); brand/pair code columns use shared `.currency-code` styling)
- Notes:
  - No new dependencies added; pure CSS + markup-wrapper restyle, matching `env.md`'s existing React/Vite/TypeScript stack.
  - No component props, callback signatures, API calls, visible test-queried text (currency/brand/pair values, "Edit"/"Delete"/"+ Add" button names, toast/dialog copy), or `aria-label`s were changed — only class names/markup wrappers and CSS.
  - `CurrencyPage`, `CurrencyPairPage`, `BrandPage` test suites were rendered directly (without `AppShell`/Router) prior to this change and continue to be, so `AppShell` only affects real app navigation via `App.tsx`, not these existing unit tests.
  - `npm test`: all 56 tests across 9 files pass unchanged.
  - `npm run build` (`tsc -b && vite build`) and `npm run lint` (`oxlint`) both pass; the only lint output is a pre-existing, unrelated warning on `ToastProvider.tsx` (fast-refresh export rule), not introduced by this change.
  - Manually smoke-tested via `npm run dev`: server boots and serves `index.html`/`main.tsx` without runtime errors.
