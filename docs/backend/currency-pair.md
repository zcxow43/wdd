# Currency Pair API (Brand-Scoped) API

此 API 提供每個品牌自己的幣種對設定（開啟關閉、自動或手動匯率）之查詢與異動。查詢一律讀取目前生效資料，不受任何待審請求影響。新增、修改、刪除這三個動作全部改為送審制：呼叫時只會建立一筆待審請求，不會立即套用，唯有審核人員核准後該筆幣種對才會真正被建立、修改或刪除。唯一的例外是系統自動產生的寫入——新增幣種對定義時對每個品牌自動建立一筆、刪除定義時連動刪除所有相關筆數——這兩者屬於定義本身操作的連帶結果，不經過審核直接寫入。

## 欄位定義

**CurrencyPair（品牌幣種對）**

| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統主鍵 |
| currencyPairDefinitionId | 所屬幣種對定義 | 建立時必填，須參照存在的定義；建立後不可修改 |
| baseCurrencyCode / quoteCurrencyCode | 基準／報價幣別代碼 | 唯讀，經由所屬定義帶出 |
| brandId | 所屬品牌 | 建立時必填，須參照存在的品牌；建立後不可修改 |
| brandCode | 品牌代碼 | 唯讀，經由品牌帶出 |
| rateType | 匯率類型 | AUTO（自動）或 MANUAL（手動），未指定時預設 AUTO |
| rate | 匯率值 | 僅 MANUAL 時必填且須大於 0，小數位數不可超過所屬定義的精度限制；AUTO 時一律強制為空 |
| active | 是否啟用 | 預設為否，可自由切換 |
| spreadGroupId | 所屬點差群組 | 唯讀，未加入任何群組時為空；不可透過本 API 寫入，指派只能透過點差群組成員端點進行 |
| spreadGroupName | 點差群組名稱 | 唯讀，經由所屬群組帶出，未加入時為空 |
| createdAt / updatedAt | 建立／更新時間 | 系統自動維護 |

## 限制條件
- currencyPairDefinitionId、brandId 皆於建立後不可修改。
- rate 僅在 rateType 為 MANUAL 時必填且須大於 0，小數位數不可超過所屬定義的精度限制；rateType 為 AUTO 時 rate 一律強制清空。
- (currencyPairDefinitionId, brandId) 組合須唯一，重複視為衝突（409）。
- spreadGroupId／spreadGroupName 為唯讀，建立或修改時即使夾帶也一律忽略，不會透過本 API 產生任何指派效果。
- 同一筆幣種對同時只能有一筆待審請求，違反視為衝突（409）。
- 刪除不受 active 狀態限制，任何啟用狀態的幣種對都能送出刪除審核。
- 新增、修改、刪除全部改為送審制，呼叫當下不會寫入資料表，需核准後才真正套用。

## 跨主題規則
- currencyPairDefinitionId 必須參照存在的幣種對定義，且手動匯率的小數位數不可超過該定義的精度設定（見 currency-pair-definition.md）。
- brandId 必須參照存在的品牌（見 brand.md）。
- spreadGroupId 的加入／移出只能透過點差群組的成員端點異動，本 API 的建立與修改一律忽略該欄位（見 spread.md）。
- 新增、修改、刪除都改為送至通用審核模組建立待審請求，並在核准後才真正套用；同一標的的待審唯一性、審核與套用流程統一由該模組管理（見 audit.md）。
- 幣種對定義建立時的自動建立（fan-out）與刪除時的連動刪除（cascade）不經過審核、直接寫入，因為它們是定義本身操作的結果，不是對品牌幣種對的直接使用者操作（見 currency-pair-definition.md）。

## API 清單

| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currency-pairs | 查詢品牌幣種對清單，可依所屬定義、品牌、啟用狀態篩選 | Live direct |
| GET | /api/currency-pairs/{id} | 查詢單筆品牌幣種對詳情 | Live direct |
| POST | /api/currency-pairs | 送出新增品牌幣種對的審核請求 | Audited |
| PUT | /api/currency-pairs/{id} | 送出修改品牌幣種對（匯率類型／匯率／啟用狀態）的審核請求 | Audited |
| DELETE | /api/currency-pairs/{id} | 送出刪除品牌幣種對的審核請求 | Audited |
