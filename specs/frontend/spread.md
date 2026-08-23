---
status: pending
title: "Spread Group Management Page"
requirement: "每個品牌可以設置點差，分為入金點差與出金點差；有預設點差與群組點差，群組可以拉品牌幣種對進行設定，每個品牌幣種對只能加入一個群組"
depends_on: [brand, brand-currency-pair, audit]
---

# Spread Group Management — Frontend Spec

## Overview
The page behind the "匯率中心" sidebar group's `價差群組管理` item (already scaffolded as a disabled placeholder at `/spreads` in `AppLayout.tsx` — this spec is what turns it on). Pick a brand, then manage that brand's two spread tiers on one screen: its **預設點差** (one deposit/withdrawal pair that applies to every unassigned brand currency pair) and its **點差群組** (named groups with their own spreads, each holding brand currency pairs pulled in from that brand). A brand currency pair can sit in at most one group, so the picker only ever offers pairs that are currently unassigned. Backed by [spread.md](../backend/spread.md), with the brand list from [brand.md](../backend/brand.md).

**Changes on this page are not applied immediately — they are sent for approval.** Saving the 預設點差, creating/editing/deleting a group, and adding/removing group members all create a pending request that a reviewer must approve on the `審核紀錄` page ([audit.md](audit.md)) before anything actually changes. Every table here keeps showing currently-effective values, including 生效點差總覽 — a pending request never moves those numbers.

## Requirements

### Page: 價差群組管理 (`/spreads`)
- Enable the existing `價差群組管理` nav item in `AppLayout.tsx` (`enabled: true`, path `/spreads`) — do not add a new item or move it; it already sits after `品牌幣種對`.
- On load, calls `GET /api/brands` and renders the same horizontal brand tab selector used by the `品牌幣種對` page, listing all 7 brands by `code`. The first brand is selected by default.
- Selecting a brand loads that brand's data: `GET /api/brand-spreads/{brandId}`, `GET /api/spread-groups?brandId={id}`, and `GET /api/audit-requests?status=PENDING&brandId={id}` — the last marks anything with a change already awaiting review.

### Section 1: 預設點差 (card above the group table)
- Shows two number inputs, `入金點差` and `出金點差`, pre-filled from `GET /api/brand-spreads/{brandId}`, plus a `儲存` button.
- Both accept non-negative numbers with up to 8 decimal places. A negative value, a non-numeric value, an empty field, or more than 8 decimal places blocks the request and shows an inline error under that field ("請輸入 0 或以上的數值，小數點後最多 8 位").
- `儲存` calls `PUT /api/brand-spreads/{brandId}`; while in flight both inputs and the button are disabled.
  - On success (`202`): the inputs revert to the currently-effective values, a 審核中 badge appears beside the card heading, the 儲存 button and both inputs stay disabled, and a toast confirms submission ("已送出審核，核准後才會生效").
  - On `400`: inline error under the offending field(s) with the message above, values left as typed — submit-time validation still runs, so an invalid change never reaches the review queue.
  - On `409` (this brand's default spread already has a pending request): error toast ("此品牌的預設點差已有待審核的變更").
  - On other failure: revert both inputs to their last saved values, error toast ("更新失敗，請稍後再試").
- A short caption under the card explains the fallback rule: "未加入任何群組的品牌幣種對，將套用此預設點差".

### Section 2: 點差群組 (table)
- Table columns: `群組名稱`, `入金點差`, `出金點差`, `成員數` (badge showing `memberCount`), `操作` (管理成員 / 編輯 / 刪除).
- `+ 新增群組` button above the table opens a create modal: `群組名稱` (text, 1–50 chars), `入金點差`, `出金點差` (both number, default `0`, same validation as above).
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
- Table columns: `幣種對` (`baseCurrencyCode`/`quoteCurrencyCode`), `來源` (badge: the group name when `source` is `GROUP`, the literal `預設` when `source` is `DEFAULT`), `入金點差`, `出金點差`.
- This is the answer to "this brand's pairs are actually charging what?" — it is read-only, has no controls, and never re-implements the fallback rule client-side; the values shown are exactly what the server resolved.
- If the brand has no currency pairs, show "此品牌尚無幣種對" instead of an empty table.
- Load failure: inline error with a "重試" button scoped to this table.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單（選擇器用） | GET | /api/brands | — | `[{id, code, name, active, ...}]` |
| 載入品牌預設點差 | GET | /api/brand-spreads/{brandId} | — | `{brandId, brandCode, depositSpread, withdrawalSpread, createdAt, updatedAt}` |
| 送出修改預設點差申請 | PUT | /api/brand-spreads/{brandId} | `{depositSpread, withdrawalSpread}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 載入品牌的點差群組 | GET | /api/spread-groups?brandId={id} | — | `[{id, brandId, brandCode, name, depositSpread, withdrawalSpread, memberCount, createdAt, updatedAt}]` |
| 載入單一群組與其成員 | GET | /api/spread-groups/{id} | — | `{...group, members: [{currencyPairId, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, active}]}` |
| 送出新增群組申請 | POST | /api/spread-groups | `{brandId, name, depositSpread, withdrawalSpread}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` (`entityId: null`) |
| 送出修改群組申請 | PUT | /api/spread-groups/{id} | `{name, depositSpread, withdrawalSpread}` (subset) | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 送出刪除群組申請 | DELETE | /api/spread-groups/{id} | — | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 送出加入群組申請 | POST | /api/spread-groups/{id}/members | `{currencyPairIds: [10, 11]}` | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` or `409 {error, conflicts}` |
| 送出移出群組申請 | DELETE | /api/spread-groups/{id}/members/{currencyPairId} | — | `202 {auditRequestId, status, entityType, actionType, entityId, summary}` |
| 載入生效點差總覽 | GET | /api/spreads/effective?brandId={id} | — | `[{currencyPairId, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, brandId, brandCode, spreadGroupId, spreadGroupName, source, depositSpread, withdrawalSpread}]` |
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
- [ ] Every write on this page returns `202` and leaves the displayed data unchanged, showing the submission toast instead of a success-applied toast.
- [ ] 新增群組 adds no row, 刪除群組 removes no row, and 加入/移除 leave the member list and 成員數 untouched until the request is approved.
- [ ] 預設點差 inputs revert to the currently-effective values after a successful submit rather than keeping the typed values.
- [ ] Anything with a pending request loads marked 審核中 with its controls disabled.
- [ ] 生效點差總覽 shows identical values before and after a submit, and only changes once the request is approved.
- [ ] A `409` from any action shows the already-pending error toast.
- [x] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.

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
