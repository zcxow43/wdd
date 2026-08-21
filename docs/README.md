# Docs Index

Generated documentation derived from `specs/`. Do not hand-edit content sections — regenerate via `/doc-backend`, `/doc-blue-print`, or `/doc-db`.

## Backend API 詳細定義
- [brand](backend/brand.md) — Brand API：欄位定義、限制條件、跨主題規則、完整 API 清單，`/doc-backend` 產出
- [currency](backend/currency.md) — Currency API：欄位定義、限制條件、跨主題規則、完整 API 清單，`/doc-backend` 產出
- [currency-pair-definition](backend/currency-pair-definition.md) — Currency Pair Definition API：欄位定義、限制條件、跨主題規則、完整 API 清單，`/doc-backend` 產出
- [currency-pair](backend/currency-pair.md) — Currency Pair API (Brand-Scoped)：欄位定義、限制條件、跨主題規則、完整 API 清單，`/doc-backend` 產出

## Blueprints (integrated architecture specs)
- [backend](blueprint/backend.md) — brand, currency, currency-pair-definition, currency-pair. Big-picture/diagram-led — see the linked `docs/backend/<slug>.md` files for field/constraint/API detail.

## ER Model (full schema)
- [er-model](db/er-model.md) — panorama of the whole schema plus one detail diagram per major function (currency / brand / currency-pair), with currency-pair sitting at the FK intersection of the other two (shown via context entities in its detail diagram)
