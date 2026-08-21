# Currency Pair Definition API

此 API 負責幣種對「定義」本身的建立、查詢、修改精度與刪除，是全站共用的主檔，不分品牌。新增一筆定義時，系統會立即為目前所有品牌各建立一筆對應的品牌幣種對（預設停用、自動匯率），因此新增操作同時影響兩張資料。定義本身沒有啟用/停用狀態；查詢一律讀取目前生效資料，修改僅能調整精度，基準幣與報價幣一經建立即不可變更。所有異動都會直接套用到正式資料，不經過送審核准流程。刪除定義前必須確認其下所有品牌幣種對皆已停用，否則會被擋下並附上仍啟用的品牌清單。

## 欄位定義
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 主鍵 |
| baseCurrencyId | 關聯欄位 | 建立時必填，須對應現有幣種；建立後不可變更 |
| baseCurrencyCode | 顯示欄位 | 唯讀，由基準幣種帶出 |
| quoteCurrencyId | 關聯欄位 | 建立時必填，須對應現有幣種，且不可與基準幣種相同；建立後不可變更 |
| quoteCurrencyCode | 顯示欄位 | 唯讀，由報價幣種帶出 |
| precision | 數值設定 | 選填，預設為 4；範圍 0–8；可異動 |
| createdAt / updatedAt | 系統時間 | 系統自動維護 |

## 限制條件
- 基準幣與報價幣一旦建立即不可變更，僅精度可調整。
- 「基準幣＋報價幣」組合須全站唯一，重複建立會被拒絕。
- 基準幣與報價幣不可為同一種幣。
- 精度僅接受 0–8 的整數。
- 刪除前必須所有下屬品牌幣種對皆已停用，否則拒絕並附上仍啟用的品牌清單；成功刪除時會連同其下所有（已停用的）品牌幣種對一併移除。

## 跨主題規則
- 新增一筆幣種對定義時，會立即為目前所有品牌各自建立一筆對應的品牌幣種對（預設自動匯率、停用狀態），兩者在同一次操作中一起完成（見 currency-pair.md）。
- 刪除定義時是否允許，取決於其下所有品牌幣種對目前的啟用狀態；只要有任何一筆仍啟用即擋下刪除（見 currency-pair.md）。

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currency-pair-definitions | 查詢所有幣種對定義 | Live direct |
| GET | /api/currency-pair-definitions/{id} | 查詢單一幣種對定義 | Live direct |
| POST | /api/currency-pair-definitions | 建立新的幣種對定義，並自動為所有品牌建立對應的品牌幣種對 | Direct |
| PUT | /api/currency-pair-definitions/{id} | 調整幣種對定義的精度設定 | Direct |
| DELETE | /api/currency-pair-definitions/{id} | 刪除幣種對定義（需其下品牌幣種對皆已停用） | Direct |
