---
status: done
title: "Brand Currency Pair Page"
requirement: "因為想看到品牌裡面有哪些幣種對，品牌幣種對應該獨立出一個標籤，裡面顯示該品牌所擁有的幣種對，可以 CRUD、設定自動/手動匯率、開啟關閉"
depends_on: [brand, audit]
---

# Brand Currency Pair — Frontend Spec

## Overview
A page under the "匯率中心" sidebar group, its own tab (label `品牌幣種對`), independent from `幣別對管理` (`currency-pair.md`, which only manages the global pair definitions). Pick a brand, see every currency pair that brand has, and manage each one's rate type/rate/active state directly — this is the brand-centric view of the same data `currency-pair.md`'s definitions page only shows an aggregate count for. Backed by [currency-pair.md](../backend/currency-pair.md) (read/update/delete) and [brand.md](../backend/brand.md) (the brand selector).

**Changes on this page are not applied immediately — they are sent for approval.** Saving a row, toggling its 狀態, or deleting it now creates a pending request that a reviewer must approve on the `審核紀錄` page ([audit.md](audit.md)) before anything actually changes. The table always shows currently-effective data, so a row does not move when you submit a change to it; it gains a 審核中 marker instead.

## Requirements

### Page: 品牌幣種對 (`/brand-currency-pairs`)
- Add a new sidebar item to `AppLayout.tsx`'s "匯率中心" group: label `品牌幣種對`, path `/brand-currency-pairs`, placed directly after `幣別對管理`.
- On load, calls `GET /api/brands` and renders a brand selector (tabs or a dropdown — pick whichever this codebase's existing patterns favor) listing all 7 brands by `code`. The first brand is selected by default.
- Selecting a brand calls `GET /api/currency-pairs?brandId={id}` and renders one row per currency pair that brand has (i.e. one row per currency pair definition that exists — every definition fans out a row per brand, so this is effectively "every definition, from this brand's angle").
- Table columns: `幣種對` (`baseCurrencyCode`/`quoteCurrencyCode`, e.g. "USD/JPY"), `匯率類型` (單選：自動/手動), `匯率` (number input, only enabled when `匯率類型` = 手動), `狀態` (active toggle switch), `審核` (a 審核中 badge when this row has a pending request, otherwise empty), `操作` (儲存 / 刪除).
- After loading the brand's pairs, also calls `GET /api/audit-requests?status=PENDING&entityType=CURRENCY_PAIR&brandId={id}` and marks every row whose `entityId` matches. A row with a pending request has its 匯率類型/匯率/狀態 controls and its 刪除 button disabled, with the badge's tooltip explaining why (`此列有待審核的變更，需先完成審核`) — a second change to the same row would be rejected by the server anyway.
- If the selected brand has no currency pairs at all (no definitions created yet), show an empty state ("此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義").

### Row interactions
- Changing `匯率類型` to `手動` enables the `匯率` input and requires a value before saving; changing to `自動` disables and clears the `匯率` input.
- Each row has its own `儲存` action (or saves on blur/toggle — either is acceptable) that calls `PUT /api/currency-pairs/{id}` with the row's current `rateType`/`rate`/`active`. While in flight, disable that row's controls.
  - On success (`202`): the row's committed values are left unchanged, its controls become disabled, a 審核中 badge appears, and a toast confirms submission ("已送出審核，核准後才會生效").
  - On `400` (e.g. manual rate missing, or exceeds the parent definition's precision): inline error under the `匯率` field ("請輸入有效匯率"). This validation still happens at submit time, so an invalid change never reaches the review queue.
  - On `409` (this row already has a pending request): error toast ("此列已有待審核的變更") and refresh the row's pending marker.
  - On other failure: revert the row's fields to their last saved values, error toast ("更新失敗，請稍後再試").
- The 狀態 toggle submits on click like the Brand page's toggle — disabled + "送審中..." label while in flight — but it must **not** stay flipped on success: because the change only takes effect after approval, the switch returns to its currently-effective position and the row gains the 審核中 badge. An optimistic flip here would state something false. On failure it also reverts, with an error toast.
- `刪除` on a row opens a confirmation dialog ("確定要送出刪除「<baseCurrencyCode>/<quoteCurrencyCode>」的申請嗎？核准後才會真正刪除。"); on confirm, calls `DELETE /api/currency-pairs/{id}`. The row is **not** removed — it stays with a 審核中 badge until the deletion is approved. Toast ("已送出審核，核准後才會生效"). No guard — allowed regardless of `active`.
- No "+新增" control on this page — a brand's currency pairs come entirely from `幣別對管理`'s definition fan-out; recreating an individually-deleted row is out of scope for this spec.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單（選擇器用） | GET | /api/brands | — | `[{id, code, name, active, ...}]` |
| 載入某品牌的幣種對 | GET | /api/currency-pairs?brandId={id} | — | `[{id, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, brandId, brandCode, rateType, rate, active, createdAt, updatedAt}]` |
| 送出修改申請 | PUT | /api/currency-pairs/{id} | `{rateType, rate, active}` (subset) | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 送出刪除申請 | DELETE | /api/currency-pairs/{id} | — | `202` same pending-request shape |
| 載入此品牌的待審申請（標記列） | GET | /api/audit-requests?status=PENDING&entityType=CURRENCY_PAIR&brandId={id} | — | `[{id, entityId, actionType, summary, ...}]` — match `entityId` to each row's `id` |

## Error States
- Brand selector load failure: inline error message with a "重試" button instead of the selector/table.
- Currency pair list load failure (for the selected brand): same pattern, scoped to the table area.
- Row save/toggle/delete failures: see Row interactions above.

## Visual Style
Same fixed light theme as the rest of the app (see `specs/frontend/brand.md`'s `## Visual Style` for the base table palette and `specs/frontend/currency-pair.md`'s for the toggle/radio palette — both reused here identically). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Page title | color | `#111827` |
| Brand selector item — inactive | background / text | `#fff` / `#374151` |
| Brand selector item — selected | background / border / text | `#eff6ff` / `#2563eb` / `#2563eb` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 幣種對 cell | text | `#374151`, monospace font |
| Danger button (`刪除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Toggle switch — off / on | track background | `#d1d5db` / `#22c55e` |
| 狀態 label — 停用 / 啟用 | color | `#6b7280` / `#16a34a` (bold) |
| Radio button (自動/手動) — selected | border / dot | `#2563eb` / `#2563eb` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form input, disabled (匯率 when 自動) | background / text | `#f3f4f6` / `#9ca3af` |
| 審核中 badge | background / text | `#fffbeb` / `#b45309` |
| Row control, disabled by pending review | background / text | `#f3f4f6` / `#9ca3af` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [x] `品牌幣種對` nav item exists in `AppLayout.tsx`'s 匯率中心 group, enabled, linking to `/brand-currency-pairs`.
- [x] Page loads all 7 brands from `GET /api/brands` and shows a brand selector; the first brand is selected by default.
- [x] Selecting a brand loads its currency pairs from `GET /api/currency-pairs?brandId={id}` and displays 幣種對/匯率類型/匯率/狀態 for each.
- [x] A brand with no currency pairs shows the empty-state message instead of an empty table.
- [x] Switching a row's `匯率類型` to 手動 requires a `匯率` value before it can save; switching to 自動 clears it.
- [x] Toggling a row's 狀態 calls `PUT` immediately with a disabled "更新中..." state, and reverts + toasts on failure.
- [x] `刪除` on a row succeeds via `DELETE` regardless of its 狀態.
- [x] Saving a row returns `202` and leaves the row's displayed values unchanged, showing a 審核中 badge and the submission toast instead.
- [x] The 狀態 toggle returns to its currently-effective position after a successful submit rather than staying flipped.
- [x] `刪除` leaves the row on screen with a 審核中 badge instead of removing it.
- [x] Rows with a pending request load with their controls disabled and the badge's explanatory tooltip.
- [x] A `409` from any action shows the already-pending toast and refreshes that row's marker.
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles in a live browser (including under a dark `prefers-color-scheme`), and does not change under a dark `prefers-color-scheme`.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/api/currencyPairDefinitions.ts` — added optional `baseCurrencyCode`/`quoteCurrencyCode` to `CurrencyPair`; added `CurrencyPairUpdateRequest` type and `fetchCurrencyPairsByBrand`, `updateCurrencyPair`, `deleteCurrencyPair` functions
  - `develop/frontend/src/pages/BrandCurrencyPairPage.tsx` (new)
  - `develop/frontend/src/pages/BrandCurrencyPairPage.css` (new)
  - `develop/frontend/src/pages/BrandCurrencyPairPage.test.tsx` (new)
  - `develop/frontend/src/layouts/AppLayout.tsx` — added `品牌幣種對` nav item (`enabled: true`, path `/brand-currency-pairs`) directly after `幣別對管理`
  - `develop/frontend/src/App.tsx` — added `/brand-currency-pairs` route
- Notes:
  - Brand selector implemented as a horizontal tab list (`role="tablist"`/`role="tab"`) showing all brands by `code`; first brand auto-selected on load. Selecting a tab triggers `GET /api/currency-pairs?brandId={id}`.
  - Table row state: local edit state per row (`rateType`, `rateInput`, inline `error`) separate from the committed `CurrencyPair`, so edits are staged until an explicit per-row `儲存` button (placed in the `操作` cell alongside `刪除`, satisfying the spec's "either is acceptable" save-mechanism note) — switching the `匯率類型` radio to `手動` only enables the input (no API call yet); clicking `儲存` validates a positive numeric rate client-side first (blocks the API call with the inline `請輸入有效匯率` error if missing/invalid), then calls `PUT /api/currency-pairs/{id}` with `{rateType, rate}`. Switching to `自動` clears the local rate input/error immediately (no API call until `儲存`).
  - On `PUT` success the row is reset from the server response (toast `品牌幣種對已更新`); on `400` the inline `請輸入有效匯率` error is shown without altering committed state; on any other failure the row's fields revert to the last committed `CurrencyPair` values and an error toast (`更新失敗，請稍後再試`) is shown. Both save and toggle track a per-row `busy` state (`'save' | 'toggle' | undefined`) that disables every control in that row while in flight.
  - `狀態` toggle reuses `ToggleSwitch` exactly like `BrandManagementPage`: optimistic flip, immediate `PUT /api/currency-pairs/{id}` with `{active}`, "更新中..." pending label + disabled switch while in flight, revert + error toast on failure.
  - `刪除` opens a confirmation modal with the exact spec wording, then calls `DELETE /api/currency-pairs/{id}` unconditionally (no active-state guard) and removes the row with an `已刪除` toast.
  - Empty state renders the exact spec copy `此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義` instead of a table when the selected brand has zero currency pairs.
  - Brand-list and pair-list load failures each render an inline error message with their own `重試` button, scoped to the brand-selector area and table area respectively, per spec.
  - All colors are hardcoded hex values (new CSS classes prefixed `bcp-*`, not reusing/aliasing any theme variable) transcribed literally from the spec's `## Visual Style` table — page background/title, brand-selector inactive/selected, table card/header/row, monospace 幣種對 cell, danger button (+hover), toggle track colors and 狀態 label colors (both inherited unchanged from the shared `ToggleSwitch` component, itself already hardcoded), radio `accent-color: #2563eb` for the selected border/dot, form input border/text/focus, disabled rate-input background/text (`#f3f4f6`/`#9ca3af`, distinct from `CurrencyPairManagementPage`'s disabled-input color, hence a dedicated `.bcp-form__input:disabled` rule rather than reusing that page's class), and validation-text red. No `prefers-color-scheme` media query or CSS variable is used anywhere in the new stylesheet.
- Verified:
  - `cd develop/frontend && npm test -- --run`: all 32 tests pass (12 new in `BrandCurrencyPairPage.test.tsx` covering: default brand selection + pair load, brand-switch reload, brand-list load failure + retry, pair-list load failure + retry (scoped to table area), empty-state message, 手動/自動 radio toggling behavior including the blocked-save-with-inline-error case, successful save reflecting the server response, inline error on a `400` response, revert-and-toast on a non-400 save failure, toggle success with pending label + disabled switch, toggle revert-and-toast on failure, and delete-after-confirm removing the row; plus the 3 pre-existing test files — `BrandManagementPage`, `CurrencyManagementPage`, `CurrencyPairManagementPage` — still pass unmodified).
  - `cd develop/frontend && npm run build` (`tsc -b && vite build`): compiles and builds cleanly with no errors.
  - Live end-to-end verification against the real stack: started the MySQL-backed backend (`mvn spring-boot:run`), confirmed `currency_pair_definition`/`currency_pair` were empty beforehand (`GET /api/currency-pair-definitions` and `/api/currency-pairs` both `[]`). Seeded via `POST /api/currency-pair-definitions` (USD/JPY, precision 4) confirming the expected 7-brand fan-out response shape (`baseCurrencyCode`/`quoteCurrencyCode`/`brandCode`/`rateType: "AUTO"`/`rate: null`/`active: false` on every row — matches the `CurrencyPair` type used by the new page). Then directly exercised the exact endpoints the new page calls: `GET /api/currency-pairs?brandId=1` (confirmed the brand-scoped list shape), `PUT /api/currency-pairs/{id}` with `{rateType:"MANUAL", rate:150.25}` (confirmed 200 + updated row), `PUT .../{id}` with `{active:true}` (confirmed 200, partial-update semantics preserved the previously-set rate), `PUT .../{id}` with `{rateType:"MANUAL"}` and no rate (confirmed `400`, matching the inline-error path), `DELETE .../{id}` on both an inactive and an active row (both `204`, confirming "no guard" per spec). Cleaned up all seeded rows and the definition afterward — confirmed both tables back to `[]`. Stopped the backend process afterward.
  - Did **not** perform: an actual browser/visual screenshot or computed-style check of rendered CSS (no browser/screenshot tool was available in this session). Color correctness was verified by direct inspection of the CSS source against the spec's `## Visual Style` table (every value transcribed literally as a hex/rgba string, no tokens or `prefers-color-scheme` queries involved) and is exercised functionally by the passing component tests (e.g. disabled/enabled state transitions, toggle on/off label text), but a rendered-DOM computed-style equality check was not run.
  - `docker/launch.json` / `.claude/launch.json`: confirmed already correctly configured (`frontend` entry present with `port: 5173`, matching `vite.config.ts`'s unset `server.port` default; symlink present) from prior spec execution — no changes needed.

### Browser verification — 2026-08-22 (`/dev` level, after agent execution)
Follow-up to the agent's "did not perform" note above: the rendered-DOM computed-style check **has now been run** against the live stack (Vite dev server on :5173 + Spring Boot on :8080 + MySQL), so Acceptance Criterion 8 is legitimately checked.

- Rendered `/brand-currency-pairs` with two seeded definitions fanned out to all 7 brands. The page showed all 7 brand tabs (`au moneta pug star um vjp vt`) with `au` selected by default and its two rows (USD/JPY 啟用, EUR/USD 停用).
- `getComputedStyle` values read from the live DOM, all exact matches to `## Visual Style`:
  page background `rgb(245,246,248)`; title `rgb(17,24,39)`; selected brand tab `rgb(239,246,255)`/border `rgb(37,99,235)`/text `rgb(37,99,235)`; inactive brand tab `#fff`/`rgb(55,65,81)`; `th` `rgb(249,250,251)`/`rgb(107,114,128)`; `td` `rgb(31,36,48)` + `border-bottom rgb(241,242,245)` + monospace 幣種對 cell; 狀態 labels 啟用 `rgb(22,163,74)` weight 600 / 停用 `rgb(107,114,128)`; danger `刪除` `rgb(220,38,38)`/`#fff`; 匯率 input disabled (自動) `rgb(243,244,246)`/`rgb(156,163,175)`, enabled (手動) `#fff`/`rgb(31,36,48)`, border `rgb(209,213,219)`; inline error `rgb(217,45,32)`.
- All of the above re-read with `prefers-color-scheme: dark` forced (`matchMedia(...).matches === true`): **values byte-identical** — no dark-mode drift.
- UI interaction checked in-browser, end to end through the real API:
  - Switching EUR/USD's 匯率類型 to 手動 enabled the 匯率 input; clicking 儲存 with an empty rate rendered the inline `請輸入有效匯率` error at `#d92d20` and made no request.
  - Entering `1.09321` and saving issued the `PUT` and showed the `品牌幣種對已更新` toast; verified server-side that the row persisted as `rateType=MANUAL, rate=1.09321`.
  - Clicking the 狀態 toggle issued the immediate `PUT`; verified server-side that `active` flipped to `true`.
- Console clean (no page errors).
- All seeded data removed afterward — `currency_pair_definition` and `currency_pair` both back to 0 rows.
- Screenshot not captured (the browser pane was not displayed in this environment); the computed-style reads above are the stronger check for this criterion and cover it fully.

### Increment 2 — 2026-08-23
Implements the five previously-unchecked Acceptance Criteria, reacting to `PUT`/`DELETE /api/currency-pairs/{id}` now returning `202` with a pending audit request instead of applying the change directly.

- Files changed:
  - `develop/frontend/src/api/currencyPairDefinitions.ts` — added `CurrencyPairAuditSubmission` (`{auditRequestId, status, entityType, actionType, entityId, summary}`, the `202` pending-request body); `updateCurrencyPair`/`deleteCurrencyPair` now return `Promise<CurrencyPairAuditSubmission>` instead of `Promise<CurrencyPair>`/`Promise<void>`.
  - `develop/frontend/src/pages/BrandCurrencyPairPage.tsx` — reworked save/toggle/delete handling for the "submit for approval" model; added pending-marker loading and row-disable/badge wiring.
  - `develop/frontend/src/pages/BrandCurrencyPairPage.css` — added `.bcp-badge`/`.bcp-badge--pending` (`#fffbeb`/`#b45309`), qualified `.bcp-page__btn--secondary:disabled`/`.bcp-page__btn--danger:disabled` (`#f3f4f6`/`#9ca3af`) to win over the variants' own background/text colors at equal specificity, and `.bcp-radio:has(input:disabled)` for the disabled radio label text color.
  - `develop/frontend/src/pages/BrandCurrencyPairPage.test.tsx` — updated the save/toggle/delete tests for the new non-applying `202` behavior and added tests for pending-marker loading on page load, row-disabling while pending, and `409` handling on save/toggle/delete.
- Notes:
  - **Pending markers**: added `loadPendingMarkers(brandId)`, calling `fetchAuditRequests({status: 'PENDING', entityType: 'CURRENCY_PAIR', brandId})` from the already-existing `src/api/audit.ts` (reused as instructed, no parallel fetch helper written). Invoked once after each successful `loadPairs`, and again to "refresh" a row's marker after any `409`. Results populate a `Set<number>` of `entityId`s (`pendingIds`) matched 1:1 against each row's `id`. A failure of this secondary lookup is swallowed silently (leaves prior markers in place) rather than surfacing a page-blocking error, since the spec's `## Error States` section only calls out the brand-selector and pair-list loads as needing an error UI.
  - **Row disabling**: `rowDisabled` is now `busy !== undefined || pendingIds.has(pair.id)`, so a row with a pending marker disables its rate-type radios, rate input, 狀態 toggle, 儲存, and 刪除 — matching "a second change to the same row would be rejected by the server anyway."
  - **Save (儲存)**: on `202`, the row's local edit draft is discarded (`revertRow(pair)`, resetting to the *pre-edit* committed `pair`, since `pairs` state itself is intentionally left untouched) instead of being applied from a server response; `pair.id` is added to `pendingIds`; toast changes from the old `品牌幣種對已更新` to `已送出審核，核准後才會生效`. `400` inline-error handling is unchanged. New `409` branch: reverts the draft, toasts `此列已有待審核的變更`, and calls `loadPendingMarkers` for the selected brand. Other failures keep the pre-existing revert + `更新失敗，請稍後再試` toast.
  - **狀態 toggle**: removed the optimistic flip entirely (previously flip-then-revert-on-failure) — the switch never changes position client-side; only `busy`/`isPending` drives the disabled `送審中...` label (renamed from `更新中...` per spec) via `ToggleSwitch`'s existing `pendingLabel` prop. On `202`, marks `pendingIds` and shows the submission toast without touching `pairs`, so the switch is already "back" at its currently-effective position (it was never moved). `409` and other-failure branches mirror save's.
  - **刪除**: confirm-dialog copy changed to the spec's exact new wording (`確定要送出刪除「<base>/<quote>」的申請嗎？核准後才會真正刪除。`). On `202`, the row is no longer filtered out of `pairs` — it stays, gains a `pendingIds` entry, and shows the submission toast (no more `已刪除`). `409` toasts `此列已有待審核的變更` and refreshes markers; other failures keep the prior `刪除失敗，請稍後再試` toast. No `active`-state guard, matching the spec's explicit "No guard" note (unchanged from Increment 1).
  - **審核 column**: added between `狀態` and `操作` in both header and body; renders a `審核中` badge (`title="此列有待審核的變更，需先完成審核"`) when `pendingIds.has(pair.id)`, otherwise an empty cell.
  - **CSS specificity**: per the task's explicit warning about the spread-page bug earlier this session, checked that `.bcp-page__btn--danger`'s own `color:#fff`/`background:#dc2626` (specificity 0,2,0, defined *after* the old generic `.bcp-page__btn:disabled` in source order) would have silently out-ranked a same-specificity, source-order-losing generic disabled rule. Fixed by removing the color/background from the generic `:disabled` rule and adding `.bcp-page__btn--secondary:disabled, .bcp-page__btn--danger:disabled` (specificity 0,3,0) instead, which unconditionally beats both variants regardless of file order. All new colors (badge, disabled row controls) are literal hex values transcribed from the spec's `## Visual Style` table; no `prefers-color-scheme` query or CSS variable used anywhere in the stylesheet, consistent with Increment 1.
  - `ToggleSwitch` (`src/components/ToggleSwitch.tsx`/`.css`) was **not** modified — it is a shared component also used by `BrandManagementPage`; its existing on/off track colors already match this spec's `## Visual Style` table (`#d1d5db`/`#22c55e`) from Increment 1, and its pending-state dimming (`opacity: 0.7` on `.toggle-switch__track:disabled`) was already accepted in the Increment 1 browser verification. Only the `pendingLabel` string passed to it from `BrandCurrencyPairPage.tsx` changed (`更新中...` → `送審中...`).
- Verified:
  - `cd develop/frontend && npm test -- --run`: all 63 tests pass — the 12 original `BrandCurrencyPairPage` tests (updated in place where they asserted the now-removed optimistic-apply behavior: save reflecting a server-returned `CurrencyPair`, toggle flipping+re-flipping, delete removing the row) plus 8 new tests (pending-marker query on load asserted inline in the first test; save/toggle/delete each on `202` — unchanged displayed values / badge / disabled controls — and on `409` — toast + marker refresh; delete confirm-dialog copy; rows loading pre-marked as pending with disabled controls, tooltip, and an unaffected sibling row) plus the 5 unmodified pre-existing test files across the rest of the app (`BrandManagementPage`, `CurrencyManagementPage`, `CurrencyPairManagementPage`, `AuditRequestPage`, `SpreadGroupManagementPage` — none of which import from `BrandCurrencyPairPage.tsx`, and only `CurrencyPairManagementPage.tsx` imports from `currencyPairDefinitions.ts`, using the untouched `updateCurrencyPairDefinitionPrecision`/`deleteCurrencyPairDefinition` functions rather than the two whose return types changed here, confirmed via `grep` before editing).
  - `cd develop/frontend && npm run build` (`tsc -b && vite build`): compiles and builds cleanly with no errors or new warnings.
  - Confirmed via `grep` that `updateCurrencyPair`/`deleteCurrencyPair` (whose return types changed) are only imported by `BrandCurrencyPairPage.tsx` and its test file — no other page depends on the old `CurrencyPair`/`void` return shape.
  - `docker/launch.json` / `.claude/launch.json`: re-checked, already correct (`frontend` entry, `port: 5173`, symlink present) — no changes needed.
  - Did **not** perform: any live browser or backend verification in this increment (no dev server or backend process was started in this session) — no `getComputedStyle` reads, no manual click-through against a running `/api/currency-pairs` or `/api/audit-requests` endpoint, and no screenshot. All verification above is `npm test`/`npm run build` plus static code/CSS review. In particular, the new `.bcp-badge--pending` and disabled-control colors were transcribed literally from the spec's `## Visual Style` table but have not been confirmed via rendered computed styles in this increment (Increment 1's already-checked colors were browser-verified previously; these newly-added ones were not).
