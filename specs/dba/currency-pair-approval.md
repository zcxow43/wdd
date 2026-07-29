---
status: skip
title: "Currency Pair as an Audit Consumer — No DBA Changes"
requirement: "Currency pair create/update/delete must go through the audit module for approval before applying"
---

# Currency Pair as an Audit Consumer — DBA Spec

## Overview
No DBA changes required. Currency pair's participation in the approval workflow is handled entirely by the standalone, entity-agnostic `audit_request` table defined in `specs/dba/audit.md` — currency pair does not need, and must not get, its own table, column, or FK for this. `currency_pair` itself (`specs/dba/currency-pair.md`) is unchanged by this feature.

This file previously (in an earlier, unimplemented iteration of this spec) defined a currency-pair-specific `currency_pair_change_request` table, later generalized in-place to `change_request`. That generic table has since been extracted into its own standalone spec, `specs/dba/audit.md`, which is now the single source of truth for the approval-workflow schema. This file is intentionally left as a `status: skip` marker so `/dev` does not attempt any DBA work for this feature area, and so a future reader knows why there's no DDL here despite the feature clearly needing a table somewhere (it's in `specs/dba/audit.md`, shared with every other approval-gated feature, present and future).

## Requirements
None — see Overview.

## Acceptance Criteria
- [x] No table, column, index, or constraint is added to any currency-pair-related table for this feature
