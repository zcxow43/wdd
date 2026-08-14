# Docs

Generated documentation derived from `specs/`. Regenerate with `/doc` after backend/DB specs change — these files are derived output, not hand-maintained. Diagrams are rendered as actual images (PNG), drafted by the `solution-architect` agent (`.claude/agents/sa.md`) and rendered via Mermaid CLI — big picture and flow, not exhaustive detail.

Frontend documentation lives separately at [docs/frontend/README.md](frontend/README.md) — real-looking screen storyboards, maintained by `/doc-fronend`, not this command.

## Backend (data flow)
- [currency-pair](backend/currency-pair.md) — Currency / Currency Pair Definition (fan-out) / Currency Pair / Currency Pair Approval, one DFD
- [brand](backend/brand.md) — Brand query + enable/disable toggle
- [spread](backend/spread.md) — Default spread + custom spread groups, mutations gated by audit approval
- [audit](backend/audit.md) — Generic, entity-agnostic approval workflow (submit / approve / reject)

## Backend API 總覽
- [api-overview](backend/api-overview.md) — 每個後端 API 的大方向說明 + 完整 URL 清單，無圖表，`/doc-backend` 產出

## DB (ER models)
- [currency-pair](db/currency-pair.md) — `currency`, `currency_pair`, `currency_pair_definition` (FK-connected cluster; `brand` shown as a referenced entity)
- [brand](db/brand.md) — `brand`, `spread_default`, `spread_group`, `spread_group_member` (FK-connected cluster; `currency_pair` shown as a referenced entity)

`audit_request` has no dedicated DB doc: its own spec (`specs/dba/audit.md`) requires it stay FK-isolated from every consumer, so there's no relationship graph to diagram — see `specs/dba/audit.md` directly.

## ER Model (full schema)
- [er-model](db/er-model.md) — panorama of the whole schema plus one detail diagram per major function (currency-pair / brand-spread / audit), cross-cluster FKs shown via context entities

## Blueprints (integrated architecture specs)
- [currency-pair-spread](blueprint/currency-pair-spread.md) — stacks brand, currency, currency-pair-definition, currency-pair, currency-pair-approval, spread, audit into one system-level spec (field tables, constraints, mutation/audit matrix, cross-spec rules, E2E scenarios). Regenerate with `/doc-blue-print` — richer than `/doc`'s diagram-only output, by design.

## Not documented separately
- `nav-menu` (frontend) — reorders/translates the existing sidebar; doesn't introduce a distinct screen or flow of its own
- `frontend-demo-restyle` (frontend) — visual restyle to match `demo/`; no new screens or flows
