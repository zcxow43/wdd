---
status: skip
title: "Currency / Currency Pair / Brand Currency Pair Seed Data"
requirement: "db 幫我塞些資料, USD 對幾個常用幣種的, 幣種 幣種對 品牌幣種對都塞"
depends_on: []
---

# Currency Seed Data — Backend Spec

## Overview
No changes required. This requirement is a one-time data seed (more `currency` rows, `currency_pair_definition` rows, and brand-scoped `currency_pair` rows) with no schema change and no new/changed API. It's folded directly into the existing `## Migration SQL` sections of [currency.md](../dba/currency.md), [currency-pair-definition.md](../dba/currency-pair-definition.md), and [currency-pair.md](../dba/currency-pair.md) per `.claude/rules/dba.md`. The existing Currency / Currency Pair Definition / Currency Pair APIs already fully support reading and managing this data — nothing here needs a new endpoint, field, or validation rule.
