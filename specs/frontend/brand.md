---
status: pending
title: "Brand Management Page"
requirement: "匯率中心需要品牌管理畫面，列出七個品牌 au, moneta, pug, star, um, vjp, vt，並可開啟/關閉品牌"
depends_on: []
---

# Brand Management — Frontend Spec

## Overview
A page under the "匯率中心" (Exchange Rate Center) section that lists all seeded brands and lets an admin toggle each brand's active/inactive state. Backed entirely by [brand.md](../backend/brand.md).

## Requirements

### Page: 品牌管理 (`/brands`)
- Located under the "匯率中心" sidebar group, alongside other exchange-rate-center pages.
- On load, calls `GET /api/brands` and renders one row per brand.
- Table columns: `品牌代碼` (code), `品牌名稱` (name), `狀態` (active — toggle switch).
- No "新增"/"刪除" controls anywhere on this page — brands are fixed at seven, seeded only.

### Layout (ASCII mockup)
```
匯率中心 > 品牌管理
┌─────────────────────────────────────┐
│ 品牌代碼   品牌名稱      狀態          │
├─────────────────────────────────────┤
│ au        au           [●  ] 啟用    │
│ moneta     moneta       [●  ] 啟用    │
│ pug        pug          [●  ] 啟用    │
│ star       star         [ ● ] 停用    │
│ um         um           [●  ] 啟用    │
│ vjp        vjp          [●  ] 啟用    │
│ vt         vt           [●  ] 啟用    │
└─────────────────────────────────────┘
```

### Interactions
- Clicking a row's toggle switch immediately calls `PUT /api/brands/{id}` with `{ "active": <new value> }`.
- While the request is in flight, disable that row's toggle and show a "更新中..." label next to it.
- On success, update the row's toggle to the new state.
- On failure, revert the toggle to its previous state and show an inline error toast ("更新品牌狀態失敗，請稍後再試").

## API Integration
| Action | Method | Path | Request | Response |
|---|---|---|---|---|
| 載入品牌清單 | GET | /api/brands | — | `[{id, code, name, active, createdAt, updatedAt}]` |
| 切換品牌狀態 | PUT | /api/brands/{id} | `{active: boolean}` | `{id, code, name, active, createdAt, updatedAt}` |

## Error States
- List load failure: show an inline error message with a "重試" button instead of the table.
- Toggle failure: see Interactions above — revert + toast, table stays interactive.

## Acceptance Criteria
- [ ] Page loads all 7 brands from `GET /api/brands` and displays code/name/active for each.
- [ ] Toggling a brand calls `PUT /api/brands/{id}` with the new `active` value and reflects the result in the table.
- [ ] A toggle shows a disabled "更新中..." state while its request is in flight.
- [ ] A failed toggle reverts the switch and shows an error toast.
- [ ] The page has no create/delete UI for brands.
