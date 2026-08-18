# Audit Module — Generic Approval Service and API

此規格定義一套通用的審核（maker-checker）機制，讓任何需要經過審核才能生效的異動都能套用同一套流程，而不需要在審核模組本身加入任何特定業務邏輯。異動送出後會先建立一筆待審核請求，狀態為待審核，直到有人核准或拒絕為止；核准時會重新檢查異動內容是否仍然合理，若不合理則請求維持待審核，不會自動被拒絕。拒絕請求時必須填寫拒絕原因，且已經被核准或拒絕過的請求不能再被重複審核（拒絕並說明衝突，409）。同一筆資料同時只能有一筆待審核的請求，避免重複送審造成衝突。此模組本身只提供查詢、核准、拒絕的能力，實際的欄位驗證與資料異動邏輯，由各個使用此模組的業務功能自行負責。

## 欄位定義
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| entityType | 業務實體類型 | 由各業務模組自行定義（例如 CURRENCY_PAIR），同一類型在所有已註冊模組中須唯一 |
| actionType | 動作類型 | CREATE / UPDATE / DELETE |
| entityId | 目標資料識別碼 | CREATE 時為 null，核准後才會設定為新建資料的識別碼 |
| beforeSnapshot | 異動前快照 | UPDATE/DELETE 時記錄異動前的資料內容；CREATE 時為 null |
| afterSnapshot | 異動後快照 | CREATE/UPDATE 時記錄提議的資料內容；DELETE 時為 null |
| summary | 摘要說明 | 由對應業務模組產生的簡短可讀文字，供審核清單顯示 |
| status | 審核狀態 | PENDING（待審核）/ APPROVED（已核准）/ REJECTED（已拒絕） |
| requestedBy | 送審人 | 選填，自由輸入文字，無登入驗證機制 |
| requestedAt | 送審時間 | 系統自動記錄 |
| reviewedBy | 審核人 | 選填，自由輸入文字 |
| reviewedAt | 審核時間 | 核准或拒絕時自動記錄 |
| rejectReason | 拒絕原因 | 拒絕時必填（非空白），核准時為 null |
| createdAt / updatedAt | 建立/更新時間 | 系統自動記錄 |

## 限制條件
- 同一 (entityType, entityId) 同時只能存在一筆待審核請求
- 只有狀態為待審核的請求才能被核准或拒絕，已審核過的請求再次審核會被拒絕並說明衝突（409）
- 核准時會重新以最新資料驗證提議內容，驗證失敗則請求維持待審核狀態，不會自動轉為拒絕
- 拒絕請求時拒絕原因為必填，缺漏會被拒絕並說明原因（400）
- 此模組本身不得包含任何特定業務實體的邏輯，所有實體專屬的驗證與套用邏輯皆由各自的處理器負責

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/audit-requests | 查詢審核請求列表，可依實體類型/狀態/動作類型篩選 | Live direct |
| GET | /api/audit-requests/{id} | 查詢單一審核請求詳情 | Live direct |
| POST | /api/audit-requests/{id}/approve | 核准一筆待審核請求，重新驗證後將提議內容套用至正式資料 | Direct |
| POST | /api/audit-requests/{id}/reject | 拒絕一筆待審核請求並記錄拒絕原因，不影響正式資料 | Direct |
