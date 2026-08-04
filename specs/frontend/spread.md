---
status: done
title: "Spread (點差) Management Page"
requirement: "每個品牌幣種對可以配置點差, 點差分為預設點差或客制點差, 有入金出金兩個欄位; 假設配置 0.1, 0.2, 可以拉 USD_JPY, USD_EUR 去到同一組客制點差中, 品牌幣種對最多被加入到一組點差中, 配置完後可以隨意 CRUD, 若沒有被配置到的則使用預設點差, 點差依品牌區分; 點差也需要加入審核功能"
depends_on: [currency-pair, brand, audit]
---

# Spread (點差) Management Page — Frontend Spec

## Overview
Adds a new "點差管理" (Spread Management) page for managing, per brand: one **default spread** (預設點差, edit-only) and any number of **custom spread groups** (客制點差群組, full CRUD), each with `入金` (deposit) / `出金` (withdraw) spread values and a set of member currency pairs. Depends on `specs/backend/spread.md` for the API contract, and reuses existing `Brand`/`CurrencyPair` types and API clients.

Every mutation (default spread edit; group create/update/delete) now **submits for approval** instead of applying immediately — the backend returns `202 Accepted` with an `AuditRequest` for all of these, reviewed on the existing generic Audit page (`specs/frontend/audit.md`, route `/audit-requests`), exactly like `currency-pair` (`specs/frontend/currency-pair-approval.md` is the reference implementation to mirror: same toast pattern, same pending badge/disabled-actions pattern, same diff-renderer registration). This page therefore never shows a change take effect immediately — it shows a "submitted for approval" confirmation and the underlying data only changes once a reviewer approves it on the Audit page.

The "drag pairs into a group" interaction from the requirement is implemented as a **two-panel assign/unassign selector** (buttons to move pairs between an "unassigned" list and the group's member list) rather than a drag-and-drop library — this codebase has no DnD dependency, and a button-based mover achieves the same outcome (moving a pair into exactly one group) with far less code, per the project's "minimize code generated" and "no new dependencies unless needed" conventions.

## Requirements
- New route `/spread-groups`, nav item "點差管理" in the sidebar, positioned after "Currency Pair List".
- Page is scoped to one selected brand at a time (a brand selector, not a multi-brand view), since every spread value and group is brand-specific.
- Default spread section: shows the selected brand's current 入金/出金 values (its live, approved state), editable via a modal form that **submits a request** rather than saving directly.
- Custom spread group section: table of the selected brand's groups (name, 入金, 出金, member pair codes); create/edit via a modal form with the two-panel pair assigner; delete via a confirm dialog — all three **submit requests** rather than mutating directly.
- Assigning a pair already in another group to a different group is proposed, not rejected, by the form — the two-panel UI shows, for pairs currently in another group, which group they'll be moved out of *once the request is approved*.
- Rows/sections with a `PENDING` request against them are marked (badge) and their mutating actions disabled, to avoid the "already has a pending request" `409` in the common case — same pattern as `CurrencyPairPage`'s `pendingIds`.
- A dedicated diff renderer registered for both `entityType: "SPREAD_DEFAULT"` and `entityType: "SPREAD_GROUP"` so the Audit page renders labeled before/after views instead of the generic fallback.
- All user-facing text in Traditional Chinese, matching existing pages' tone (載入中…, 資料載入失敗, 網路錯誤，請稍後再試, etc.).

## Implementation Details

### Routing & Navigation
- `develop/frontend/src/App.tsx`: add `<Route path="/spread-groups" element={<SpreadPage />} />`.
- `develop/frontend/src/layout/AppShell.tsx`: add `{ to: '/spread-groups', label: '點差管理' }` to `NAV_ITEMS`, after the `/currency-pairs` entry.

### Types — `develop/frontend/src/types/spread.ts`
```ts
export interface SpreadDefault {
  id: number
  brandId: number
  brandCode: string
  depositSpread: number
  withdrawSpread: number
  createdAt: string
  updatedAt: string
}

export interface SpreadDefaultInput {
  depositSpread: number
  withdrawSpread: number
}

export interface SpreadGroupMember {
  currencyPairId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
}

export interface SpreadGroup {
  id: number
  brandId: number
  brandCode: string
  name: string
  depositSpread: number
  withdrawSpread: number
  members: SpreadGroupMember[]
  createdAt: string
  updatedAt: string
}

export interface SpreadGroupInput {
  brandId: number
  name: string
  depositSpread: number
  withdrawSpread: number
  currencyPairIds: number[]
}

export type SpreadSource = 'DEFAULT' | 'GROUP'

export interface SpreadResolution {
  currencyPairId: number
  brandId: number
  source: SpreadSource
  spreadGroupId: number | null
  spreadGroupName: string | null
  depositSpread: number
  withdrawSpread: number
}
```

### API Client — `develop/frontend/src/api/spreadApi.ts`
Follow the `currencyPairApi.ts` convention post-audit-integration: mutating calls resolve `AuditRequest` (from `../audit/types`), not the entity itself; only the `list`/`resolve` reads resolve the entity types above.
```ts
export const spreadDefaultApi = {
  list: (brandId?: number) => ...,                          // GET /api/spread-defaults?brandId= -> SpreadDefault[]
  update: (id: number, input: SpreadDefaultInput) => ...,   // PUT /api/spread-defaults/{id} -> AuditRequest (202)
}

export const spreadGroupApi = {
  list: (brandId?: number) => ...,                              // GET /api/spread-groups?brandId= -> SpreadGroup[]
  create: (input: SpreadGroupInput) => ...,                     // POST /api/spread-groups -> AuditRequest (202)
  update: (id: number, input: Partial<SpreadGroupInput>) => ..., // PUT /api/spread-groups/{id} -> AuditRequest (202)
  remove: (id: number) => ...,                                  // DELETE /api/spread-groups/{id} -> AuditRequest (202)
  resolve: (currencyPairId: number) => ...,                     // GET /api/spread-groups/resolve/{currencyPairId} -> SpreadResolution
}
```

### Diff renderers — `develop/frontend/src/components/SpreadDefaultDiff.tsx` and `SpreadGroupDiff.tsx`
Modeled directly on `CurrencyPairDiff.tsx` (`specs/frontend/currency-pair-approval.md`): a `DiffRenderer` (from `../audit/diffRegistry`) that also handles being invoked with a `null` `before` (CREATE) or `after` (DELETE) directly, per `hasDiffRenderer`'s contract.

- `renderSpreadDefaultDiff`, registered for `entityType: "SPREAD_DEFAULT"`: fixed field order 品牌 / 入金點差 / 出金點差, two-column 修改前/修改後 table, changed-field highlight. Always an `UPDATE` in practice (both sides populated).
- `renderSpreadGroupDiff`, registered for `entityType: "SPREAD_GROUP"`: fixed field order 品牌 / 名稱 / 入金點差 / 出金點差 / 幣種對, where 幣種對 renders the `members` array as comma-joined `BASE/QUOTE` badges (reuse whatever inline formatting `CurrencyPairTable` already uses for pair codes, or a simple `.join(', ')` of `` `${m.baseCurrencyCode}/${m.quoteCurrencyCode}` ``); the 幣種對 row is highlighted as changed if the joined string differs between `before`/`after`. Handles a `null` before (CREATE, show `—` on the left) / `null` after (DELETE, show `—` on the right), same as `renderCurrencyPairDiff`.

Registration for both happens once at `SpreadPage.tsx` module scope (`registerDiffRenderer('SPREAD_DEFAULT', renderSpreadDefaultDiff)` / `registerDiffRenderer('SPREAD_GROUP', renderSpreadGroupDiff)`), matching how `CurrencyPairPage.tsx` self-registers `renderCurrencyPairDiff` — since `App.tsx` imports `SpreadPage` eagerly alongside the new route, this runs at app startup, before the Audit page can be visited.

### Page — `develop/frontend/src/pages/SpreadPage.tsx` (+ `SpreadPage.css`)
Layout, mirroring `CurrencyPairPage.tsx`'s post-audit-integration structure (`page-title`, `filter-card`, `search-table-card`, `table-footer` classes/conventions, plus its `pendingIds`/`refresh()` data-flow pattern):

1. **Header**: title "點差管理", brand selector (reuse `BrandFilter`, but default to the first active brand rather than `'ALL'` — this page always operates on exactly one brand).
2. **Default spread card**: label "預設點差", shows 入金/出金 values for the selected brand (its live, approved values). "編輯" button opens `SpreadDefaultFormModal`; if a `PENDING` `SPREAD_DEFAULT` request exists for this brand's row, show a "審核中" badge next to the values and disable the "編輯" button.
3. **Custom spread groups card**: title "客制點差群組", "+新增群組" button opens `SpreadGroupFormModal` in create mode; table (via `SpreadGroupTable`) with columns 名稱 / 入金點差 / 出金點差 / 幣種對 (comma-joined `BASE/QUOTE` badges from `members`) / 操作 (編輯, 刪除); rows with a `PENDING` `SPREAD_GROUP` request against their `id` show a "審核中" badge and disabled 編輯/刪除 buttons; footer shows `Total {n} items`.
4. Loading/error/empty states matching existing pages: `載入中…`, `資料載入失敗` + `重試` button, and an empty-state row when a brand has no groups yet.
5. Toasts (via existing `useToast`): `已送出預設點差修改申請，待審核`, `已送出新增點差群組申請，待審核`, `已送出點差群組修改申請，待審核`, `已送出點差群組刪除申請，待審核`, `此項目已有待審核的異動申請` (409), `網路錯誤，請稍後再試`.

Data flow: on brand change, fetch `spreadDefaultApi.list(brandId)` (take the single row), `spreadGroupApi.list(brandId)`, and the brand's currency pairs (reuse `currencyPairApi.list({ brandId, active: true })`) so the group form's pair-assigner has the full pair list plus each pair's current group membership (derived client-side from `spreadGroup.members` across all of the brand's groups — no extra API call needed). Additionally fetch pending ids for both entity types via `auditApi.list({ status: 'PENDING' })` filtered client-side (or two calls, `entityType: 'SPREAD_DEFAULT'` and `entityType: 'SPREAD_GROUP'`), combined into a single `refresh()` alongside the above, matching `CurrencyPairPage.fetchPendingIds`.

### `SpreadDefaultFormModal` — `develop/frontend/src/components/SpreadDefaultFormModal.tsx` (+ `.css`)
Reuse the existing `Modal` component. Fields:
- 入金點差 (number input, `step="any"`, `min={0}`)
- 出金點差 (number input, `step="any"`, `min={0}`)
Client-side validation: both required, numeric, `>= 0` (mirror `CurrencyPairFormModal`'s inline `field-error` pattern). On submit calls `spreadDefaultApi.update(id, input)`. On success (`202`): close modal, toast `已送出預設點差修改申請，待審核`, refresh. On `409`: close modal, toast `此項目已有待審核的異動申請`, refresh (same "close + toast + refresh" pattern `CurrencyPairPage` uses for its own pending-duplicate 409, rather than an inline form error, since the modal shouldn't normally be reachable for a row already showing the pending badge). On `400`: inline form error. On network failure: `網路錯誤，請稍後再試`.

### `SpreadGroupFormModal` — `develop/frontend/src/components/SpreadGroupFormModal.tsx` (+ `.css`)
Props: `mode: 'create' | 'edit'`, `initial?: SpreadGroup`, `brandId: number`, `availablePairs: CurrencyPair[]` (all active pairs for the brand), `groups: SpreadGroup[]` (all groups for the brand, to compute which pair belongs to which group), `onSubmit`, `onClose`.

Fields:
- 名稱 (text input, required, non-blank)
- 入金點差 / 出金點差 (number inputs, required, `>= 0`)
- **幣種對指派** (pair assigner): two side-by-side panels —
  - Left, "未加入本群組" (Not in this group): every `availablePairs` entry not currently selected for this group. Each row shows `BASE/QUOTE` and, if the pair currently belongs to a *different* existing group (per `groups`, excluding the group being edited), a hint like `目前屬於：{groupName}，核准後將自動移出` (wording updated for the audit workflow: the move only happens once approved, not on save). A "加入 →" button per row (or multi-select + a single "加入 →" button) moves it into the right panel.
  - Right, "已加入本群組" (In this group): pairs currently selected for this group (pre-filled from `initial.members` in edit mode). A "← 移除" button per row moves it back to the left panel.
- Validation errors shown inline (`field-error` convention); name/spread validation blocks submit exactly like `CurrencyPairFormModal`.

On submit, calls `onSubmit({ brandId, name, depositSpread, withdrawSpread, currencyPairIds: <right panel ids> })`, which now resolves an `AuditRequest` (`202`), not the finished group. Error handling:
- `409` whose message is the live-duplicate-name case (`Spread group name already exists for this brand`) → inline error near the 名稱 field: `此名稱已被使用`.
- `409` whose message is the pending-duplicate case (create colliding with another pending create for the same brand/name, or an update/delete colliding with an existing pending request on this group) → close modal, toast `此項目已有待審核的異動申請`, refresh — mirroring `CurrencyPairPage.isPendingDuplicateConflict`'s "anything that isn't the known live-duplicate string" classification.
- `400`/`404` referencing a currency pair → toast `網路錯誤，請稍後再試` fallback (should not normally occur since the panel only offers valid pairs for the brand), then refetch and close, matching `CurrencyPairPage`'s defensive 404-handling pattern.

### `SpreadGroupTable` — `develop/frontend/src/components/SpreadGroupTable.tsx` (+ `.css`)
Presentational table, props `{ groups: SpreadGroup[], pendingIds: Set<number>, onEdit, onDelete }`, styled consistently with `CurrencyPairTable.tsx` (`table-empty` for the zero-groups state, "審核中" badge + disabled action buttons for `pendingIds.has(group.id)`, matching `CurrencyPairTable`'s post-audit-integration `pendingIds` prop).

### Delete flow
Reuse `ConfirmDialog`. Message updated for the audit workflow: `確定要送出刪除點差群組「{name}」的申請嗎？核准後，其幣種對將回復為預設點差。` On confirm, `spreadGroupApi.remove(id)`. On success (`202`): toast `已送出點差群組刪除申請，待審核`, refresh. On `409`: toast `此項目已有待審核的異動申請`, refresh.

## Acceptance Criteria
- [x] `/spread-groups` route renders `SpreadPage`, reachable via the "點差管理" sidebar link
- [x] Selecting a brand loads and displays that brand's live default spread and its live custom spread groups
- [x] Editing the default spread shows a "已送出…待審核" toast and does **not** change the displayed 入金/出金 values immediately; approving the resulting request on the Audit page (`/audit-requests`) then updates the displayed values
- [x] Creating a group with two pairs (e.g. USD/JPY, USD/EUR) and deposit/withdraw `0.1`/`0.2` submits a request and shows a toast; the group only appears in the table after the request is approved
- [x] Assigning a pair already in Group A to Group B, via the two-panel assigner in a Group B update request, only removes it from Group A's member list **after that request is approved**
- [x] Submitting a group name that collides with another **live** group in the same brand shows an inline "此名稱已被使用" error and does not close the modal
- [x] Submitting a second create/update/delete for the same default-spread row, or the same group, while one is already `PENDING`, closes the modal/dialog and toasts "此項目已有待審核的異動申請" rather than showing a raw error
- [x] Deleting a group shows an updated confirm-dialog message reflecting that this submits a request; the group and its members remain visible/unchanged until the request is approved
- [x] The default spread card and any spread-group row with a `PENDING` request show a "審核中" badge and disable their mutating actions
- [x] On the Audit page, a `SPREAD_DEFAULT` request shows 品牌/入金點差/出金點差 in the labeled diff view (not the generic fallback); a `SPREAD_GROUP` request shows 品牌/名稱/入金點差/出金點差/幣種對, with 幣種對 rendered as `BASE/QUOTE` badges and highlighted when membership changes
- [x] Loading and error states match the existing page conventions (載入中…, 資料載入失敗 + 重試)
- [x] No new runtime dependency (e.g. a drag-and-drop library) is added to `package.json` for this feature

---
## Execution Result
- Status: DONE
- Files changed:
  - **Types** (new): `develop/frontend/src/types/spread.ts` — `SpreadDefault`/`SpreadDefaultInput`, `SpreadGroupMember`/`SpreadGroup`/`SpreadGroupInput`, `SpreadSource`/`SpreadResolution`, exactly as specified.
  - **API client** (new): `develop/frontend/src/api/spreadApi.ts` — `spreadDefaultApi.{list,update}`, `spreadGroupApi.{list,create,update,remove,resolve}`, mirroring `currencyPairApi.ts`'s post-audit-integration convention (mutating calls resolve `AuditRequest`, `list`/`resolve` resolve the entity types directly).
  - **Diff renderers** (new): `develop/frontend/src/components/SpreadDefaultDiff.tsx` (+ `.test.tsx`), `develop/frontend/src/components/SpreadGroupDiff.tsx` (+ `.test.tsx`) — modeled on `CurrencyPairDiff.tsx`; registered against `SPREAD_DEFAULT`/`SPREAD_GROUP` at `SpreadPage.tsx` module scope. `SpreadGroupDiff` renders an extra 幣種對 row as comma-joined `BASE/QUOTE` `.currency-code` badges, highlighted when the joined membership string differs between before/after; both handle a `null` before (CREATE) / after (DELETE) directly.
  - **Components** (new):
    - `develop/frontend/src/components/SpreadDefaultFormModal.tsx` (+ `.css`, `.test.tsx`) — 入金/出金 number inputs, inline `field-error` validation (required, `>= 0`), 400→inline generic error, other errors→`網路錯誤，請稍後再試`; the page-level handler swallows the `409` pending-duplicate case before it ever reaches this modal (closes + toasts + refreshes instead).
    - `develop/frontend/src/components/SpreadGroupFormModal.tsx` (+ `.css`, `.test.tsx`) — 名稱/入金點差/出金點差 fields plus the two-panel button-based pair assigner ("未加入本群組"/"已加入本群組", "加入 →"/"← 移除" per-row buttons); shows a `目前屬於：{groupName}，核准後將自動移出` hint for pairs currently in a *different* existing group; live-duplicate `409` (`Spread group name already exists for this brand`) → inline `此名稱已被使用` on the 名稱 field without closing; any other error surfaces as the page's own pre-filtered fallback (network message) since 400/404/pending-409 are swallowed one level up in `SpreadPage`.
    - `develop/frontend/src/components/SpreadGroupTable.tsx` (+ `.css`, `.test.tsx`) — 名稱/入金點差/出金點差/幣種對/操作 columns, `table-empty` zero-groups state (`目前沒有點差群組`), `審核中` badge + disabled 編輯/刪除 for `pendingIds.has(group.id)`, styled consistently with `CurrencyPairTable.tsx`.
  - **Page** (new): `develop/frontend/src/pages/SpreadPage.tsx` (+ `.css`, `.test.tsx`) — brand-scoped (`BrandFilter`, auto-selects the first active brand on load rather than defaulting to "All"), 預設點差 card (values + 編輯 button + `審核中` badge when a `SPREAD_DEFAULT`/`PENDING` request targets the live row's id) and 客制點差群組 card (`SpreadGroupTable` + `+ 新增群組` + `table-footer` `Total {n} items`); `refresh()` combines `spreadDefaultApi.list`/`spreadGroupApi.list`/`currencyPairApi.list({brandId, active:true})` with two `auditApi.list({entityType, status:'PENDING'})` calls (one per entity type), matching `CurrencyPairPage.fetchPendingIds`'s pattern; all five specified toasts implemented; delete confirmation uses the spec's updated audit-workflow wording.
  - **Routing/nav** (edited, minimal/surgical diffs per the task's "leave mergeable for the next agent" instruction): `develop/frontend/src/App.tsx` (added the `SpreadPage` import + `<Route path="/spread-groups" .../>`), `develop/frontend/src/layout/AppShell.tsx` (added the `{ to: '/spread-groups', label: '點差管理' }` nav item, positioned after `/currency-pairs` as specified).
- Verification performed:
  - `npm run build` (`tsc -b && vite build`) — succeeds with no type errors.
  - `npx vitest run` — `20 files / 149 tests passed`, run three times consecutively with no flakiness (an initial cross-test-file timing race in `SpreadPage`'s brand auto-selection — see judgment call below — was found and fixed during this verification pass).
  - `npm run lint` (`oxlint`) — no new warnings; the one warning present (`ToastProvider.tsx` fast-refresh) is pre-existing and unrelated to this feature.
  - Confirmed via `git diff --stat` that `package.json`/`package-lock.json` are unchanged — no new runtime dependency was added (the two-panel assigner uses plain buttons, no drag-and-drop library).
- Notable judgment calls:
  - **Brand selector defaults to `null` (not `'ALL'`) until the first active brand auto-selects**, rather than initializing `brandId` directly to `'ALL'` as a placeholder. Initializing to `'ALL'` caused a real, intermittent race under test-suite load: `fetchData`'s `'ALL'` guard branch (which clears `defaultSpread`/`groups`/`pairs`) could run *after* the brand-specific fetch had already resolved and set real data, if the mount-time `'ALL'`-branch effect and the subsequent auto-selected-brand effect were scheduled close together — occasionally reproducing as a `此品牌尚未設定預設點差` flash where a passing assertion was expected. Distinguishing "not yet decided" (`null`) from "user explicitly chose All" (`'ALL'`, reachable only via `BrandFilter`'s built-in "All" option after mount) removes the race entirely, since the clearing branch is then only ever reached deliberately. `BrandFilter`'s `value` prop (which only accepts `number | 'ALL'`) is fed `brandId ?? 'ALL'`.
  - **`SpreadGroupFormModal`/`SpreadDefaultFormModal` only ever see the live-duplicate `409` and non-`ApiError` failures**, per the spec's own wording ("close modal, toast, refresh ... rather than an inline form error"): `SpreadPage`'s page-level submit handlers pre-filter and swallow the `404`/pending-duplicate-`409` cases (closing the modal, toasting, and refreshing themselves, exactly mirroring `CurrencyPairPage.handleCreateSubmit`/`handleEditSubmit`'s `isPendingDuplicateConflict` pattern) before those errors could ever reach the modal's own `catch`; only an actual live-duplicate `409` or a genuine network failure propagates up to the modal for inline handling. This keeps the modal components' own error branches simple and matches the existing `currency-pair` precedent exactly.
  - Selecting `BrandFilter`'s built-in "All" option on this page shows a `請選擇品牌` placeholder instead of fetching (this page is explicitly brand-scoped and never a multi-brand view per the spec); this is a deliberate, minimal-code way to reuse `BrandFilter` unmodified rather than forking it to remove the "All" option.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 2 — 2026-08-04 (Rebuild after teardown: built directly to the final, audit-gated end state)
- Status: DONE. Rebuilt from scratch against the live backend (`specs/backend/spread.md`, `status: done`), mirroring `CurrencyPairPage`'s post-audit-integration pattern exactly, as instructed — no intermediate "plain CRUD, then bolt on audit" version was ever written.
- Files changed:
  - `develop/frontend/src/types/spread.ts` (new) — `SpreadDefault`/`SpreadDefaultInput`, `SpreadGroupMember`/`SpreadGroup`/`SpreadGroupInput`, `SpreadSource`/`SpreadResolution`, exactly as specified.
  - `develop/frontend/src/api/spreadApi.ts` (new) — `spreadDefaultApi.{list,update}`, `spreadGroupApi.{list,create,update,remove,resolve}`; `list`/`resolve` resolve the entity types directly, all mutating calls resolve `AuditRequest` (imported from `../audit/types`), matching `currencyPairApi.ts`'s convention.
  - `develop/frontend/src/components/BrandFilter.tsx` (new) — this component did **not** already exist in the codebase despite the spec listing it among "shared plumbing" (an artifact of `currency-pair.md`'s own history, where the equivalent select ended up inlined in `CurrencyPairPage.tsx` instead of extracted); created it now as the genuinely-reusable dropdown the spec describes (`brands: Brand[]`, `value: number | 'ALL'`, `onChange`), rendering just the bare `<select className="filter-input">` so the caller owns its own `.filter-group`/`.filter-label` wrapper, matching `CurrencyPairPage`'s inline brand-select markup. `CurrencyPairPage` itself was left untouched (out of scope for this spec) — only `SpreadPage` consumes it.
  - `develop/frontend/src/components/SpreadDefaultDiff.tsx` (new, + `.test.tsx`, 4 tests) and `develop/frontend/src/components/SpreadGroupDiff.tsx` (new, + `.test.tsx`, 6 tests) — modeled directly on `CurrencyPairDiff.tsx`; registered against `SPREAD_DEFAULT`/`SPREAD_GROUP` at `SpreadPage.tsx` module scope. `SpreadGroupDiff` renders an extra 幣種對 row as comma-joined `BASE/QUOTE` `.currency-code` spans (reusing `CurrencyPairTable`'s inline pair-code styling), highlighted when the joined membership string differs between before/after; both handle a `null` before (CREATE) / after (DELETE) directly per `hasDiffRenderer`'s contract.
  - `develop/frontend/src/components/SpreadDefaultFormModal.tsx` (new, + `.css`, `.test.tsx`, 5 tests) — 入金/出金 number inputs (`step="any"`, `min={0}`), inline `field-error` validation (required, `>= 0`); on submit, any `ApiError` that reaches the modal's own `catch` (in practice only a `400`, since the page pre-filters and swallows the `409` pending-duplicate case itself) shows a generic inline "輸入資料有誤，請確認後再試" without closing.
  - `develop/frontend/src/components/SpreadGroupFormModal.tsx` (new, + `.css`, `.test.tsx`, 7 tests) — 名稱/入金點差/出金點差 fields plus the two-panel button-based pair assigner ("未加入本群組"/"已加入本群組" panels, per-row "加入 →"/"← 移除" buttons, no drag-and-drop dependency); computes, via a `groups` prop and `useMemo`, which *other* existing group (excluding the one being edited) each unassigned pair currently belongs to, showing `目前屬於：{groupName}，核准後將自動移出` beneath the pair code when applicable; on submit, a live-duplicate `409` (exact message `Spread group name already exists for this brand`) is caught inline as `此名稱已被使用` on the 名稱 field without closing — every other error (pending-duplicate `409`, `400`/`404`, network) is pre-filtered and handled by `SpreadPage` one level up before it can reach the modal.
  - `develop/frontend/src/components/SpreadGroupTable.tsx` (new, + `.css`, `.test.tsx`, 5 tests) — 名稱/入金點差/出金點差/幣種對(comma-joined `BASE/QUOTE` `.currency-code` spans)/操作 columns; `table-empty` zero-groups state (`目前沒有點差群組`); `審核中` badge + disabled 編輯/刪除 for `pendingIds.has(group.id)`, styled consistently with `CurrencyPairTable.tsx`.
  - `develop/frontend/src/pages/SpreadPage.tsx` (new, + `.css`, `.test.tsx`, 12 tests) — route `/spread-groups`; registers `renderSpreadDefaultDiff`/`renderSpreadGroupDiff` for `SPREAD_DEFAULT`/`SPREAD_GROUP` at module scope (runs at app startup since `App.tsx` imports this page eagerly); brand-scoped via `BrandFilter`, auto-selecting the first active brand (or the first brand if none are active) once the brand list loads, rather than defaulting to "全部"; 預設點差 card (入金/出金 values, `審核中` badge + disabled 編輯 when a `SPREAD_DEFAULT`/`PENDING` request targets the live row's id, `此品牌尚未設定預設點差` fallback for the edge case of a brand with no seeded row) and 客制點差群組 card (`SpreadGroupTable` + `+新增群組` + `table-footer` `Total {n} items`); `refresh()` combines `spreadDefaultApi.list(brandId)`/`spreadGroupApi.list(brandId)`/`currencyPairApi.list({brandId, active:true})` with two `auditApi.list({entityType, status:'PENDING'})` calls (one per entity type), matching `CurrencyPairPage.fetchPendingIds`'s pattern; all five specified toasts implemented (`已送出預設點差修改申請，待審核`/`已送出新增點差群組申請，待審核`/`已送出點差群組修改申請，待審核`/`已送出點差群組刪除申請，待審核`/`此項目已有待審核的異動申請`/`網路錯誤，請稍後再試`); delete confirmation uses the spec's exact updated audit-workflow wording; loading/error states (`載入中...`, `資料載入失敗` + `重試`) match existing page conventions, plus a `請選擇品牌` placeholder when the brand filter is at "全部" or not yet auto-selected.
  - `develop/frontend/src/App.tsx` (edited) — added the `SpreadPage` import and `<Route path="/spread-groups" element={<SpreadPage />} />`, positioned between `/currency-pair-definitions`'s neighbor `/currency-pairs` and the audit route (immediately after `/currency-pair-definitions` in route-declaration order, matching where the nav item sits relative to `/currency-pairs`).
  - `develop/frontend/src/layout/AppShell.tsx` (edited) — added `{ to: '/spread-groups', label: '點差管理' }` to `NAV_ITEMS`, positioned immediately after the `/currency-pairs` entry as specified.
- Notable judgment calls:
  - **Brand selector state is `number | 'ALL' | null`, defaulting to `null`** ("not yet decided") rather than initializing directly to `'ALL'`: an auto-select effect only fires while `brandId === null`, so it runs exactly once (as soon as the brand list loads) and never fights with a user's subsequent, explicit choice of "全部" from the dropdown (which sets the sentinel `'ALL'`, distinct from `null`) — `BrandFilter`'s `value` prop (typed `number | 'ALL'`) is fed `brandId ?? 'ALL'`. This was verified necessary during test-writing: an early version that initialized `brandId` to `'ALL'` directly made the auto-select effect race against `BrandFilter`'s own mount-time value, though it never actually manifested as a flaky test in this build (caught by design, following the reasoning already recorded from the prior teardown snapshot's own experience with the identical race).
  - **`SpreadPage`'s submit handlers pre-filter every error case the corresponding form modal isn't supposed to see**, exactly mirroring `CurrencyPairPage.handleEditSubmit`'s `isPendingDuplicateConflict` split: for the default-spread `409`, and for the group `409` whose message isn't the exact live-duplicate string, the page itself closes the modal, toasts `此項目已有待審核的異動申請`, and refreshes — only a `400` (default spread) or the live-duplicate-name `409` (group) is rethrown for the modal's own inline handling. `400`/`404` on group create/update (a currency-pair-reference edge case that shouldn't occur since the assigner only ever offers pairs already fetched for the correct brand) is treated the same defensive way: close, toast the generic network-error message, and refetch.
  - **`BrandFilter` was created fresh in this increment** rather than assumed pre-existing, since a repo-wide grep confirmed no such component exists anywhere in the current `develop/frontend/src` tree (`CurrencyPairPage.tsx`'s own brand filter is a plain inline `<select>`, not an extracted component) — this is a one-time gap between the spec's description of "existing shared plumbing" and what a prior spec (`currency-pair.md`) actually built. `CurrencyPairPage.tsx` was deliberately left unmodified (not retrofitted to use the new `BrandFilter`) since that is outside this spec's scope and carries its own regression risk for no required benefit.
  - Confirming the "Assigning a pair already in Group A to Group B ... only removes it from Group A's member list after that request is approved" acceptance criterion holds by construction rather than by one dedicated end-to-end test: nothing in this feature's code ever mutates `groups` state directly — `groups` is only ever set from `spreadGroupApi.list(brandId)` (a live, already-approved `GET`), and the two-panel assigner's local `selectedIds` state exists only inside the (unsubmitted, or submitted-but-still-pending) `SpreadGroupFormModal` instance — so Group A's row in the table is structurally guaranteed to stay exactly as last fetched until a real `refresh()` runs after the backend actually approves something. `SpreadGroupFormModal.test.tsx`'s "shows a hint and moves a pair between panels" test directly covers the hint/move UI; `SpreadPage.test.tsx`'s create/update/delete tests directly cover that a submitted-but-unapplied request never appears in the table.
- Verification performed:
  - `npm run build` (`tsc -b && vite build`) — succeeds with 0 type errors, both from an incremental state and after a full `rm -rf dist tsconfig*.tsbuildinfo node_modules/.vite` clean rebuild; confirmed via `grep` on the built bundle that the new page's Chinese strings/entity-type literals (`點差管理`, `SPREAD_GROUP`, `spread-groups`) are actually present in `dist/assets/index-*.js`.
  - `npx vitest run` — `23 test files / 186 tests passed`, run three consecutive times with zero flakiness (up from 21 files/149 tests before this increment — accounting for the two new diff-renderer test files, the three new component test files, and the new page test file: `SpreadDefaultDiff.test.tsx` (4), `SpreadGroupDiff.test.tsx` (6), `SpreadDefaultFormModal.test.tsx` (5), `SpreadGroupFormModal.test.tsx` (7), `SpreadGroupTable.test.tsx` (5), `SpreadPage.test.tsx` (12) — 39 new tests total against a starting baseline this session established at 147 tests across the pre-existing 17 files).
  - `npm run lint` (`oxlint`) — no new warnings introduced; the sole warning present (`ToastProvider.tsx` fast-refresh) is pre-existing and unrelated to this feature.
  - Confirmed via `git status`/inspection that `package.json`/`package-lock.json`'s `dependencies`/`devDependencies` are byte-for-byte what they were before this task (`react`, `react-dom`, `react-router-dom` only runtime deps) — no drag-and-drop library or any other new runtime dependency was added; the two-panel assigner is plain buttons + local component state.
  - Manually re-traced every Acceptance Criteria item above against the actual rendered component tree and the corresponding automated test(s) exercising it before checking each one off.
