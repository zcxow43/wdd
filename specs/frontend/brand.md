---
status: done
title: "Brand Management Page"
requirement: "Display the fixed set of brands (AU, MONETA, PUG, STAR, UM, VJP, VT) with an enable/disable toggle"
depends_on: []
---

# Brand Management Page — Frontend Spec

## Overview
Build a simple brand management page listing the fixed set of brands, each with a toggle to enable/disable it. Consumes the API defined in `specs/backend/brand.md`. Brands are seeded (not creatable/deletable from the UI), so this page has no "Add" button and no delete action — only a per-row active toggle.

The brand list is also used elsewhere in the app: the Currency Pair page (`specs/frontend/currency-pair.md`) uses `GET /api/brands` to populate its brand filter/picker.

## Requirements
- Table page showing all 7 brands
- Each row has an active/inactive toggle switch that calls the API immediately (no separate save step)
- No create, edit-name, or delete actions — `code` and `name` are read-only, seeded values

## Page Layout

### Route
`/brands`

### Page Structure
```
┌──────────────────────────────────────┐
│  Brand Management                     │
│                                        │
│  ┌──────────────────────────────────┐│
│  │ Code   │ Name   │ Status          ││
│  │────────│────────│─────────────────││
│  │ AU     │ AU     │ [●  ] 啟用      ││
│  │ MONETA │ MONETA │ [●  ] 啟用      ││
│  │ PUG    │ PUG    │ [ ○ ] 停用      ││
│  │ ...    │ ...    │ ...             ││
│  └──────────────────────────────────┘│
└──────────────────────────────────────┘
```

### Table Columns

| Column | Source Field | Width  | Notes                                   |
|--------|--------------|--------|-------------------------------------------|
| 代碼   | code         | 100px  | Bold, monospace, uppercase                |
| 名稱   | name         | auto   |                                            |
| 狀態   | active       | 120px  | Toggle switch, label "啟用"/"停用" beside it |

Toggling immediately calls `PUT /api/brands/{id}` with the new `active` value; on success, update the row in place. On failure, revert the toggle and show a toast.

## API Integration

| Action | Method | Endpoint          | Trigger                     |
|--------|--------|--------------------|-------------------------------|
| List   | GET    | /api/brands        | Page load                     |
| Toggle | PUT    | /api/brands/{id}   | Clicking a row's toggle switch |

### Error Handling
- **404 on toggle**: show toast "品牌不存在，請重新整理頁面", refresh list
- **400 on toggle**: show toast "更新失敗，請稍後再試" (shouldn't normally occur since the toggle always sends a valid boolean)
- **Network error**: show toast "網路錯誤，請稍後再試", revert toggle to previous state
- **Loading state**: show skeleton/spinner while fetching

## Acceptance Criteria
- [x] Brand table renders all 7 brands with code, name, and active toggle
- [x] Brands load from API on page mount
- [x] Toggling a row calls `PUT /api/brands/{id}` and updates state on success
- [x] Toggle reverts and shows a toast on network/API failure
- [x] No add/edit-name/delete controls are present on this page
- [x] Error states display correct Chinese messages

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/frontend/src/types/brand.ts (new — `Brand` type: `id`, `code`, `name`, `active`, `createdAt`, `updatedAt`, matching the backend contract in `specs/backend/brand.md`)
  - develop/frontend/src/api/brandApi.ts (new — `brandApi.list(active?)` against `GET /api/brands` and `brandApi.updateActive(id, active)` against `PUT /api/brands/{id}`, following the same `apiClient` wrapper pattern as `currencyApi.ts`; exported for reuse by the future Currency Pair brand picker per the spec note)
  - develop/frontend/src/components/BrandTable.tsx + BrandTable.css (new — read-only table with 代碼/名稱/狀態 columns; 狀態 column renders an accessible toggle switch, `aria-label="{code} 狀態"`, disabled while that row's toggle request is in flight; empty state "目前沒有品牌資料"; no edit/delete/add controls)
  - develop/frontend/src/components/BrandTable.test.tsx (new — 5 tests: renders all rows with correct 啟用/停用 label, empty state, `onToggle` callback fires with the clicked brand, per-row disabled state while toggling, and confirms no Edit/Delete/+Add buttons are rendered)
  - develop/frontend/src/pages/BrandPage.tsx + BrandPage.css (new — page composing load-on-mount, loading/error/retry states (reusing the same wrapper pattern as `CurrencyPage`), and the optimistic toggle handler: flips the row locally, calls the API, replaces the row with the server response on success, or reverts to the previous value and shows a toast on failure)
  - develop/frontend/src/pages/BrandPage.test.tsx (new — 7 integration tests mocking `brandApi`: initial load, empty state, network-error toast on load failure, successful toggle updates the row, 404 reverts + toast + list refresh, network error on toggle reverts + toast, 400 on toggle shows the "更新失敗" toast without reverting-and-refreshing)
  - develop/frontend/src/App.tsx (edited — added `/brands` route rendering `BrandPage`, alongside the existing `/currencies` route; no default-redirect change)
- Notes:
  - Implemented `/brands` exactly per spec: a read-only table (no add/edit-name/delete) with an immediate-effect toggle switch per row. Toggling is optimistic — the row flips instantly, then on success is replaced with the server's response (so `updatedAt` etc. stay in sync); on failure the row reverts to its prior `active` value.
  - Error handling matches the spec's Chinese copy exactly: 404 → "品牌不存在，請重新整理頁面" (toast + revert + full list refetch, since the backend row may have disappeared/changed), 400 → "更新失敗，請稍後再試" (toast + revert only, no refetch, since the list itself is presumably still valid), network error → "網路錯誤，請稍後再試" (toast + revert). Initial load failure also shows the network-error toast, matching the "Loading state" requirement (a "載入中…" status is shown while fetching, and a "資料載入失敗" message with a retry button on failure — same UX as `CurrencyPage`).
  - Reused existing conventions rather than introducing new patterns: `apiClient`/`ApiError`/`NetworkError` from `api/client.ts`, `useToast` from `ToastProvider`, and the same page-level loading/error/wrapper CSS classes as `CurrencyPage.css` (renamed to `brand-*`). No `Modal`/`ConfirmDialog` were needed since there is no create/edit/delete flow on this page. The toggle switch itself is a new, self-contained component (visually-hidden checkbox + styled track/knob) since no existing toggle-switch UI component existed in the codebase (the currency form's boolean field is a plain checkbox, not a switch).
  - `brandApi` was written as a small, standalone module (not page-specific) precisely so the Currency Pair page's brand picker (per `specs/frontend/currency-pair.md`) can import `brandApi.list()` directly without duplication.
  - 36 tests passing (`npm test` → Vitest): all pre-existing 24 currency tests plus 5 new `BrandTable` tests and 7 new `BrandPage` tests. `npm run build` (`tsc -b && vite build`) and `npm run lint` (Oxlint) both pass cleanly (the sole lint warning is pre-existing and unrelated, in `ToastProvider.tsx`).
  - End-to-end verified against the live backend + MySQL (`wdd-mysql` docker container, already running, plus `mvn spring-boot:run`): started the Vite dev server and used curl through the dev proxy to confirm `GET /api/brands` returns all 7 seeded brands (AU, MONETA, PUG, STAR, UM, VJP, VT, all initially active), that `PUT /api/brands/3` with `{"active": false}` toggles PUG off and is reflected in the subsequent list call, and that `PUT /api/brands/999` returns the expected `404` body — exercising the exact request/response shapes the page's toggle handler depends on. Restored PUG back to `active: true` afterward to leave the seed data unchanged; both the dev server and backend processes were stopped after verification.
