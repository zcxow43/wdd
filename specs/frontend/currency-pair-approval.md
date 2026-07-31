---
status: done
title: "Currency Pair as an Audit Consumer"
requirement: "Currency pair update/delete must not apply directly — they must be submitted for approval through the standalone audit module, with before/after visible before approving. Create was originally in scope here too, but per a later requirement the page's create action (Add button/create modal) has been removed entirely — a brand's pair can now only come into existence via the global 幣種對主檔 page (specs/frontend/currency-pair-definition.md)."
depends_on: [currency-pair, audit]
---

# Currency Pair as an Audit Consumer — Frontend Spec

## Overview
Currency pair's update/delete submit for approval instead of applying immediately, and are reviewed on the generic Audit page (`specs/frontend/audit.md`, route `/audit-requests`). This spec covers **only** currency pair's plug-in into that generic page — a `renderCurrencyPairDiff` renderer registered for `entityType: "CURRENCY_PAIR"` — and the required updates to the existing Currency Pair page (`develop/frontend/src/pages/CurrencyPairPage.tsx`) for the backend's `202` responses on `PUT`/`DELETE` (`specs/backend/currency-pair-approval.md`). There is no create flow on this page at all — see the Delta below. The generic Audit page's route, table, modal chrome, and renderer registry mechanism are entirely specified in `specs/frontend/audit.md` — implement that first (or alongside this).

This file previously (in an earlier, unimplemented iteration) defined the entire generic review page itself, coupled to currency pairs. That generic machinery has been extracted into `specs/frontend/audit.md`; this file now contains only what's genuinely currency-pair-specific.

## Requirements
- A `renderCurrencyPairDiff(before, after)` function, registered against `entityType: "CURRENCY_PAIR"` in the Audit module's renderer registry (`specs/frontend/audit.md`), rendering the known field labels in a fixed order: 品牌/基準幣別/對應幣別/匯率/匯率類型/狀態 — still renders correctly for historical `CREATE` audit requests that exist from before the create action was removed (the renderer itself is action-type-agnostic; it just formats whatever `before`/`after` it's given)
- Registration happens once, at a point reachable during app startup (e.g. a side-effecting import from `App.tsx` alongside route registration, or from the currency-pair feature's own entry point) — implementer's choice of exact wiring, as long as it runs before the Audit page can be visited
- Currency Pair page's edit/delete flows show a "submitted for approval" confirmation instead of assuming the change applied immediately, and the table itself is not expected to change until the request is approved
- Currency Pair page rows with a `PENDING` request against them are marked (badge) and their Edit/Delete actions are disabled, to avoid the "already has a pending request" `409` in the common case

### Delta: no create flow on this page
Per a later requirement ("所以品牌幣種對不需新增按鈕" — the brand currency pair page doesn't need an Add button, since a brand pair now only ever comes from the global definition fan-out, `specs/frontend/currency-pair-definition.md`), the Currency Pair page's "+ Add" button and create modal have been removed entirely (`specs/frontend/currency-pair.md`) — not merely gated behind approval. There is no `currencyPairApi.create` call anywhere in this page, and `renderCurrencyPairDiff` is never invoked with a `CREATE` request going forward (though it must still render any that already exist in the audit history from before this change, per the Requirements above).

## `renderCurrencyPairDiff`

For a `CURRENCY_PAIR` audit request, `before`/`after` (when non-null) have this shape (matching `specs/backend/currency-pair-approval.md`'s snapshot):
```ts
interface CurrencyPairSnapshot {
  brandId: number; brandCode: string
  baseCurrencyId: number; baseCurrencyCode: string
  quoteCurrencyId: number; quoteCurrencyCode: string
  rate: number | null; rateType: 'MANUAL' | 'AUTO'; active: boolean
}
```

Rendering, reusing the audit module's generic before/after-column and changed-field-highlight behavior (`specs/frontend/audit.md`):
```
           修改前              修改後
  品牌      PUG                PUG
  基準幣別  USD                USD
  對應幣別  TWD                TWD
  匯率      32.5               33.0   ← changed
  匯率類型  手動                手動
  狀態      啟用                啟用
```
- 匯率 renders `—` when `null` (an `AUTO` pair's rate, matching `specs/frontend/currency-pair.md`'s existing table convention).
- 狀態 renders 啟用/停用 for `active` true/false.
- 匯率類型 renders 手動/自動 for `MANUAL`/`AUTO`.
- Any field with a different value between `before` and `after` is highlighted the same way the audit module's generic diff view highlights changes.

## Required changes to the existing Currency Pair page

`develop/frontend/src/pages/CurrencyPairPage.tsx` and `currencyPairApi.ts` (`specs/frontend/currency-pair.md`) must be updated for the backend's `202`-instead-of-`200`/`204` responses on `PUT`/`DELETE` (`specs/backend/currency-pair-approval.md`). There is no `create` case — see the Delta above.

- **Edit**: on success (`202`), show toast "已送出修改申請，待審核". Close the modal. The row's displayed values remain the pre-change ones until approved.
- **Delete**: on success (`202`), show toast "已送出刪除申請，待審核" instead of removing the row. Confirmation dialog copy should reflect that this submits a request, not an immediate delete, e.g. "確定要送出刪除 {brandCode} 品牌幣種對 {baseCode}/{quoteCode} 的申請嗎？"
- **New 409** (duplicate pending request): toast "此幣種對已有待審核的異動申請"
- **Pending-request badge**: on page load (and after each refetch), also fetch `GET /api/audit-requests?entityType=CURRENCY_PAIR&status=PENDING` (`specs/backend/audit.md`); for any pair whose `id` matches a pending request's `entityId`, render a "審核中" badge in the table row and disable that row's Edit/Delete buttons (they'd otherwise hit the new 409).
- The optional `requestedBy` field is not exposed as a form input in this iteration (no auth system to default it from) — omit it from the request payload; it will simply be `null`/absent on submitted requests.

## Acceptance Criteria
- [x] `renderCurrencyPairDiff` is registered for `entityType: "CURRENCY_PAIR"` before the Audit page (`specs/frontend/audit.md`) is reachable, and produces the labeled before/after layout shown above (not the generic fallback) for a `CURRENCY_PAIR` request
- [x] On the Audit page, a `CREATE` request shows 品牌/基準幣別/對應幣別/匯率/匯率類型/狀態 correctly in the 修改後 column with 修改前 as the placeholder; `DELETE` is the mirror; `UPDATE` shows both columns with changed fields highlighted
- [x] 匯率 renders `—` for a `null` rate (`AUTO`) in the diff view
- [x] Currency Pair page's Add/Edit/Delete now show "已送出…申請，待審核" toasts instead of assuming the change applied, and no longer expect the table to reflect the change immediately
- [x] Currency Pair page rows with a pending request show a "審核中" badge and disabled Edit/Delete buttons
- [x] Currency Pair page surfaces the new 409 "此幣種對已有待審核的異動申請" message on create/edit/delete

### Delta: no create flow on this page
(The `[x]` items above mentioning `CREATE`/"Add" remain historically accurate for what was built and tested at the time.)
- [x] The "+ Add" button and create modal no longer exist on `CurrencyPairPage` — see `specs/frontend/currency-pair.md`
- [x] `renderCurrencyPairDiff` still renders correctly for a `CREATE` request already present in audit history (no regression to historical-record viewing), even though no new one can ever be submitted
- [x] Edit/Delete toasts, the pending badge, and the 409 "此幣種對已有待審核的異動申請" message are all unchanged by this delta

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/components/CurrencyPairDiff.tsx` (new) — `renderCurrencyPairDiff`, a `DiffRenderer` (from `../audit/diffRegistry`) rendering the fixed-order 品牌/基準幣別/對應幣別/匯率/匯率類型/狀態 fields in a 修改前/修改後 two-column table, reusing the generic diff module's `audit-generic-diff-table`/`audit-generic-diff-row--changed` CSS classes. `匯率` renders `—` for `null`; `狀態` renders 啟用/停用; `匯率類型` renders 手動/自動. Unlike most renderers it's also invoked directly with a `null` `before` (CREATE) or `after` (DELETE) — see the `AuditReviewModal` change below — so it shows the real field values on the populated side and `—` on the null side instead of a blanket placeholder, without ever highlighting a row against a null side.
  - `develop/frontend/src/components/CurrencyPairDiff.test.tsx` (new) — unit tests: field order/labels, 修改前/修改後 headers, only-the-changed-field highlighted, `—` for null rate, 啟用/停用 and 手動/自動 label mapping, and the CREATE/DELETE (one side `null`) real-values-plus-`—` behavior.
  - `develop/frontend/src/audit/diffRegistry.tsx` (edited) — added `hasDiffRenderer(entityType): boolean`, exported alongside the existing registry functions, so callers (the review modal) can distinguish "a dedicated renderer exists" from "falling back to the generic one" without changing `renderAuditDiff`'s own fallback behavior.
  - `develop/frontend/src/audit/diffRegistry.test.tsx` (edited) — added `hasDiffRenderer` tests (false for an unregistered entityType, true after `registerDiffRenderer`).
  - `develop/frontend/src/audit/AuditReviewModal.tsx` (edited) — `renderDiffArea()` now checks `hasDiffRenderer(request.entityType)` first: if a dedicated renderer is registered, it's handed the raw `before`/`after` (including `null` for CREATE/DELETE) directly, letting it decide how to display the populated side instead of the modal's own blanket placeholder text. Entity types with **no** dedicated renderer keep the exact original behavior (generic "（新增，無先前資料）"/"（將被刪除）" placeholders), so this is purely additive and doesn't touch the audit module's currency-pair-agnostic design — it has no knowledge of `CURRENCY_PAIR` itself, only of whether *some* renderer is registered for whatever `entityType` string it's given.
  - `develop/frontend/src/audit/AuditReviewModal.test.tsx` (edited) — added a test registering a null-aware fake renderer and asserting the modal calls it with `before: null` directly (not the generic placeholder), while the pre-existing "no registered renderer" placeholder tests are untouched and still pass.
  - `develop/frontend/src/pages/CurrencyPairPage.tsx` (edited) — this is the currency-pair feature's own entry point, so it calls `registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)` once at module scope (runs as soon as the module is imported — `App.tsx` imports `CurrencyPairPage` eagerly, so this executes at app startup, before the Audit page can be visited, regardless of which route the user actually lands on first). Also: `handleCreateSubmit`/`handleEditSubmit` show "已送出新增申請，待審核"/"已送出修改申請，待審核" toasts (success variant) on success instead of assuming the change applied, and no longer expect the table to reflect it; `handleConfirmDelete` shows "已送出刪除申請，待審核" instead of removing the row; the delete `ConfirmDialog` message is now "確定要送出刪除 {brandCode} 品牌幣種對 {baseCode}/{quoteCode} 的申請嗎？"; all three now also catch a `409` whose body message isn't the pre-existing "Currency pair already exists for this brand" live-duplicate case (`isPendingDuplicateConflict`) and toast "此幣種對已有待審核的異動申請" (closing the modal / clearing the delete target, then refreshing, exactly like the existing 404 handling); a new `fetchPendingIds` calls `GET /api/audit-requests?entityType=CURRENCY_PAIR&status=PENDING` (via `auditApi.list`) alongside `fetchPairs` (combined into a `refresh()` used everywhere `fetchPairs` used to be called alone: initial load, filter changes, and after every create/edit/delete outcome) and derives a `Set<number>` of pending `entityId`s (ignoring `null` `entityId`s, i.e. `CREATE` requests, which have no existing row to badge) passed to `CurrencyPairTable` as `pendingIds`; a failed `fetchPendingIds` is swallowed (non-critical — leaves the previous badge state rather than adding another error toast on top of the main list's own error handling).
  - `develop/frontend/src/pages/CurrencyPairPage.test.tsx` (rewritten) — updated every create/edit/delete test for the `202`/toast contract instead of the old `201`/`200`/`204` assumptions (mocking `../audit/auditApi` in addition to the pre-existing mocks); added tests for the new pending-duplicate `409` toast on edit and delete, and two new tests asserting the 審核中 badge + disabled Edit/Delete for a row whose id matches a pending request's `entityId`, and that a pending request for a *different* pair's id does not badge/disable the unrelated row.
  - `develop/frontend/src/components/CurrencyPairTable.tsx` (edited) — added a required `pendingIds: Set<number>` prop; each row now renders a "審核中" badge and disables its Edit/Delete buttons when `pendingIds.has(pair.id)`.
  - `develop/frontend/src/components/CurrencyPairTable.css` (edited) — added `.pending-badge` styling (mirroring the audit module's own `--pending` badge colors, kept as an independent, self-contained class rather than importing the audit module's CSS, so this component has no build-time dependency on `../audit/*`).
  - `develop/frontend/src/components/CurrencyPairTable.test.tsx` (edited) — added `pendingIds` to every existing render call (default `new Set()`, non-breaking) and a new test for the badge/disabled-buttons behavior plus a same-page-different-pair negative case.
  - `develop/frontend/src/components/CurrencyPairFormModal.tsx` (edited) — the existing inline `409` handler now distinguishes the live-duplicate message ("Currency pair already exists for this brand" → "此品牌已存在相同的幣種對", unchanged) from any other `409` body (→ "此幣種對已有待審核的異動申請"). In practice `CurrencyPairPage` intercepts and toasts the pending-duplicate case before it reaches the modal (per the spec's explicit "toast" instruction for that case), so this inline branch is a defense-in-depth fallback, not the primary path for that message.
  - `develop/frontend/src/components/CurrencyPairFormModal.test.tsx` (edited) — added a test asserting the modal's own inline fallback message for a non-live-duplicate `409` body, verifying that defense-in-depth path independently of the page.
  - `develop/frontend/src/api/currencyPairApi.ts` (edited) — `create`/`update`/`remove` are now typed to resolve `AuditRequest` (imported from `../audit/types`) instead of `CurrencyPair`/`void`, matching the backend's new `202 Accepted` + `AuditRequestResponse` body on all three endpoints (`list` is unchanged). No payload changes were needed since `requestedBy` is intentionally omitted from the client (no auth system yet) and the rest of `CurrencyPairInput` is untouched.
- Verification performed:
  - `npm run build` (`tsc -b && vite build`) — succeeds with no type errors.
  - `npm test` (`vitest run`) — all 14 test files / 113 tests pass (up from 96 pre-existing, +17 new/rewritten across `CurrencyPairDiff.test.tsx` (new, 8), `diffRegistry.test.tsx` (+2), `AuditReviewModal.test.tsx` (+1), `CurrencyPairPage.test.tsx` (rewritten, net +6 vs. the pre-existing 11), `CurrencyPairTable.test.tsx` (+1), `CurrencyPairFormModal.test.tsx` (+1)).
  - `npm run lint` (`oxlint`) — no new warnings/errors; only the pre-existing, unrelated `ToastProvider.tsx` fast-refresh warning remains.
  - Manually traced the exact backend error-message strings this frontend now branches on (`Currency pair already exists for this brand`, `A pending create request already exists for this brand/base/quote combination`, `A pending audit request already exists for this entity`) against `develop/backend/.../GlobalExceptionHandler.java` and `specs/backend/currency-pair-approval.md`'s own "Execution Result", to confirm the create-dedup and update/delete-dedup `409`s are both correctly classified as "pending duplicate" (anything that isn't the one known live-duplicate string) rather than hardcoding both exact dedup strings and risking a drift if the backend's generic audit module's wording ever changes again.
- Notable judgment calls:
  - **`AuditReviewModal`'s null-handling change.** The already-implemented (per `specs/frontend/audit.md`) modal never invoked any renderer at all when `before`/`after` was `null` — it always showed a static Chinese placeholder, by design, for every entity type. That directly conflicted with this spec's requirement that a `CREATE`/`DELETE` `CURRENCY_PAIR` request render the *real* field values on its populated side (in the 修改後/修改前 column respectively), not a placeholder. Rather than special-casing `CURRENCY_PAIR` inside the generic audit module (explicitly against `audit.md`'s "must contain no reference to currency pairs" boundary) or duplicating the modal's chrome inside a currency-pair-specific wrapper, the minimal fix was to let `hasDiffRenderer` gate the decision: entity types with a dedicated renderer opt into receiving `null` directly (and must handle it themselves, which `renderCurrencyPairDiff` now does); entity types without one keep the exact original placeholder behavior, verified by the pre-existing "no registered renderer" placeholder tests still passing unmodified.
  - **Pending-duplicate `409` classification is negative, not positive** (`error.body?.error !== 'Currency pair already exists for this brand'`) rather than matching the two known dedup strings verbatim. Chosen because the two dedup messages come from two different sources on the backend (`CurrencyPairAuditHandler`'s own CREATE-dedup exception vs. the generic audit module's UPDATE/DELETE-dedup exception) with independently-worded text, and a `409` from this API can only mean one of "live duplicate" or "pending duplicate already exists" per `specs/backend/currency-pair-approval.md`'s error contract — so excluding the one known non-pending case is more robust than hardcoding both dedup strings.
  - **Where the `409` pending-duplicate toast fires**: at the `CurrencyPairPage` level (alongside the existing 404 handling), not inside `CurrencyPairFormModal`, per the spec's explicit "toast" instruction (vs. the modal's existing inline-error convention for user-correctable input problems like the live-duplicate case). `CurrencyPairFormModal` still has its own fallback branch for this message in case an error somehow reaches it uncaught, but the page is the primary path.
  - Did not add a currency-pair-specific formatting for `匯率` beyond reusing the same `Number(rate.toFixed(8)).toString()` convention already used by `CurrencyPairTable.formatRate` (duplicated rather than extracted into a shared util, since it's a two-line function and extraction would add an import edge between two otherwise-independent modules for no real benefit at this size).

### Increment 2 — 2026-07-30
- Status: DONE
- Delta implemented: no create flow on this page
- Files changed:
  - `develop/frontend/src/pages/CurrencyPairPage.tsx` (edited — removed `handleCreateSubmit` and the "+ Add" button; see `specs/frontend/currency-pair.md`'s Increment 2 for the full file-change list, since this is the shared implementation for both specs' Delta sections)
  - `develop/frontend/src/components/CurrencyPairFormModal.tsx` (edited — dropped the `mode` prop; the component is edit-only going forward, so it is never invoked with a `CREATE` outcome again)
  - `develop/frontend/src/api/currencyPairApi.ts` (edited — removed the `create` export; `currencyPairApi.create` is no longer callable from anywhere)
  - `develop/frontend/src/components/CurrencyPairDiff.tsx` — **not modified**. `renderCurrencyPairDiff` remains entirely action-type-agnostic (it only ever looks at `before`/`after` field values, never `actionType`), so no code change was needed for it to keep rendering historical `CREATE` audit requests correctly.
  - `develop/frontend/src/components/CurrencyPairDiff.test.tsx` — **not modified**; its existing CREATE-shaped (`before: null`) test case continues to pass unmodified, confirming no regression to historical-record viewing.
  - `develop/frontend/src/pages/CurrencyPairPage.test.tsx` (edited — removed the create-flow test per `specs/frontend/currency-pair.md`'s Increment 2; every Edit/Delete/pending-badge/409-conflict test is untouched and still passes)
- Acceptance criteria completed (all 3 Delta items now checked):
  - [x] The "+ Add" button and create modal no longer exist on `CurrencyPairPage`
  - [x] `renderCurrencyPairDiff` still renders correctly for a `CREATE` request already present in audit history
  - [x] Edit/Delete toasts, the pending badge, and the 409 "此幣種對已有待審核的異動申請" message are all unchanged by this delta
- Verification:
  - `npm run build` (`tsc -b && vite build`) passes with 0 type errors.
  - `npm test` (`vitest run`) passes: 23 test files, 168 tests, 0 failures — including `CurrencyPairDiff.test.tsx`'s unmodified CREATE-shaped-diff test and the Audit module's own `AuditReviewModal.test.tsx`, confirming the audit-consumption side of this delta needed no code changes.
  - `npm run lint` (`oxlint`) passes with only the pre-existing `ToastProvider.tsx` fast-refresh warning.
- Notes:
  - This delta turned out to require zero changes to the audit-consumption code itself (`CurrencyPairDiff.tsx`, `diffRegistry.tsx`, `AuditReviewModal.tsx`): the renderer was already, by design, agnostic to which `actionType` produced the `before`/`after` it's handed, so a `CREATE` request already sitting in the audit history renders exactly as it did before this change. The only real work for this file's Delta was on the producer side (`CurrencyPairPage`/`CurrencyPairFormModal`/`currencyPairApi`), which is why the bulk of the file-change list mirrors `specs/frontend/currency-pair.md`'s Increment 2 — these two specs describe one atomic change from two angles (the page's own UI contract vs. this page's role as an audit producer/consumer), and were implemented together in a single pass.
  - Confirmed by inspection that `registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)` still runs at `CurrencyPairPage` module scope unchanged, so the Audit page continues to render both historical `CREATE`/`UPDATE`/`DELETE` `CURRENCY_PAIR` requests with the dedicated layout — only new `CREATE` requests can no longer be produced, which is enforced entirely by the absence of any `create`-calling code path, not by any change to the audit module.
