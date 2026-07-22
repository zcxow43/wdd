---
status: pending
title: "Currency Table Page"
requirement: "Display currencies in a table with CRUD operations"
---

# Currency Table Page — Frontend Spec

## Overview
Build a currency management page that displays all currencies in a table. Users can view, add, edit, and delete currencies. Consumes the API defined in `specs/backend/currency.md`.

## Requirements
- Table page showing all currencies
- Add new currency via modal/dialog form
- Edit existing currency inline or via modal
- Delete with confirmation
- Filter by active/inactive status

## Page Layout

### Route
`/currencies`

### Page Structure
```
┌──────────────────────────────────────────────┐
│  Currency Management                         │
│                                              │
│  [Active ▼]  [Search...]      [+ Add]        │
│                                              │
│  ┌──────────────────────────────────────────┐│
│  │ Code │ Name   │ 中文名 │ Symbol │ Active ││
│  │──────│────────│────────│────────│────────││
│  │ TWD  │ New..  │ 新台幣 │ NT$    │  ✓     ││
│  │ USD  │ Unit.. │ 美元   │ $      │  ✓     ││
│  │ ...  │ ...    │ ...    │ ...    │  ...   ││
│  └──────────────────────────────────────────┘│
└──────────────────────────────────────────────┘
```

### Table Columns

| Column         | Source Field   | Width  | Notes                        |
|----------------|----------------|--------|------------------------------|
| Code           | code           | 80px   | Bold, monospace              |
| Name           | name           | auto   |                              |
| 中文名稱       | nameZh         | 120px  | Fallback: dash if empty      |
| Symbol         | symbol         | 80px   | Center aligned               |
| Decimal Places | decimalPlaces  | 100px  | Center aligned               |
| Active         | active         | 80px   | Green dot / grey dot         |
| Actions        | —              | 120px  | Edit, Delete buttons         |

### Filter Bar
- **Status filter**: dropdown with options: All / Active / Inactive
- **Add button**: opens create modal

### Add/Edit Modal

Form fields:
| Field          | Input Type | Validation                          |
|----------------|------------|-------------------------------------|
| Code           | Text       | Required, 3 uppercase letters, disabled on edit |
| Name           | Text       | Required, max 100                   |
| 中文名稱       | Text       | Optional, max 100                   |
| Symbol         | Text       | Optional, max 10                    |
| Decimal Places | Number     | Required, 0–8                       |
| Active         | Toggle     | Default: on                         |

### Delete Confirmation
- Show confirmation dialog: "確定要刪除幣種 {code} 嗎？"
- On confirm: call DELETE API, refresh table

## API Integration

| Action   | Method | Endpoint                | Trigger           |
|----------|--------|-------------------------|--------------------|
| List     | GET    | /api/currencies         | Page load, filter change |
| Create   | POST   | /api/currencies         | Modal submit       |
| Update   | PUT    | /api/currencies/{id}    | Modal submit       |
| Delete   | DELETE | /api/currencies/{id}    | Confirm dialog     |

### Error Handling
- **409 on create**: show "幣種代碼已存在" inline error
- **404 on edit/delete**: show toast "幣種不存在，請重新整理頁面"
- **Network error**: show toast "網路錯誤，請稍後再試"
- **Loading state**: show skeleton/spinner while fetching

## Acceptance Criteria
- [ ] Currency table renders with all columns
- [ ] Currencies load from API on page mount
- [ ] Status filter works (All / Active / Inactive)
- [ ] Add modal opens, validates, and creates via API
- [ ] Edit modal pre-fills data, code field disabled, updates via API
- [ ] Delete shows confirmation and deletes via API
- [ ] Error states display correct Chinese messages
- [ ] Table refreshes after create/update/delete
- [ ] Empty state shown when no currencies match filter
