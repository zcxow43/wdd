---
status: pending
title: "Currency Pair Management Page"
requirement: "匯率中心需要幣別對管理畫面：管理幣種對定義（CRUD、精度）。品牌幣種對改為獨立頁面（見 brand-currency-pair.md），不再是本頁面的一部分。"
depends_on: [brand, currency]
---

# Currency Pair Management — Frontend Spec

## Overview
A page under the "匯率中心" sidebar group (label `幣別對管理`, already scaffolded as a disabled placeholder at `/currency-pairs` in `AppLayout.tsx` — this spec is what turns it on). Lists every currency pair definition (base/quote currency, precision) and lets an admin create/edit/delete definitions. Managing each brand's own currency pair settings (auto/manual rate, active toggle) is a separate page reached via its own sidebar tab — see [brand-currency-pair.md](brand-currency-pair.md) — not something you drill into from a row here. Backed by [currency-pair-definition.md](../backend/currency-pair-definition.md).

## Requirements

### Page: 幣別對管理 (`/currency-pairs`)
- On load, calls `GET /api/currency-pair-definitions` and renders one row per definition.
- Table columns: `基準幣`/`報價幣` (base/quote currency code), `精度` (precision), `啟用品牌數` (count of its currency pairs with `active: true`, out of the total count — e.g. "2 / 7"), `操作` (編輯 / 刪除).
- `+ 新增幣種對` button above the table opens a create form (modal): `基準幣`/`報價幣` (currency dropdowns, populated from `GET /api/currencies`), `精度` (number, 0–8, default 4).
  - On success: close modal, add the new row (啟用品牌數 shows "0 / 7"), show a success toast ("幣種對已新增，已為 7 個品牌建立品牌幣種對").
  - On duplicate pair (`409`): inline error under `報價幣` ("此幣種對已存在"), keep modal open.
  - On other failure: error toast ("儲存失敗，請稍後再試"), keep modal open.
- Each row's `編輯` button opens a form with only `精度` editable (`基準幣`/`報價幣` read-only, immutable). On success: update the row, toast ("幣種對已更新").
- Each row's `刪除` button:
  - If `啟用品牌數` shows any active brand (e.g. "2 / 7"), the button is disabled with a tooltip ("需先於「品牌幣種對」頁面關閉所有品牌幣種對才能刪除").
  - Otherwise, opens a confirmation dialog ("確定要刪除幣種對「<base>/<quote>」嗎？此操作無法復原。"); on confirm, calls `DELETE`, removes the row, toast ("幣種對已刪除"). On a `409` response (a pair became active between page load and delete), show the error toast with the message from the response and refresh the row's count.
- `啟用品牌數` is a plain badge here, not a link — viewing or editing the underlying brand rows happens on the dedicated `品牌幣種對` page, not from this page.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入幣種對定義清單 | GET | /api/currency-pair-definitions | — | `[{id, baseCurrencyId, baseCurrencyCode, quoteCurrencyId, quoteCurrencyCode, precision, createdAt, updatedAt}]` |
| 新增幣種對定義 | POST | /api/currency-pair-definitions | `{baseCurrencyId, quoteCurrencyId, precision}` | `{...definition, currencyPairs: [{id, brandId, brandCode, rateType, rate, active, ...}]}` |
| 修改幣種對定義精度 | PUT | /api/currency-pair-definitions/{id} | `{precision}` | updated definition |
| 刪除幣種對定義 | DELETE | /api/currency-pair-definitions/{id} | — | (no body, 204) or `409 {error, activeBrandCodes}` |
| 載入幣種清單（新增表單用） | GET | /api/currencies | — | `[{id, code, name, symbol, decimalPlaces, ...}]` |
| 計算啟用品牌數 | GET | /api/currency-pairs?currencyPairDefinitionId={id} | — | `[{id, active, ...}]` — count `active: true` entries client-side to render the badge |

## Error States
- Definition list load failure: inline error message with a "重試" button instead of the table.
- Form/delete failures: see the per-action descriptions above.

## Visual Style
Same fixed light theme as the rest of the app (see `specs/frontend/brand.md`'s `## Visual Style` for the base table/page palette and `specs/frontend/currency.md`'s for the modal/button palette — both reused here identically). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Page title | color | `#111827` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 代碼 cell (基準幣/報價幣) | text | `#374151`, monospace font |
| Primary button (`+ 新增幣種對`, 儲存) | background / text / hover | `#2563eb` / `#fff` / `#1d4ed8` |
| Secondary button (`編輯`, 取消) | background / border / text | `#fff` / `#d1d5db` / `#374151` |
| Danger button (`刪除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Danger button, disabled (blocked delete) | background / text / border | `#f3f4f6` / `#9ca3af` / `#e5e7eb` |
| 啟用品牌數 badge, all inactive | background / text | `#f3f4f6` / `#6b7280` |
| 啟用品牌數 badge, some active | background / text | `#eff6ff` / `#2563eb` |
| Modal overlay | background | `rgba(0, 0, 0, 0.4)` |
| Modal card | background / border / shadow | `#fff` / `#e2e5eb` / `rgba(0, 0, 0, 0.15)` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form label | color | `#374151` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [ ] `幣別對管理` nav item in `AppLayout.tsx` is enabled (`enabled: true`) and links to `/currency-pairs`.
- [ ] Definition list loads from `GET /api/currency-pair-definitions` and shows base/quote/precision/啟用品牌數 for each.
- [ ] `+ 新增幣種對` creates a definition via `POST` and the success toast reflects the number of brand pairs created.
- [ ] Creating a duplicate `(base, quote)` shows the inline "此幣種對已存在" error without closing the modal.
- [ ] `編輯` updates only `precision` via `PUT`; base/quote are not editable.
- [ ] `刪除` is disabled (with tooltip) whenever 啟用品牌數 > 0, and succeeds via `DELETE` when 0.
- [ ] This page has no per-definition drill-down UI for managing brand currency pairs — that lives entirely on the `品牌幣種對` page (`brand-currency-pair.md`).
- [ ] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.
