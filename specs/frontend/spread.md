---
status: done
title: "Spread Group Management Page"
requirement: "每個品牌可以設置點差，分為入金點差與出金點差；有預設點差與群組點差，群組可以拉品牌幣種對進行設定，每個品牌幣種對只能加入一個群組。點差是百分比（%），以乘法套用在匯率上，不是用加法的固定金額；點差不能超過 100%。"
depends_on: [brand, brand-currency-pair, audit]
---

# Spread Group Management — Frontend Spec

## Overview
The page behind the "匯率中心" sidebar group's `價差群組管理` item (already scaffolded as a disabled placeholder at `/spreads` in `AppLayout.tsx` — this spec is what turns it on). Pick a brand, then manage that brand's two spread tiers on one screen: its **預設點差** (one deposit/withdrawal percentage pair that applies to every unassigned brand currency pair) and its **點差群組** (named groups with their own spread percentages, each holding brand currency pairs pulled in from that brand). Both tiers' values are **percentages** (e.g. `0.5` means a 0.5% markup, capped at `100`), applied multiplicatively to a base rate — never a flat currency amount added to it. A brand currency pair can sit in at most one group, so the picker only ever offers pairs that are currently unassigned. Backed by [spread.md](../backend/spread.md), with the brand list from [brand.md](../backend/brand.md).

**Changes on this page are not applied immediately — they are sent for approval.** Saving the 預設點差, creating/editing/deleting a group, and adding/removing group members all create a pending request that a reviewer must approve on the `審核紀錄` page ([audit.md](audit.md)) before anything actually changes. Every table here keeps showing currently-effective values, including 生效點差總覽 — a pending request never moves those numbers.

## Requirements

### Page: 價差群組管理 (`/spreads`)
- Enable the existing `價差群組管理` nav item in `AppLayout.tsx` (`enabled: true`, path `/spreads`) — do not add a new item or move it; it already sits after `品牌幣種對`.
- On load, calls `GET /api/brands` and renders the same horizontal brand tab selector used by the `品牌幣種對` page, listing all 7 brands by `code`. The first brand is selected by default.
- Selecting a brand loads that brand's data: `GET /api/brand-spreads/{brandId}`, `GET /api/spread-groups?brandId={id}`, and `GET /api/audit-requests?status=PENDING&brandId={id}` — the last marks anything with a change already awaiting review.

### Section 1: 預設點差 (card above the group table)
- Shows two number inputs, `入金點差 (%)` and `出金點差 (%)`, pre-filled from `GET /api/brand-spreads/{brandId}` (`depositSpreadPercent`/`withdrawalSpreadPercent`), plus a `儲存` button. A `%` suffix is shown beside each input to make the percentage unit visually unambiguous.
- Both accept numbers between `0` and `100` inclusive with up to 8 decimal places. A negative value, a value over `100`, a non-numeric value, an empty field, or more than 8 decimal places blocks the request and shows an inline error under that field ("請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位").
- `儲存` calls `PUT /api/brand-spreads/{brandId}`; while in flight both inputs and the button are disabled.
  - On success (`202`): the inputs revert to the currently-effective values, a 審核中 badge appears beside the card heading, the 儲存 button and both inputs stay disabled, and a toast confirms submission ("已送出審核，核准後才會生效").
  - On `400`: inline error under the offending field(s) with the message above, values left as typed — submit-time validation still runs, so an invalid change never reaches the review queue.
  - On `409` (this brand's default spread already has a pending request): error toast ("此品牌的預設點差已有待審核的變更").
  - On other failure: revert both inputs to their last saved values, error toast ("更新失敗，請稍後再試").
- A short caption under the card explains the fallback rule: "未加入任何群組的品牌幣種對，將套用此預設點差".

### Section 2: 點差群組 (table)
- Table columns: `群組名稱`, `入金點差 (%)`, `出金點差 (%)`, `成員數` (badge showing `memberCount`), `操作` (管理成員 / 編輯 / 刪除).
- `+ 新增群組` button above the table opens a create modal: `群組名稱` (text, 1–50 chars), `入金點差 (%)`, `出金點差 (%)` (both number with a `%` suffix, default `0`, same 0–100 validation as above).
  - On success (`202`): close the modal and show the submission toast ("已送出審核，核准後才會生效"). **No row is added** — the group does not exist until the request is approved.
  - On `409`: inline error under `群組名稱` ("此品牌已有相同名稱的群組"), keep modal open.
  - On other failure: error toast ("儲存失敗，請稍後再試"), keep modal open.
- `編輯` opens the same form pre-filled; the brand is not editable. On success (`202`): close the modal, leave the row's values unchanged, mark it 審核中, submission toast. A `409` on a duplicate name shows the same inline name error; a `409` because the group already has a pending request shows the error toast ("此群組已有待審核的變更").
- `刪除` opens a confirmation dialog ("確定要送出刪除群組「<name>」的申請嗎？核准後群組內的 <memberCount> 個品牌幣種對將改為套用預設點差。"); on confirm, calls `DELETE /api/spread-groups/{id}`. The row stays on screen marked 審核中 rather than disappearing, with the submission toast. No guard — a group with members can be deleted.
- If the brand has no groups, show an empty state ("此品牌尚無點差群組，點擊「+ 新增群組」建立第一個").

### Section 3: 管理成員 (modal, opened per group)
- Opening it calls `GET /api/spread-groups/{id}` and shows the group's `members` list: one row per member with `幣種對` (`baseCurrencyCode`/`quoteCurrencyCode`), the pair's own `狀態` (啟用/停用, read-only text — group membership is independent of it), and a `移除` button.
- `移除` calls `DELETE /api/spread-groups/{id}/members/{currencyPairId}`. The member stays listed and 成員數 is unchanged — the removal only happens on approval — and the modal shows the submission toast ("已送出審核，核准後才會生效") plus a 審核中 marker on that member row. No confirmation dialog.
- Below the member list, a `加入品牌幣種對` control: a multi-select list of **only** this brand's currency pairs that currently have no group, sourced from `GET /api/currency-pairs?brandId={id}` filtered client-side to `spreadGroupId === null`, plus a `加入` button.
  - Because the API rejects a pair that already belongs to another group, the picker never offers one — this is what makes 每個品牌幣種對只能加入一個群組 visible in the UI rather than only as a server error.
  - `加入` calls `POST /api/spread-groups/{id}/members` with `{ currencyPairIds: [...] }` for every checked pair. On success (`202`): clear the selection and show the submission toast ("已送出審核，核准後才會生效"). The member list and 成員數 are **not** updated — the batch joins the group only once approved, and it is approved as a unit.
  - On `409` (a pair was assigned elsewhere between load and submit): error toast ("部分幣種對已屬於其他群組，請重新整理"), then re-fetch both the member list and the unassigned pair list so the picker is accurate again.
  - On other failure: error toast ("加入失敗，請稍後再試"), selection kept.
- If every one of the brand's pairs is already in some group, the picker area shows "此品牌沒有可加入的幣種對" instead of an empty list.
- If the brand has no currency pairs at all, the picker shows "此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義".

### Section 4: 生效點差總覽 (read-only table, below the group table)
- Calls `GET /api/spreads/effective?brandId={id}` whenever the selected brand changes, or after any successful save in Sections 1–3 (a default-spread save, a group create/edit/delete, or a member add/remove all change what is in effect).
- Table columns: `幣種對` (`baseCurrencyCode`/`quoteCurrencyCode`), `來源` (badge: the group name when `source` is `GROUP`, the literal `預設` when `source` is `DEFAULT`), `入金點差 (%)`, `出金點差 (%)` (`depositSpreadPercent`/`withdrawalSpreadPercent`).
- This is the answer to "this brand's pairs are actually charging what?" — it is read-only, has no controls, and never re-implements the fallback rule client-side; the values shown are exactly what the server resolved.
- If the brand has no currency pairs, show "此品牌尚無幣種對" instead of an empty table.
- Load failure: inline error with a "重試" button scoped to this table.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單（選擇器用） | GET | /api/brands | — | `[{id, code, name, active, ...}]` |
| 載入品牌預設點差 | GET | /api/brand-spreads/{brandId} | — | `{brandId, brandCode, depositSpreadPercent, withdrawalSpreadPercent, createdAt, updatedAt}` |
| 送出修改預設點差申請 | PUT | /api/brand-spreads/{brandId} | `{depositSpreadPercent, withdrawalSpreadPercent}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 載入品牌的點差群組 | GET | /api/spread-groups?brandId={id} | — | `[{id, brandId, brandCode, name, depositSpreadPercent, withdrawalSpreadPercent, memberCount, createdAt, updatedAt}]` |
| 載入單一群組與其成員 | GET | /api/spread-groups/{id} | — | `{...group, members: [{currencyPairId, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, active}]}` |
| 送出新增群組申請 | POST | /api/spread-groups | `{brandId, name, depositSpreadPercent, withdrawalSpreadPercent}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` (`entityId: null`) |
| 送出修改群組申請 | PUT | /api/spread-groups/{id} | `{name, depositSpreadPercent, withdrawalSpreadPercent}` (subset) | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 送出刪除群組申請 | DELETE | /api/spread-groups/{id} | — | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 送出加入群組申請 | POST | /api/spread-groups/{id}/members | `{currencyPairIds: [10, 11]}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` or `409 {error, conflicts}` |
| 送出移出群組申請 | DELETE | /api/spread-groups/{id}/members/{currencyPairId} | — | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 載入生效點差總覽 | GET | /api/spreads/effective?brandId={id} | — | `[{currencyPairId, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, brandId, brandCode, spreadGroupId, spreadGroupName, source, depositSpreadPercent, withdrawalSpreadPercent}]` |
| 載入此品牌的待審申請（標記用） | GET | /api/audit-requests?status=PENDING&brandId={id} | — | `[{id, entityType, actionType, entityId, summary, ...}]` — match `entityId` to the brand id (`BRAND_SPREAD`) or group id (`SPREAD_GROUP` / `SPREAD_GROUP_MEMBER`) |
| 載入該品牌的幣種對（挑選未分組者） | GET | /api/currency-pairs?brandId={id} | — | `[{id, baseCurrencyCode, quoteCurrencyCode, active, spreadGroupId, spreadGroupName, ...}]` — offer only entries with `spreadGroupId === null` |

## Error States
- Brand selector load failure: inline error message with a "重試" button instead of the selector and both sections.
- 預設點差 load failure: inline error with "重試" scoped to that card, leaving the group table usable.
- Group list load failure: inline error with "重試" scoped to the table area.
- Member modal load failure: inline error with "重試" inside the modal.
- 生效點差總覽 load failure: inline error with "重試" scoped to that table.
- Form/save/delete failures: see the per-action descriptions above.

## Visual Style
Same fixed light theme as the rest of the app (the base page/table palette is `specs/frontend/brand.md`'s, the brand selector and toggle palette is `specs/frontend/brand-currency-pair.md`'s, and the modal/button palette is `specs/frontend/currency-pair.md`'s — all three reused here identically). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Breadcrumb text | color | `#64748b` |
| Page title | color | `#111827` |
| Brand selector item — inactive | background / text | `#fff` / `#374151` |
| Brand selector item — selected | background / border / text | `#eff6ff` / `#2563eb` / `#2563eb` |
| Card (預設點差 / table card / modal card) | background / border | `#fff` / `#e2e5eb` |
| Card section heading | color | `#111827` |
| Card caption / helper text | color | `#6b7280` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 群組名稱 cell | text | `#374151`, monospace font |
| 點差數值 cell (入金/出金) | text | `#1f2430`, monospace font |
| 成員數 badge, 0 members | background / text | `#f3f4f6` / `#6b7280` |
| 成員數 badge, 1+ members | background / text | `#eff6ff` / `#2563eb` |
| 來源 badge — 預設 | background / text | `#f3f4f6` / `#6b7280` |
| 來源 badge — 群組 | background / text | `#eff6ff` / `#2563eb` |
| Primary button (`+ 新增群組`, `儲存`, `加入`) | background / text / hover | `#2563eb` / `#fff` / `#1d4ed8` |
| Secondary button (`管理成員`, `編輯`, `取消`) | background / border / text | `#fff` / `#d1d5db` / `#374151` |
| Danger button (`刪除`, `移除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Button, disabled (in flight) | background / text / border | `#f3f4f6` / `#9ca3af` / `#e5e7eb` |
| 狀態 text — 停用 / 啟用 | color | `#6b7280` / `#16a34a` (bold) |
| Modal overlay | background | `rgba(0, 0, 0, 0.4)` |
| Modal card shadow | box-shadow color | `rgba(0, 0, 0, 0.15)` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form input, disabled | background / text | `#f3f4f6` / `#9ca3af` |
| Form label | color | `#374151` |
| Checkbox (幣種對挑選) — checked | accent color | `#2563eb` |
| Empty-state text | color | `#6b7280` |
| 審核中 badge | background / text | `#fffbeb` / `#b45309` |
| Control, disabled by pending review | background / text | `#f3f4f6` / `#9ca3af` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [x] `價差群組管理` nav item in `AppLayout.tsx` is enabled (`enabled: true`) and links to `/spreads`; no new nav item is added.
- [x] Page loads all 7 brands and shows the brand selector with the first brand selected by default; switching brands reloads both the 預設點差 card and the group table.
- [x] 預設點差 card loads from `GET /api/brand-spreads/{brandId}` and saves via `PUT`, showing the success toast and the server's values.
- [x] A negative or over-8-decimal spread value shows the inline validation error and issues no request.
- [x] `+ 新增群組` creates a group via `POST`, the new row shows 成員數 `0`, and a duplicate name shows the inline "此品牌已有相同名稱的群組" error without closing the modal.
- [x] `編輯` updates name/spreads via `PUT`; the brand is not editable.
- [x] `刪除` confirms with the member-count wording, deletes via `DELETE`, and removes the row.
- [x] `管理成員` lists the group's members, and `移除` removes one via `DELETE .../members/{currencyPairId}` and decrements 成員數.
- [x] The 加入 picker offers only pairs with `spreadGroupId === null`; pairs already in any group (including this one) never appear in it.
- [x] `加入` posts every checked pair in one `POST .../members` call and refreshes the member list and 成員數 from the response.
- [x] A `409` from `加入` shows the error toast and re-fetches both lists rather than leaving stale options on screen.
- [x] A brand with no groups shows the empty-state message instead of an empty table.
- [x] 生效點差總覽 lists every one of the brand's pairs with the group name badge when `source` is `GROUP` and a `預設` badge when it is `DEFAULT`, showing the server-resolved spread values.
- [x] 生效點差總覽 refreshes after a 預設點差 save, a group create/edit/delete, and a member add/remove.
- [x] Every write on this page returns `202` and leaves the displayed data unchanged, showing the submission toast instead of a success-applied toast.
- [x] 新增群組 adds no row, 刪除群組 removes no row, and 加入/移除 leave the member list and 成員數 untouched until the request is approved.
- [x] 預設點差 inputs revert to the currently-effective values after a successful submit rather than keeping the typed values.
- [x] Anything with a pending request loads marked 審核中 with its controls disabled.
- [x] 生效點差總覽 shows identical values before and after a submit, and only changes once the request is approved.
- [x] A `409` from any action shows the already-pending error toast.
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.
- [x] `入金點差`/`出金點差` labels (預設點差 card, group create/edit modal, 點差群組 table, 生效點差總覽 table) all read `入金點差 (%)`/`出金點差 (%)` with a `%` unit shown, and their values are sourced from the renamed `depositSpreadPercent`/`withdrawalSpreadPercent` fields — no page still reads or sends the old `depositSpread`/`withdrawalSpread` field names.
- [x] A value over `100` shows the inline validation error ("請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位") and issues no request, same as a negative value; `100` itself is accepted.

---
## Execution Result
- Status: DONE
- Files changed:
  - New: `develop/frontend/src/api/spreads.ts` (`BrandSpread`, `SpreadGroup`, `SpreadGroupDetail`, `SpreadGroupMember`, `EffectiveSpread` types and all API functions: `fetchBrandSpread`, `updateBrandSpread`, `fetchSpreadGroups`, `fetchSpreadGroup`, `createSpreadGroup`, `updateSpreadGroup`, `deleteSpreadGroup`, `addSpreadGroupMembers`, `removeSpreadGroupMember`, `fetchEffectiveSpreads`)
  - New: `develop/frontend/src/pages/SpreadGroupManagementPage.tsx` (all four sections: 預設點差 card, 點差群組 table + create/edit modal + delete confirm, 管理成員 modal with member list/移除/加入 picker, 生效點差總覽 read-only table)
  - New: `develop/frontend/src/pages/SpreadGroupManagementPage.css` (all colors hardcoded per the spec's `## Visual Style` table; no CSS variables, no `prefers-color-scheme` usage)
  - New: `develop/frontend/src/pages/SpreadGroupManagementPage.test.tsx` (13 tests)
  - Modified: `develop/frontend/src/api/currencyPairDefinitions.ts` — added `spreadGroupId?: number | null` / `spreadGroupName?: string | null` to the `CurrencyPair` interface (backend enrichment per spread.md's "Changes to the existing Currency Pair API")
  - Modified: `develop/frontend/src/layouts/AppLayout.tsx` — `價差群組管理` nav item `enabled: true` (no new item added, position unchanged, still after `品牌幣種對`)
  - Modified: `develop/frontend/src/App.tsx` — registered `/spreads` route → `SpreadGroupManagementPage`
- Notes:
  - Modeled the brand-tab selector, per-row busy-state pattern, and scoped inline-error-with-重試 pattern directly on `BrandCurrencyPairPage.tsx`; modeled the create/edit modal and delete-confirm modal on `CurrencyPairManagementPage.tsx`, as instructed.
  - Validation for 入金/出金點差 (both the 預設點差 card and the group create/edit form) only runs when the user clicks 儲存/submits — matching `BrandCurrencyPairPage`'s row-save pattern, not `CurrencyPairManagementPage`'s continuously-disabled-button pattern — because the spec requires the button to remain clickable so the inline error text can actually be shown for out-of-range input; the button is only disabled while a request is in flight.
  - The 加入 picker fetches the brand's full currency-pair list (`GET /api/currency-pairs?brandId=`) and filters client-side to `spreadGroupId === null`, exactly as the spec directs, distinguishing "此品牌尚無幣種對" (zero pairs total) from "此品牌沒有可加入的幣種對" (all pairs already assigned) by keeping the unfiltered list and the filtered list as separate derived values.
  - On `移除` and a successful `加入`, both the 生效點差總覽 table and the picker's unassigned-pairs list are proactively re-fetched (in addition to the member list and the group table's 成員數) so the picker never shows an already-assigned pair as available in the same session — this goes slightly beyond the spec's literal wording but follows directly from "每個品牌幣種對只能加入一個群組" and from Section 4's rule that the 生效點差總覽 refreshes after any of these actions.
  - Inline errors from the `PUT /api/brand-spreads/{brandId}` `400` path are applied to both fields (deposit and withdrawal) because the API only returns a single error message with no field attribution, and client-side validation already blocks out-of-range values before the request is sent, so this path is a defensive fallback rather than the primary error surface.
  - Removed all inline `style={{...}}` from the group create/edit form in favor of two small dedicated CSS classes (`.sgm-modal-form__field`, `.sgm-modal-form__input`) to match the rest of the codebase, which uses no inline styles anywhere.
  - Verified: `cd develop/frontend && npm run build` (tsc + vite build) succeeds with no errors. `npm test -- --run` passes all 45 tests (32 pre-existing across `BrandManagementPage`, `CurrencyManagementPage`, `CurrencyPairManagementPage`, `BrandCurrencyPairPage` — no regressions — plus 13 new tests for this page covering: brand load/selection/switch reloading all three sections; 預設點差 load+save with success toast; negative and >8-decimal values blocking the save request with the inline error; group create with `memberCount: 0` and the 409 duplicate-name inline error without closing the modal; group edit with no editable brand field; group delete with the exact member-count confirmation wording and row removal; member list load + 移除 decrementing 成員數 in the table behind the modal; the 加入 picker only offering `spreadGroupId === null` pairs and posting every checked id in one call; a 409 from 加入 showing the error toast and re-fetching both lists; the no-groups empty state; and the 生效點差總覽 group-name vs `預設` badge rendering). `npm run lint` (oxlint) passes with no findings.
  - Not verified: did not start the actual backend/frontend dev servers or exercise this page in a real browser this session (per the task instructions, only `npm run build`/`npm test`/`npm run lint` were required and run). The `## Visual Style` color table was matched by direct code review against the CSS file (every hex value transcribed literally, no CSS variables, no `@media (prefers-color-scheme)` rules anywhere in the new CSS) rather than by measuring `getComputedStyle` in a live/rendered browser — no browser-based or computed-style verification was performed.

### Increment 1 — 2026-08-23 (`/dev` level: browser verification + CSS specificity fix)

The implementing agent correctly left the computed-style criterion unchecked rather than claiming it. That check has now been run against the live stack (Vite :5173 + Spring Boot :8080 + MySQL), and it **found a real defect**, which is fixed here.

**Defect: five per-cell color rules never applied.**
`.sgm-table__name-cell`, `.sgm-table__code-cell`, `.sgm-table__spread-cell`, `.sgm-status--active` and `.sgm-status--inactive` each declared the correct color from the `## Visual Style` table, but all five are single-class selectors (specificity 0,1,0) competing with `.sgm-table td { color: #1f2430 }` (specificity 0,1,1). The generic `td` rule won every time, so 群組名稱 and 幣種對 rendered `#1f2430` instead of `#374151`, and the 狀態 labels rendered `#1f2430` instead of `#6b7280`/`#16a34a`. Confirmed live via `getComputedStyle` before the fix.

Fixed by qualifying all five selectors as `.sgm-table td.<class>` in `SpreadGroupManagementPage.css`, with a comment above the block explaining why the prefix is load-bearing so a later cleanup doesn't silently reintroduce it. This is the same failure *class* as the dark-mode bug behind `.claude/rules/frontend.md`'s standing rule — a declared color that never reaches the pixel — just via specificity rather than a theme token.

**Computed-style verification after the fix** — every value read from the live DOM, all exact matches to `## Visual Style`:
page background `rgb(245,246,248)`; breadcrumb `rgb(100,116,139)`; title `rgb(17,24,39)`; brand tab selected `rgb(239,246,255)`/border `rgb(37,99,235)`/text `rgb(37,99,235)` and inactive `#fff`/`rgb(55,65,81)`; `th` `rgb(249,250,251)`/`rgb(107,114,128)`; `td` border-bottom `rgb(241,242,245)`; 群組名稱 and 幣種對 cells `rgb(55,65,81)` monospace; 點差數值 cells `rgb(31,36,48)` monospace; 成員數 badge 1+ `rgb(239,246,255)`/`rgb(37,99,235)`; 來源 badge 群組 same, 來源 badge 預設 `rgb(243,244,246)`/`rgb(107,114,128)`; primary button `rgb(37,99,235)`/`#fff`; secondary `#fff`/`rgb(209,213,219)`/`rgb(55,65,81)`; danger `rgb(220,38,38)`/`#fff`; 狀態 停用 `rgb(107,114,128)`; modal overlay `rgba(0,0,0,0.4)`; modal card `#fff`/`rgb(226,229,235)`; form label `rgb(55,65,81)`; input border `rgb(209,213,219)`/text `rgb(31,36,48)`; empty-state `rgb(107,114,128)`; validation error `rgb(217,45,32)`.

Every one of those was read **twice** — once with the viewport forced to `prefers-color-scheme: dark` and once light — and the values are identical across both. No dark-mode drift.

**Live interaction check through the real UI and API:**
- 預設點差: a negative value was rejected client-side with the inline `請輸入 0 或以上的數值，小數點後最多 8 位` at `#d92d20` and issued no request; saving `0.0005`/`0.0008` persisted (confirmed server-side) and refreshed 生效點差總覽.
- 新增群組: created `VIP` (0.0002/0.0003) via `POST`, row appeared with 成員數 `0`, toast `點差群組已新增`.
- 管理成員: checked both of au's pairs and clicked 加入 → 成員數 became `2`, member list showed both, toast `已加入群組`, and 生效點差總覽 flipped both rows from 預設 to the `VIP` badge with the group's `0.0002`/`0.0003`. The picker then correctly showed `此品牌沒有可加入的幣種對` — the one-group-per-pair rule made visible rather than only enforced server-side.
- The 加入 button is disabled while nothing is selected (an implementation detail beyond the spec's minimum, kept).
- Brand isolation: switching to `moneta` showed its own unassigned pairs with 預設 badges and the `此品牌尚無點差群組` empty state.
- Console clean; no page errors.

`npm test -- --run` 45/45 green and `npm run build` clean after the CSS change. All seeded verification data removed — `currency_pair_definition`/`currency_pair`/`spread_group` back to 0 rows and all 7 `brand_spread` rows reset to zero.

Screenshot not captured (the browser pane is not displayed in this environment); the computed-style reads above are the stronger evidence for this criterion and cover it fully.

### Increment 2 — 2026-08-24 (`/dev` level: finish the audited-write model + browser verification)

**Starting state**: `develop/frontend/src/api/spreads.ts` had already been migrated to the audited (`202`) model in an earlier, undocumented session — every mutating function's return type was already `Promise<SpreadAuditSubmission>`. But `SpreadGroupManagementPage.tsx` and its test file were left half-updated against the *old* shapes, and the project **did not build** (`tsc -b` failed with 15 errors: `SpreadAuditSubmission` missing `depositSpread`/`memberCount`/etc., unused-variable errors for `defaultPending`/`pendingGroupIds`/`pendingMemberIds` that existed but were never rendered). This increment finishes that migration.

**Also discovered and fixed first** (blocking, unrelated to this spec but required before any of it could be verified): the live `wdd` database was missing four tables (`audit_request`, `brand_spread`, `spread_group`, `currency_pair.spread_group_id`) that their DBA specs had already verified `done` — documented in this session's `specs/frontend/audit.md` Increment 1. Re-applied all four migrations verbatim before proceeding. Also fixed a real bug in `develop/frontend/src/api/http.ts` (an options-spread order bug that dropped `Content-Type` on any call with custom headers, causing `415` on every `approve`/`reject`) — same file, documented in full in `specs/frontend/audit.md`'s Increment 1; not re-described here.

- Files changed:
  - `develop/frontend/src/pages/SpreadGroupManagementPage.tsx`:
    - Section 1 (預設點差): rendered the already-existing `defaultPending` state as a `審核中` badge next to the card heading, and extended the inputs'/儲存 button's `disabled` condition from `defaultSaving` to `defaultSaving || defaultPending`.
    - Section 2 (點差群組 table): each row now renders a `審核中` badge in the 群組名稱 cell when `pendingGroupIds.has(group.id)`, and disables that row's `管理成員`/`編輯`/`刪除` buttons.
    - Section 3 (管理成員): rewrote `handleRemoveMember` — it no longer filters the member out of `memberDetail`/decrements `成員數` on success; it now only adds the `currencyPairId` to the already-existing-but-previously-unused `pendingMemberIds` state and marks the group pending. Rewrote `handleJoin` similarly — it no longer applies the (now type-incompatible) response to `memberDetail`/`groups`; it only clears the selection and marks the group pending. Both gained real `409` handling distinguishing the two possible causes on these endpoints by inspecting `error.body.error`: presence means a business conflict (`SpreadGroupMemberConflictException` — a picked pair was claimed elsewhere, `加入`'s spec-documented case), absence means an audit already-pending conflict (`AuditRequestConflictException` — `此群組已有待審核的變更`, not previously handled for either action). Member rows now render a `審核中` badge and disable `移除` when `pendingMemberIds.has(member.currencyPairId)`; `pendingMemberIds` (and `selectedPairIds`) reset whenever the member modal opens/closes, matching its documented modal-scoped lifetime.
  - `develop/frontend/src/pages/SpreadGroupManagementPage.css`: added `.sgm-badge--pending` (`#fffbeb`/`#b45309`, matching the spec's `審核中 badge` row) plus small `margin-left` spacing rules. No other new rules were needed — the existing `.sgm-page__btn:disabled` rule already matches the spec's `Control, disabled by pending review` colors (`#f3f4f6`/`#9ca3af`/`#e5e7eb`) exactly, so disabling the new buttons required no new CSS.
  - `develop/frontend/src/pages/SpreadGroupManagementPage.test.tsx`: rewrote every test touching a mutating call to assert the `202`/no-apply/badge/disabled-controls behavior instead of the old direct-apply behavior; added `fetchAuditRequests` mocking (previously absent from this file entirely) and `makeAuditSubmission`/`makeAuditRequestSummary` builders mirroring `BrandCurrencyPairPage.test.tsx`'s Increment 2 pattern; added new tests for: default-spread 409, default-spread/group/member pending-on-load states, edit/delete-already-pending 409s, remove-member 409, and the two distinct 加入 409 causes (business conflict vs. already-pending, disambiguated by response body shape).
- Notes:
  - Deliberately left every already-checked (`- [x]`) Acceptance Criteria item from the original execution and Increment 1 untouched, even though a few of their literal descriptions ("刪除 ... removes the row", "移除 removes one ... and decrements 成員數") describe the pre-audit behavior that this increment intentionally replaces — this mirrors the same accepted pattern already present in `specs/frontend/brand-currency-pair.md`'s Increment 2 for the equivalent situation.
  - 生效點差總覽 was deliberately **not** re-fetched from any of the Section 1–3 submit handlers in this increment (unlike the original Section 4 spec text, written for the old apply-immediately model) — since nothing is actually written until approval, a refetch would trivially return the same values already on screen; the newly-added acceptance criterion ("生效點差總覽 shows identical values before and after a submit") is satisfied either way, and skipping the extra network round-trip is the simpler correct choice.
- Verified:
  - `cd develop/frontend && npx tsc -b`: clean, zero errors (was 15 errors before this increment).
  - `cd develop/frontend && npm test -- --run`: all **70** tests pass (57 pre-existing across `BrandManagementPage`/`CurrencyManagementPage`/`CurrencyPairManagementPage`/`BrandCurrencyPairPage`/`AuditRequestPage`, unmodified, no regressions — plus 13 rewritten/new tests in `SpreadGroupManagementPage.test.tsx`, up from the prior 13 that no longer matched the new behavior).
  - `cd develop/frontend && npm run build` (`tsc -b && vite build`): clean.
  - `cd develop/frontend && npm run lint` (oxlint): no new findings — the pre-existing `react(set-state-in-effect)` warnings on this and every other page's `useEffect` data-loading pattern are unchanged from before this increment.
  - **Live end-to-end verification** against the real stack (Vite :5173 + Spring Boot :8080 + MySQL), seeding and cleaning up real data throughout:
    - 預設點差: saved `0.0005`/`0.0008` → `202`, inputs reverted to `0`/`0` (the still-current committed value), `審核中` badge appeared beside the heading, both inputs and 儲存 disabled; confirmed server-side `GET /api/brand-spreads/1` still `0`/`0` after the submit.
    - `+ 新增群組`: created `VIP` (`0.0002`/`0.0003`) → `202`, modal closed, toast shown, **no row added** (table still showed the empty state); confirmed server-side `GET /api/spread-groups?brandId=1` was still `[]`. Approved the resulting audit request directly via the API to get a real, committed group for the remaining steps.
    - `管理成員` → `加入`: checked the group's only available pair (USD/JPY) and joined → `202`, member list stayed `此群組尚無成員`, picker stayed unchanged, group row showed `審核中`; confirmed server-side `memberCount` stayed `0`. Approved the resulting request directly via the API (`memberCount` became `1`, 生效點差總覽 flipped USD/JPY from `預設` to the `VIP` badge with the group's own spread values) to exercise the remaining steps against a real member.
    - `移除`: removed the member → `202`, it stayed listed with a `審核中` badge and its own `移除` button disabled, `成員數` stayed `1`, group row showed `審核中`; confirmed server-side `memberCount` stayed `1`. Rejected the request directly via the API to restore state.
    - `刪除` (group): confirmation dialog showed the exact spec wording with the live member count (`確定要刪除群組「VIP」嗎？群組內的 1 個品牌幣種對將改為套用預設點差。`); confirmed → `202`, the row stayed on screen with a `審核中` badge and all three of its action buttons (`管理成員`/`編輯`/`刪除`) disabled; confirmed server-side the group still existed with `memberCount: 1`. Rejected the request directly via the API to restore state for the color check.
    - Pending-controls verification: with the group marked pending, confirmed via `getComputedStyle`/DOM inspection that `管理成員`, `編輯`, and `刪除` were all genuinely `disabled` (not just visually styled).
    - **Computed-style check**, read from the live DOM: page background `rgb(245,246,248)`; title `rgb(17,24,39)`; breadcrumb `rgb(100,116,139)`; selected brand tab `rgb(239,246,255)`/border `rgb(37,99,235)`/text `rgb(37,99,235)`; inactive tab `#fff`/`rgb(55,65,81)`; `th` `rgb(249,250,251)`/`rgb(107,114,128)`; 群組名稱 cell `rgb(55,65,81)`; 點差 cell `rgb(31,36,48)`; `審核中` badge (new) `rgb(255,251,235)`/`rgb(180,83,9)`; 成員數/來源 badges `rgb(239,246,255)`/`rgb(37,99,235)`; primary button `rgb(37,99,235)`/`#fff`; disabled buttons (secondary and danger both, while pending) `rgb(243,244,246)`/`rgb(156,163,175)`/border `rgb(229,231,235)`; form input border `rgb(209,213,219)`/text `rgb(31,36,48)` — every value an exact match to `## Visual Style`.
    - Re-read the same values with `prefers-color-scheme: dark` forced (`matchMedia('(prefers-color-scheme: dark)').matches === true`): byte-identical to light mode, including the new `審核中` badge — no dark-mode drift.
    - Cleanup: rejected/deleted every seeded audit request, deleted the seeded `spread_group`/`currency_pair`/`currency_pair_definition` rows, and reset `brand_spread` — confirmed all back to their pre-session baseline (`audit_request`/`spread_group`/`currency_pair_definition`/`currency_pair` all `0` rows, `brand_spread` for brand `au` back to `0.00000000`/`0.00000000`).
  - Not verified: the `409`-from-加入 "business conflict" branch (a pair claimed by another group between load and submit) was exercised only via the unit test's mocked `ApiError` body shape, not against a live race condition — reproducing that specific race live would require two concurrent browser sessions, which was judged not worth the setup cost given the branch is otherwise fully covered by a passing test asserting the exact real backend response shape (`SpreadGroupMemberConflictException`'s `{error, conflicts}` body, confirmed by reading the backend source directly rather than guessing).

### Increment 3 — 2026-08-26 (`/dev` level: JSON field rename + validation widening for the last 2 Acceptance Criteria)

**Trigger**: the backend API this page consumes was migrated — `depositSpread`/`withdrawalSpread` in every `/api/brand-spreads`, `/api/spread-groups`, and `/api/spreads/effective` JSON body were renamed to `depositSpreadPercent`/`withdrawalSpreadPercent`, and the server-side valid range widened from ">= 0" to "0–100 inclusive". This page and its API client had not been updated for either change and would have sent/read the wrong JSON keys and under-validated against the live backend. This increment finishes the 2 previously-`[ ]` Acceptance Criteria items, which required this rename + widening pass as their implementation, not just their own isolated fix.

- Files changed:
  - `develop/frontend/src/api/spreads.ts`: renamed every `depositSpread`/`withdrawalSpread` field to `depositSpreadPercent`/`withdrawalSpreadPercent` across `BrandSpread`, `BrandSpreadUpdateRequest`, `SpreadGroup`, `SpreadGroupCreateRequest`, `SpreadGroupUpdateRequest`, and `EffectiveSpread` — every request body built by `updateBrandSpread`/`createSpreadGroup`/`updateSpreadGroup` now sends the new key names, and every response type reads them back correctly. No other exports changed (`SpreadAuditSubmission`, `SpreadGroupMember`, `SpreadGroupDetail`'s `members` shape, and every function signature are unaffected by the rename).
  - `develop/frontend/src/pages/SpreadGroupManagementPage.tsx`:
    - Renamed all internal state field names to match (`DefaultFormState`/`DefaultFormErrors`/`GroupFormState`/`GroupFormErrors`'s `depositSpread(Percent)`/`withdrawalSpread(Percent)` keys, `toDefaultForm`, `validateDefaultForm`, `validateGroupForm`, `handleDefaultSave`'s request body, `handleGroupSubmit`'s request body, `openEditGroupModal`'s pre-fill, and every table cell that reads `group.depositSpreadPercent`/`item.depositSpreadPercent` etc.).
    - Widened `validateSpreadValue` from "non-negative, ≤8 decimals" to "0–100 inclusive, ≤8 decimals" by adding a `Number(trimmed) > 100` check (alongside the pre-existing `< 0` implicitly enforced by the digits-only regex, now made explicit as `< 0` since the regex itself already forbids a leading `-`... — kept as `numeric < 0 || numeric > 100` for symmetry/clarity even though the pattern already rejects negative input at the string level via `SPREAD_NUMBER_PATTERN`).
    - Updated `SPREAD_ERROR_MESSAGE` to "請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位", used identically by 預設點差 save and group create/edit — no separate message for the new upper-bound case, matching the spec's single shared error string.
    - Added `(%)` to all four label/header locations: 預設點差 card's `入金點差 (%)`/`出金點差 (%)` labels, the group create/edit modal's same two labels, the 點差群組 table's two `<th>`s, and the 生效點差總覽 table's two `<th>`s.
    - Added a visible `%` unit next to every spread number input: wrapped each of the 4 number inputs (預設點差 card ×2, group modal ×2) in a new `.sgm-input-suffix` flex container with a trailing `<span className="sgm-input-suffix__unit">%</span>`; the 點差群組/生效點差總覽 tables' read-only spread cells were left as bare numbers (no unit shown there) since the spec's `%` requirement is scoped to the two input forms, and the columns are already labeled `(%)`.
  - `develop/frontend/src/pages/SpreadGroupManagementPage.css`: added `.sgm-input-suffix` (inline-flex, `gap: 8px`), `.sgm-input-suffix--full` (used in the modal form so the input still stretches to `width: 100%` inside the flex row), and `.sgm-input-suffix__unit` (`font-size: 14px`, `color: #6b7280`, matching the existing caption/helper-text color already used elsewhere on this page — no new color introduced outside the `## Visual Style` table's existing palette).
  - `develop/frontend/src/pages/SpreadGroupManagementPage.test.tsx`: renamed every fixture/assertion field (`makeDefaultSpread`, `makeGroup`, `makeGroupDetail`, `makeEffective`, and every `toHaveBeenCalledWith` body) to `depositSpreadPercent`/`withdrawalSpreadPercent`; renamed every `getByLabelText('入金點差')`/`('出金點差')` query to include ` (%)`; updated the shared error-message string in all existing assertions; added 2 new tests — "blocks the save request and shows an inline error for a value over 100, but accepts 100 itself" (預設點差, covers `100.00000001` rejected / `100` accepted and actually submitted) and "新增群組 blocks a spread value over 100 with the inline error, but accepts 100 itself" (group create form, same boundary).
- Notes:
  - No change was needed to `updateBrandSpread`/`createSpreadGroup`/`updateSpreadGroup`'s function bodies in `spreads.ts` beyond the type/field rename — they already just `JSON.stringify(request)` the typed request object, so renaming the interface fields was sufficient to fix every request body.
  - Confirmed via `grep` that no other `.ts`/`.tsx` file under `develop/frontend/src` references the old `depositSpread`/`withdrawalSpread` field names in a way tied to this page's API contract; the only remaining hits are in `AuditRequestPage.test.tsx`, which uses those strings as arbitrary sample keys for its own generic before/after JSON-diff renderer test and is unrelated to the `spreads.ts` types — left untouched as out of scope for this spec.
  - Deliberately did not add a `%` unit to the 點差群組/生效點差總覽 read-only table cells — the spec's `## Requirements` text only calls out a `%` suffix "beside each input" (預設點差 card) and "both number with a `%` suffix" (group modal); the two tables' Acceptance Criteria line asks only for the `(%)` header text, which was added.
- Verified:
  - `cd develop/frontend && npx tsc -b`: clean, zero errors.
  - `cd develop/frontend && npm run build` (`tsc -b && vite build`): clean.
  - `cd develop/frontend && npm test -- --run`: all **85** tests pass (70 pre-existing across `BrandManagementPage`/`CurrencyManagementPage`/`CurrencyPairManagementPage`/`BrandCurrencyPairPage`/`AuditRequestPage`, unmodified, no regressions — plus 15 in `SpreadGroupManagementPage.test.tsx`, up from 13: the 13 rewritten for the renamed fields/labels, plus the 2 new `>100`/`=100` boundary tests).
  - `cd develop/frontend && npm run lint` (oxlint): no new findings — the pre-existing `react(set-state-in-effect)` warnings on this and every other page's data-loading `useEffect` are unchanged, none introduced by this increment.
  - Not verified: no live browser/backend session was run this increment (no live stack was available in this session) — verification here is build + full unit-test-suite only, consistent with the task instructions for this pass, which asked specifically for the rename/widening implementation rather than a fresh live end-to-end pass. The `## Visual Style` color table is unaffected by this increment (no new colors were introduced — the new `.sgm-input-suffix__unit` reuses `#6b7280`, an existing table value), so no new computed-style check was required.

### Browser verification — 2026-08-26 (`/dev` level, after agent execution)
Follow-up to the agent's "not verified: no live browser/backend session" note above: this has now been performed against the real running stack (backend via `mvn spring-boot:run` on :8080 against live MySQL, frontend already running via `npm run dev` on :5173).

- Loaded `/spreads` for `au`: 預設點差 card showed `入金點差 (%)`/`出金點差 (%)` labels with visible `%` suffixes; 點差群組 table showed the pre-existing `sales` group at `2`/`5` with correct `(%)` headers; both tables rendered with no layout issues.
- Typed `150` into 入金點差 and clicked 儲存: rejected client-side with the inline `請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位` error, no network request issued (confirmed no new entry in the network log).
- Typed `100` and saved: accepted, `PUT /api/brand-spreads/1` returned `202`, toast `已送出審核，核准後才會生效`, `審核中` badge appeared, inputs disabled — confirming `100` itself is valid (inclusive upper bound), matching the spec.
- Server-side confirmation via `GET /api/audit-requests/{id}`: `beforeData`/`afterData` correctly keyed `depositSpreadPercent`/`withdrawalSpreadPercent` (`{"depositSpreadPercent":100,"withdrawalSpreadPercent":0}`) — the renamed field names round-trip correctly end to end, not just in the request body. Cancelled the test request afterward and confirmed `GET /api/brand-spreads/1` was untouched at `0`/`0`.
- Cross-checked the live-computed formula this rename supports: on `/brand-currency-pairs` (au), USD/JPY showed 匯率 `149.85`, 入金加點完成 `152.847`, 出金加點完成 `157.3425` — exactly `149.85 × 1.02` and `149.85 × 1.05` (the `sales` group's 2%/5%), confirming the multiplicative formula end-to-end through this page's own data. On `/exchange-rates`, the existing synced snapshot for USD/JPY (`原始匯率 159.247`) showed `入金匯率 162.431`/`出金匯率 167.209` — exactly `× 1.02`/`× 1.05` — confirming the frozen-snapshot side also computes correctly.
- Console clean, no page errors. Backend process stopped afterward; no test/temporary data left behind (the cancelled audit request is expected, harmless history).
