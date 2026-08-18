# Currency Pair Definition (Global Master) API

此規格提供一個與品牌無關的全域幣種對定義功能，讓使用者可以針對一組基礎/報價幣別方向建立一次定義，建立完成後系統會自動為所有品牌各建立一筆對應的幣種對資料（已存在的品牌資料則保留不動）。此功能的所有異動（建立、更新精度、刪除）都是直接套用，不需要經過審核流程。同一方向或其反方向只能存在一筆定義，避免重複；刪除定義前必須先確認所有品牌的對應幣種對都已停用，否則會被拒絕。刪除定義只會移除定義本身，並不會影響已經建立出來的各品牌幣種對資料。

## 欄位定義
| Field | Type/Role | Rule |
|---|---|---|
| id | 識別碼 | 系統自動產生 |
| baseCurrencyId / baseCurrencyCode | 基礎幣別 | 建立後不可變更 |
| quoteCurrencyId / quoteCurrencyCode | 報價幣別 | 建立後不可變更，且須與基礎幣別不同 |
| forwardPrecision（正向精度） | 精度設定 | 必填，整數 0–8，建立後仍可編輯 |
| reversePrecision（反向精度） | 精度設定 | 必填，整數 0–8，建立後仍可編輯 |
| createdAt / updatedAt | 時間戳 | 系統自動記錄 |

## 限制條件
- baseCurrencyId/quoteCurrencyId 建立後不可變更，只有精度可編輯
- 同一方向或其反方向只能存在一筆定義（例如已有 USD/JPY 定義，則 JPY/USD 不可再建立）
- 刪除前必須確認所有品牌的對應幣種對皆已停用（active=false），只要有任一品牌仍啟用即拒絕刪除
- 若某品牌完全沒有對應幣種對資料（例如被獨立刪除），不會阻擋定義的刪除，只有仍啟用中的資料才會阻擋
- 刪除定義只移除定義本身，不會刪除或修改任何已建立的品牌幣種對資料，且刪除後其反方向可再被建立

## 跨主題規則
- 建立定義時會自動為每個品牌建立一筆幣種對資料（已有相同組合者則跳過），此建立動作直接套用、不經過審核流程（見 currency-pair.md）
- 刪除定義前必須確認所有品牌的對應幣種對皆已停用，此停用狀態的變更需透過幣種對既有的審核流程完成（見 currency-pair-approval.md）

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| GET | /api/currency-pair-definitions | 查詢幣種對定義列表，可依基礎/報價幣別篩選 | Live direct |
| GET | /api/currency-pair-definitions/{id} | 查詢單一幣種對定義 | Live direct |
| POST | /api/currency-pair-definitions | 建立幣種對定義，並自動為所有品牌建立對應幣種對 | Direct |
| PUT | /api/currency-pair-definitions/{id} | 更新幣種對定義的精度設定 | Direct |
| DELETE | /api/currency-pair-definitions/{id} | 刪除幣種對定義（需先確認所有品牌該幣種對皆已停用） | Direct |
