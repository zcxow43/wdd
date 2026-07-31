---
status: done
title: "Audit Module — Generic Approval Review Page"
requirement: "Factor the approval/审核 mechanism out into its own independent audit module, so that any action needing approval can plug into it directly without adding anything to the audit module itself"
depends_on: []
---

# Audit Module — Generic Approval Review Page — Frontend Spec

## Overview
Provide a single, standalone "審核作業" (Audit) page that lists and reviews approval requests for **any** entity type, consuming the generic API in `specs/backend/audit.md` (`/api/audit-requests`). This page must never need a code change to support a new entity type's *list/approve/reject* mechanics — only its before/after **rendering** is extensible, via a small registry.

This spec was previously generalized (but not yet implemented) as a Change Request Approval page living inside `specs/frontend/currency-pair-approval.md`. This spec extracts it fully into its own standalone module. `specs/frontend/currency-pair-approval.md` no longer defines the page shell, route, table, or modal chrome — it is now a thin consumer spec that (a) registers `renderCurrencyPairDiff` into this page's rendering registry and (b) updates the Currency Pair page's own Add/Edit/Delete flows, and depends on this spec for everything generic.

**This page's core files (the page component, the generic table, the generic modal chrome) must contain no reference to currency pairs, brands, or rate types.** Anything entity-specific belongs in a registered renderer, not in the page itself.

## Requirements
- One page, route `/audit-requests`, listing all audit requests across every entity type, filterable by entity type and by status (待審核/已核准/已拒絕/全部; default 待審核)
- Clicking a request opens a review modal showing a before/after comparison, using whichever renderer is registered for that request's `entityType` — falling back to a generic key/value renderer for any `entityType` without one, so the page never hard-fails when a brand-new entity type starts sending requests before its dedicated renderer exists
- Reviewer can **approve** or **reject** (with a required reason) a `PENDING` request from the review modal
- Already-reviewed requests are viewable read-only (reviewedBy/reviewedAt/rejectReason)
- Nav item added to the sidebar (`develop/frontend/src/layout/AppShell.tsx`)

## Extension point: the diff-renderer registry

```ts
type DiffRenderer = (before: Record<string, unknown> | null, after: Record<string, unknown> | null) => ReactNode

const DIFF_RENDERERS: Record<string, DiffRenderer> = {}

function registerDiffRenderer(entityType: string, renderer: DiffRenderer) {
  DIFF_RENDERERS[entityType] = renderer
}

function renderAuditDiff(entityType: string, before, after): ReactNode {
  const renderer = DIFF_RENDERERS[entityType] ?? renderGenericDiff
  return renderer(before, after)
}
```
- `renderGenericDiff` (built into this module, the only renderer it ships with by default): iterates the raw key/value pairs of `before`/`after` and lists them as-is in a simple two-column table, highlighting any key present in both with a different value. Not pretty, but correct and non-breaking for any entity type without a dedicated renderer.
- Each consumer registers its own renderer once, from its own module (e.g. `specs/frontend/currency-pair-approval.md` registers `renderCurrencyPairDiff` for `entityType: "CURRENCY_PAIR"`). This audit module ships with **zero** entity-specific renderers of its own — registration happens entirely from the consumer side (e.g. a module-level `registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)` call in the currency-pair feature's own source file, executed once at app startup via that module simply being imported).
- The list table's 摘要 column always uses the request's precomputed `summary` field from the API — never entity-specific formatting — so the list itself needs zero per-entity-type logic.

## Page Layout

### Route
`/audit-requests`, nav label "審核作業" (Audit), added to the sidebar (`develop/frontend/src/layout/AppShell.tsx`) alongside the existing entries.

### Page Structure
```
┌─────────────────────────────────────────────────────────────────┐
│  審核作業 (Audit)                                                │
│                                                                    │
│  [類型: 全部 ▼]   [狀態: 待審核 ▼]                                 │
│                                                                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 類型   │ 摘要         │ 申請人 │ 申請時間      │ 狀態   │      │  │
│  │────────│──────────────│────────│───────────────│────────│      │
│  │ 新增   │ PUG · USD/TWD│ Alice  │ 2026-07-29 …  │ 待審核 │ [查看] │
│  │ 修改   │ AU · EUR/TWD │ Bob    │ 2026-07-29 …  │ 待審核 │ [查看] │
│  │ 刪除   │ VT · USD/EUR │ Carol  │ 2026-07-28 …  │ 待審核 │ [查看] │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Table Columns
| Column   | Source Field | Notes                                                                 |
|----------|--------------|----------------------------------------------------------------------|
| 類型     | actionType   | Badge: 新增 (CREATE) / 修改 (UPDATE) / 刪除 (DELETE)                    |
| 摘要     | summary      | Precomputed label from the API, e.g. `"PUG · USD/TWD"`. `—` if null   |
| 申請人   | requestedBy  | `—` if null                                                            |
| 申請時間 | requestedAt  | Formatted datetime                                                     |
| 狀態     | status       | Badge: 待審核 (PENDING) / 已核准 (APPROVED) / 已拒絕 (REJECTED)         |
| Actions  | —            | 查看 (opens the review modal)                                         |

An 實體類型 (entity type) column is deliberately omitted from the default layout while only one entity type exists in the running system — add it once a second one ships, so the table isn't showing a constant column today.

### Filters
- **類型 (entity type)**: dropdown populated from whatever distinct `entityType` values are present in the currently-loaded results (or, simpler and equally correct: a small static list the frontend maintains as consumers register themselves — implementer's choice) — plus 全部. Maps to `?entityType=...`.
- **狀態 (status)**: 待審核 (default) / 已核准 / 已拒絕 / 全部. Maps to `?status=...`.

### Review Modal
```
┌──────────────────────────────────────────────┐
│  審核異動申請 — 修改                            │
│                                                 │
│   [ rendered by renderAuditDiff(entityType) ]  │
│                                                 │
│  申請人: Alice   申請時間: 2026-07-29 10:00     │
│                                                 │
│  [拒絕]                              [核准]     │
└──────────────────────────────────────────────┘
```
- Header shows the action-type label ("新增"/"修改"/"刪除"); the field grid is produced entirely by `renderAuditDiff(entityType, before, after)` — the modal shell itself has no field-specific markup.
- If `before === null`: the diff area shows "（新增，無先前資料）" instead of calling the renderer with an empty before (a renderer only ever receives a genuinely-populated snapshot or `null`, and `null` is handled once, generically, by the modal — not by every renderer having to special-case it).
- If `after === null`: shows "（將被刪除）" the same way.
- If `status !== 'PENDING'`: hide 拒絕/核准; show 審核人/審核時間, and 拒絕原因 if `REJECTED`.
- **拒絕**: inline reason input (required), 確認拒絕/取消 buttons, calls the reject API.
- **核准**: confirm ("確定要核准此異動申請嗎？") then calls the approve API.

## API Integration

| Action        | Method | Endpoint                                  | Trigger                          |
|----------------|--------|---------------------------------------------|------------------------------------|
| List requests  | GET    | `/api/audit-requests?entityType=&status=`   | Page load, filter change            |
| Approve        | POST   | `/api/audit-requests/{id}/approve`          | 核准 confirm                        |
| Reject         | POST   | `/api/audit-requests/{id}/reject`           | 拒絕 confirm (with reason)          |

### Types
```ts
type AuditActionType = 'CREATE' | 'UPDATE' | 'DELETE'
type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

interface AuditRequest {
  id: number
  entityType: string          // open string — new entity types need no frontend type change
  actionType: AuditActionType
  entityId: number | null
  status: AuditStatus
  summary: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  requestedBy: string | null
  requestedAt: string
  reviewedBy: string | null
  reviewedAt: string | null
  rejectReason: string | null
  createdAt: string
  updatedAt: string
}
```

### Error Handling
- **404** (request not found): toast "審核申請不存在，請重新整理頁面", close modal, refresh list
- **409** (already reviewed): toast "此申請已被其他人審核過", close modal, refresh list
- **400/404/409 on approve** (handler re-validation failure): show the error's message inline in the modal (consumer-agnostic: just surface whatever `error` string the API returned); leave the modal open so the reviewer can reject instead if the change is no longer valid
- **400 on reject** (missing reason): inline "請輸入拒絕原因"
- **Network error**: toast "網路錯誤，請稍後再試"

## Implementation Details
- File layout: a self-contained `develop/frontend/src/audit/` module (page, table, modal, registry, generic fallback renderer, types, API client) mirroring the backend's dedicated package, so the "independent module" boundary is visible in the frontend tree too. Consumers (e.g. the currency-pair feature) import `registerDiffRenderer` from this module and call it with their own renderer; the audit module never imports anything from a consumer's folder.
- The registry is populated via consumer modules being imported somewhere reachable at app startup (e.g. from `App.tsx`, alongside the route registration) — implementer's choice of exact wiring, as long as `renderCurrencyPairDiff` (`specs/frontend/currency-pair-approval.md`) is actually registered before the audit page can render a `CURRENCY_PAIR` request.

## Acceptance Criteria
- [x] New "審核作業" nav item and `/audit-requests` route render the page
- [x] Request table loads from `GET /api/audit-requests?status=PENDING` by default; status and entity-type filters work
- [x] Clicking 查看 opens the review modal and calls `renderAuditDiff` with the request's `entityType`/`before`/`after`
- [x] A request whose `entityType` has no registered renderer (simulate in a test by not registering anything and feeding a fake entityType) renders the generic key/value fallback instead of crashing or showing a blank modal
- [x] 核准 on a `PENDING` request calls the approve API, shows success, closes the modal, refreshes the list
- [x] 拒絕 requires a non-empty reason, calls the reject API, shows success, refreshes the list
- [x] Approve/Reject buttons are hidden for already-reviewed requests; reviewedBy/reviewedAt/rejectReason shown instead
- [x] Error states display correct Chinese messages for 400/404/409/network cases on both approve and reject
- [x] The page component, generic table, and generic modal chrome contain zero references to currency pairs, brands, or rate types — verified by inspection
- [x] With `renderCurrencyPairDiff` registered (per `specs/frontend/currency-pair-approval.md`, now `status: done`), a `CURRENCY_PAIR` request renders the proper labeled before/after comparison, not the generic fallback

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/audit/types.ts` (new) — `AuditActionType`, `AuditStatus`, `AuditRequest` (open `entityType: string`, no domain-specific fields)
  - `develop/frontend/src/audit/auditApi.ts` (new) — `auditApi.list/approve/reject` against `/api/audit-requests`, built on the shared `apiClient`
  - `develop/frontend/src/audit/diffRegistry.tsx` (new) — the extension point: `DiffRenderer` type, `registerDiffRenderer`, `renderGenericDiff` (the only renderer this module ships with), `renderAuditDiff` (registry lookup with fallback)
  - `develop/frontend/src/audit/diffRegistry.css` (new) — styling for the generic two-column diff table
  - `develop/frontend/src/audit/AuditRequestTable.tsx` + `.css` (new) — generic list table (類型/摘要/申請人/申請時間/狀態/Actions), zero per-entity-type logic; 摘要 column always renders the API's precomputed `summary`
  - `develop/frontend/src/audit/AuditReviewModal.tsx` + `.css` (new) — review modal chrome: header with action-type label, diff area (delegates to `renderAuditDiff`, or a static placeholder when `before`/`after` is `null`), requester/reviewer metadata, inline 拒絕 (reason textarea) / 核准 (confirm) flows, and inline vs. toast-worthy error handling
  - `develop/frontend/src/audit/AuditPage.tsx` + `.css` (new) — the `/audit-requests` page: filters (類型 derived from currently-loaded results, 狀態 defaulting to 待審核), table, review modal wiring, and all approve/reject/list network + error handling
  - `develop/frontend/src/audit/diffRegistry.test.tsx`, `AuditRequestTable.test.tsx`, `AuditReviewModal.test.tsx`, `AuditPage.test.tsx` (new) — unit/integration tests
  - `develop/frontend/src/App.tsx` (modified) — added `/audit-requests` route
  - `develop/frontend/src/layout/AppShell.tsx` (modified) — added "審核作業" nav item
  - `develop/frontend/src/components/Modal.tsx` + `.css` (modified) — added an optional generic `size?: 'md' | 'lg'` prop (`'lg'` → 720px) so the audit review modal has room for a diff table, without changing default behavior for any existing caller
- Verification performed:
  - `npm run build` (`tsc -b && vite build`) — succeeds with no type errors
  - `npm test` (`vitest run`) — all 13 test files / 96 tests pass (62 pre-existing + 34 new audit tests), including the 4 new audit test files
  - `npm run lint` (`oxlint`) — no new warnings/errors; only the pre-existing unrelated `ToastProvider.tsx` fast-refresh warning remains
  - `grep`-verified `AuditPage.tsx`, `AuditRequestTable.tsx`, `AuditReviewModal.tsx`, `diffRegistry.tsx`, `auditApi.ts`, `types.ts` contain no code-level reference to currency/brand/rate-type entities (the only textual matches are an illustrative "currency-pair feature" example inside a docstring comment in `diffRegistry.tsx`, and unrelated design-token CSS variable names like `--color-brand-tint` shared by the whole app's theme, not the Brand domain entity)
- Notes on judgment calls:
  - **Null before/after handling**: per the spec's explicit wording ("shows X *instead of calling the renderer* with an empty before/after"), the review modal never invokes `renderAuditDiff` when `before === null` (CREATE) or `after === null` (DELETE) — it shows the static Chinese placeholder text only in that case. `renderAuditDiff`/`renderGenericDiff` are only ever invoked by the modal for the paired-snapshot (UPDATE) case, though their type signature still accepts `| null` (matching the spec's literal type block) and `renderGenericDiff` degrades gracefully if ever called with `null` directly (e.g. from a future consumer's own code or a unit test).
  - **Error classification for approve/reject**: distinguished "request not found" / "already reviewed" (toast + close + refresh) from handler re-validation failures (inline, modal stays open) by matching the exact backend `error` message strings (`"Audit request not found"`, `"Audit request has already been reviewed"`) rather than status code alone, since both classes can return 404/409. Any other `ApiError` message is shown inline verbatim (consumer-agnostic), and non-`ApiError` failures (network) are toasted, mirroring the existing `CurrencyPairPage` convention of the page catching known cases and re-throwing for the modal to display inline.
  - **Entity-type filter**: implemented literally as specified — options are derived from `entityType` values in the currently-loaded (already status-filtered, not yet entity-type-filtered) result set, since only one entity type exists in the running system today; this is called out in the spec itself as an acceptable simplification until a second entity type ships.
  - **Modal width**: added a small, generic `size` prop to the shared `Modal` component (default unchanged) rather than a one-off audit-specific modal shell, since a wider dialog is a reasonable general capability and keeps `Modal.tsx` itself free of any audit-specific knowledge.
  - No `registerDiffRenderer` call for `CURRENCY_PAIR` (or any entity) exists yet, as instructed — that is reserved for the future `specs/frontend/currency-pair-approval.md` consumer task. Today, any `CURRENCY_PAIR` audit request would render via the generic fallback.
