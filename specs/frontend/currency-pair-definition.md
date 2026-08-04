---
status: done
title: "Currency Pair Definition (Global Master) Page"
requirement: "幣種對可以被單獨建立, 建立完後所有品牌都有這一個幣種對, 幣種對可以設定正向與反向的精度, 幣種對如果建立正向, 反向就不可被建立. 全域幣種對, 需要確認全部品牌幣種對都關閉, 才可刪除."
depends_on: [currency-pair, currency]
---

# Currency Pair Definition (Global Master) Page — Frontend Spec

## Overview
Adds a new "幣種對主檔" (Currency Pair Master) page for creating brand-agnostic currency pair definitions — a (base, quote) direction with a forward/reverse display precision — that, on creation, are automatically provisioned to every brand (via the backend, `specs/backend/currency-pair-definition.md`). This is **separate from** the existing per-brand Currency Pair List page (`specs/frontend/currency-pair.md`, route `/currency-pairs`), which continues to manage each brand's individual rate/active flag through the existing audit-approval flow, untouched by this spec.

Unlike `currency-pair`/`spread`, this feature **applies immediately** — no audit-approval submission, no "審核中" badge, no diff renderer. Create/update/delete all take effect directly, matching the backend's direct-apply design.

## Requirements
- New route `/currency-pair-definitions`, nav item "幣種對主檔" in the sidebar, positioned after "Currency Pair List".
- Page shows a brand-agnostic list: 基準幣別/對應幣別 (base/quote currency codes), 正向精度, 反向精度, actions.
- Create form: pick base currency, pick quote currency (from the existing currency list, reusing `currencyApi`), forward precision, reverse precision. On success, an inline confirmation communicates that the pair is now available for every brand.
- Edit form: precision only — base/quote currency are read-only/immutable once created.
- Delete: confirm dialog, with copy clarifying that brands' already-provisioned pairs are **not** removed by deleting the definition. Deletion is blocked (backend `409`) while any brand still has this pair active — the error must name which brands, so the user knows what to disable first.
- All user-facing text in Traditional Chinese, matching existing pages' tone.

## Implementation Details

### Routing & Navigation
- `develop/frontend/src/App.tsx`: add `<Route path="/currency-pair-definitions" element={<CurrencyPairDefinitionPage />} />`.
- `develop/frontend/src/layout/AppShell.tsx`: add `{ to: '/currency-pair-definitions', label: '幣種對主檔' }` to `NAV_ITEMS`, immediately after the `/currency-pairs` entry.

### Types — `develop/frontend/src/types/currencyPairDefinition.ts`
```ts
export interface CurrencyPairDefinition {
  id: number
  baseCurrencyId: number
  baseCurrencyCode: string
  quoteCurrencyId: number
  quoteCurrencyCode: string
  forwardPrecision: number
  reversePrecision: number
  createdAt: string
  updatedAt: string
}

export interface CurrencyPairDefinitionCreateInput {
  baseCurrencyId: number
  quoteCurrencyId: number
  forwardPrecision: number
  reversePrecision: number
}

export interface CurrencyPairDefinitionUpdateInput {
  forwardPrecision: number
  reversePrecision: number
}
```

### API Client — `develop/frontend/src/api/currencyPairDefinitionApi.ts`
Follow the `apiClient` wrapper convention used by `currencyPairApi.ts`. All calls resolve the entity directly (no `AuditRequest` involved — this feature is not audit-gated):
```ts
export const currencyPairDefinitionApi = {
  list: (params?: { baseCurrencyId?: number; quoteCurrencyId?: number }) => ...,  // GET /api/currency-pair-definitions
  create: (input: CurrencyPairDefinitionCreateInput) => ...,                      // POST /api/currency-pair-definitions
  update: (id: number, input: CurrencyPairDefinitionUpdateInput) => ...,          // PUT /api/currency-pair-definitions/{id}
  remove: (id: number) => ...,                                                    // DELETE /api/currency-pair-definitions/{id}
}
```

### Page — `develop/frontend/src/pages/CurrencyPairDefinitionPage.tsx` (+ `.css`)
Layout, mirroring `CurrencyPairPage.tsx`'s non-audit-affected structure (`page-title`, `search-table-card`, `table-footer` classes/conventions — no brand filter needed since this list is brand-agnostic):

1. **Header**: title "幣種對主檔", "+新增幣種對" button opens `CurrencyPairDefinitionFormModal` in create mode.
2. **Table** (via `CurrencyPairDefinitionTable`): columns 基準幣別 / 對應幣別 / 正向精度 / 反向精度 / 操作 (編輯, 刪除); footer `Total {n} items`.
3. Loading/error/empty states matching existing pages: `載入中…`, `資料載入失敗` + `重試` button, empty-state row when no definitions exist yet.
4. Toasts (via existing `useToast`): `已建立幣種對，所有品牌已自動套用`, `已更新精度設定`, `已刪除幣種對主檔`, `網路錯誤，請稍後再試`.

Data flow: on mount, fetch `currencyPairDefinitionApi.list()` and the full currency list (reuse `currencyApi.list()`) for the create form's dropdowns.

### `CurrencyPairDefinitionFormModal` — `develop/frontend/src/components/CurrencyPairDefinitionFormModal.tsx` (+ `.css`)
Reuse the existing `Modal` component. Props: `mode: 'create' | 'edit'`, `initial?: CurrencyPairDefinition`, `currencies: Currency[]`, `onSubmit`, `onClose`.

Fields:
- 基準幣別 (select from `currencies`, required) — **disabled in edit mode**, pre-filled and read-only.
- 對應幣別 (select from `currencies`, required, must differ from 基準幣別 — reuse the "基準幣別與對應幣別不可相同" inline-error convention from `CurrencyPairFormModal`) — **disabled in edit mode**.
- 正向精度 (number input, integer, `min={0}`, `max={8}`, required)
- 反向精度 (number input, integer, `min={0}`, `max={8}`, required)

Validation errors shown inline (`field-error` convention), blocking submit, matching `CurrencyPairFormModal`'s pattern.

On submit:
- Create mode: `currencyPairDefinitionApi.create({ baseCurrencyId, quoteCurrencyId, forwardPrecision, reversePrecision })`. Success → close modal, toast `已建立幣種對，所有品牌已自動套用`, refetch list.
- Edit mode: `currencyPairDefinitionApi.update(id, { forwardPrecision, reversePrecision })`. Success → close modal, toast `已更新精度設定`, refetch list.

Error handling:
- `409` (reverse-or-duplicate direction already exists) → inline error under 對應幣別: `此幣種對（或其反向）已存在`.
- `400` (`baseCurrencyId == quoteCurrencyId`, or precision out of range) → inline field errors, same convention as other forms.
- `404` (a selected currency no longer exists) → toast `網路錯誤，請稍後再試`, close modal, refetch.
- Network failure → toast `網路錯誤，請稍後再試`.

### `CurrencyPairDefinitionTable` — `develop/frontend/src/components/CurrencyPairDefinitionTable.tsx` (+ `.css`)
Presentational table, props `{ definitions: CurrencyPairDefinition[], onEdit, onDelete }`, styled consistently with `CurrencyPairTable.tsx` (`table-empty` for the zero-rows state, action buttons in the last column). No pending-badge column — this feature has no audit workflow.

### Delete flow
Reuse `ConfirmDialog`. Message: `確定要刪除幣種對主檔「{baseCode}/{quoteCode}」嗎？已套用至各品牌的幣種對不會被移除，但刪除後可重新建立其反向幣種對。若仍有品牌啟用此幣種對，將無法刪除。` On confirm, `currencyPairDefinitionApi.remove(id)`.

- Success (`204`): close dialog, toast `已刪除幣種對主檔`, refetch list.
- `409` (one or more brands still active — body includes `activeBrandCodes: string[]`, per `specs/backend/currency-pair-definition.md`): close the dialog and toast `以下品牌仍啟用此幣種對，請先停用：{activeBrandCodes.join(', ')}` (fall back to a generic `尚有品牌啟用此幣種對，請先停用` if the field is missing/empty for any reason). Do not refetch — nothing changed server-side.
- `404`/network failure: same fallback pattern as other pages (`網路錯誤，請稍後再試`).

## Acceptance Criteria
- [x] `/currency-pair-definitions` route renders `CurrencyPairDefinitionPage`, reachable via the "幣種對主檔" sidebar link
- [x] Creating a definition for USD/JPY with precision `2`/`5` succeeds, shows the confirmation toast, and the new row appears in the table immediately (no approval step)
- [x] Attempting to create the reverse (JPY/USD) of an existing definition shows an inline "此幣種對（或其反向）已存在" error and does not close the modal
- [x] Editing a definition only exposes 正向精度/反向精度 as editable fields; 基準幣別/對應幣別 are visibly disabled/read-only
- [x] Deleting a definition shows the updated confirm-dialog copy and removes it from this page's table; the existing Currency Pair List page (`/currency-pairs`) is unaffected by the deletion — its rows for that pair (if previously provisioned) remain exactly as they were (verified by inspection: no shared state/component/API between this page and `CurrencyPairPage.tsx`/`currencyPairApi.ts` was touched or introduced)
- [x] Loading and error states match the existing page conventions (載入中…, 資料載入失敗 + 重試)
- [x] No pending/"審核中" badge or diff-renderer registration is added for this feature — confirmed it is not wired into the audit module in any way

### Delta: block deletion while any brand's pair is still active
(The `[x]` delete item above remains accurate for the unguarded case; the backend now guards it — see `specs/backend/currency-pair-definition.md`.)
- [x] The delete confirm-dialog copy mentions that deletion is blocked while any brand still has the pair active
- [x] Attempting to delete a definition while at least one brand's pair is active shows a toast naming exactly which brands (`activeBrandCodes`) still need to be disabled, and does not remove the row from the table
- [x] Deleting a definition succeeds normally once every brand's pair for that direction is inactive
- [x] A generic fallback toast is shown if the `409` response is missing `activeBrandCodes` for any reason (defensive, should not normally happen)

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/types/currencyPairDefinition.ts` (new)
  - `develop/frontend/src/api/currencyPairDefinitionApi.ts` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.css` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.test.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.css` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.test.tsx` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.tsx` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.css` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.test.tsx` (new)
  - `develop/frontend/src/App.tsx` (added route, alongside the pre-existing spread-group route)
  - `develop/frontend/src/layout/AppShell.tsx` (added nav item after "Currency Pair List", alongside the pre-existing 點差管理 item)
- Notes:
  - Implemented as a direct-apply (non-audit-gated) CRUD page, mirroring `CurrencyPage.tsx`'s structure rather than `CurrencyPairPage.tsx`'s audit-aware one — no `pendingIds`, no `registerDiffRenderer`, no `AuditRequest` types anywhere in the new code.
  - `CurrencyPairDefinitionFormModal` disables both currency `<select>`s in edit mode and only submits `{ forwardPrecision, reversePrecision }` for updates; create submits the full `CurrencyPairDefinitionCreateInput`. A 409 response sets an inline error under 對應幣別 (`此幣種對（或其反向）已存在`) without closing the modal; a 400 shows a generic inline `輸入資料有誤，請確認後再試` form error; any other/network error shows `網路錯誤，請稍後再試` — all without closing the modal, so the page itself only special-cases 404 (currency-not-found) by toasting, closing the modal, and refetching, matching the spec's split of responsibilities.
  - `CurrencyPairDefinitionTable` has no pending-badge column and Edit/Delete are always enabled (no audit workflow to gate on).
  - Verified `/currency-pairs` (`CurrencyPairPage.tsx`, `currencyPairApi.ts`, audit module) were not modified, read, or referenced by any new file, satisfying the "unaffected by deletion" criterion structurally.
  - Verification performed: `npm run build` (tsc -b && vite build) succeeds with no errors; `npm test` (vitest run) passes all 23 test files / 168 tests, including the 3 new test files / 19 new tests added for this feature; `npm run lint` (oxlint) reports only the pre-existing unrelated `ToastProvider.tsx` fast-refresh warning, no new issues.

### Increment 2 — 2026-07-30
- Status: DONE
- Files changed:
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.tsx` — updated the delete `ConfirmDialog` copy to add `若仍有品牌啟用此幣種對，將無法刪除。`; `handleConfirmDelete` now special-cases `ApiError` with `status === 409`: closes the dialog, toasts `以下品牌仍啟用此幣種對，請先停用：{activeBrandCodes.join(', ')}` (reading `error.body?.activeBrandCodes`, matching the `error.body?.error` convention used elsewhere in the codebase, e.g. `CurrencyPairPage.tsx`), falls back to the generic `尚有品牌啟用此幣種對，請先停用` when `activeBrandCodes` is missing/empty, and does **not** call `fetchDefinitions()` (no refetch, row stays as-is). The pre-existing `404`/network fallback branch (`NETWORK_ERROR_MESSAGE` + refetch) is unchanged for all other error statuses.
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.test.tsx` — updated the existing delete-confirmation test's expected dialog message to the new copy; added two new tests: (1) a `409` with `activeBrandCodes: ['BR1', 'BR2']` shows the exact toast naming both brands, the row (`USD`) remains in the table, and `list()` is not called a second time (no refetch); (2) a `409` missing `activeBrandCodes` shows the generic fallback toast, likewise with no row removal/refetch.
  - `specs/frontend/currency-pair-definition.md` — checked off all 4 Delta acceptance criteria, set `status: done`, appended this increment.
- Notes:
  - Scope was strictly limited to the Delta section per instructions — no changes to `CurrencyPairFormModal`, `CurrencyPairPage`/`currencyPairApi`, `CurrencyPairDefinitionFormModal`/Table, routing, or the audit module. This feature remains fully non-audit-gated, as before.
  - Verification performed: `npm run build` succeeds with no errors; `npm test` passes all 23 test files / 170 tests (168 pre-existing + 2 new); `npm run lint` (oxlint) reports only the pre-existing unrelated `ToastProvider.tsx` fast-refresh warning, no new issues, no regressions.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 3 — 2026-08-04
- Status: DONE
- Files changed (re-executed from scratch, base CRUD + Delta section together in one pass, since `develop/` had been wiped by the Teardown above):
  - `develop/frontend/src/types/currencyPairDefinition.ts` (new)
  - `develop/frontend/src/api/currencyPairDefinitionApi.ts` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.css` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionTable.test.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.tsx` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.css` (new)
  - `develop/frontend/src/components/CurrencyPairDefinitionFormModal.test.tsx` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.tsx` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.css` (new)
  - `develop/frontend/src/pages/CurrencyPairDefinitionPage.test.tsx` (new)
  - `develop/frontend/src/App.tsx` (added the `/currency-pair-definitions` route)
  - `develop/frontend/src/layout/AppShell.tsx` (added the "幣種對主檔" nav item after the `/currency-pairs` entry)
- Notes:
  - Implemented directly to the spec's final end state in a single pass: a brand-agnostic, direct-apply (non-audit-gated) CRUD page mirroring `CurrencyPage.tsx`'s structure — no `pendingIds`, no `registerDiffRenderer`, no `AuditRequest` types anywhere in the new code — with the Delta's 409-active-brands delete guard built in from the start (no separate follow-up increment needed this time).
  - `CurrencyPairDefinitionFormModal` disables both currency `<select>`s in edit mode (pre-filled from `initial`) and always calls `onSubmit` with the full `{ baseCurrencyId, quoteCurrencyId, forwardPrecision, reversePrecision }` shape; the page strips it down to `{ forwardPrecision, reversePrecision }` before calling `update` in edit mode, matching `CurrencyPage.tsx`'s create/edit split convention. A 409 sets an inline error under 對應幣別 (`此幣種對（或其反向）已存在`); a 400 sets a generic inline `輸入資料有誤，請確認後再試` form error — both without closing the modal. The page itself only special-cases `404` (a selected currency no longer exists) by toasting `網路錯誤，請稍後再試`, closing the modal, and refetching; any other/network error on submit shows the same toast without closing the modal.
  - Delete flow: confirm dialog copy includes `若仍有品牌啟用此幣種對，將無法刪除。` per the Delta. On `409`, the page reads `error.body?.activeBrandCodes` and toasts `以下品牌仍啟用此幣種對，請先停用：{codes.join(', ')}`, falling back to the generic `尚有品牌啟用此幣種對，請先停用` when the field is missing/empty; either way the dialog closes without a refetch (nothing changed server-side) and the row stays in the table. A `404` on delete toasts the generic `網路錯誤，請稍後再試` and refetches (the row is gone server-side); a plain network failure toasts the same message without refetching.
  - `CurrencyPairDefinitionTable` has no pending-badge column (Edit/Delete are always enabled — no audit workflow to gate on) and gained a dedicated `error`/`onRetry` prop pair: when `error` is true it renders a `資料載入失敗` message with a `重試` button in place of the table, taking precedence over the loading/empty states — this is a new UI convention (no other existing page implements it yet, despite `brand.md`/`spread.md` describing similar intent) introduced here to satisfy this spec's explicit Acceptance Criterion, and the page's `fetchDefinitions` both toasts `網路錯誤，請稍後再試` and sets this error flag on initial-load failure.
  - Verified `/currency-pairs` (`CurrencyPairPage.tsx`, `currencyPairApi.ts`, `src/audit/*`) were not modified, read from, or referenced by any new file, satisfying the "unaffected by deletion" / "not wired into the audit module" criteria structurally.
  - Verification performed: `npm run build` (`tsc -b && vite build`) succeeds with no errors; `npm test` (`vitest run`) passes all 17 test files / 148 tests, including the 3 new test files added for this feature (covering create/edit/delete, the 409 duplicate-direction and 400 inline-error paths, the load error/retry state, and all four Delta scenarios — named-brands toast, generic fallback toast, no-refetch-on-409, and normal delete once inactive); `npm run lint` (oxlint) reports only the pre-existing unrelated `ToastProvider.tsx` fast-refresh warning, no new issues.
