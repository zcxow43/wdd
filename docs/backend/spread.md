# Spread（品牌預設點差 + 點差群組） API

每個品牌的點差設定分為兩層：一層是每品牌恰好一筆的「預設點差」，另一層是品牌可自訂多筆的「點差群組」，每個品牌幣種對至多只能加入一個群組，若有加入群組就採用該群組的點差，否則採用品牌預設點差。點差是介於 0 到 100（含）之間的百分比數值，套用方式是以乘法反映在基礎匯率上（baseRate × (1 + spreadPercent / 100)），而非用固定金額相加；本 API 只負責儲存與提供這個百分比原始值，實際套用到匯率的計算是由其他主題完成。所有異動（更新預設點差、新增/修改/刪除群組、將幣種對加入或移出群組）都不會直接生效，而是建立一筆待審核請求，需經審核者核准後才會真正套用；查詢一律讀取目前已生效的資料，不受待審請求影響。

## 欄位定義

**BrandSpread（預設點差）**
| Field | Type/Role | Rule |
|---|---|---|
| brandId | 識別鍵 | 每個品牌固定一筆，不可透過本 API 新增或刪除 |
| brandCode | 唯讀顯示欄位 | 由品牌資料帶出 |
| depositSpreadPercent | 業務欄位（入金點差百分比） | 更新時必填；須介於 0～100（含）；小數最多 8 位；以乘法套用在基礎匯率上 |
| withdrawalSpreadPercent | 業務欄位（出金點差百分比） | 更新時必填；須介於 0～100（含）；小數最多 8 位；語意同上 |
| createdAt / updatedAt | 系統時間戳 | 系統自動維護 |

**SpreadGroup（群組點差）**
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別鍵 | 系統產生 |
| brandId | 所屬品牌 | 建立時必填、須為已存在品牌；建立後不可更改 |
| brandCode | 唯讀顯示欄位 | 由品牌資料帶出 |
| name | 群組名稱 | 必填，去除頭尾空白後 1～50 字元；同一品牌內不可重複 |
| depositSpreadPercent | 業務欄位（入金點差百分比） | 0～100（含）、小數最多 8 位；建立時未填預設為 0 |
| withdrawalSpreadPercent | 業務欄位（出金點差百分比） | 0～100（含）、小數最多 8 位；建立時未填預設為 0 |
| memberCount | 唯讀統計欄位 | 目前歸屬此群組的幣種對數量 |
| createdAt / updatedAt | 系統時間戳 | 系統自動維護 |

**SpreadGroupMember（群組成員唯讀投影）**
| Field | Type/Role | Rule |
|---|---|---|
| currencyPairId | 品牌幣種對識別鍵 | 對應一筆品牌幣種對 |
| currencyPairDefinitionId | 幣種對定義識別鍵 | 由幣種對帶出 |
| baseCurrencyCode / quoteCurrencyCode | 顯示欄位 | 由定義帶出 |
| active | 顯示欄位 | 該幣種對本身是否啟用，僅供參考，與是否屬於本群組無關 |

## 限制條件

BrandSpread
- 每個品牌恰好一筆，永遠不會透過本 API 被建立或刪除
- 查無資料時（例如新品牌尚未有預設點差列）會在查詢時自動補一筆全部為 0 的資料，讀取端永遠拿得到資料
- 每個品牌的預設點差同時只能有一筆待審核請求，重複送出會被拒絕（409）

SpreadGroup
- 所屬品牌建立後不可更改
- 同一品牌內群組名稱不可重複
- 一個群組同時只能有一筆待審核的「群組編輯」請求（409）
- 刪除群組沒有任何限制條件——即使群組還有成員也可以刪除，刪除後所有成員幣種對會回退為使用品牌預設點差

SpreadGroupMember
- 每個品牌幣種對同時只能加入一個群組；要換到別的群組須先移出目前群組
- 一次加入群組是整批全有或全無：批次內任何一筆驗證失敗，整批都不會生效
- 重複加入已在本群組內的幣種對視為不做任何事，不視為錯誤
- 一個群組同時只能有一筆待審核的「成員異動」請求（409），且與「群組編輯」請求各自獨立計算，可同時各有一筆待審

## 跨主題規則

- 幣種對是否加入點差群組（`spreadGroupId`）只能透過本 API 的群組成員端點異動，幣種對自己的建立/更新 API 無法直接指定或修改（見 currency-pair.md）。
- 幣種對顯示的「入金/出金加點完成匯率」是把本 API 解析出的目前生效點差百分比（群組優先、否則品牌預設）套用到基礎匯率上算出來的即時結果（見 currency-pair.md）。
- 每次執行匯率同步時，會凍結當下每個品牌的目前生效點差百分比，計算出入金/出金匯率快照存起來，之後品牌點差再變動也不會回頭改寫已同步的快照（見 exchange-rate.md）。

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/brand-spreads | 查詢各品牌的預設點差（可依品牌篩選） | Live direct |
| GET | /api/brand-spreads/{brandId} | 查詢單一品牌的預設點差 | Live direct |
| PUT | /api/brand-spreads/{brandId} | 送出品牌預設點差調整申請 | Audited |
| GET | /api/spread-groups | 查詢點差群組清單（可依品牌篩選） | Live direct |
| GET | /api/spread-groups/{id} | 查詢單一點差群組明細與成員清單 | Live direct |
| POST | /api/spread-groups | 送出新增點差群組申請 | Audited |
| PUT | /api/spread-groups/{id} | 送出點差群組修改申請 | Audited |
| DELETE | /api/spread-groups/{id} | 送出刪除點差群組申請 | Audited |
| POST | /api/spread-groups/{id}/members | 送出將幣種對批次加入群組的申請 | Audited |
| DELETE | /api/spread-groups/{id}/members/{currencyPairId} | 送出將指定幣種對移出群組的申請 | Audited |
| GET | /api/spreads/effective | 查詢每個品牌幣種對目前實際生效的點差百分比（群組或品牌預設） | Live direct |
