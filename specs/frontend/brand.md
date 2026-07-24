---
status: pending
title: "Brand Management Page"
requirement: "Display the fixed set of brands (AU, MONETA, PUG, STAR, UM, VJP, VT) with an enable/disable toggle"
---

# Brand Management Page — Frontend Spec

## Overview
Build a simple brand management page listing the fixed set of brands, each with a toggle to enable/disable it. Consumes the API defined in `specs/backend/brand.md`. Brands are seeded (not creatable/deletable from the UI), so this page has no "Add" button and no delete action — only a per-row active toggle.

The brand list is also used elsewhere in the app: the Currency Pair page (`specs/frontend/currency-pair.md`) uses `GET /api/brands` to populate its brand filter/picker.

## Requirements
- Table page showing all 7 brands
- Each row has an active/inactive toggle switch that calls the API immediately (no separate save step)
- No create, edit-name, or delete actions — `code` and `name` are read-only, seeded values

## Page Layout

### Route
`/brands`

### Page Structure
```
┌──────────────────────────────────────┐
│  Brand Management                     │
│                                        │
│  ┌──────────────────────────────────┐│
│  │ Code   │ Name   │ Status          ││
│  │────────│────────│─────────────────││
│  │ AU     │ AU     │ [●  ] 啟用      ││
│  │ MONETA │ MONETA │ [●  ] 啟用      ││
│  │ PUG    │ PUG    │ [ ○ ] 停用      ││
│  │ ...    │ ...    │ ...             ││
│  └──────────────────────────────────┘│
└──────────────────────────────────────┘
```

### Table Columns

| Column | Source Field | Width  | Notes                                   |
|--------|--------------|--------|-------------------------------------------|
| 代碼   | code         | 100px  | Bold, monospace, uppercase                |
| 名稱   | name         | auto   |                                            |
| 狀態   | active       | 120px  | Toggle switch, label "啟用"/"停用" beside it |

Toggling immediately calls `PUT /api/brands/{id}` with the new `active` value; on success, update the row in place. On failure, revert the toggle and show a toast.

## API Integration

| Action | Method | Endpoint          | Trigger                     |
|--------|--------|--------------------|-------------------------------|
| List   | GET    | /api/brands        | Page load                     |
| Toggle | PUT    | /api/brands/{id}   | Clicking a row's toggle switch |

### Error Handling
- **404 on toggle**: show toast "品牌不存在，請重新整理頁面", refresh list
- **400 on toggle**: show toast "更新失敗，請稍後再試" (shouldn't normally occur since the toggle always sends a valid boolean)
- **Network error**: show toast "網路錯誤，請稍後再試", revert toggle to previous state
- **Loading state**: show skeleton/spinner while fetching

## Acceptance Criteria
- [ ] Brand table renders all 7 brands with code, name, and active toggle
- [ ] Brands load from API on page mount
- [ ] Toggling a row calls `PUT /api/brands/{id}` and updates state on success
- [ ] Toggle reverts and shows a toast on network/API failure
- [ ] No add/edit-name/delete controls are present on this page
- [ ] Error states display correct Chinese messages
