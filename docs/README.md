# Docs

Generated documentation derived from `specs/`. Regenerate with `/doc` after specs change — these files are derived output, not hand-maintained. Diagrams are rendered as actual images (PNG), drafted by the `solution-architect` agent (`.claude/agents/sa.md`) and rendered via Mermaid CLI — big picture and flow, not exhaustive detail.

## Frontend (storyboards)
- [currency-pair](frontend/currency-pair.md) — Currency / Currency Pair / Currency Pair Definition screens, plus the hand-off into Audit review
- [brand](frontend/brand.md) — Single Brand Management screen, toggle-in-place
- [spread](frontend/spread.md) — Spread management screen, its modals, and the hand-off into Audit review
- [audit](frontend/audit.md) — Audit review screen and its approve/reject modal

## Backend (data flow)
- [currency-pair](backend/currency-pair.md) — Currency / Currency Pair Definition (fan-out) / Currency Pair / Currency Pair Approval, one DFD
- [brand](backend/brand.md) — Brand query + enable/disable toggle
- [spread](backend/spread.md) — Default spread + custom spread groups, mutations gated by audit approval
- [audit](backend/audit.md) — Generic, entity-agnostic approval workflow (submit / approve / reject)

## DB (ER models)
- [currency-pair](db/currency-pair.md) — `currency`, `currency_pair`, `currency_pair_definition` (FK-connected cluster; `brand` shown as a referenced entity)
- [brand](db/brand.md) — `brand`, `spread_default`, `spread_group`, `spread_group_member` (FK-connected cluster; `currency_pair` shown as a referenced entity)

`audit_request` has no dedicated DB doc: its own spec (`specs/dba/audit.md`) requires it stay FK-isolated from every consumer, so there's no relationship graph to diagram — see `specs/dba/audit.md` directly.

## Blueprints (integrated architecture specs)
- [backend](blueprint/backend.md) — stacks brand, currency, currency-pair-definition, currency-pair, currency-pair-approval, spread, audit into one system-level spec (field tables, constraints, mutation/audit matrix, cross-spec rules, E2E scenarios). Regenerate with `/blueprint` — richer than `/doc`'s diagram-only output, by design.

## Not documented separately
- `nav-menu` (frontend) — reorders/translates the existing sidebar; doesn't introduce a distinct screen or flow of its own
- `frontend-demo-restyle` (frontend) — visual restyle to match `demo/`; no new screens or flows
