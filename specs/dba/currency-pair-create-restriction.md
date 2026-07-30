---
status: skip
title: "Brand Currency Pair Requires a Global Definition First"
requirement: "要補全域幣種對, 必須先有全域, 品牌幣種對才會有, 所以品牌幣種對不需新增按鈕, 全域增加, 全部品牌就增加"
---

# Brand Currency Pair Requires a Global Definition First — DBA Spec

## Overview
No changes required. This requirement removes the ability to create a brand-scoped `currency_pair` row directly (`POST /api/currency-pairs` and its "+ Add" button — see `specs/backend/currency-pair.md`/`specs/backend/currency-pair-approval.md` and `specs/frontend/currency-pair.md`/`specs/frontend/currency-pair-approval.md`), leaving the already-built `currency_pair_definition` fan-out (`specs/dba/currency-pair-definition.md`, `specs/backend/currency-pair-definition.md`) as the sole path by which a `currency_pair` row comes into existence. That fan-out mechanism, and both tables it touches, are already fully built and unchanged by this requirement — there is no new table, column, index, or constraint to add.

## Requirements
No changes required.

## Implementation Details
No changes required.

## Acceptance Criteria
- [x] No DBA work required for this requirement
