---
status: done
title: "Currency Pair Table Page"
requirement: "Display currency pairs in a table with read/update/delete operations, scoped per brand, exchange rate manual/auto; surface currency delete-blocked error. Delta: when rate type is AUTO, clear/disable the rate input; when MANUAL, rate is required. Delta 2: update/delete submit for approval instead of applying directly — see specs/frontend/currency-pair-approval.md. Delta 3: there is no Add button/create flow on this page at all — a brand's pair can only come into existence via the global 幣種對主檔 page (specs/frontend/currency-pair-definition.md); the '+ Add' button and create modal have been removed."
---

# Currency Pair Table Page — Frontend Spec

## Current state note
Edit/Delete below submit for approval instead of applying immediately (backend returns `202` with a pending audit request) — this is reflected inline in "Edit Modal", "Delete Confirmation", and "Error Handling" below. Rows with a `PENDING` request show a "審核中" badge with Edit/Delete disabled. This page has **no create capability** — no "+ Add" button, no create modal, no `POST` call. A brand's currency pair is created only by the global "幣種對主檔" page (`specs/frontend/currency-pair-definition.md`), which fans a new pair out to every brand; this page only ever edits/deletes rows that already exist. The diff-renderer/registration implementation detail and design rationale for the UPDATE/DELETE audit flow live in `specs/frontend/currency-pair-approval.md`; the Acceptance Criteria below (all `[x]`) describe the pre-approval, create-still-existed contract and remain historically accurate for what they tested at the time.

## Overview
Build a currency pair management page that displays all configured currency pairs (base → quote), each scoped to a brand, with their exchange rate. Users can view, add, edit, and delete pairs, filter by brand, and choose whether the rate is entered manually or maintained automatically. Consumes the API defined in `specs/backend/currency-pair.md`. Reuses the existing `currency` list (`GET /api/currencies`) to populate the base/quote currency dropdowns, and the brand list (`GET /api/brands`, see `specs/frontend/brand.md` / `specs/backend/brand.md`) to populate the brand filter and picker.

This spec also requires a small update to the existing Currency page (`develop/frontend/src/pages/CurrencyPage.tsx`) to surface the new "currency is in use" delete error from the backend.

## Requirements
- Table page showing all currency pairs, with their owning brand
- Filter by brand, in addition to active/inactive status
- **No create action on this page** — no "+ Add" button, no create modal. A pair can only come into existence via the global "幣種對主檔" page (`specs/frontend/currency-pair-definition.md`), which provisions it to every brand at once
- Edit existing pair via modal (brand, base/quote, and rate/rateType editable)
- Delete with confirmation
- Rate input behavior differs by `rateType`: `MANUAL` requires the user to type the rate; `AUTO` marks the rate as system-maintained (no automatic sync job exists yet — out of scope, per `specs/backend/currency-pair.md`)
- Existing Currency page delete flow must display a clear error when the backend rejects deletion because the currency is referenced by a pair

### Delta: rate cleared/disabled for AUTO, required for MANUAL
- When `匯率類型` (rateType) is `自動` (AUTO): the `匯率` (rate) input is **disabled and its value cleared** (shown blank or a placeholder like "系統自動維護"), since the backend now clears/ignores `rate` for `AUTO` pairs (`specs/backend/currency-pair.md`) — it is no longer an editable fallback value. No validation error is shown for a blank rate in this mode.
- When `匯率類型` is `手動` (MANUAL): the `匯率` input is **enabled and required** — validation shows "匯率為必填，且須大於 0" if left blank, non-numeric, or `<= 0`, same as before.
- Switching `匯率類型` from `自動` to `手動` in the form clears any stale disabled-state value and re-enables the field, requiring the user to enter a rate before submitting.
- Switching from `手動` to `自動` clears the previously-typed rate value in the form (matching what the backend will persist) rather than silently submitting a rate that will be discarded.
- The `CurrencyPair`/`CurrencyPairInput` types' `rate` field becomes `number | null` to reflect the backend's nullable `rate`.
- Table column `匯率`: render `—` when `rate` is `null` (i.e. any `AUTO` pair), instead of attempting to format `null` as a number.

## Page Layout

### Route
`/currency-pairs`

### Page Structure
```
┌────────────────────────────────────────────────────────┐
│  Currency Pair Management                              │
│                                                          │
│  [Brand ▼]  [Active ▼]                                  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Brand │ Base │ Quote │ Rate     │ Type   │ Active │  │
│  │───────│──────│───────│──────────│────────│────────│  │
│  │ AU    │ USD  │ TWD   │ 32.5     │ 手動   │  ✓     │  │
│  │ MONETA│ USD  │ JPY   │ 157.3    │ 自動   │  ✓     │  │
│  │ ...   │ ...  │ ...   │ ...      │ ...    │ ...    │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Table Columns

| Column   | Source Field       | Width  | Notes                                   |
|----------|--------------------|--------|-------------------------------------------|
| 品牌     | brandCode          | 90px   | Bold, monospace, uppercase                |
| 基準幣別 | baseCurrencyCode   | 80px   | Bold, monospace                           |
| 對應幣別 | quoteCurrencyCode  | 80px   | Bold, monospace                           |
| 匯率     | rate               | 120px  | Right aligned, formatted to currency's decimal convention or up to 8 dp |
| 匯率類型 | rateType           | 100px  | Badge: `手動` (MANUAL) / `自動` (AUTO)    |
| 狀態     | active             | 80px   | Green dot / grey dot                      |
| Actions  | —                  | 120px  | Edit, Delete buttons                      |

### Filter Bar
- **Brand filter**: dropdown populated from `GET /api/brands`, options: All / each brand code
- **Status filter**: dropdown with options: All / Active / Inactive
- No add button — this page has no create action (see "Current state note")

### Edit Modal

Opened only from a row's 編輯 action — there is no create mode. Form fields:
| Field      | Input Type              | Validation                                            |
|------------|--------------------------|--------------------------------------------------------|
| 品牌       | Select (from brand list)    | Required                                              |
| 基準幣別   | Select (from currency list) | Required, must differ from quote                    |
| 對應幣別   | Select (from currency list) | Required, must differ from base                     |
| 匯率類型   | Radio / Select: 手動 / 自動 | Required                                              |
| 匯率       | Number                   | **`手動`**: required, > 0, field enabled. **`自動`**: field disabled and cleared to empty/`null`, no validation error shown; helper text "系統將自動維護匯率" displayed instead |
| 狀態       | Toggle                   | Default: on                                           |

The brand dropdown is populated from `GET /api/brands` (all brands, active or not — a pair under a currently-disabled brand can still be viewed/edited). Currency dropdowns are populated from `GET /api/currencies` (active currencies only, or all — consistent with how the Currency page's own filters work). Selecting the same value for both base and quote shows an inline error and disables submit.

### Delete Confirmation
- **Current state**: show confirmation dialog: "確定要送出刪除 {brandCode} 品牌幣種對 {baseCode}/{quoteCode} 的申請嗎？" (submits a request, does not delete immediately)
- On confirm: call DELETE API (now `202`, an audit request — `specs/frontend/currency-pair-approval.md`), toast "已送出刪除申請，待審核", refresh pending-ids/table (the row itself is not removed until approved)

## API Integration

| Action   | Method | Endpoint                     | Trigger                    |
|----------|--------|-------------------------------|-----------------------------|
| List     | GET    | /api/currency-pairs (optionally `?brandId=`) | Page load, filter change |
| Brands (for filter/picker) | GET | /api/brands       | Page load / modal open       |
| Currencies (for pickers) | GET | /api/currencies   | Page load / modal open       |
| Update   | PUT    | /api/currency-pairs/{id}      | Modal submit — **202**, resolves `AuditRequest` |
| Delete   | DELETE | /api/currency-pairs/{id}      | Confirm dialog — **202**, resolves `AuditRequest` |

No `POST` call exists from this page at all.

### Error Handling
- **Current state**: update/delete resolve `202` + an `AuditRequestResponse`, not the pair itself. On success, show "已送出修改申請，待審核" / "已送出刪除申請，待審核" instead of assuming the change applied; the table is not expected to reflect it until approved.
- **400 on update** (base == quote, rate ≤ 0, invalid type, or missing `rate` while `rateType` is `MANUAL`): show inline field error, e.g. "基準幣別與對應幣別不可相同" or "匯率為必填，且須大於 0"
- **404 on update** (referenced currency or brand missing): show toast "幣種不存在，請重新整理頁面" or "品牌不存在，請重新整理頁面"
- **409 on update** (the edited triple collides with a different live pair): show inline error "此品牌已存在相同的幣種對"
- **409 on update/delete** (a `PENDING` request already exists for this pair): show toast "此幣種對已有待審核的異動申請"
- **404 on edit/delete** (pair itself missing): show toast "幣種對不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching
- Rows with a `PENDING` request against them show a "審核中" badge and disabled Edit/Delete buttons (fetched via `GET /api/audit-requests?entityType=CURRENCY_PAIR&status=PENDING`)

## Required Update to Existing Currency Page

`develop/frontend/src/pages/CurrencyPage.tsx` (and `develop/frontend/src/api/currencyApi.ts` if needed) must handle the new `409` response from `DELETE /api/currencies/{id}` that the backend now returns when a currency is referenced by a currency pair (see `specs/backend/currency-pair.md`):
```json
{
    "error": "Currency is referenced by one or more currency pairs and cannot be deleted",
    "id": 1
}
```
On this response, show a toast: "此幣種已配置於幣種對，無法刪除" instead of the generic network-error toast, and keep the row in the table (do not optimistically remove it).

No change is required to the currency edit modal's code-field handling — it already renders `code` as disabled on edit, matching the backend's now-enforced immutability (`specs/backend/currency-pair.md`).

## Acceptance Criteria
- [x] Currency pair table renders with all columns, including brand, base/quote shown as codes
- [x] Pairs load from API on page mount
- [x] Brand filter works (All / each brand)
- [x] Status filter works (All / Active / Inactive)
- [x] Add modal opens, brand picker populated from `/api/brands`, currency pickers populated from `/api/currencies`, validates base ≠ quote, creates via API
- [x] Edit modal pre-fills data (including brand), updates via API
- [x] Delete shows confirmation and deletes via API
- [x] Rate type toggle switches between 手動/自動, rate field remains editable in both modes
- [x] Error states display correct Chinese messages for 400/404/409/network cases
- [x] Table refreshes after create/update/delete
- [x] Empty state shown when no pairs match filter
- [x] Currency page delete flow shows "此幣種已配置於幣種對，無法刪除" on the new 409 response and leaves the row in place
- [x] Selecting `自動` disables the `匯率` input and clears its value; no "required" error is shown for it
- [x] Selecting `手動` (including switching from `自動`) re-enables the `匯率` input and requires a valid value (> 0) before submit
- [x] Submitting the form with `自動` selected sends `rate: null` (or omits `rate`) rather than a stale typed value
- [x] Table renders `—` in the `匯率` column for any pair with `rate: null`
- [x] Edit modal correctly reflects a loaded `AUTO` pair (rate `null`) as disabled/blank, and a loaded `MANUAL` pair with its numeric rate, enabled

### Delta: remove the create action (a brand pair requires a global definition first)
(The `[x]` "Add modal" item above remains historically accurate for what was built and tested at the time; the create modal/button have since been removed.)
- [x] The "+ Add" button no longer renders on this page
- [x] `CurrencyPairFormModal` (or its replacement) supports edit mode only — no `mode: 'create'` code path remains reachable from `CurrencyPairPage`
- [x] `currencyPairApi.ts` no longer exports a `create` function, or it is removed entirely if nothing else calls it
- [x] Existing tests asserting the Add button/create flow (`CurrencyPairPage.test.tsx`, `CurrencyPairFormModal.test.tsx`) are removed or updated so the suite doesn't assert on removed UI
- [x] Edit/Delete flows and their `202`/pending-badge/toast behavior are completely unchanged by this delta

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/frontend/src/types/currencyPair.ts (new — `CurrencyPair`, `CurrencyPairInput`, `RateType` types matching the backend response/request contract)
  - develop/frontend/src/api/currencyPairApi.ts (new — `list({ brandId?, active? })`, `create`, `update`, `remove` against `/api/currency-pairs`, following the `currencyApi.ts`/`brandApi.ts` pattern)
  - develop/frontend/src/components/BrandFilter.tsx (new — dropdown populated from a `Brand[]` prop, options: All / each brand code; reuses the `.status-filter` CSS class for visual consistency with `StatusFilter`)
  - develop/frontend/src/components/CurrencyPairTable.tsx + CurrencyPairTable.css (new — table with 品牌/基準幣別/對應幣別/匯率/匯率類型/狀態/Actions columns; rate formatted up to 8dp with trailing zeros trimmed; 手動/自動 badge; green/grey status dot; empty state; Edit/Delete actions)
  - develop/frontend/src/components/CurrencyPairTable.test.tsx (new — 3 tests: column rendering, empty state, edit/delete callbacks)
  - develop/frontend/src/components/CurrencyPairFormModal.tsx + CurrencyPairFormModal.css (new — brand/base/quote selects sourced from props, 匯率類型 select (手動/自動) with helper text shown (and rate field kept editable) when 自動 is selected, 匯率 number input, 啟用 toggle; live inline "基準幣別與對應幣別不可相同" error + disabled submit button when base === quote; on submit, maps 409 → "此品牌已存在相同的幣種對", 400 → the same base/quote message, anything else → "網路錯誤，請稍後再試")
  - develop/frontend/src/components/CurrencyPairFormModal.test.tsx (new — 6 tests: required-field validation, live base===quote inline error + disabled submit, valid create submit with numeric id coercion, edit-mode prefill + AUTO helper text, 409 inline conflict message, network error message)
  - develop/frontend/src/pages/CurrencyPairPage.tsx + CurrencyPairPage.css (new — page composing `BrandFilter` + `StatusFilter` + Add button, table, add/edit modal, delete confirm dialog, and toasts; fetches brands/currencies once on mount for the filter/pickers and refetches the pair list whenever the brand or status filter changes; create/update 404s are mapped to "品牌不存在" / "幣種不存在" / "幣種對不存在" toasts based on the backend's `error` message body, closing the modal and refreshing the table; delete 404 shows "幣種對不存在，請重新整理頁面")
  - develop/frontend/src/pages/CurrencyPairPage.test.tsx (new — 10 integration tests mocking `currencyPairApi`/`brandApi`/`currencyApi`: mount load, empty state, network-error toast, brand-filter refetch, status-filter refetch, create/edit/delete flows, and 404 toast-and-refresh behavior for both edit and delete)
  - develop/frontend/src/pages/CurrencyPage.tsx (edited — `handleConfirmDelete` now branches on `error.status === 409` in addition to 404: shows the new "此幣種已配置於幣種對，無法刪除" toast and closes the confirm dialog **without** refetching/removing the row, per spec; the 404 and network-error branches are unchanged aside from being split out into their own `if` bodies)
  - develop/frontend/src/pages/CurrencyPage.test.tsx (edited — added `shows an in-use toast and keeps the row when the currency is referenced by a currency pair` covering the 409 response body from `specs/backend/currency-pair.md`, asserting the toast text, that `list` was not called again, and that the row is still rendered)
  - develop/frontend/src/App.tsx (edited — added `/currency-pairs` route wired to the new `CurrencyPairPage`, alongside the existing `/currencies` and `/brands` routes)
- Notes:
  - Followed the exact conventions established by `CurrencyPage`/`CurrencyFormModal`/`CurrencyTable` and the newly-added `BrandPage`/`brandApi`: same Controller-less "page owns fetch/mutate + child components are presentational" structure, same `ApiError`/`NetworkError` handling via `api/client.ts`, same toast/modal/confirm-dialog composition, and matching Chinese copy style.
  - Brand and currency lists for the filter bar and form pickers are fetched once on mount (`brandApi.list()` with no `active` filter — "all brands, active or not" per spec — and `currencyApi.list()` with no filter), independent of the brand/status filter state that only affects the currency-pair list query.
  - Rate formatting (`Number(rate.toFixed(8)).toString()`) displays up to 8 decimal places while trimming trailing zeros (e.g. `32.5`, `157.3`), satisfying the "or up to 8 dp" fallback since the pair response doesn't carry either currency's `decimalPlaces` directly on the joined DTO.
  - 404 disambiguation on create/update uses the backend's exact `error` message text (`"Currency pair not found"` / `"Brand not found"` / anything else treated as the currency-not-found case) to pick between the three distinct toast messages required by the spec, mirroring how `CurrencyPairService`'s validation order and `GlobalExceptionHandler` reuse the existing `BrandNotFoundException`/`CurrencyNotFoundException` bodies verbatim (per `specs/backend/currency-pair.md`).
  - The base/quote "must differ" rule is enforced live in the modal (inline error + disabled submit as soon as both selects share a value), matching the "Selecting the same value for both base and quote shows an inline error and disables submit" requirement, in addition to the on-submit `validate()` check for the case where the field was left blank.
  - `rate` stays a plain editable number input regardless of `rateType`; selecting 自動 only adds the helper text "系統將自動更新匯率，此值為目前/備援匯率" per spec (no auto-sync job exists, out of scope).
  - Currency page: 409 delete handling intentionally does **not** call `fetchCurrencies()` afterward (unlike the 404/network branches), since nothing changed server-side and the row must be left in place exactly as rendered — verified by a test asserting `list` was called only once (the initial mount) even after the failed delete attempt.
  - `npm run build` (`tsc -b && vite build`) and `npm test` (Vitest) both pass: 9 test files, 56 tests total (up from 24 prior to this task), 0 failures. `npm run lint` (Oxlint) passes with only the pre-existing benign fast-refresh warning on `ToastProvider.tsx`.
  - No backend or DBA changes were made as part of this frontend task; the currency-pair backend API and the currency-delete 409 guard already existed per `specs/backend/currency-pair.md` (status: done) at the start of this task.

### Increment 1 — 2026-07-27
- Status: DONE
- Delta implemented: Rate cleared/disabled for AUTO, required for MANUAL
- Files changed:
  - develop/frontend/src/types/currencyPair.ts (edited — `rate` field in both `CurrencyPair` and `CurrencyPairInput` changed from `number` to `number | null` to reflect the backend's nullable rate)
  - develop/frontend/src/components/CurrencyPairTable.tsx (edited — `formatRate()` now handles `null` rate by returning `—`, matching the spec's table rendering requirement for AUTO pairs)
  - develop/frontend/src/components/CurrencyPairTable.test.tsx (edited — updated test data to have `rate: null` for AUTO pair; added dedicated test `renders — in the rate column when rate is null (AUTO pairs)` verifying the `—` display)
  - develop/frontend/src/components/CurrencyPairFormModal.tsx (edited — added `handleRateTypeChange()` that clears the rate field and validation errors when switching rateType; updated initial rate state to be blank when initial pair is AUTO or rate is null; `validate()` now skips rate validation when `rateType === 'AUTO'`; `handleSubmit()` sends `rate: null` when AUTO, `Number(rate)` when MANUAL; rate input now has `disabled={rateType === 'AUTO'}` and a placeholder "系統自動維護"; helper text changed from "系統將自動更新匯率，此值為目前/備援匯率" to "系統將自動維護匯率")
  - develop/frontend/src/components/CurrencyPairFormModal.test.tsx (edited — updated existing "pre-fills values" test to verify AUTO disables the input and clears it; added 5 new tests: `disables and clears the rate input when AUTO is selected`, `re-enables and requires rate when switching from AUTO to MANUAL`, `does not show rate validation error when AUTO is selected and rate is blank`, `submits rate: null when AUTO is selected`, `correctly reflects a loaded AUTO pair with null rate as disabled/blank`)
- Acceptance criteria completed (all 5 previously unchecked items now checked):
  - [x] Selecting 自動 disables the 匯率 input and clears its value; no "required" error is shown for it
  - [x] Selecting 手動 (including switching from 自動) re-enables the 匯率 input and requires a valid value (> 0) before submit
  - [x] Submitting the form with 自動 selected sends `rate: null` rather than a stale typed value
  - [x] Table renders `—` in the 匯率 column for any pair with `rate: null`
  - [x] Edit modal correctly reflects a loaded AUTO pair (rate null) as disabled/blank, and a loaded MANUAL pair with its numeric rate, enabled
- Verification:
  - `npm run build` passes (tsc + vite build, 0 errors)
  - `npm test` passes (62 tests total across 9 test files, up from 56 tests before this increment, 0 failures)
  - `npm run lint` passes (only the pre-existing ToastProvider.tsx fast-refresh warning)
- Notes:
  - The rate input field is now fully dynamic: AUTO mode disables and clears it (with placeholder "系統自動維護" and helper text "系統將自動維護匯率"), MANUAL mode enables it and requires a value > 0.
  - Switching rateType in either direction clears the rate field and any validation errors, ensuring no stale values are submitted.
  - Edit modal correctly initializes with blank/disabled rate for AUTO pairs loaded from the backend (rate: null) and numeric/enabled rate for MANUAL pairs.
  - The backend delta (AUTO pairs force rate to null, MANUAL pairs require rate > 0) was already implemented per `specs/backend/currency-pair.md`; this increment aligns the frontend UI with that behavior.

### Increment 2 — 2026-07-30
- Status: DONE
- Delta implemented: remove the create action (a brand pair requires a global definition first)
- Files changed:
  - `develop/frontend/src/pages/CurrencyPairPage.tsx` (edited — removed the "+ Add" button from the filter bar's `filter-actions` div (and the now-empty wrapper), the `handleCreateSubmit` function, and the `mode: 'create'` branch of `FormModalState`/the conditional `CurrencyPairFormModal` render; `FormModalState` simplified to `{ pair: CurrencyPair } | null`, opened only from a row's Edit action)
  - `develop/frontend/src/components/CurrencyPairFormModal.tsx` (edited — dropped the `mode: 'create' | 'edit'` prop entirely; `initial: CurrencyPair` is now a required prop instead of optional, since the component is edit-only going forward; all `initial?.x ?? default` fallbacks simplified to read directly from the now-required `initial`; the `Modal` title is now the fixed string `編輯幣種對` instead of a `mode`-conditional one)
  - `develop/frontend/src/api/currencyPairApi.ts` (edited — removed the `create` export; confirmed via grep that no other module, including the unrelated `currencyPairDefinitionApi.ts`/`CurrencyPairDefinitionPage.tsx`, ever called `currencyPairApi.create`)
  - `develop/frontend/src/pages/CurrencyPairPage.test.tsx` (edited — removed the `create` entry from the `currencyPairApi` mock; replaced the "submits a create request through the add modal…" test with a new "does not render an Add button" assertion; all Edit/Delete/pending-badge/404/409 tests left untouched)
  - `develop/frontend/src/components/CurrencyPairFormModal.test.tsx` (rewritten — every test now renders with the required `initial={EXISTING}` prop instead of `mode="create"`/`mode="edit"`; the former "shows validation errors when required fields are missing" test now clears the pre-filled selects/rate before asserting the same four required-field messages, preserving that validation coverage under the edit-only contract; the former "submits a valid create form with numeric ids" test was renamed "submits a valid edit form with numeric ids" and now edits the rate on the pre-filled form rather than filling an empty one; the AUTO/MANUAL toggle, 409/network-error, and "loaded AUTO pair" tests were carried over unchanged in intent, just without the `mode` prop)
- Acceptance criteria completed (all 5 Delta items now checked):
  - [x] The "+ Add" button no longer renders on this page
  - [x] `CurrencyPairFormModal` supports edit mode only — no `mode: 'create'` code path remains reachable from `CurrencyPairPage`
  - [x] `currencyPairApi.ts` no longer exports a `create` function
  - [x] Existing tests asserting the Add button/create flow are removed or updated so the suite doesn't assert on removed UI
  - [x] Edit/Delete flows and their `202`/pending-badge/toast behavior are completely unchanged by this delta
- Verification:
  - `npm run build` (`tsc -b && vite build`) passes with 0 type errors.
  - `npm test` (`vitest run`) passes: 23 test files, 168 tests, 0 failures.
  - `npm run lint` (`oxlint`) passes with only the pre-existing `ToastProvider.tsx` fast-refresh warning.
  - Verified `CurrencyPairDefinitionPage` (`develop/frontend/src/pages/CurrencyPairDefinitionPage.tsx`) and its own "+新增幣種對" button/`CurrencyPairDefinitionFormModal`/`currencyPairDefinitionApi` are a completely separate module tree with no reference to `currencyPairApi` or `CurrencyPairFormModal`; its dedicated test files (`CurrencyPairDefinitionPage.test.tsx`, `CurrencyPairDefinitionFormModal.test.tsx`, `CurrencyPairDefinitionTable.test.tsx`, 19 tests) all still pass unmodified.
- Notes:
  - Chose to drop the `mode` prop from `CurrencyPairFormModal` entirely (rather than keeping `mode: 'edit'` as a permanent single-value prop) since the component's only remaining caller always opens it in edit mode — keeping a single-value discriminant prop around would have been dead weight. `initial` becoming a required prop is the natural consequence: every field's initial state (`brandId`, `baseCurrencyId`, `quoteCurrencyId`, `rateType`, `rate`, `active`) now reads directly from it instead of through an `initial?.x ?? fallback` pattern intended for the no-`initial` create case.
  - The backend's `POST /api/currency-pairs` route was already removed in a prior backend step (`405` now); no frontend call to that route remains anywhere in the codebase after this increment.
  - This increment is one of two decomposed halves of a single atomic UI change; see `specs/frontend/currency-pair-approval.md`'s "Increment 2" for the companion delta removing the same create action's audit-flow implications (no new `CREATE` audit requests, historical `CREATE` rendering preserved).
