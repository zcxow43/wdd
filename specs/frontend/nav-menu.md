---
status: pending
title: "Sidebar Nav Menu — Reorder and Full Chinese Translation"
requirement: "前端根據這個順序調整, 翻譯成中文 — reorder the sidebar nav to: 品牌管理(Brand), 幣種管理(Currency), 幣種對主檔(Currency Pair Definition), 品牌幣種對(Currency Pair List), 點差管理(Spread), 審核作業(Audit); translate the remaining English nav labels/page titles to Traditional Chinese"
depends_on: [brand, currency, currency-pair, currency-pair-definition, spread, audit]
---

# Sidebar Nav Menu — Reorder and Full Chinese Translation — Frontend Spec

## Overview
The sidebar nav (`develop/frontend/src/layout/AppShell.tsx`'s `NAV_ITEMS`) was built up incrementally, one entry per feature spec, so its order reflects build order rather than a deliberate UX order, and three of its six labels are still in English while the other three are already Chinese. This spec makes `NAV_ITEMS` — and each page's own `<h1>`, which should read the same as its nav label — the definitive source of truth for the nav's order and (fully Chinese) labels going forward. No new page, route, or business logic is added; this is purely reordering + i18n text.

## Requirements
Final nav order and labels (top to bottom), translating the three still-English labels to Traditional Chinese and reusing the three already-Chinese ones as-is:

| Position | Route                          | Current label            | Final label  |
|----------|----------------------------------|---------------------------|---------------|
| 1        | `/brands`                        | `Brand Management` (English) | `品牌管理`     |
| 2        | `/currencies`                    | `Currency Management` (English) | `幣種管理`     |
| 3        | `/currency-pair-definitions`     | `幣種對主檔` (already Chinese) | `幣種對主檔` (unchanged) |
| 4        | `/currency-pairs`                | `Currency Pair List` (English) | `品牌幣種對`   |
| 5        | `/spread-groups`                 | `點差管理` (already Chinese) | `點差管理` (unchanged) |
| 6        | `/audit-requests`                | `審核作業` (already Chinese) | `審核作業` (unchanged) |

- Each page's own `<h1>` must read identically to its nav label, so the sidebar and the page header never disagree (`CurrencyPairPage.tsx`'s `<h1>` currently says "Currency Pair Management", not even matching its own nav label "Currency Pair List" today — this spec fixes that mismatch too, landing both at `品牌幣種對`).
- No change to any route path, page component, business logic, API call, or the underlying feature specs (`specs/frontend/brand.md`, `currency.md`, `currency-pair.md`, `currency-pair-definition.md`, `spread.md`, `audit.md`) — those files' own Overview/Requirements/API sections are unaffected; only the shared `NAV_ITEMS` array and three `<h1>` strings change.

## Implementation Details

### `develop/frontend/src/layout/AppShell.tsx`
Replace the `NAV_ITEMS` array with, in this exact order:
```ts
const NAV_ITEMS: NavItem[] = [
  { to: '/brands', label: '品牌管理' },
  { to: '/currencies', label: '幣種管理' },
  { to: '/currency-pair-definitions', label: '幣種對主檔' },
  { to: '/currency-pairs', label: '品牌幣種對' },
  { to: '/spread-groups', label: '點差管理' },
  { to: '/audit-requests', label: '審核作業' },
]
```

### Page `<h1>` updates (label text only — no layout/structure change)
- `develop/frontend/src/pages/BrandPage.tsx`: `<h1>Brand Management</h1>` → `<h1>品牌管理</h1>`
- `develop/frontend/src/pages/CurrencyPage.tsx`: `<h1>Currency Management</h1>` → `<h1>幣種管理</h1>`
- `develop/frontend/src/pages/CurrencyPairPage.tsx`: `<h1>Currency Pair Management</h1>` → `<h1>品牌幣種對</h1>`
- `CurrencyPairDefinitionPage.tsx` (`幣種對主檔`), `SpreadPage.tsx` (`點差管理`), `AuditPage.tsx` (`審核作業`): already correct, no change.

### Out of scope
- No change to any test asserting route navigation by `to` path (unaffected). Tests asserting the old English `<h1>` text or nav label text (e.g. any `getByText('Brand Management')`/`getByText('Currency Pair List')`-style assertions in `AppShell`/page test files) must be updated to the new Chinese text so the suite doesn't regress — check `BrandPage.test.tsx`, `CurrencyPage.test.tsx`, `CurrencyPairPage.test.tsx`, and any `AppShell`-level test for these strings.

## Acceptance Criteria
- [ ] Sidebar renders the six nav items in the exact order: 品牌管理, 幣種管理, 幣種對主檔, 品牌幣種對, 點差管理, 審核作業
- [ ] All six nav labels are Traditional Chinese — no English label remains
- [ ] Each page's `<h1>` matches its nav label exactly (品牌管理/幣種管理/幣種對主檔/品牌幣種對/點差管理/審核作業)
- [ ] No route path, page component, or API integration changed — verified by the full existing frontend test suite still passing (aside from any test updated per "Out of scope" above to expect the new Chinese text)
- [ ] `npm run build`/`npm test`/`npm run lint` all pass with no new warnings

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/frontend/src/layout/AppShell.tsx` — reordered `NAV_ITEMS` to 品牌管理 → 幣種管理 → 幣種對主檔 → 品牌幣種對 → 點差管理 → 審核作業, translating the three English labels
  - `develop/frontend/src/pages/BrandPage.tsx` — `<h1>Brand Management</h1>` → `<h1>品牌管理</h1>`
  - `develop/frontend/src/pages/CurrencyPage.tsx` — `<h1>Currency Management</h1>` → `<h1>幣種管理</h1>`
  - `develop/frontend/src/pages/CurrencyPairPage.tsx` — `<h1>Currency Pair Management</h1>` → `<h1>品牌幣種對</h1>`
- Notes:
  - No route paths, page components, business logic, or API calls were touched — only the `NAV_ITEMS` array and three `<h1>` strings, per spec.
  - `CurrencyPairDefinitionPage.tsx`, `SpreadPage.tsx`, `AuditPage.tsx` already had the correct Chinese `<h1>` and required no change.
  - Searched all page/AppShell test files for assertions on the old English strings (`Brand Management`, `Currency Management`, `Currency Pair List`, `Currency Pair Management`) and on nav labels — none exist (no `AppShell.test.tsx` file present, and no page test asserts `<h1>` text), so no test files needed updating.
  - Verification: `npm run build` succeeded; `npm test -- --run` — 23 test files / 170 tests passed; `npm run lint` (oxlint) — only one pre-existing, unrelated warning in `ToastProvider.tsx` (file not touched by this change), no new warnings introduced.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.
