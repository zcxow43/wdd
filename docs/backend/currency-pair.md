# Currency Pair（品牌幣種對） API

這個 API 管理每個品牌對各幣種對的個別設定，包含匯率類型（自動或手動）、匯率值與啟用狀態的新增、修改、刪除；查詢時每一列還會即時附上套用目前生效點差後的入金/出金匯率，方便直接顯示給使用者看，而不需要呼叫端自行計算。使用者對本 API 的每一筆新增、修改、刪除都不會立即生效，而是建立一筆待審核請求，需經審核核准後才會真正套用；查詢一律讀取目前生效資料。唯一例外是由「幣種對定義」新增/刪除所連動產生的批次建立或刪除，這類系統自動觸發的異動會直接套用，不經過審核流程。

## 欄位定義

**CurrencyPair**
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別鍵 | 系統產生 |
| currencyPairDefinitionId | 所屬幣種對定義 | 建立時必填、須為已存在定義；建立後不可更改 |
| baseCurrencyCode / quoteCurrencyCode | 顯示欄位 | 由定義帶出 |
| brandId | 所屬品牌 | 建立時必填、須為已存在品牌；建立後不可更改 |
| brandCode | 顯示欄位 | 由品牌帶出 |
| rateType | 匯率類型 | AUTO（自動）或 MANUAL（手動）；未填預設為 AUTO |
| rate | 手動匯率值 | 當類型為 MANUAL 時必填且須大於 0，小數位數不可超過所屬定義的精度限制；當類型為 AUTO 時一律強制為空，即使有送值也會被忽略 |
| active | 啟用狀態 | 未填預設為停用（false）；可自由切換 |
| spreadGroupId | 唯讀顯示欄位（所屬點差群組） | 未加入群組時為空；只能透過點差群組成員端點異動，本 API 的新增/修改一律忽略此欄位 |
| spreadGroupName | 唯讀顯示欄位 | 未加入群組時為空 |
| depositRate | 唯讀計算欄位（入金加點完成匯率） | 以基礎匯率乘上「(1 + 目前生效入金點差百分比 / 100)」即時算出的百分比加成結果，不是固定金額相加；基礎匯率若無法取得（AUTO 且尚未同步過）則為空；本 API 的新增/修改一律忽略此欄位 |
| withdrawalRate | 唯讀計算欄位（出金加點完成匯率） | 計算方式同上，改用目前生效出金點差百分比；同樣情況下可能為空；一律忽略送入的值 |
| createdAt / updatedAt | 系統時間戳 | 系統自動維護 |

## 限制條件

- `currencyPairDefinitionId`、`brandId` 建立後皆不可更改
- 同一組（幣種對定義, 品牌）只能存在一筆資料，重複建立會被拒絕
- 匯率類型為 MANUAL 時，匯率值為必填且必須大於 0，小數位數不可超過所屬定義的精度上限；切換回 AUTO 會使既有匯率值清空
- 刪除沒有任何限制條件——不論目前是否啟用都可以刪除
- 每一列同時只能有一筆待審核請求，重複送出異動會被拒絕
- `spreadGroupId`、`spreadGroupName`、`depositRate`、`withdrawalRate` 皆為唯讀，透過本 API 新增或修改時一律被忽略，不會被寫入

## 跨主題規則

- 幣種對加入或移出點差群組，只能透過點差群組成員的異動端點進行，本 API 完全不提供這個寫入路徑（見 spread.md）。
- `depositRate`/`withdrawalRate` 是把該幣種對目前生效的點差百分比（群組優先、否則品牌預設）即時套用到基礎匯率上算出的結果，與 spread.md 的「目前生效點差」查詢採用同一套解析邏輯，只是這裡直接回傳算好的匯率而不是原始百分比（見 spread.md）。
- 當匯率類型為 AUTO 時，基礎匯率取自該幣種對定義最近一次的同步匯率，若定義從未同步過則基礎匯率、進而入金/出金匯率皆為空（見 exchange-rate.md）。
- 由「幣種對定義」新增所連動批次建立、及定義刪除所連動批次移除的幣種對列，屬系統自動異動，直接套用不經審核（見 currency-pair-definition.md）。

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currency-pairs | 查詢品牌幣種對清單（可依定義/品牌/啟用狀態篩選） | Live direct |
| GET | /api/currency-pairs/{id} | 查詢單一品牌幣種對明細 | Live direct |
| POST | /api/currency-pairs | 送出新增品牌幣種對申請 | Audited |
| PUT | /api/currency-pairs/{id} | 送出修改品牌幣種對申請 | Audited |
| DELETE | /api/currency-pairs/{id} | 送出刪除品牌幣種對申請 | Audited |
