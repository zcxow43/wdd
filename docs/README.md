# Docs Index

Generated documentation derived from `specs/`. Do not hand-edit content sections — regenerate via `/doc-backend`, `/doc-blue-print`, or `/doc-db`.

## Backend API 詳細定義
- [brand](backend/brand.md) — Brand API：欄位定義、限制條件、跨主題規則、完整 API 清單，`/doc-backend` 產出

## Blueprints (integrated architecture specs)
- [brand](blueprint/brand.md) — brand. Big-picture/diagram-led — see the linked `docs/backend/brand.md` file for field/constraint/API detail.

## ER Model (full schema)
- [er-model](db/er-model.md) — panorama of the whole schema plus one detail diagram per major function (brand — currently the only table, FK-isolated), no cross-cluster FKs yet
