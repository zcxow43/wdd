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
- [ ] Status filter works (All / Active / Inactive)
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


### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 3 — 2026-08-04
- Status: DONE — rebuilt from scratch after the 2026-08-03 teardown, built directly in the final/no-`active` end state (skipped the historical build-then-remove two-step, since `specs/backend/currency.md` is already implemented with no `active` field at all)
- Files changed:
  - develop/frontend/ (bare Vite + React 19 + TypeScript 6 + Vite 8 scaffold already existed on disk from a prior re-scaffold; this increment adds all app code on top of it)
  - develop/frontend/package.json (added `react-router-dom` dependency; added `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`, `@vitest/coverage-v8` devDependencies; added `test`/`test:watch` scripts)
  - develop/frontend/vite.config.ts (edited — added a dev server proxy: `/api/*` → `VITE_BACKEND_PROXY_TARGET` (default `http://localhost:8080`), avoiding CORS in dev; no `server.port` set, so the default 5173 stays authoritative and matches `docker/launch.json`)
  - develop/frontend/vitest.config.ts (new — `vitest/config`'s `defineConfig`, jsdom environment, non-global test APIs, `./src/test/setup.ts` setup file)
  - develop/frontend/tsconfig.node.json (edited — added `vitest.config.ts` to the `include` list)
  - develop/frontend/.env.development, develop/frontend/.env.example (new — `VITE_API_BASE_URL` (empty/relative by default) and `VITE_BACKEND_PROXY_TARGET`)
  - develop/frontend/src/vite-env.d.ts (new — `ImportMetaEnv`/`ImportMeta` typing for the two env vars)
  - develop/frontend/src/test/setup.ts (new — jest-dom matchers + explicit `afterEach(cleanup)`, required since `globals: false`)
  - develop/frontend/src/types/currency.ts (new — `Currency` (id/code/name/nameZh/symbol/decimalPlaces/createdAt/updatedAt) and `CurrencyInput` (code/name/nameZh/symbol/decimalPlaces); no `active` field on either, matching the backend contract exactly)
  - develop/frontend/src/api/client.ts (new — generic `fetch` wrapper (`get`/`post`/`put`/`del`), `ApiError` (status + parsed body, message derived from the body's `error` field), `NetworkError`, 204 handling)
  - develop/frontend/src/api/client.test.ts (new — 5 tests: 200 GET, 204 DELETE, 409 ApiError, 404 ApiError, NetworkError on fetch rejection)
  - develop/frontend/src/api/currencyApi.ts (new — `list()` with no parameters/query string, `create`, `update` (typed `Omit<CurrencyInput, 'code'>` since code is immutable and never sent on `PUT`), `remove`, all against `/api/currencies`)
  - develop/frontend/src/components/Modal.tsx + Modal.css (new — generic overlay/dialog shell used by the currency form modal)
  - develop/frontend/src/components/ConfirmDialog.tsx + ConfirmDialog.css (new — generic confirm/cancel dialog used by the delete flow)
  - develop/frontend/src/components/ToastProvider.tsx + ToastProvider.css (new — `ToastProvider`/`useToast()` context, auto-dismissing after 4s, click-to-dismiss)
  - develop/frontend/src/components/CurrencyTable.tsx + CurrencyTable.css (new — Code/Name/中文名稱/Symbol/Decimal Places/Actions columns only, no Active column; dash fallback for empty `nameZh`/`symbol`; loading and empty states)
  - develop/frontend/src/components/CurrencyTable.test.tsx (new — 5 tests: column rendering + no Active column, dash fallback, empty state, loading state, edit/delete callbacks)
  - develop/frontend/src/components/CurrencyFormModal.tsx + CurrencyFormModal.css (new — Code/Name/中文名稱/Symbol/Decimal Places fields only, no Active toggle; client-side validation mirroring the backend (code = 3 uppercase letters, disabled-but-visible on edit; name required ≤100; nameZh/symbol optional ≤100/≤10; decimalPlaces integer 0–8); code auto-uppercased as typed; on submit, a `409 ApiError` sets the inline "幣種代碼已存在" error under the Code field and keeps the modal open — all other errors (404, network) are left to the parent page to handle as toasts)
  - develop/frontend/src/components/CurrencyFormModal.test.tsx (new — 5 tests: no Active toggle/checkbox present, required-field validation messages, code normalized to uppercase on a valid create submit, Code field disabled+prefilled on edit, inline "幣種代碼已存在" shown on a 409)
  - develop/frontend/src/pages/CurrencyPage.tsx + CurrencyPage.css (new — page composing a client-side search box + "+ Add" button (no status filter), the table, add/edit modal, delete confirm dialog, and toasts; `fetchCurrencies` calls `currencyApi.list()` with no arguments; create errors other than 409 and edit/delete 404s are handled here with the exact Chinese toast copy from the spec; a 409 on create is rethrown so `CurrencyFormModal` can show it inline instead)
  - develop/frontend/src/pages/CurrencyPage.test.tsx (new — 9 integration tests mocking `currencyApi`: mount load with no filter args, no status filter/dropdown rendered, empty state, network-error toast on load failure, create via modal + table refresh, edit via modal with Code field disabled, delete confirmation + successful delete, 404-on-delete toast + refetch, client-side search filtering)
  - develop/frontend/src/App.tsx (edited — router with `/` → `/currencies` redirect and a `/currencies` route rendering `CurrencyPage`)
  - develop/frontend/src/main.tsx (edited — wraps `App` in `BrowserRouter` and `ToastProvider`)
  - develop/frontend/src/index.css (edited — removed the centered-flex template boilerplate; added shared `.btn`/`.btn-primary`/`.btn-secondary`/`.btn-danger`/`.btn-link` styles reused by the modal, confirm dialog, and table row actions)
- Notes:
  - Built directly in the spec's final end state per instructions: no active/inactive concept anywhere (no `StatusFilter` import, no Active column, no Active toggle, no `active` field on either `Currency` or `CurrencyInput`, `currencyApi.list()` takes and sends no parameters at all).
  - Error-handling split cleanly between the two layers per the spec's exact Chinese copy: `CurrencyFormModal` owns only the create-time 409 → inline "幣種代碼已存在" (keeping the modal open so the user can fix the code); `CurrencyPage` owns 404-on-edit/delete → toast "幣種不存在，請重新整理頁面" (closing the modal/dialog and refetching the list) and any other failure (network or otherwise) → toast "網路錯誤，請稍後再試". This avoids the double-handling/dead-code branches an earlier draft of this increment had (a `NetworkError` check duplicated in both the page and the modal); simplified to a single owner per error type before finalizing.
  - `npm run build` (`tsc -b && vite build`) and `npm run lint` (Oxlint) both pass with 0 errors (the only lint output is the pre-existing, unavoidable `react/only-export-components` fast-refresh warning on `ToastProvider.tsx`, since it exports both the provider component and the `useToast` hook from the same file).
  - `npm test` (Vitest): 4 test files, 24 tests, 0 failures (`client.test.ts` x5, `CurrencyTable.test.tsx` x5, `CurrencyFormModal.test.tsx` x5, `CurrencyPage.test.tsx` x9).
  - End-to-end verified against the live backend (`mvn -f develop/backend/pom.xml spring-boot:run`, port 8080) + the already-running `wdd-mysql` container: started the Vite dev server (port 5173, matching `docker/launch.json`) and confirmed via `curl` through the dev proxy that `GET /api/currencies` returns all 10 seeded currencies with no `active` field; drove a full create → duplicate-code 409 → update → delete → 404-after-delete lifecycle against a throwaway `ZZZ` test currency through the exact `/api/*` path the frontend calls, confirming the final row count was restored to 10 afterward. Both the backend (`mvn spring-boot:run`) and frontend (`npm run dev`) processes were stopped after verification, confirmed via a follow-up `curl` (both `8080` and `5173` unreachable) and `pgrep` (no lingering `spring-boot:run`/`vite` processes).
  - Confirmed `docker/launch.json` already had a correct `frontend` entry (`npm --prefix develop/frontend run dev`, port 5173) alongside the existing `backend` entry, and `.claude/launch.json` was already a valid symlink to it — no changes needed to either.
  - No `StatusFilter` component exists anywhere in the codebase yet (this is the first frontend page built since the teardown) — the Delta item about not touching the shared `StatusFilter` component is satisfied trivially, since nothing referencing it was created or removed. A future spec (e.g. `currency-pair.md`, `brand.md`) will introduce that shared component when it needs an actual active/inactive filter.
