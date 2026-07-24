---
status: done
title: "Currency Table Page"
requirement: "Display currencies in a table with CRUD operations"
---

# Currency Table Page — Frontend Spec

## Overview
Build a currency management page that displays all currencies in a table. Users can view, add, edit, and delete currencies. Consumes the API defined in `specs/backend/currency.md`.

## Requirements
- Table page showing all currencies
- Add new currency via modal/dialog form
- Edit existing currency inline or via modal
- Delete with confirmation
- Filter by active/inactive status

## Page Layout

### Route
`/currencies`

### Page Structure
```
┌──────────────────────────────────────────────┐
│  Currency Management                         │
│                                              │
│  [Active ▼]  [Search...]      [+ Add]        │
│                                              │
│  ┌──────────────────────────────────────────┐│
│  │ Code │ Name   │ 中文名 │ Symbol │ Active ││
│  │──────│────────│────────│────────│────────││
│  │ TWD  │ New..  │ 新台幣 │ NT$    │  ✓     ││
│  │ USD  │ Unit.. │ 美元   │ $      │  ✓     ││
│  │ ...  │ ...    │ ...    │ ...    │  ...   ││
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
| Active         | active         | 80px   | Green dot / grey dot         |
| Actions        | —              | 120px  | Edit, Delete buttons         |

### Filter Bar
- **Status filter**: dropdown with options: All / Active / Inactive
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
| Active         | Toggle     | Default: on                         |

### Delete Confirmation
- Show confirmation dialog: "確定要刪除幣種 {code} 嗎？"
- On confirm: call DELETE API, refresh table

## API Integration

| Action   | Method | Endpoint                | Trigger           |
|----------|--------|-------------------------|--------------------|
| List     | GET    | /api/currencies         | Page load, filter change |
| Create   | POST   | /api/currencies         | Modal submit       |
| Update   | PUT    | /api/currencies/{id}    | Modal submit       |
| Delete   | DELETE | /api/currencies/{id}    | Confirm dialog     |

### Error Handling
- **409 on create**: show "幣種代碼已存在" inline error
- **404 on edit/delete**: show toast "幣種不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching

## Acceptance Criteria
- [ ] Currency table renders with all columns
- [ ] Currencies load from API on page mount
- [ ] Status filter works (All / Active / Inactive)
- [ ] Add modal opens, validates, and creates via API
- [ ] Edit modal pre-fills data, code field disabled, updates via API
- [ ] Delete shows confirmation and deletes via API
- [ ] Error states display correct Chinese messages
- [ ] Table refreshes after create/update/delete
- [ ] Empty state shown when no currencies match filter

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

