---
status: pending
title: "Brand Currency Pair Page"
requirement: "因為想看到品牌裡面有哪些幣種對，品牌幣種對應該獨立出一個標籤，裡面顯示該品牌所擁有的幣種對，可以 CRUD、設定自動/手動匯率、開啟關閉"
depends_on: [brand]
---

# Brand Currency Pair — Frontend Spec

## Overview
A page under the "匯率中心" sidebar group, its own tab (label `品牌幣種對`), independent from `幣別對管理` (`currency-pair.md`, which only manages the global pair definitions). Pick a brand, see every currency pair that brand has, and manage each one's rate type/rate/active state directly — this is the brand-centric view of the same data `currency-pair.md`'s definitions page only shows an aggregate count for. Backed by [currency-pair.md](../backend/currency-pair.md) (read/update/delete) and [brand.md](../backend/brand.md) (the brand selector).

## Requirements

### Page: 品牌幣種對 (`/brand-currency-pairs`)
- Add a new sidebar item to `AppLayout.tsx`'s "匯率中心" group: label `品牌幣種對`, path `/brand-currency-pairs`, placed directly after `幣別對管理`.
- On load, calls `GET /api/brands` and renders a brand selector (tabs or a dropdown — pick whichever this codebase's existing patterns favor) listing all 7 brands by `code`. The first brand is selected by default.
- Selecting a brand calls `GET /api/currency-pairs?brandId={id}` and renders one row per currency pair that brand has (i.e. one row per currency pair definition that exists — every definition fans out a row per brand, so this is effectively "every definition, from this brand's angle").
- Table columns: `幣種對` (`baseCurrencyCode`/`quoteCurrencyCode`, e.g. "USD/JPY"), `匯率類型` (單選：自動/手動), `匯率` (number input, only enabled when `匯率類型` = 手動), `狀態` (active toggle switch), `操作` (刪除).
- If the selected brand has no currency pairs at all (no definitions created yet), show an empty state ("此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義").

### Row interactions
- Changing `匯率類型` to `手動` enables the `匯率` input and requires a value before saving; changing to `自動` disables and clears the `匯率` input.
- Each row has its own `儲存` action (or saves on blur/toggle — either is acceptable) that calls `PUT /api/currency-pairs/{id}` with the row's current `rateType`/`rate`/`active`. While in flight, disable that row's controls.
  - On success: reflect the server response in the row, toast ("品牌幣種對已更新").
  - On `400` (e.g. manual rate missing, or exceeds the parent definition's precision): inline error under the `匯率` field ("請輸入有效匯率").
  - On other failure: revert the row's fields to their last saved values, error toast ("更新失敗，請稍後再試").
- The 狀態 toggle behaves like the Brand page's toggle: immediate `PUT` on click, disabled + "更新中..." label while in flight, revert + toast on failure.
- `刪除` on a row opens a confirmation dialog ("確定要刪除「<baseCurrencyCode>/<quoteCurrencyCode>」的品牌幣種對設定嗎？"); on confirm, calls `DELETE /api/currency-pairs/{id}`, removes the row, toast ("已刪除"). No guard — allowed regardless of `active`.
- No "+新增" control on this page — a brand's currency pairs come entirely from `幣別對管理`'s definition fan-out; recreating an individually-deleted row is out of scope for this spec.

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單（選擇器用） | GET | /api/brands | — | `[{id, code, name, active, ...}]` |
| 載入某品牌的幣種對 | GET | /api/currency-pairs?brandId={id} | — | `[{id, currencyPairDefinitionId, baseCurrencyCode, quoteCurrencyCode, brandId, brandCode, rateType, rate, active, createdAt, updatedAt}]` |
| 修改品牌幣種對 | PUT | /api/currency-pairs/{id} | `{rateType, rate, active}` (subset) | updated currency pair |
| 刪除品牌幣種對 | DELETE | /api/currency-pairs/{id} | — | (no body, 204) |

## Error States
- Brand selector load failure: inline error message with a "重試" button instead of the selector/table.
- Currency pair list load failure (for the selected brand): same pattern, scoped to the table area.
- Row save/toggle/delete failures: see Row interactions above.

## Visual Style
Same fixed light theme as the rest of the app (see `specs/frontend/brand.md`'s `## Visual Style` for the base table palette and `specs/frontend/currency-pair.md`'s for the toggle/radio palette — both reused here identically). No color on this page varies with `prefers-color-scheme` or any other OS/browser theme preference.

| Element | Property | Value |
|---|---|---|
| Page background | background | `#f5f6f8` |
| Page title | color | `#111827` |
| Brand selector item — inactive | background / text | `#fff` / `#374151` |
| Brand selector item — selected | background / border / text | `#eff6ff` / `#2563eb` / `#2563eb` |
| Table card | background / border | `#fff` / `#e2e5eb` |
| Table header (`th`) | background / text | `#f9fafb` / `#6b7280` |
| Table row (`td`) | text / border-bottom | `#1f2430` / `#f1f2f5` |
| 幣種對 cell | text | `#374151`, monospace font |
| Danger button (`刪除`) | background / text / hover | `#dc2626` / `#fff` / `#b91c1c` |
| Toggle switch — off / on | track background | `#d1d5db` / `#22c55e` |
| 狀態 label — 停用 / 啟用 | color | `#6b7280` / `#16a34a` (bold) |
| Radio button (自動/手動) — selected | border / dot | `#2563eb` / `#2563eb` |
| Form input | border / text / focus border | `#d1d5db` / `#1f2430` / `#2563eb` |
| Form input, disabled (匯率 when 自動) | background / text | `#f3f4f6` / `#9ca3af` |
| Validation/error text | color | `#d92d20` |

## Acceptance Criteria
- [ ] `品牌幣種對` nav item exists in `AppLayout.tsx`'s 匯率中心 group, enabled, linking to `/brand-currency-pairs`.
- [ ] Page loads all 7 brands from `GET /api/brands` and shows a brand selector; the first brand is selected by default.
- [ ] Selecting a brand loads its currency pairs from `GET /api/currency-pairs?brandId={id}` and displays 幣種對/匯率類型/匯率/狀態 for each.
- [ ] A brand with no currency pairs shows the empty-state message instead of an empty table.
- [ ] Switching a row's `匯率類型` to 手動 requires a `匯率` value before it can save; switching to 自動 clears it.
- [ ] Toggling a row's 狀態 calls `PUT` immediately with a disabled "更新中..." state, and reverts + toasts on failure.
- [ ] `刪除` on a row succeeds via `DELETE` regardless of its 狀態.
- [ ] Every color used matches the `## Visual Style` table exactly, verified via computed styles, and does not change under a dark `prefers-color-scheme`.
