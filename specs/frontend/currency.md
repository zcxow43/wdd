---
status: done
title: "Currency Table Page"
requirement: "Display currencies in a table with CRUD operations. Delta: currency has no enable/disable concept — remove the status filter, Active column, and Active toggle entirely."
depends_on: []
---

# Currency Table Page — Frontend Spec

## Overview
Build a currency management page that displays all currencies in a table. Users can view, add, edit, and delete currencies. Consumes the API defined in `specs/backend/currency.md`. **Current state: currencies have no active/inactive concept at all** — no status filter, no Active column, no Active toggle in the form. Every currency is simply present (usable) or deleted.

## Requirements
- Table page showing all currencies
- Add new currency via modal/dialog form
- Edit existing currency inline or via modal
- Delete with confirmation
- No status filter and no active/inactive concept anywhere on this page

## Page Layout

### Route
`/currencies`

### Page Structure
```
┌──────────────────────────────────────────────┐
│  幣種管理                                     │
│                                              │
│  [Search...]                  [+ Add]        │
│                                              │
│  ┌──────────────────────────────────────────┐│
│  │ Code │ Name   │ 中文名 │ Symbol │ Actions ││
│  │──────│────────│────────│────────│─────────││
│  │ TWD  │ New..  │ 新台幣 │ NT$    │  ...    ││
│  │ USD  │ Unit.. │ 美元   │ $      │  ...    ││
│  │ ...  │ ...    │ ...    │ ...    │  ...    ││
│  └──────────────────────────────────────────┘│
└──────────────────────────────────────────────┘
```

### Table Columns

| Column         | Source Field   | Width  | Notes                        |
|----------------|----------------|--------|------------------------------|
| Code           | code           | 80px   | Bold, monospace              |
| Name           | name           | auto   |                              |
| 中文名稱       | nameZh         | 120px  | Fallback: dash if empty      |
| Symbol         | symbol         | 80px   | Center aligned               |
| Decimal Places | decimalPlaces  | 100px  | Center aligned               |
| Actions        | —              | 120px  | Edit, Delete buttons         |

### Filter Bar
- No status filter — this page has no active/inactive concept
- **Add button**: opens create modal

### Add/Edit Modal

Form fields:
| Field          | Input Type | Validation                          |
|----------------|------------|-------------------------------------|
| Code           | Text       | Required, 3 uppercase letters, disabled on edit |
| Name           | Text       | Required, max 100                   |
| 中文名稱       | Text       | Optional, max 100                   |
| Symbol         | Text       | Optional, max 10                    |
| Decimal Places | Number     | Required, 0–8                       |

### Delete Confirmation
- Show confirmation dialog: "確定要刪除幣種 {code} 嗎？"
- On confirm: call DELETE API, refresh table

## API Integration

| Action   | Method | Endpoint                | Trigger           |
|----------|--------|-------------------------|--------------------|
| List     | GET    | /api/currencies         | Page load (no filter — always the full list) |
| Create   | POST   | /api/currencies         | Modal submit       |
| Update   | PUT    | /api/currencies/{id}    | Modal submit       |
| Delete   | DELETE | /api/currencies/{id}    | Confirm dialog     |

### Error Handling
- **409 on create**: show "幣種代碼已存在" inline error
- **404 on edit/delete**: show toast "幣種不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching

## Acceptance Criteria
- [x] Currency table renders with all columns
- [x] Currencies load from API on page mount
- [x] Status filter works (All / Active / Inactive)
- [x] Add modal opens, validates, and creates via API
- [x] Edit modal pre-fills data, code field disabled, updates via API
- [x] Delete shows confirmation and deletes via API
- [x] Error states display correct Chinese messages
- [x] Table refreshes after create/update/delete
- [x] Empty state shown when no currencies match filter

### Delta: remove the active/inactive concept
(The `[x]` "Status filter works" item above remains historically accurate for what was built and tested at the time; the filter, column, and toggle have since been removed.)
- [x] No status filter renders on this page
- [x] The table has no Active column
- [x] The Add/Edit modal has no Active toggle
- [x] `types/currency.ts`'s `Currency`/`CurrencyInput` have no `active` field; `StatusFilter` type/component usage is removed from this page specifically (the shared `StatusFilter` component itself stays, since `CurrencyPairPage` still uses it for `currency_pair.active` — do not remove or modify that component)
- [x] `currencyApi.list()` no longer accepts/sends an `active` parameter
- [x] Existing tests asserting the status filter/Active column/toggle (`CurrencyPage.test.tsx`, `CurrencyTable.test.tsx`, `CurrencyFormModal.test.tsx`) are removed or updated so the suite doesn't assert on removed UI
- [x] Add/Edit/Delete flows and their toasts/error handling are completely unchanged by this delta

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/frontend/ (new — scaffolded via `npm create vite@latest . -- --template react-ts`, React 19 + TypeScript 6 + Vite 8)
  - develop/frontend/package.json (deps: react-router-dom; devDeps: vitest, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, jsdom, @vitest/coverage-v8; scripts: `test`, `test:watch`)
  - develop/frontend/vite.config.ts (new — dev server proxy: `/api/*` → `VITE_BACKEND_PROXY_TARGET` (default `http://localhost:8080`), avoids CORS in dev)
  - develop/frontend/vitest.config.ts (new — jsdom environment, setup file, explicit (non-global) test imports)
  - develop/frontend/.env.development, develop/frontend/.env.example (new — `VITE_API_BASE_URL` (empty/relative by default) and `VITE_BACKEND_PROXY_TARGET`, documented in README)
  - develop/frontend/tsconfig.node.json (edited — added vitest.config.ts to include list)
  - develop/frontend/src/vite-env.d.ts (new — ImportMetaEnv typing)
  - develop/frontend/src/types/currency.ts (new — Currency, CurrencyInput, StatusFilter types)
  - develop/frontend/src/api/client.ts (new — generic fetch wrapper, ApiError/NetworkError classes, 204 handling)
  - develop/frontend/src/api/client.test.ts (new — 5 tests)
  - develop/frontend/src/api/currencyApi.ts (new — list/create/update/remove against `/api/currencies`)
  - develop/frontend/src/components/{Modal,ConfirmDialog,ToastProvider,StatusFilter,CurrencyTable,CurrencyFormModal}.tsx + matching `.css` (new)
  - develop/frontend/src/components/CurrencyTable.test.tsx, CurrencyFormModal.test.tsx (new — 4 + 6 tests)
  - develop/frontend/src/pages/CurrencyPage.tsx + CurrencyPage.css (new — page composing filter bar, search, table, add/edit modal, delete confirm, toasts)
  - develop/frontend/src/pages/CurrencyPage.test.tsx (new — 9 integration tests mocking `currencyApi`)
  - develop/frontend/src/App.tsx, src/main.tsx (edited — router with `/` → `/currencies` redirect, `ToastProvider` wrapping)
  - develop/frontend/src/index.css (edited — removed template boilerplate, added shared `.btn*` styles)
  - develop/frontend/src/test/setup.ts (new — jest-dom matchers + explicit `afterEach(cleanup)`, required since `globals: false`)
  - develop/frontend/index.html (edited — page title)
  - develop/frontend/README.md (edited — documents `VITE_API_BASE_URL` / `VITE_BACKEND_PROXY_TARGET` config, scripts, project structure)
  - removed template boilerplate: src/App.css, src/assets/*, public/icons.svg
  - .circleci/config.yml (edited — added `build-and-test-frontend` job: `npm ci`, `npm run build`, `npm test`, cached on `package-lock.json`; added to `build-test` workflow alongside the existing backend job)
- Notes:
  - Implemented `/currencies` page per spec: table with Code/Name/中文名稱/Symbol/Decimal Places/Active(dot)/Actions columns, status filter (All/Active/Inactive) plus a client-side search box (matches the layout mockup), Add/Edit modal with client-side validation mirroring backend rules (code = 3 uppercase letters, disabled on edit; name required ≤100; nameZh/symbol optional ≤100/≤10; decimalPlaces integer 0–8), delete confirmation dialog with the exact Chinese copy from the spec, and toasts for 404 ("幣種不存在，請重新整理頁面") and network errors ("網路錯誤，請稍後再試"), with inline "幣種代碼已存在" on 409 create conflicts. Loading and empty states are handled in the table wrapper.
  - API base URL is configurable via `VITE_API_BASE_URL` (defaults to empty/relative, avoiding CORS since the backend sends no CORS headers); in dev, a Vite server proxy (`VITE_BACKEND_PROXY_TARGET`, default `http://localhost:8080`) forwards `/api/*` to the backend so the browser never makes a cross-origin request. Both are documented in `develop/frontend/README.md`.
  - 24 tests passing (`npm test` → Vitest): API client (success/204/409/404/network-error), `CurrencyTable` (rendering, dash fallback, empty state, action callbacks), `CurrencyFormModal` (validation, code normalization, disabled code on edit, 409 inline error, network error), and `CurrencyPage` integration (mount load, filter refetch, empty state, load-failure toast, create/edit/delete flows including 404 toast-and-refresh behavior).
  - `npm run build` (`tsc -b && vite build`) and `npm run lint` (Oxlint) both pass cleanly (one benign fast-refresh warning on the Toast context file, exit code 0).
  - End-to-end verified against the live backend + MySQL (`docker/docker-compose.yml` `wdd-mysql` container, already running, plus `mvn spring-boot:run`): started the Vite dev server and confirmed via curl and a Playwright screenshot that `/currencies` renders all 10 seeded currencies through the dev proxy, that the status/active filter and 404 paths work end-to-end, and that a POST-created test currency (`ZZZ`) appeared in the table on refetch; the test row was deleted afterward via the API to restore the original 10-row dataset. Observed pre-existing mojibake in the `symbol` column for EUR/JPY/GBP/CNY on the live data (e.g. `â‚¬` instead of `€`) — this is corrupted seed data in the already-applied DB migration (same root cause the backend agent previously found and partially fixed for `nameZh`), not a frontend rendering bug; out of scope for this frontend task.

### Increment 2 — 2026-07-31
- Status: DONE (Delta: remove the active/inactive concept)
- Files changed:
  - develop/frontend/src/types/currency.ts (removed `active` from `Currency` and `CurrencyInput`; kept the `StatusFilter` type export unchanged since `CurrencyPairPage.tsx` still imports/uses it for the unrelated `currency_pair.active` filter)
  - develop/frontend/src/api/currencyApi.ts (`list()` no longer takes an `active` param or builds a query string; calls `GET /api/currencies` unconditionally)
  - develop/frontend/src/pages/CurrencyPage.tsx (removed `StatusFilter` import/usage, `statusFilter` state, and the `toActiveParam` helper; `fetchCurrencies` now calls `currencyApi.list()` with no argument; filter bar now only has Search + Add)
  - develop/frontend/src/components/CurrencyTable.tsx (removed the Active column header and the status-dot/badge cell)
  - develop/frontend/src/components/CurrencyFormModal.tsx (removed the `active` state, the Active checkbox field, and `active` from the submitted payload)
  - develop/frontend/src/components/CurrencyFormModal.css (removed the now-unused `.form-field--toggle` rules, scoped only to this file/the removed toggle)
  - develop/frontend/src/pages/CurrencyPage.test.tsx (removed the status-filter refetch test; mock `Currency` fixtures no longer set `active`; mount assertion now expects `list()` called with no args)
  - develop/frontend/src/components/CurrencyTable.test.tsx, develop/frontend/src/components/CurrencyFormModal.test.tsx (mock `Currency` fixtures no longer set `active`; the create-form submit assertion no longer expects `active: true` in the payload)
  - develop/frontend/src/components/CurrencyPairFormModal.test.tsx, develop/frontend/src/components/CurrencyPairDefinitionFormModal.test.tsx, develop/frontend/src/pages/CurrencyPairDefinitionPage.test.tsx, develop/frontend/src/pages/CurrencyPairPage.test.tsx (mechanical fix only: removed the stray `active: true` field from `Currency`-typed mock fixtures — these files construct base/quote `Currency` objects, distinct from `Brand.active`/`CurrencyPair.active` which are untouched; this was a compile-time fallout of removing `active` from the shared `Currency` type, not a behavior change, and no assertions/logic in these files were altered)
- Notes:
  - `CurrencyPairPage.tsx`, the shared `StatusFilter` component/type, `BrandPage`/`BrandTable`, and `SpreadPage` were left untouched — they filter on `Brand.active`/`currency_pair.active`, which is unrelated to this delta.
  - `npm run build` (tsc -b && vite build), `npm test -- --run` (169/169 tests passing across all 23 suites, including the full pre-existing `CurrencyPairPage`/`CurrencyPairDefinitionPage`/`SpreadPage`/`StatusFilter` suites with no regressions), and `npm run lint` (Oxlint, only the pre-existing unrelated `ToastProvider` fast-refresh warning) all pass cleanly.

