---
status: skip
title: "Currency Pair Deposit/Withdrawal Rate (加點完成)"
requirement: "匯率同步要有加點完成的欄位, 出入金都要"
depends_on: []
---

# Currency Pair Deposit/Withdrawal Rate — DBA Spec

## Overview
No schema changes required. `入金加點完成`/`出金加點完成` (deposit/withdrawal rate) are computed, read-only values — a currency pair's base rate (its own `rate` when `MANUAL`, or the latest `exchange_rate.rate` for its definition when `AUTO`) with its already-existing effective `deposit_spread`/`withdrawal_spread` (from `brand_spread` or `spread_group`, whichever currently applies) added on top. Every table this reads — `currency_pair`, `currency_pair_definition`, `exchange_rate`, `brand_spread`, `spread_group` — already exists; nothing new is stored. See [currency-pair.md](../backend/currency-pair.md) for where this is actually computed (in the `GET /api/currency-pairs` read query).
