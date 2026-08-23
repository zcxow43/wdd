# Spread API (Brand Default + Spread Groups) API

此 API 管理每個品牌的兩層點差設定：每個品牌固定一筆預設點差（入金／出金各一個數值），以及可自由新增的多個具名點差群組，各自擁有自己的入金／出金點差；一個品牌幣種對至多加入一個群組，有加入群組者採群組的點差，否則採品牌預設點差。此 API 涵蓋預設點差查改、群組的建立/修改/刪除、以及幣種對加入/移出群組三大部分。所有寫入動作（含成員異動）全部改為送審制，呼叫時只建立待審請求，需核准後才真正套用；所有查詢（包含解析後的「目前生效點差」查詢）一律讀取已核准的資料，完全不受任何待審請求影響。

## 欄位定義

**BrandSpread（品牌預設點差）**

| Field | Type/Role | Rule |
|---|---|---|
| brandId | 所屬品牌 | 唯一識別該筆資料；每個品牌固定一筆，不透過本 API 建立或刪除 |
| brandCode | 品牌代碼 | 唯讀，經由品牌帶出 |
| depositSpread | 入金點差 | 修改時必填，須大於等於 0，小數位數上限 8 位 |
| withdrawalSpread | 出金點差 | 修改時必填，須大於等於 0，小數位數上限 8 位 |
| createdAt / updatedAt | 建立／更新時間 | 系統自動維護 |

**SpreadGroup（點差群組）**

| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統主鍵 |
| brandId | 所屬品牌 | 建立時必填，須參照存在的品牌；建立後不可修改 |
| brandCode | 品牌代碼 | 唯讀，經由品牌帶出 |
| name | 群組名稱 | 必填，去除前後空白後長度 1–50 字，同一品牌內須唯一 |
| depositSpread | 入金點差 | 須大於等於 0，小數位數上限 8 位，未提供時預設 0 |
| withdrawalSpread | 出金點差 | 須大於等於 0，小數位數上限 8 位，未提供時預設 0 |
| memberCount | 成員數 | 唯讀，反映目前歸屬此群組的品牌幣種對筆數 |
| createdAt / updatedAt | 建立／更新時間 | 系統自動維護 |

**SpreadGroupMember（群組成員，品牌幣種對的唯讀投影）**

| Field | Type/Role | Rule |
|---|---|---|
| currencyPairId | 品牌幣種對識別碼 | 對應成員的幣種對 |
| currencyPairDefinitionId | 所屬幣種對定義 | 經由幣種對帶出 |
| baseCurrencyCode / quoteCurrencyCode | 基準／報價幣別代碼 | 經由所屬定義帶出 |
| active | 該幣種對自身啟用狀態 | 僅供顯示參考，與是否加入群組無關 |

## 限制條件

**BrandSpread**
- 每個品牌固定一筆，本 API 不建立也不刪除該筆資料；若品牌存在但尚無該筆資料，查詢時會自動以零點差建立一筆再回傳。
- depositSpread、withdrawalSpread 修改時皆必填、須大於等於 0，小數位數不可超過 8 位。
- 同一品牌的預設點差同時只能有一筆待審請求，違反視為衝突（409）。
- 修改一律改為送審制，呼叫當下不寫入，需核准後才真正套用。

**SpreadGroup**
- brandId 建立後不可修改。
- name 於同一品牌內須唯一（不同品牌可使用相同名稱），改名若與同品牌其他群組撞名視為衝突（409）。
- depositSpread、withdrawalSpread 須大於等於 0，小數位數不可超過 8 位，未提供時預設為 0。
- memberCount 為唯讀，不可寫入。
- 同一群組本身的建立/修改/刪除，同時只能有一筆待審請求，違反視為衝突（409）。
- 刪除群組不受成員數量限制，即使仍有成員也可送出刪除審核；核准後成員的群組歸屬會清空，回到品牌預設點差。
- 新增、修改、刪除全部改為送審制，呼叫當下不寫入，需核准後才真正套用。

**SpreadGroupMember**
- 每個品牌幣種對同時只能屬於一個群組；欲改指派到別的群組前，必須先將其自目前群組移出。
- 加入群組的幣種對必須與群組屬於同一品牌，否則視為請求格式錯誤（400）。
- 一批加入請求以整批為單位審核，全部通過或全部不通過，不會只套用部分。
- 已在此群組中的幣種對再次送入加入清單，視為無異動，不算錯誤。
- 移出時若該幣種對並非此群組的現有成員，視為找不到（404）。
- 同一群組的成員異動（加入或移出）同時只能有一筆待審請求，以群組識別碼作為判斷鍵；群組本身異動與成員異動分屬不同標的類型，兩者可同時各自有一筆待審請求互不阻擋。
- 加入、移出全部改為送審制，呼叫當下不寫入，需核准後才真正套用。

## 跨主題規則
- SpreadGroup 的 brandId 必須參照存在的品牌（見 brand.md）。
- 群組成員加入的對象必須是存在的品牌幣種對，且移出/加入核准後實際異動的是該幣種對本身的所屬群組欄位（見 currency-pair.md）。
- 品牌幣種對查詢回應上的 spreadGroupId／spreadGroupName 兩個唯讀欄位，只能透過本 API 的群組成員端點異動，不可經由幣種對自身的建立/修改端點寫入（見 currency-pair.md）。
- 所有寫入動作（預設點差修改、群組建立/修改/刪除、成員加入/移出）都改為送至通用審核模組建立待審請求，並在核准後才真正套用；同一標的待審唯一性、審核與套用流程統一由該模組管理（見 audit.md）。

## API 清單

| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/brand-spreads | 查詢品牌預設點差清單，可依品牌篩選 | Live direct |
| GET | /api/brand-spreads/{brandId} | 查詢單一品牌的預設點差 | Live direct |
| PUT | /api/brand-spreads/{brandId} | 送出修改品牌預設點差的審核請求 | Audited |
| GET | /api/spread-groups | 查詢點差群組清單，可依品牌篩選 | Live direct |
| GET | /api/spread-groups/{id} | 查詢單一點差群組詳情與成員清單 | Live direct |
| POST | /api/spread-groups | 送出新增點差群組的審核請求 | Audited |
| PUT | /api/spread-groups/{id} | 送出修改點差群組（名稱／點差）的審核請求 | Audited |
| DELETE | /api/spread-groups/{id} | 送出刪除點差群組的審核請求 | Audited |
| POST | /api/spread-groups/{id}/members | 送出將一批品牌幣種對加入此群組的審核請求 | Audited |
| DELETE | /api/spread-groups/{id}/members/{currencyPairId} | 送出將一個品牌幣種對移出此群組的審核請求 | Audited |
| GET | /api/spreads/effective | 查詢一個品牌下每個幣種對目前實際生效的點差（群組或預設） | Live direct |
