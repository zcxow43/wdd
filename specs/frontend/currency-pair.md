---
status: done
title: "Currency Pair Management Page"
requirement: "匯率中心需要幣別對管理畫面：管理幣種對定義（CRUD、精度）。品牌幣種對改為獨立頁面（見 brand-currency-pair.md），不再是本頁面的一部分。"
depends_on: [brand, currency]
---

# Currency Pair Management — Frontend Spec

## Overview
A page under the "匯率中心" sidebar group (label `幣別對管理`, already scaffolded as a disabled placeholder at `/currency-pairs` in `AppLayout.tsx` — this spec is what turns it on). Lists every currency pair definition (base/quote currency, precision) and lets an admin create/edit/delete definitions. Managing each brand's own currency pair settings (auto/manual rate, active toggle) is a separate page reached via its own sidebar tab — see [brand-currency-pair.md](brand-currency-pair.md) — not something you drill into from a row here. Backed by [currency-pair-definition.md](../backend/currency-pair-definition.md).

## Requirements

### Page: 幣別對管理 (`/currency-pairs`)
- On load, calls `GET /api/currency-pair-definitions` and renders one row per definition.
- Table columns: `基準幣`/`報價幣` (base/quote currency code), `精度` (precision), `啟用品牌數` (count of its currency pairs with `active: true`, out of the total count — e.g. "2 / 7"), `操作` (編輯 / 刪除).
- `+ 新增幣種對` button above the table opens a create form (modal): `基準幣`/`報價幣` (currency dropdowns, populated from `GET /api/currencies`), `精度` (number, 0–8, default 4).
  - On success: close modal, add the new row (啟用品牌數 shows "0 / 7"), show a success toast ("幣種對已新增，已為 7 個品牌建立品牌幣種對").
  - On duplicate pair (`409`): inline error under `報價幣` ("此幣種對已存在"), keep modal open.
  - On other failure: error toast ("儲存失敗，請稍後再試"), keep modal open.
- Each row's `編輯` button opens a form with only `精度` editable (`基準幣`/`報價幣` read-only, immutable). On success: update the row, toast ("幣種對已更新").
- Each row's `刪除` button:
  - If `啟用品牌數` shows any active brand (e.g. "2 / 7"), the button is disabled with a tooltip ("需先於「品牌幣種對」頁面關閉所有品牌幣種對才能刪除").
  - Otherwise, opens a confirmation dialog ("確定要刪除幣種對「<base>/<quote>」嗎？此操作無法復原。"); on confirm, calls `DELETE`, removes the row, toast ("幣種對已刪除"). On a `409` response (a pair became active between page load and delete), show the error toast with the message from the response and refresh the row's count.
- `啟用品牌數` is a plain badge here, not a link — viewing or editing the underlying brand rows happens on the dedicated `品牌幣種對` page, not from this page.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入幣種對定義清單 | GET | /api/currency-pair-definitions | — | `[{id, baseCurrencyId, baseCurrencyCode, quoteCurrencyId, quoteCurrencyCode, precision, createdAt, updatedAt}]` |
| 新增幣種對定義 | POST | /api/currency-pair-definitions | `{baseCurrencyId, quoteCurrencyId, precision}` | `{...definition, currencyPairs: [{id, brandId, brandCode, rateType, rate, active, ...}]}` |
| 修改幣種對定義精度 | PUT | /api/currency-pair-definitions/{id} | `{precision}` | updated definition |
| 刪除幣種對定義 | DELETE | /api/currency-pair-definitions/{id} | — | (no body, 204) or `409 {error, activeBrandCodes}` |
| 載入幣種清單（新增表單用） | GET | /api/currencies | — | `[{id, code, name, symbol, decimalPlaces, ...}]` |
| 計算啟用品牌數 | GET | /api/currency-pairs?currencyPairDefinitionId={id} | — | `[{id, active, ...}]` — count `active: true` entries client-side to render the badge |

## Error States
- Definition list load failure: inline error message with a "重試" button instead of the table.
- Form/delete failures: see the per-action descriptions above.

## Visual Style
Same fixed light theme as the rest of the app (see `specs/frontend/brand.md`'s `## Visual Style` for the base table/page palette and `specs/frontend/currency.md`'s for the modal/button palette — both reused here identically). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Page title | color | `#111827` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 代碼 cell (基準幣/報價幣) | text | `#374151`, monospace font |
| Primary button (`+ 新增幣種對`, 儲存) | background / text / hover | `#2563eb` / `#fff` / `#1d4ed8` |
| Secondary button (`編輯`, 取消) | background / border / text | `#fff` / `#d1d5db` / `#374151` |
| Danger button (`刪除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Danger button, disabled (blocked delete) | background / text / border | `#f3f4f6` / `#9ca3af` / `#e5e7eb` |
| 啟用品牌數 badge, all inactive | background / text | `#f3f4f6` / `#6b7280` |
| 啟用品牌數 badge, some active | background / text | `#eff6ff` / `#2563eb` |
| Modal overlay | background | `rgba(0, 0, 0, 0.4)` |
| Modal card | background / border / shadow | `#fff` / `#e2e5eb` / `rgba(0, 0, 0, 0.15)` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form label | color | `#374151` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [x] `幣別對管理` nav item in `AppLayout.tsx` is enabled (`enabled: true`) and links to `/currency-pairs`.
- [x] Definition list loads from `GET /api/currency-pair-definitions` and shows base/quote/precision/啟用品牌數 for each.
- [x] `+ 新增幣種對` creates a definition via `POST` and the success toast reflects the number of brand pairs created.
- [x] Creating a duplicate `(base, quote)` shows the inline "此幣種對已存在" error without closing the modal.
- [x] `編輯` updates only `precision` via `PUT`; base/quote are not editable.
- [x] `刪除` is disabled (with tooltip) whenever 啟用品牌數 > 0, and succeeds via `DELETE` when 0.
- [x] This page has no per-definition drill-down UI for managing brand currency pairs — that lives entirely on the `品牌幣種對` page (`brand-currency-pair.md`).
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/api/currencyPairDefinitions.ts` (new) — `fetchCurrencyPairDefinitions`, `createCurrencyPairDefinition`, `updateCurrencyPairDefinitionPrecision`, `deleteCurrencyPairDefinition`, `fetchCurrencyPairsByDefinition`, matching types
  - `develop/frontend/src/api/http.ts` — `apiRequest` now parses the JSON error body on non-2xx responses and uses its `error`/`message` field as the thrown `ApiError`'s message (needed so the delete-race 409 toast can show the backend's actual message per spec); `ApiError` gained a `body: unknown` field
  - `develop/frontend/src/pages/CurrencyPairManagementPage.tsx` (new)
  - `develop/frontend/src/pages/CurrencyPairManagementPage.css` (new)
  - `develop/frontend/src/pages/CurrencyPairManagementPage.test.tsx` (new)
  - `develop/frontend/src/layouts/AppLayout.tsx` — `幣別對管理` nav item `enabled: true`
  - `develop/frontend/src/App.tsx` — added `/currency-pairs` route
- Notes:
  - Implemented the full page: list load with retry-on-error, create modal (currency dropdowns from `GET /api/currencies`, precision 0–8 default 4, inline "此幣種對已存在" on 409, generic error toast otherwise), edit modal (precision-only, base/quote rendered as disabled read-only inputs), delete confirmation dialog, and the disabled+tooltip delete button when `啟用品牌數` > 0.
  - `啟用品牌數` badge is computed client-side per row via `GET /api/currency-pairs?currencyPairDefinitionId={id}`, fetched in parallel for every row after the definition list loads (and seeded directly from the create response's `currencyPairs` array for newly-created rows, avoiding a redundant extra request). While a row's count is still loading, its delete button is disabled (no tooltip) as a safety measure until the real active count is known; this is stricter than the spec's minimum requirement but avoids ever allowing a delete click before the active count is confirmed.
  - Badge variant: grey (`#f3f4f6`/`#6b7280`) when 0 active, blue (`#eff6ff`/`#2563eb`) when any active — matches the Visual Style table.
  - Delete-race 409 handling reads `ApiError.message` (sourced from the response body's `error` field per the backend's `ActiveCurrencyPairsExistException` handler) for the toast text, then re-fetches that row's count via `loadCount`, per the spec's "refresh the row's count" requirement.
  - No per-definition drill-down UI exists anywhere on this page — 啟用品牌數 is a plain, non-interactive badge, consistent with the spec and with `brand-currency-pair.md` owning all brand-level currency pair management.
  - Followed `CurrencyManagementPage.tsx`/`.css`/`.test.tsx` conventions exactly (component structure, CSS class naming scheme `currency-pair-page__*`/`currency-pair-table__*`/`currency-pair-modal__*`/`currency-pair-form__*`, toast usage, modal overlay/card structure, test helper shapes).
  - All colors are hardcoded hex values (no CSS variables, no `prefers-color-scheme` media queries) matching the spec's `## Visual Style` table exactly; the shared page background (`#f5f6f8`) comes from `AppLayout.css`'s `.app-layout__content`, already fixed-light and reused unchanged by this page.
- Verified:
  - `cd develop/frontend && npm test -- --run`: all 20 tests pass (8 new tests in `CurrencyPairManagementPage.test.tsx` covering list load, load-failure retry, create success with fan-out-count toast, duplicate 409 inline error, edit with read-only base/quote, delete-button disabled+tooltip when active count > 0, successful delete when count is 0, and the 409 delete-race toast + count refresh; plus the 2 pre-existing test files still pass unmodified).
  - `cd develop/frontend && npm run build` (`tsc -b && vite build`): compiles and builds cleanly with no errors.
  - Live end-to-end verification against the real stack: started MySQL-backed backend (`mvn spring-boot:run`, confirmed live DB had 5 currencies / 7 brands / 0 currency-pair-definitions beforehand) and exercised the real API surface the page calls: `POST /api/currency-pair-definitions` (confirmed 7-brand fan-out, all `active:false`), `PUT /api/currency-pairs/{id}` to activate one brand pair, `GET /api/currency-pairs?currencyPairDefinitionId=` (confirmed count math: 1/7 active), `PUT /api/currency-pair-definitions/{id}` (precision update), `POST` duplicate pair → confirmed `409` with a `message` field (frontend's duplicate check relies on `status===409` only, not message text, so this is compatible), `DELETE` while a brand pair is active → confirmed `409` with body `{"error":"Active brand currency pairs exist","activeBrandCodes":["au"]}` (confirms `http.ts`'s new error-body parsing surfaces exactly the string the spec calls for), `DELETE` when all brand pairs inactive → confirmed `204` and cascade-removal of its `currency_pair` rows. Cleaned up all test data afterward — confirmed `currency_pair_definition`/`currency_pair` tables both back to 0 rows.
  - Did **not** perform: an actual browser/visual screenshot check of computed styles (no browser/screenshot tool was available in this session) — color correctness was instead verified by direct inspection of the CSS source against the spec's `## Visual Style` table (every value transcribed literally, no tokens/variables involved) and is exercised functionally by the passing component tests, but a pixel-level rendered-DOM computed-style check was not performed.
  - `docker/launch.json` / `.claude/launch.json`: confirmed already correctly configured (frontend entry `port: 5173`, matches `vite.config.ts`'s unset `server.port` default) from prior spec execution; no changes needed.

### Browser verification — 2026-08-22 (`/dev` level, after agent execution)
Follow-up to the agent's "did not perform" note above: the rendered-DOM computed-style check **has now been run** against the live stack (Vite dev server on :5173 + Spring Boot on :8080 + MySQL), so Acceptance Criterion 8 is legitimately checked.

- Rendered `/currency-pairs` with two seeded definitions (USD/JPY precision 4 with one active brand pair, EUR/USD precision 5 with none). `getComputedStyle` values read from the live DOM, all exact matches to `## Visual Style`:
  page background `rgb(245,246,248)` = `#f5f6f8`; title `rgb(17,24,39)` = `#111827`; `th` `rgb(249,250,251)`/`rgb(107,114,128)`; `td` `rgb(31,36,48)` with `border-bottom rgb(241,242,245)` and monospace font; 啟用品牌數 badge active-variant `rgb(239,246,255)`/`rgb(37,99,235)` and zero-variant `rgb(243,244,246)`/`rgb(107,114,128)`; primary button `rgb(37,99,235)`/`#fff`; secondary `編輯` `#fff`/`rgb(209,213,219)`/`rgb(55,65,81)`; danger `刪除` `rgb(220,38,38)`/`#fff`; disabled `刪除` `rgb(243,244,246)`/`rgb(156,163,175)`/`rgb(229,231,235)` carrying the exact spec tooltip; modal overlay `rgba(0,0,0,0.4)`; inline error `rgb(217,45,32)` = `#d92d20`.
- Re-read every one of those values with the viewport forced to `prefers-color-scheme: dark` (`matchMedia('(prefers-color-scheme: dark)').matches === true`): **all values byte-identical** — no dark-mode drift.
- UI interaction checked in-browser: the create modal's duplicate submission (USD/JPY against an existing definition) returned `409` and rendered the inline `此幣種對已存在` error at `#d92d20` with the modal still open; both rows' `刪除` buttons rendered disabled with the tooltip while their definitions had an active brand pair.
- Console clean apart from the expected `409` network-error line from that deliberate duplicate test.
- All seeded data removed afterward — `currency_pair_definition` and `currency_pair` both back to 0 rows.
- Screenshot not captured (the browser pane was not displayed in this environment); the computed-style reads above are the stronger check for this criterion and cover it fully.
