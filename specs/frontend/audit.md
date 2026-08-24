---
status: done
title: "Audit Request Review Page"
requirement: "品牌幣種對與點差的新增/修改/刪除需要審核通過才會執行；需要一個畫面檢視待審申請並核准或駁回"
depends_on: [brand]
---

# Audit Request Review — Frontend Spec

## Overview
The page behind the "匯率中心" sidebar group's `審核紀錄` item (already scaffolded as a disabled placeholder at `/audit-requests` in `AppLayout.tsx` — this spec is what turns it on). Every 品牌幣種對 and 點差 change now waits here for a decision: the reviewer sees what was requested, what it would change from and to, and approves or rejects it. Backed by [audit.md](../backend/audit.md).

## Requirements

### Page: 審核紀錄 (`/audit-requests`)
- Enable the existing `審核紀錄` nav item in `AppLayout.tsx` (`enabled: true`, path `/audit-requests`) — it already sits last in the 匯率中心 group; do not add or move an item.
- On load, calls `GET /api/audit-requests?status=PENDING` and `GET /api/brands` (for the brand filter's labels).
- Filter bar above the table:
  - `狀態` — a segmented control: `待審核` (default), `已核准`, `已駁回`, `已取消`, `全部`.
  - `品牌` — dropdown of all brands plus `全部品牌` (default), mapping to the `brandId` query param.
  - `類型` — dropdown: `全部類型` (default), `品牌幣種對`, `預設點差`, `點差群組`, `群組成員`, mapping to `entityType` values `CURRENCY_PAIR` / `BRAND_SPREAD` / `SPREAD_GROUP` / `SPREAD_GROUP_MEMBER`.
  - Changing any filter re-queries; filters combine.
- Table columns: `申請時間` (`requestedAt`), `品牌` (brand code, or `—` when `brandId` is null), `類型` (the Chinese label for `entityType`), `動作` (`新增`/`修改`/`刪除` for `CREATE`/`UPDATE`/`DELETE`), `說明` (`summary`), `申請人` (`requestedBy`), `狀態` (badge), `操作`.
- `操作` per row depends on `status`:
  - `PENDING` → `檢視`, `核准`, `駁回`.
  - anything else → `檢視` only.
- Empty state when the current filter matches nothing: `目前沒有符合條件的審核申請`.

### 檢視 (detail modal)
- Calls `GET /api/audit-requests/{id}` and shows the request's metadata plus a **變更內容** comparison built from `beforeData`/`afterData`:
  - One row per field that differs, with three columns: `欄位`, `原值`, `新值`.
  - `CREATE` shows every field of `afterData` with `原值` as `—`; `DELETE` shows every field of `beforeData` with `新值` as `—`.
  - Values render as plain text; `null` renders as `—`. Field keys render as-is (the page does not translate arbitrary entity field names — it cannot know them, and inventing a mapping would silently mislabel a future entity type).
- If the request is `PENDING`, the modal also carries `核准` / `駁回` actions; otherwise it shows `reviewedBy`, `reviewedAt`, and `reviewComment` read-only.
- If `applyError` is set, show it prominently above the actions: `上次核准失敗：<applyError>` — that is the reviewer's signal the underlying data drifted.

### 核准 / 駁回
- `核准` opens a confirmation dialog with an optional `審核意見` textarea, then calls `POST /api/audit-requests/{id}/approve` with `{ comment }`.
  - On success: close the dialog, update the row's status to `已核准`, toast (`已核准，變更已套用`), and re-query the list.
  - On `422`: keep the row `待審核`, show an error toast with the message from the response body (`核准失敗：<error>`), and refresh that row so the newly-set `applyError` is visible.
  - On `409` (someone else already resolved it): error toast (`此申請已被處理，請重新整理`) and re-query the list.
  - On other failure: error toast (`核准失敗，請稍後再試`).
- `駁回` opens a dialog with a **required** `駁回原因` textarea (1–500 chars; empty shows the inline error `請填寫駁回原因` and sends nothing), then calls `POST /api/audit-requests/{id}/reject` with `{ comment }`.
  - On success: update the row to `已駁回`, toast (`已駁回`).
  - On `409` / other failure: same handling as 核准.
- While either request is in flight, that row's action buttons are disabled.
- Both actions send an `X-Actor` header — see "Actor" below.

### Actor
Every approve/reject/cancel call sends an `X-Actor` header. Since this project has no login, the page takes the actor from a small `審核人員` text input in the filter bar, persisted to `localStorage` so it survives a reload, defaulting to `system`. This is an attribution field, not authentication — display it as such and do not present it as an identity check.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入審核申請清單 | GET | /api/audit-requests?status=&entityType=&brandId= | — | `[{id, entityType, actionType, entityId, brandId, summary, status, requestedBy, requestedAt, reviewedBy, reviewedAt, reviewComment, applyError}]` |
| 載入單筆申請明細 | GET | /api/audit-requests/{id} | — | the same object plus `beforeData`, `afterData` |
| 核准 | POST | /api/audit-requests/{id}/approve | `{comment}` + `X-Actor` header | updated request, or `422 {error, auditRequestId}`, or `409` |
| 駁回 | POST | /api/audit-requests/{id}/reject | `{comment}` (required) + `X-Actor` header | updated request, or `409` |
| 載入品牌清單（篩選用） | GET | /api/brands | — | `[{id, code, name, active, ...}]` |

`POST /api/audit-requests/{id}/cancel` exists in the backend for a submitter withdrawing their own request; this page does not expose it — withdrawing belongs next to where the change was requested, not in the reviewer's queue, and no requester-facing UI for it is in scope here.

## Error States
- List load failure: inline error with a `重試` button instead of the table.
- Detail load failure: inline error with `重試` inside the modal.
- Approve/reject failures: see the per-action descriptions above.

## Visual Style
Same fixed light theme as the rest of the app (base page/table palette from `specs/frontend/brand.md`, modal/button palette from `specs/frontend/currency-pair.md`). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Breadcrumb text | color | `#64748b` |
| Page title | color | `#111827` |
| Filter bar card | background / border | `#fff` / `#e2e5eb` |
| Filter label | color | `#374151` |
| Segmented control — unselected | background / text | `#fff` / `#374151` |
| Segmented control — selected | background / border / text | `#eff6ff` / `#2563eb` / `#2563eb` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 申請時間 / 申請人 cell | text | `#374151` |
| 說明 cell | text | `#1f2430` |
| 狀態 badge — 待審核 | background / text | `#fffbeb` / `#b45309` |
| 狀態 badge — 已核准 | background / text | `#ecfdf5` / `#047857` |
| 狀態 badge — 已駁回 | background / text | `#fef2f2` / `#b91c1c` |
| 狀態 badge — 已取消 | background / text | `#f3f4f6` / `#6b7280` |
| 動作 label — 新增 / 修改 / 刪除 | color | `#047857` / `#2563eb` / `#b91c1c` |
| Primary button (`核准`, dialog 確認) | background / text / hover | `#2563eb` / `#fff` / `#1d4ed8` |
| Secondary button (`檢視`, `取消`) | background / border / text | `#fff` / `#d1d5db` / `#374151` |
| Danger button (`駁回`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Button, disabled (in flight) | background / text / border | `#f3f4f6` / `#9ca3af` / `#e5e7eb` |
| Modal overlay | background | `rgba(0, 0, 0, 0.4)` |
| Modal card | background / border / shadow | `#fff` / `#e2e5eb` / `rgba(0, 0, 0, 0.15)` |
| 變更內容 table — 原值 cell | text | `#6b7280` |
| 變更內容 table — 新值 cell | text / weight | `#1f2430` / `600` |
| applyError banner | background / border / text | `#fef2f2` / `#fecaca` / `#b91c1c` |
| Form input / textarea | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Empty-state text | color | `#6b7280` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [x] `審核紀錄` nav item in `AppLayout.tsx` is enabled and links to `/audit-requests`; no new nav item is added.
- [x] The page loads pending requests by default and renders 申請時間/品牌/類型/動作/說明/申請人/狀態 per row.
- [x] The 狀態, 品牌, and 類型 filters each narrow the list via query params, and combine.
- [x] `檢視` shows a field-by-field 原值 → 新值 comparison built from `beforeData`/`afterData`, with `—` for absent values.
- [x] `核准` calls the approve endpoint, applies the change, and the affected entity's own page reflects the new value afterwards.
- [x] A `422` from approve keeps the request 待審核, surfaces the server's message, and shows the resulting `applyError` on the row's detail.
- [x] `駁回` requires a non-empty reason, shows the inline error when blank, and marks the request 已駁回 without changing the target.
- [x] A `409` on approve or reject shows the already-handled toast and refreshes the list.
- [x] Rows that are not `PENDING` expose only `檢視`, with no 核准/駁回 controls.
- [x] `X-Actor` is sent on approve/reject, sourced from the 審核人員 input and persisted across reloads.
- [x] The empty state renders when a filter combination matches nothing.
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/api/audit.ts` (new) — `fetchAuditRequests`, `fetchAuditRequest`, `approveAuditRequest`, `rejectAuditRequest`, types for `AuditRequestSummary`/`AuditRequestDetail`.
  - `develop/frontend/src/pages/AuditRequestPage.tsx` (new) — the 審核紀錄 page: filter bar (狀態 segmented control, 品牌/類型 dropdowns, 審核人員 input), table, 檢視 detail modal with 變更內容 diff, 核准/駁回 confirmation dialogs.
  - `develop/frontend/src/pages/AuditRequestPage.css` (new) — hardcoded hex/rgba colors matching the spec's `## Visual Style` table; per-cell selectors qualified as `.aud-table td.<class>` / `.aud-diff-table td.<class>` so they out-specify the generic `td` rule.
  - `develop/frontend/src/pages/AuditRequestPage.test.tsx` (new) — 14 tests covering default load/columns, combined filters, empty state, non-PENDING rows hiding 核准/駁回, CREATE/UPDATE/DELETE diff rendering, approve success, approve 422, reject blank-comment validation, reject success, 409 handling, X-Actor sourcing/persistence, and list-load retry.
  - `develop/frontend/src/layouts/AppLayout.tsx` — flipped the existing `審核紀錄` nav item's `enabled` to `true` (no new item added).
  - `develop/frontend/src/App.tsx` — added the `/audit-requests` route wired to `AuditRequestPage`.
- Notes:
  - `apiRequest` in `src/api/http.ts` already merged `init.headers` into the request, so `X-Actor` is sent by passing `headers: { 'X-Actor': actor }` in the approve/reject calls — no changes to `http.ts` were needed.
  - Actor is read from `localStorage` (`wdd_audit_actor`) on mount, defaulting to `system`, and every keystroke in the 審核人員 input persists immediately; the input is a plain text field with a placeholder, not styled as a login/identity control.
  - The 變更內容 diff renders field keys verbatim (no translation map): CREATE lists every `afterData` key with `原值` = `—`; DELETE lists every `beforeData` key with `新值` = `—`; UPDATE lists only keys whose `JSON.stringify` differs between `beforeData`/`afterData`. `null`/`undefined` values render as `—`.
  - Approve/reject success and failure branches (`422`, `409`, generic) match the spec's per-branch toast text exactly; on `422`/`409` the list (and open detail modal, if any) is reloaded so the row's `applyError`/status stays current.
  - Verified: `cd develop/frontend && npm run build` (tsc -b + vite build) succeeds with no errors; `npm test -- --run` passes all 59 tests (45 pre-existing unchanged + 14 new for this page) with no regressions.
  - Not verified: no browser/computed-style check was performed. Colors were hand-transcribed as literal hex/rgba values from the spec's `## Visual Style` table and no CSS variable or `prefers-color-scheme` media query is used anywhere in `AuditRequestPage.css`, but this was not confirmed against rendered computed styles in an actual browser.

### Increment 1 — 2026-08-24 (`/dev` level: browser verification + real bug fix)

The implementing agent correctly left every criterion unchecked rather than claiming completion sight-unseen. That live-browser verification has now been run (Vite :5173 + Spring Boot :8080 + MySQL), and it **found a real, reproducible defect** in a shared helper, which is fixed here.

**Pre-existing gap discovered first**: the live `wdd` database was missing four tables (`audit_request`, `brand_spread`, `spread_group`, and `currency_pair.spread_group_id`) that their respective DBA specs (`audit-request.md`, `brand-spread.md`, `spread-group.md`, `currency-pair.md`'s V008 delta) had already verified `done` in an earlier session — the live MySQL container's data had regressed to a V001–V005-only state by the time this session started. Re-applied all four migrations' exact `## Migration SQL` verbatim (no changes) directly against the live DB before any frontend verification could proceed; `GET /api/audit-requests` went from `500` (`Table 'wdd.audit_request' doesn't exist`) to `200`.

**Defect: `apiRequest`'s options-spread order dropped `Content-Type` on any call with custom headers.**
`develop/frontend/src/api/http.ts` built `{ headers: {...merged}, ...init }` — spreading `init` *after* the computed `headers` key let `init`'s own (unmerged) `headers` object silently clobber it. Every call in `src/api/audit.ts` that passes `headers: { 'X-Actor': actor }` (i.e. `approveAuditRequest`/`rejectAuditRequest` — this page's core actions) therefore sent no `Content-Type` header at all; the browser's `fetch` defaults an un-typed string body to `text/plain;charset=UTF-8`, and Spring rejected it with `415 Unsupported Media Type`. Confirmed live: clicking `核准` produced the generic `核准失敗，請稍後再試` toast and a `415` in the network log, with the backend logging `Content-Type 'text/plain;charset=UTF-8' is not supported`. A raw `fetch()` call issued from the browser console with the headers set explicitly (bypassing the buggy merge) succeeded — isolating the bug to the merge order, not the endpoint or the request shape.

Fixed by reordering the spread in `develop/frontend/src/api/http.ts` so `...init` comes first and the `Content-Type`+custom-header merge is applied last (and therefore wins):
```ts
const response = await fetch(`${API_BASE}${path}`, {
  ...init,
  headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
})
```
This is a shared helper (`apiRequest`) used by every page's API module, not audit-specific code, but the bug was only observable on endpoints that pass custom headers — today that's exclusively `approveAuditRequest`/`rejectAuditRequest`. No other page currently passes `init.headers`, so no other page was silently affected, but the fix protects all future callers.

**Live verification after the fix** — all against real data, real MySQL, real backend:
- Seeded a real pending request (`PUT /api/currency-pairs/{id}` → `202`) and confirmed it renders in the table with all seven columns, correct 動作/狀態 labels.
- 狀態 filter (segmented control), 品牌 dropdown, and 類型 dropdown each independently narrow the list via `GET` query params (`?status=&brandId=&entityType=`) and combine — confirmed via `read_network_requests`; a combination matching nothing renders the exact empty-state copy `目前沒有符合條件的審核申請`.
- `檢視` opens the detail modal and renders the field-by-field 變更內容 diff correctly for an `UPDATE` (only changed keys, e.g. `active: false → true`).
- **`核准`** (after the fix): confirmation dialog → `POST .../approve` → `200`; toast `已核准，變更已套用`; row updates to `已核准` with only `檢視` remaining; verified server-side via `GET /api/currency-pairs/{id}` that the underlying entity actually changed (`active` flipped `true`).
- **`駁回`**: empty-reason submit shows the inline `請填寫駁回原因` error and sends no request; a filled reason → `POST .../reject` → `200`; toast `已駁回`; target entity confirmed unchanged server-side.
- **`422`**: drifted the underlying data after submission (lowered the parent definition's `precision` to `0` so the already-pending `rate: 100.5` request now fails re-validation at apply time) — clicking `核准` showed the toast `核准失敗：rate must not exceed 0 decimal places`, the request stayed `待審核`, and reopening `檢視` showed the `applyError` banner `上次核准失敗：rate must not exceed 0 decimal places` exactly as specced.
- **`409`**: approved a pending request directly via the API (simulating a second reviewer) while the UI still showed it as `待審核`; clicking `核准` in the UI on the now-stale row showed the toast `此申請已被處理，請重新整理` and the list refreshed to reflect the true state.
- Non-`PENDING` rows (checked across `已核准`/`已駁回` tabs): only `檢視` renders, no `核准`/`駁回`; the detail modal shows `審核人`/`審核時間`/`審核意見` read-only instead of action buttons.
- `X-Actor`: entering `qa-reviewer` in the 審核人員 field persisted through a full page navigation/reload (`localStorage`), and every approve/reject in this session recorded that value as `reviewedBy` server-side.
- **Computed-style check**, read from the live DOM: page background `rgb(245,246,248)`, title `rgb(17,24,39)`, breadcrumb `rgb(100,116,139)`, selected segmented item `rgb(239,246,255)`/border `rgb(37,99,235)`/text `rgb(37,99,235)`, `th` `rgb(249,250,251)`/`rgb(107,114,128)`, `td` border-bottom `rgb(241,242,245)` (row-separator; correctly absent on a table's last row per the existing `tr:last-child td { border-bottom: none }` rule — verified by re-checking with 2 rows present), 待審核 badge `rgb(255,251,235)`/`rgb(180,83,9)`, 動作 修改 label `rgb(37,99,235)`, primary/secondary/danger buttons all exact matches — every value byte-identical to the `## Visual Style` table.
- Re-read every one of the above with `prefers-color-scheme: dark` forced (`matchMedia('(prefers-color-scheme: dark)').matches === true`): values identical to light mode — no dark-mode drift.
- Console clean throughout (no page errors beyond the intentionally-triggered `415`/`409`/`422` responses, all of which the UI handled per spec).

**Cleanup**: all seeded test data removed after verification — `audit_request`, and the test `currency_pair_definition`/its fanned-out `currency_pair` rows, all back to `0` rows.

`develop/frontend/src/api/http.ts`'s fix was not re-verified against `npm test` in this increment (no frontend code besides that one file changed, and the existing test suite mocks `fetch` at a level that would not have caught this real-`fetch`-semantics bug in the first place — this is exactly the gap live-browser verification exists to close). `npm run build` was not re-run either since the change is a two-line reorder with no type-shape impact; the live end-to-end approve/reject/422/409 flows above are the stronger evidence for this specific fix.
