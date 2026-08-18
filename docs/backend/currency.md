# Currency API

此規格提供貨幣資料的完整查詢、新增、修改與刪除功能，所有異動都是直接套用，不需要經過審核流程。貨幣沒有啟用/停用的中間狀態，一個貨幣資料只有「存在」與「被刪除」兩種狀態。查詢貨幣清單一律回傳全部資料，沒有任何篩選條件。

## 欄位定義
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| code | 貨幣代碼 | 必填，須為3個大寫字母，且不可與既有貨幣重複 |
| name | 英文名稱 | 必填，最多100字元 |
| nameZh | 中文名稱 | 選填，最多100字元 |
| symbol | 貨幣符號 | 選填，最多10字元 |
| decimalPlaces | 小數位數 | 必填，整數0–8 |
| createdAt / updatedAt | 時間戳 | 系統自動記錄 |

## 限制條件
- code 為必填，格式須為3個大寫字母，且不可與既有貨幣重複（建立時檢查唯一性）
- decimalPlaces 為必填，範圍0–8的整數
- name 為必填，最多100字元；nameZh、symbol 為選填
- 沒有啟用/停用（active）的中間狀態，整個實體已完全移除此欄位與篩選條件

## 跨主題規則
- 貨幣若被任何幣種對引用（作為基礎或報價幣別），即無法被刪除，會被拒絕並說明原因（409，見 currency-pair.md）

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currencies | 查詢貨幣列表（無篩選參數，一律回傳全部） | Live direct |
| GET | /api/currencies/{id} | 查詢單一貨幣 | Live direct |
| POST | /api/currencies | 建立新貨幣 | Direct |
| PUT | /api/currencies/{id} | 更新貨幣資料（部分欄位更新） | Direct |
| DELETE | /api/currencies/{id} | 刪除貨幣 | Direct |
