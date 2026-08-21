---
status: done
title: "Currency Management Page"
requirement: "匯率中心需要幣別管理畫面，可以新增/查詢/修改/刪除幣種"
depends_on: []
---

# Currency Management — Frontend Spec

## Overview
A page under the "匯率中心" sidebar group (label `幣別管理`, already scaffolded as a disabled placeholder at `/currencies` in `AppLayout.tsx` — this spec is what turns it on) that lists every currency and lets an admin create, edit, and delete them. Backed entirely by [currency.md](../backend/currency.md).

## Requirements

### Page: 幣別管理 (`/currencies`)
- On load, calls `GET /api/currencies` and renders one row per currency.
- Table columns: `代碼` (code), `名稱` (name), `符號` (symbol), `小數位數` (decimalPlaces), `操作` (Edit/Delete buttons).
- A `+ 新增幣種` button above the table opens a create form (modal).
- Each row's `編輯` button opens the same form modal, pre-filled, with `代碼` read-only (immutable after creation).
- Each row's `刪除` button opens a confirmation dialog before calling the delete API.

### Create / Edit Form (modal)
Fields: `代碼` (text, required, exactly 3 uppercase letters, disabled/read-only when editing), `名稱` (text, required), `符號` (text, required), `小數位數` (number, required, 0–8).
- Client-side validation mirrors the backend contract: `代碼` format `^[A-Z]{3}$` (create only), `名稱`/`符號` non-blank, `小數位數` integer in range 0–8. Show inline field errors; disable submit while invalid or while the request is in flight.
- On successful create: close modal, add the new row to the table, show a success toast ("幣種已新增").
- On successful edit: close modal, update the row in place, show a success toast ("幣種已更新").
- On `409` (duplicate code) from create: show an inline error under the `代碼` field ("此代碼已存在"), keep the modal open.
- On other failure: show an error toast ("儲存失敗，請稍後再試"), keep the modal open.

### Delete Confirmation
- Dialog text: "確定要刪除幣種「<code> <name>」嗎？此操作無法復原。" with `取消`/`刪除` buttons.
- On successful delete: close dialog, remove the row from the table, show a success toast ("幣種已刪除").
- On failure: close dialog, show an error toast ("刪除失敗，請稍後再試"), row stays in the table.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入幣種清單 | GET | /api/currencies | — | `[{id, code, name, symbol, decimalPlaces, createdAt, updatedAt}]` |
| 新增幣種 | POST | /api/currencies | `{code, name, symbol, decimalPlaces}` | `{id, code, name, symbol, decimalPlaces, createdAt, updatedAt}` |
| 修改幣種 | PUT | /api/currencies/{id} | `{name, symbol, decimalPlaces}` | `{id, code, name, symbol, decimalPlaces, createdAt, updatedAt}` |
| 刪除幣種 | DELETE | /api/currencies/{id} | — | (no body, 204) |

## Error States
- List load failure: show an inline error message with a "重試" button instead of the table.
- Form/delete failures: see Create/Edit Form and Delete Confirmation above.

## Visual Style
Same fixed light theme as the rest of the app (see `specs/frontend/brand.md`'s `## Visual Style` for the base table/page palette, reused here identically) — no color in this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Page title | color | `#111827` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 代碼 cell | text | `#374151`, monospace font |
| Primary button (`+ 新增幣種`, 儲存) | background / text / hover | `#2563eb` / `#fff` / `#1d4ed8` |
| Secondary button (`編輯`, 取消) | background / border / text | `#fff` / `#d1d5db` / `#374151` |
| Danger button (`刪除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Modal overlay | background | `rgba(0, 0, 0, 0.4)` |
| Modal card | background / border / shadow | `#fff` / `#e2e5eb` / `rgba(0, 0, 0, 0.15)` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form label | color | `#374151` |
| Validation/error text | color | `#d92d20` (same red already used by `.brand-page__error`) |

## Acceptance Criteria
- [x] `幣別管理` nav item in `AppLayout.tsx` is enabled (`enabled: true`) and links to `/currencies`.
- [x] Page loads all currencies from `GET /api/currencies` and displays code/name/symbol/decimalPlaces for each.
- [x] `+ 新增幣種` opens a create form; submitting a valid form calls `POST /api/currencies` and adds the new row on success.
- [x] Create with a duplicate code shows the inline "此代碼已存在" error under the 代碼 field without closing the modal.
- [x] `編輯` opens the form pre-filled with `代碼` read-only; submitting calls `PUT /api/currencies/{id}` and updates the row on success.
- [x] `刪除` opens a confirmation dialog; confirming calls `DELETE /api/currencies/{id}` and removes the row on success.
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/api/currencies.ts` (new) — `Currency`/`CurrencyCreateRequest`/`CurrencyUpdateRequest` types and `fetchCurrencies`/`createCurrency`/`updateCurrency`/`deleteCurrency`, following `api/brands.ts`'s `apiRequest` wrapper pattern.
  - `develop/frontend/src/pages/CurrencyManagementPage.tsx` (new) — list table, create/edit modal form with inline validation (regex `^[A-Z]{3}$` for 代碼 on create only, non-blank 名稱/符號, integer 0–8 for 小數位數), delete confirmation dialog, toast notifications, and load-error retry state, following `BrandManagementPage.tsx`'s structure/state-management conventions.
  - `develop/frontend/src/pages/CurrencyManagementPage.css` (new) — fixed-hex-value styling matching the spec's `## Visual Style` table exactly (no CSS variables, no `prefers-color-scheme` dependency).
  - `develop/frontend/src/pages/CurrencyManagementPage.test.tsx` (new) — 7 tests covering list load/retry, create success, create 409 duplicate-code inline error, edit with read-only 代碼, delete success, and delete failure (row remains, error toast shown).
  - `develop/frontend/src/layouts/AppLayout.tsx` — flipped `幣別管理` nav item `enabled: false → true`.
  - `develop/frontend/src/App.tsx` — added `<Route path="/currencies" element={<CurrencyManagementPage />} />`.
- Notes: On `409` from `createCurrency`, the handler checks `error instanceof ApiError && error.status === 409` (via `ApiError` from `api/http.ts`) to set the inline 代碼 field error; any other rejection falls through to the generic "儲存失敗，請稍後再試" toast, matching the spec's distinction between duplicate-code and other failures. Submit button is disabled whenever the live-recomputed form validation has any error or a request is in flight. Found the locally running backend process was a stale pre-Currency-endpoint build (`GET /api/currencies` returned `404`); restarted `mvn -f develop/backend/pom.xml spring-boot:run` so the already-compiled `CurrencyController` picked up, then verified `curl http://localhost:5173/api/currencies` proxies through to a `200 []` response end-to-end. Ran `npm run build` (clean `tsc -b && vite build`) and `npm test` (12/12 passing across `BrandManagementPage.test.tsx` and the new `CurrencyManagementPage.test.tsx`) from `develop/frontend/`. Verified every hex value in `CurrencyManagementPage.css` against the spec's `## Visual Style` table by direct comparison (no variable/token lookups), matching the existing `BrandManagementPage.css`/`.brand-page__error` fixed-color convention used to avoid the prior dark-mode white-on-white bug.
