---
status: done
title: "Currency Pair Table Page"
requirement: "Display currency pairs in a table with CRUD operations, scoped per brand, exchange rate manual/auto; surface currency delete-blocked error"
---

# Currency Pair Table Page — Frontend Spec

## Overview
Build a currency pair management page that displays all configured currency pairs (base → quote), each scoped to a brand, with their exchange rate. Users can view, add, edit, and delete pairs, filter by brand, and choose whether the rate is entered manually or maintained automatically. Consumes the API defined in `specs/backend/currency-pair.md`. Reuses the existing `currency` list (`GET /api/currencies`) to populate the base/quote currency dropdowns, and the brand list (`GET /api/brands`, see `specs/frontend/brand.md` / `specs/backend/brand.md`) to populate the brand filter and picker.

This spec also requires a small update to the existing Currency page (`develop/frontend/src/pages/CurrencyPage.tsx`) to surface the new "currency is in use" delete error from the backend.

## Requirements
- Table page showing all currency pairs, with their owning brand
- Filter by brand, in addition to active/inactive status
- Add new pair via modal form; brand, base/quote pickers sourced from `/api/brands` and `/api/currencies`
- Edit existing pair via modal (brand, base/quote, and rate/rateType editable)
- Delete with confirmation
- Rate input behavior differs by `rateType`: `MANUAL` requires the user to type the rate; `AUTO` marks the rate as system-maintained but still allows an editable fallback value (no automatic sync job exists yet — out of scope, per `specs/backend/currency-pair.md`)
- Existing Currency page delete flow must display a clear error when the backend rejects deletion because the currency is referenced by a pair

## Page Layout

### Route
`/currency-pairs`

### Page Structure
```
┌────────────────────────────────────────────────────────┐
│  Currency Pair Management                              │
│                                                          │
│  [Brand ▼]  [Active ▼]                     [+ Add]      │
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
- **Add button**: opens create modal

### Add/Edit Modal

Form fields:
| Field      | Input Type              | Validation                                            |
|------------|--------------------------|--------------------------------------------------------|
| 品牌       | Select (from brand list)    | Required                                              |
| 基準幣別   | Select (from currency list) | Required, must differ from quote                    |
| 對應幣別   | Select (from currency list) | Required, must differ from base                     |
| 匯率類型   | Radio / Select: 手動 / 自動 | Required                                              |
| 匯率       | Number                   | Required, > 0. When `自動` selected, show helper text "系統將自動更新匯率，此值為目前/備援匯率" but keep the field editable |
| 狀態       | Toggle                   | Default: on                                           |

The brand dropdown is populated from `GET /api/brands` (all brands, active or not — a pair under a currently-disabled brand can still be viewed/edited). Currency dropdowns are populated from `GET /api/currencies` (active currencies only, or all — consistent with how the Currency page's own filters work). Selecting the same value for both base and quote shows an inline error and disables submit.

### Delete Confirmation
- Show confirmation dialog: "確定要刪除 {brandCode} 品牌的幣種對 {baseCode}/{quoteCode} 嗎？"
- On confirm: call DELETE API, refresh table

## API Integration

| Action   | Method | Endpoint                     | Trigger                    |
|----------|--------|-------------------------------|-----------------------------|
| List     | GET    | /api/currency-pairs (optionally `?brandId=`) | Page load, filter change |
| Brands (for filter/picker) | GET | /api/brands       | Page load / modal open       |
| Currencies (for pickers) | GET | /api/currencies   | Page load / modal open       |
| Create   | POST   | /api/currency-pairs           | Modal submit                 |
| Update   | PUT    | /api/currency-pairs/{id}      | Modal submit                 |
| Delete   | DELETE | /api/currency-pairs/{id}      | Confirm dialog                |

### Error Handling
- **400 on create/update** (base == quote, rate ≤ 0, invalid type): show inline field error, e.g. "基準幣別與對應幣別不可相同"
- **404 on create/update** (referenced currency or brand missing): show toast "幣種不存在，請重新整理頁面" or "品牌不存在，請重新整理頁面"
- **409 on create/update** (duplicate pair within the same brand): show inline error "此品牌已存在相同的幣種對"
- **404 on edit/delete** (pair itself missing): show toast "幣種對不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching

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
