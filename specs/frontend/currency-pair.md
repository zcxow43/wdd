---
status: pending
title: "Currency Pair Table Page"
requirement: "Display currency pairs in a table with CRUD operations, scoped per brand, exchange rate manual/auto; surface currency delete-blocked error"
---

# Currency Pair Table Page — Frontend Spec

## Overview
Build a currency pair management page that displays all configured currency pairs (base → quote), each scoped to a brand, with their exchange rate. Users can view, add, edit, and delete pairs, filter by brand, and choose whether the rate is entered manually or maintained automatically. Consumes the API defined in `specs/backend/currency-pair.md`. Reuses the existing `currency` list (`GET /api/currencies`) to populate the base/quote currency dropdowns, and the brand list (`GET /api/brands`, see `specs/frontend/brand.md` / `specs/backend/brand.md`) to populate the brand filter and picker.

This spec also requires a small update to the existing Currency page (`develop/frontend/src/pages/CurrencyPage.tsx`) to surface the new "currency is in use" delete error from the backend.

## Requirements
- Table page showing all currency pairs, with their owning brand
- Filter by brand, in addition to active/inactive status
- Add new pair via modal form; brand, base/quote pickers sourced from `/api/brands` and `/api/currencies`
- Edit existing pair via modal (brand, base/quote, and rate/rateType editable)
- Delete with confirmation
- Rate input behavior differs by `rateType`: `MANUAL` requires the user to type the rate; `AUTO` marks the rate as system-maintained but still allows an editable fallback value (no automatic sync job exists yet — out of scope, per `specs/backend/currency-pair.md`)
- Existing Currency page delete flow must display a clear error when the backend rejects deletion because the currency is referenced by a pair

## Page Layout

### Route
`/currency-pairs`

### Page Structure
```
┌────────────────────────────────────────────────────────┐
│  Currency Pair Management                              │
│                                                          │
│  [Brand ▼]  [Active ▼]                     [+ Add]      │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Brand │ Base │ Quote │ Rate     │ Type   │ Active │  │
│  │───────│──────│───────│──────────│────────│────────│  │
│  │ AU    │ USD  │ TWD   │ 32.5     │ 手動   │  ✓     │  │
│  │ MONETA│ USD  │ JPY   │ 157.3    │ 自動   │  ✓     │  │
│  │ ...   │ ...  │ ...   │ ...      │ ...    │ ...    │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Table Columns

| Column   | Source Field       | Width  | Notes                                   |
|----------|--------------------|--------|-------------------------------------------|
| 品牌     | brandCode          | 90px   | Bold, monospace, uppercase                |
| 基準幣別 | baseCurrencyCode   | 80px   | Bold, monospace                           |
| 對應幣別 | quoteCurrencyCode  | 80px   | Bold, monospace                           |
| 匯率     | rate               | 120px  | Right aligned, formatted to currency's decimal convention or up to 8 dp |
| 匯率類型 | rateType           | 100px  | Badge: `手動` (MANUAL) / `自動` (AUTO)    |
| 狀態     | active             | 80px   | Green dot / grey dot                      |
| Actions  | —                  | 120px  | Edit, Delete buttons                      |

### Filter Bar
- **Brand filter**: dropdown populated from `GET /api/brands`, options: All / each brand code
- **Status filter**: dropdown with options: All / Active / Inactive
- **Add button**: opens create modal

### Add/Edit Modal

Form fields:
| Field      | Input Type              | Validation                                            |
|------------|--------------------------|--------------------------------------------------------|
| 品牌       | Select (from brand list)    | Required                                              |
| 基準幣別   | Select (from currency list) | Required, must differ from quote                    |
| 對應幣別   | Select (from currency list) | Required, must differ from base                     |
| 匯率類型   | Radio / Select: 手動 / 自動 | Required                                              |
| 匯率       | Number                   | Required, > 0. When `自動` selected, show helper text "系統將自動更新匯率，此值為目前/備援匯率" but keep the field editable |
| 狀態       | Toggle                   | Default: on                                           |

The brand dropdown is populated from `GET /api/brands` (all brands, active or not — a pair under a currently-disabled brand can still be viewed/edited). Currency dropdowns are populated from `GET /api/currencies` (active currencies only, or all — consistent with how the Currency page's own filters work). Selecting the same value for both base and quote shows an inline error and disables submit.

### Delete Confirmation
- Show confirmation dialog: "確定要刪除 {brandCode} 品牌的幣種對 {baseCode}/{quoteCode} 嗎？"
- On confirm: call DELETE API, refresh table

## API Integration

| Action   | Method | Endpoint                     | Trigger                    |
|----------|--------|-------------------------------|-----------------------------|
| List     | GET    | /api/currency-pairs (optionally `?brandId=`) | Page load, filter change |
| Brands (for filter/picker) | GET | /api/brands       | Page load / modal open       |
| Currencies (for pickers) | GET | /api/currencies   | Page load / modal open       |
| Create   | POST   | /api/currency-pairs           | Modal submit                 |
| Update   | PUT    | /api/currency-pairs/{id}      | Modal submit                 |
| Delete   | DELETE | /api/currency-pairs/{id}      | Confirm dialog                |

### Error Handling
- **400 on create/update** (base == quote, rate ≤ 0, invalid type): show inline field error, e.g. "基準幣別與對應幣別不可相同"
- **404 on create/update** (referenced currency or brand missing): show toast "幣種不存在，請重新整理頁面" or "品牌不存在，請重新整理頁面"
- **409 on create/update** (duplicate pair within the same brand): show inline error "此品牌已存在相同的幣種對"
- **404 on edit/delete** (pair itself missing): show toast "幣種對不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching

## Required Update to Existing Currency Page

`develop/frontend/src/pages/CurrencyPage.tsx` (and `develop/frontend/src/api/currencyApi.ts` if needed) must handle the new `409` response from `DELETE /api/currencies/{id}` that the backend now returns when a currency is referenced by a currency pair (see `specs/backend/currency-pair.md`):
```json
{
    "error": "Currency is referenced by one or more currency pairs and cannot be deleted",
    "id": 1
}
```
On this response, show a toast: "此幣種已配置於幣種對，無法刪除" instead of the generic network-error toast, and keep the row in the table (do not optimistically remove it).

No change is required to the currency edit modal's code-field handling — it already renders `code` as disabled on edit, matching the backend's now-enforced immutability (`specs/backend/currency-pair.md`).

## Acceptance Criteria
- [ ] Currency pair table renders with all columns, including brand, base/quote shown as codes
- [ ] Pairs load from API on page mount
- [ ] Brand filter works (All / each brand)
- [ ] Status filter works (All / Active / Inactive)
- [ ] Add modal opens, brand picker populated from `/api/brands`, currency pickers populated from `/api/currencies`, validates base ≠ quote, creates via API
- [ ] Edit modal pre-fills data (including brand), updates via API
- [ ] Delete shows confirmation and deletes via API
- [ ] Rate type toggle switches between 手動/自動, rate field remains editable in both modes
- [ ] Error states display correct Chinese messages for 400/404/409/network cases
- [ ] Table refreshes after create/update/delete
- [ ] Empty state shown when no pairs match filter
- [ ] Currency page delete flow shows "此幣種已配置於幣種對，無法刪除" on the new 409 response and leaves the row in place
