# Currency Pair API

此規格提供幣種對的查詢、更新與刪除功能，每個幣種對都歸屬於某一個品牌，並可設定手動或自動維護的匯率。查詢一律讀取目前生效的資料，不受任何審核流程影響。更新與刪除則不會直接套用，而是先建立一筆待審核請求，等待核准後才會真正套用到正式資料上（回傳202，表示已受理但待審核）。目前沒有任何建立幣種對的功能——不論是直接建立還是送審建立都不存在，一個品牌若要擁有某個幣種對，必須先由全域幣種對定義建立該方向的定義，系統才會自動產生對應的幣種對資料。

## 欄位定義
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| brandId / brandCode | 所屬品牌 | 必須參照既有品牌；brandCode 為查詢時附加的顯示欄位 |
| baseCurrencyId / baseCurrencyCode | 基礎幣別 | 必須參照既有貨幣，且不可與報價幣別相同 |
| quoteCurrencyId / quoteCurrencyCode | 報價幣別 | 必須參照既有貨幣，且不可與基礎幣別相同 |
| rate | 匯率 | rateType 為 MANUAL 時必填且須大於0；rateType 為 AUTO 時一律為 null |
| rateType | 匯率模式 | MANUAL（手動輸入）或 AUTO（系統維護） |
| active | 啟用狀態 | 布林值 |
| createdAt / updatedAt | 時間戳 | 系統自動記錄 |

## 限制條件
- 每個幣種對必須屬於一個品牌，且 (品牌, 基礎幣別, 報價幣別) 組合不可重複，不同品牌可有相同的基礎/報價組合
- 基礎幣別與報價幣別不可相同
- rateType=MANUAL 時必須提供大於0的匯率；若更新時未提供匯率，則沿用既有匯率，若原本也沒有匯率則拒絕（400）
- rateType=AUTO 時匯率一律清空為 null，無論請求提供什麼值都不會被接受，也不會因此被拒絕
- 沒有建立幣種對的功能，幣種對只能透過全域定義的自動建立機制產生
- 更新與刪除不會直接套用，需送審核准後才生效

## 跨主題規則
- 幣種對只能透過全域幣種對定義建立時的自動建立機制產生，沒有任何直接或送審的建立路徑（見 currency-pair-definition.md）
- 幣種對必須歸屬於既有品牌（見 brand.md）
- 更新與刪除需透過通用審核模組送審才能生效（見 currency-pair-approval.md、audit.md）
- 幣別若被任何幣種對引用，該幣別即無法被刪除（見 currency.md）

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currency-pairs | 查詢幣種對列表，可依品牌與啟用狀態篩選 | Live direct |
| GET | /api/currency-pairs/{id} | 查詢單一幣種對 | Live direct |
| POST | /api/currency-pairs | 不存在——幣種對只能透過全域定義的自動建立機制產生，沒有任何建立端點 | No API |
| PUT | /api/currency-pairs/{id} | 提交幣種對更新請求，需送審核准後才套用至正式資料 | Audited |
| DELETE | /api/currency-pairs/{id} | 提交幣種對刪除請求，需送審核准後才真正刪除 | Audited |
