# 幣別匯率中心 ER 模型

本資料庫涵蓋交易所匯率中心的核心資料結構，依外鍵關聯拆分為三大功能群組：**幣種對管理**（全系統共用的幣種與幣種對定義、各品牌各自的幣種對設定）、**品牌與點差管理**（品牌主檔、品牌預設點差、品牌自訂點差群組），以及**審核紀錄**（品牌幣種對與點差變更的待審/已審申請歷程）。其中幣種對管理與品牌與點差管理彼此有跨群組外鍵互相參照，審核紀錄群組則刻意與所有資料表完全隔離，不與任何其他表存在外鍵關聯。

## 全景

![全景](er-model/0.png)

## currency-pair

此群組管理「幣種對這件事本身要不要存在、精度多少」（全系統共用）與「哪個品牌要不要開啟這個幣種對、用自動還是手動匯率、套用哪個點差群組」（各品牌各自一份）。

![currency-pair 叢集](er-model/currency-pair.png)

- **currency**：全系統共用的幣種主檔（如 USD、JPY、TWD），`code` 建立後不可變更；本身無外鍵欄位，但被 `currency_pair_definition` 以 `base_currency_id`/`quote_currency_id` 參照——未指定 `ON DELETE`，MySQL 預設為限制刪除（RESTRICT），即已被任何幣種對定義使用的幣別無法直接刪除。
- **currency_pair_definition**：全系統、品牌無關的幣種對定義（基準幣/報價幣/精度），無開關狀態；`base_currency_id`/`quote_currency_id` 皆為 RESTRICT（預設，未設 `ON DELETE`）；被 `currency_pair` 以 `ON DELETE CASCADE` 參照——刪除定義會級聯刪光其下所有品牌的 `currency_pair` 列，僅在應用層確認所有列皆未啟用後才允許觸發此刪除。
- **currency_pair**：每個品牌對某一幣種對定義的個別設定（開關、自動/手動匯率、所屬點差群組）；`currency_pair_definition_id` 為 `ON DELETE CASCADE`，`brand_id` 為 RESTRICT（預設，未設 `ON DELETE`），`spread_group_id` 為 `ON DELETE SET NULL`（刪除點差群組時退回品牌預設點差，而非刪除此列）。

## brand-spread

此群組管理品牌主檔，以及每個品牌的兩層點差設定：品牌層級的預設點差（無群組時的 fallback），以及品牌可自訂的多個點差群組。

![brand-spread 叢集](er-model/brand-spread.png)

- **brand**：品牌主檔，內建七個品牌，`code`/`name` 建立後不可變更、僅 `active` 可調整；被 `brand_spread`（`ON DELETE CASCADE`）、`spread_group`（`ON DELETE CASCADE`）與跨群組的 `currency_pair.brand_id`（RESTRICT，預設）參照。
- **brand_spread**：品牌的預設點差（入金/出金），每品牌恰一列（`brand_id` 唯一）；`brand_id` 為 `ON DELETE CASCADE`，刪除品牌時一併刪除其預設點差列。
- **spread_group**：品牌自訂的具名點差群組，同品牌內名稱唯一；`brand_id` 為 `ON DELETE CASCADE`（刪除品牌時連帶刪除其所有群組），並被跨群組的 `currency_pair.spread_group_id` 以 `ON DELETE SET NULL` 參照（刪除群組不會刪除成員幣種對，僅將其 `spread_group_id` 清為 `NULL`，退回品牌預設點差）。

## audit

此群組僅有 `audit_request` 一張表，記錄品牌幣種對與點差的新增/修改/刪除申請，在設計上刻意不與任何資料表建立外鍵關聯——因此這張圖沒有任何關聯線，是正確的呈現而非遺漏。

![audit 叢集](er-model/audit.png)

- **audit_request**：記錄品牌幣種對與點差（`brand_spread`／`spread_group`／群組成員關係）的新增/修改/刪除申請，含待審與歷史紀錄。**刻意不設任何外鍵**——`entity_id`、`brand_id` 皆為一般欄位，可能指向已不存在的資料列；理由是核准後的 `DELETE` 會直接刪除目標列，若加上外鍵，要嘛級聯刪光審核歷史、要嘛反過來阻擋業務資料的刪除，兩者都會讓審核紀錄失去查核價值，因此這是設計上的刻意選擇而非疏漏。`pending_key` 是一個 stored generated column，僅在 `status = 'PENDING'` 且 `entity_id` 非空時產生 `<entity_type>:<entity_id>`，並以唯一鍵保證「同一標的同時只能有一筆待審申請」。
