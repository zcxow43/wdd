# User Flow Storyboards

Real-looking screen storyboards inferred from `specs/frontend/` — every frame is a rendered, realistically-populated HTML mockup (not a drawn diagram), captured with `_scripts/render.mjs`. No live app required. Regenerate a flow with `/doc-fronend <group>` after its spec changes.

- [brand](brand.md) — 走一遍品牌管理頁面：載入七個品牌並切換其中一個品牌的啟用/停用狀態
- [currency](currency.md) — 幣別管理頁面的新增幣種流程：載入清單、開啟新增表單、填寫並儲存、新增列成功顯示
- [currency-pair](currency-pair.md) — 幣別對管理頁面：載入定義清單、新增一組幣種對定義並確認新列出現
- [brand-currency-pair](brand-currency-pair.md) — 品牌幣種對頁面：切換匯率類型與刪除都改為送出審核申請，資料在核准前不會變動；每列顯示入金/出金加點完成
- [spread](spread.md) — 價差群組管理頁面：修改預設點差、把幣種對加入群組，同樣都是送審後才生效
- [audit](audit.md) — 審核紀錄頁面：檢視待審申請的變更前後內容，核准後立即套用，或填寫理由駁回
- [exchange-rate](exchange-rate.md) — 匯率同步頁面：按下按鈕向外部來源同步（成功後進入 60 秒冷卻倒數），並可依基準幣/報價幣即時搜尋篩選
