---
status: skip
title: "Currency Pair Definition Delete Guard — All Brand Pairs Must Be Inactive"
requirement: "全域幣種對, 需要確認全部品牌幣種對都關閉, 才可刪除"
---

# Currency Pair Definition Delete Guard — DBA Spec

## Overview
No changes required. This requirement adds an application-level check to `DELETE /api/currency-pair-definitions/{id}` (`specs/backend/currency-pair-definition.md`): before deleting, the backend queries the existing `currency_pair.active` column (already present, `specs/dba/currency-pair.md`) across all brands for that (base, quote) direction. No new column, table, index, or constraint is needed — `active` already exists and already means exactly what this check needs.

## Requirements
No changes required.

## Implementation Details
No changes required.

## Acceptance Criteria
- [x] No DBA work required for this requirement — `currency_pair.active` already exists and is sufficient
