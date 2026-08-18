# Currency Pair as an Audit Consumer

此規格說明幣種對的更新與刪除如何透過通用審核模組送審，而不是直接套用到正式資料。呼叫更新或刪除時，系統會先建立一筆待審核請求，並回傳「已受理但待審核」的提示（202），實際的資料異動要等到審核核准後才會真正套用。這個流程完全沒有建立（CREATE）幣種對的能力——幣種對只能透過全域幣種對定義的自動建立機制產生，因此審核流程本身也不處理任何建立請求。查詢幣種對的功能不受此審核機制影響，一律讀取目前已核准、生效中的資料。

## 欄位定義
CURRENCY_PAIR 審核快照內容（送審與核准時比對用的資料結構）：

| Field | Type/Role | Rule |
|---|---|---|
| brandId / brandCode | 所屬品牌 | 快照內容，brandCode 為驗證時附加的品牌代碼 |
| baseCurrencyId / baseCurrencyCode | 基礎幣別 | 快照內容，須與報價幣別不同 |
| quoteCurrencyId / quoteCurrencyCode | 報價幣別 | 快照內容，須與基礎幣別不同 |
| rate | 匯率 | rateType 為 MANUAL 時為提議數值；AUTO 時強制清為 null |
| rateType | 匯率模式 | MANUAL 或 AUTO |
| active | 啟用狀態 | 快照內容 |

## 限制條件
- 此審核流程只處理 UPDATE 與 DELETE，沒有 CREATE 動作可用
- 同一幣種對同時只能存在一筆待審核請求，重複送審會被拒絕並說明衝突（409）
- 核准時會重新驗證品牌/幣別是否仍存在、幣別是否相同、匯率規則與唯一性，驗證失敗則請求維持待審核

## 跨主題規則
- 幣種對的建立完全不屬於此審核流程的範圍，唯一的建立方式是全域幣種對定義的自動建立機制（見 currency-pair-definition.md）
- 更新/刪除審核所沿用的品牌與幣別存在性、幣別不可相同、匯率規則與唯一性驗證，皆是 currency-pair.md 既有的業務規則（見 currency-pair.md）

## API 清單
| Method | Path | 用途 | 送審分類 |
|---|---|---|---|
| PUT | /api/currency-pairs/{id} | 提交幣種對更新請求，經核准後才套用至正式資料；GET 查詢端點不受影響，定義於 currency-pair.md | Audited |
| DELETE | /api/currency-pairs/{id} | 提交幣種對刪除請求，經核准後才真正刪除；GET 查詢端點不受影響，定義於 currency-pair.md | Audited |
