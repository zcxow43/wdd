# Spread (點差) API

此規格提供兩種點差設定：每個品牌固定一筆的預設點差，以及可以自由新增/修改/刪除的客制點差群組，群組可以把多個幣種對放在一起共用同一組點差設定。查詢一律讀取目前生效、已核准的資料，但預設點差的更新，以及客制點差群組的建立、更新、刪除，都不會直接套用，而是要先送出審核請求，等待核准後才會真正生效。另外提供一個查詢端點，用來查出某個幣種對目前實際適用的點差——如果它屬於某個客制群組就用群組的點差，否則就使用其品牌的預設點差，這個查詢一律讀取即時、已核准的資料，不受任何待審核請求影響。

## 欄位定義
**SpreadDefault（預設點差）**

| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| brandId / brandCode | 所屬品牌 | 每個品牌固定一筆，無法新增或刪除 |
| depositSpread（入金點差） | 點差數值 | 必填，須大於等於0 |
| withdrawSpread（出金點差） | 點差數值 | 必填，須大於等於0 |
| createdAt / updatedAt | 時間戳 | 系統自動記錄 |

**SpreadGroup（客制點差群組）**

| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| brandId / brandCode | 所屬品牌 | 必須參照既有品牌 |
| name | 群組名稱 | 必填，非空白，最多100字元，同品牌內不可重複 |
| depositSpread（入金點差） | 點差數值 | 必填，須大於等於0 |
| withdrawSpread（出金點差） | 點差數值 | 必填，須大於等於0 |
| currencyPairIds / members | 成員幣種對清單 | 選填（預設為空清單），清單內不可有重複幣種對，每個幣種對必須屬於同一品牌 |
| createdAt / updatedAt | 時間戳 | 系統自動記錄 |

## 限制條件
**SpreadDefault**
- 每個品牌固定有一筆預設點差資料，沒有新增或刪除的功能
- depositSpread、withdrawSpread 皆為必填，且不可為負數
- 更新需送審核准後才生效

**SpreadGroup**
- 群組名稱在同一品牌內不可重複，包含與其他待審核建立請求的品牌/名稱組合比對
- depositSpread、withdrawSpread 為必填，且不可為負數
- 一個幣種對同時只能屬於一個客制點差群組，將其指派至新群組不會被拒絕，而是核准後自動將其從原群組移出
- 群組成員的幣種對必須屬於與群組相同的品牌
- 建立、更新、刪除皆需送審核准後才生效；核准刪除會一併移除其所有成員關係

## 跨主題規則
- 點差群組的成員必須是屬於同品牌、已存在的幣種對（見 currency-pair.md）
- 未配置客制點差群組的幣種對，會使用其所屬品牌的預設點差（見 currency-pair.md、brand.md）
- 所有點差異動皆透過通用審核模組送審，套用時機與幣種對的審核流程相同（見 audit.md、currency-pair-approval.md）

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/spread-defaults | 查詢預設點差列表，可依品牌篩選 | Live direct |
| GET | /api/spread-defaults/{id} | 查詢單一預設點差 | Live direct |
| PUT | /api/spread-defaults/{id} | 提交預設點差更新請求，需送審核准後才套用 | Audited |
| GET | /api/spread-groups | 查詢客制點差群組列表，可依品牌篩選 | Live direct |
| GET | /api/spread-groups/{id} | 查詢單一客制點差群組（含成員清單） | Live direct |
| POST | /api/spread-groups | 提交建立客制點差群組請求，需送審核准後才建立 | Audited |
| PUT | /api/spread-groups/{id} | 提交客制點差群組更新請求（含成員異動），需送審核准後才套用 | Audited |
| DELETE | /api/spread-groups/{id} | 提交刪除客制點差群組請求，需送審核准後才刪除 | Audited |
| GET | /api/spread-groups/resolve/{currencyPairId} | 查詢指定幣種對目前實際生效的點差（群組點差或預設點差） | Live direct |
