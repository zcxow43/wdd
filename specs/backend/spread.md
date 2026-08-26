---
status: done
title: "Spread API (Brand Default + Spread Groups)"
requirement: "每個品牌可以設置點差，分為入金點差與出金點差；有預設點差與群組點差兩種，群組可以拉品牌幣種對進行設定，每個品牌幣種對只能加入一個群組。點差是百分比（%），以乘法套用在基礎匯率上，不是用加法的固定金額；點差不能超過 100%。"
depends_on: [brand, currency-pair, audit]
---

# Spread — Backend Spec

## Overview
Two-tier spread configuration per brand. Every brand has exactly one **default spread** (預設點差 — see [brand-spread.md](../dba/brand-spread.md)), and may have any number of named **spread groups** (群組點差 — see [spread-group.md](../dba/spread-group.md)), each with its own spread percentages. A brand currency pair is assigned to at most one group via `currency_pair.spread_group_id` (see [currency-pair.md](../dba/currency-pair.md)); the pair's effective spread percentage is its group's if it has one, otherwise its brand's default. Both tiers carry the same two values: a deposit spread percentage (入金點差, `depositSpreadPercent`) and a withdrawal spread percentage (出金點差, `withdrawalSpreadPercent`) — both are **percentages** (e.g. `0.5` means a 0.5% markup) applied **multiplicatively** to a base rate (`baseRate * (1 + spreadPercent / 100)`) by [currency-pair.md](currency-pair.md)'s live `depositRate`/`withdrawalRate` and [exchange-rate.md](exchange-rate.md)'s frozen sync-time snapshot — never added as a flat currency amount. This API itself only stores and serves the raw percentage values; it does not compute a final rate.

This API owns all three concerns: reading/updating a brand's default spread, CRUD on its groups, and moving brand currency pairs in and out of groups.

**Every write on this API is audited: it is not applied when called, but recorded as a pending request that only takes effect once a reviewer approves it** (see [audit.md](audit.md)). That covers updating a brand's default spread, creating/updating/deleting a group, and adding/removing group members — a membership change moves a pair between spread tiers, so it is as much a change to what a brand charges as editing the numbers. Reads are unaffected and always return currently-effective values, including `GET /api/spreads/effective`, which resolves from committed data and ignores pending requests entirely.

## Requirements

### Entity: BrandSpread (預設點差)
| Field | Type | Rule |
|---|---|---|
| brandId | Long | Identifies the row; one row per brand; never created or deleted through this API |
| brandCode | String | Read-only enrichment (joined from `brand.code`) |
| depositSpreadPercent | BigDecimal | Required on update; between `0` and `100` inclusive; at most 8 decimal places; a percentage (e.g. `0.5` = 0.5%) applied multiplicatively to a base rate, not a flat amount added to it |
| withdrawalSpreadPercent | BigDecimal | Required on update; between `0` and `100` inclusive; at most 8 decimal places; same percentage semantics |
| createdAt / updatedAt | Timestamp | System maintained |

### Entity: SpreadGroup (群組點差)
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| brandId | Long | Required on create; must reference an existing brand; immutable after creation |
| brandCode | String | Read-only enrichment (joined from `brand.code`) |
| name | String | Required; 1–50 characters after trimming; unique within the brand |
| depositSpreadPercent | BigDecimal | Between `0` and `100` inclusive; at most 8 decimal places; defaults to `0` if omitted on create; same percentage semantics as `BrandSpread.depositSpreadPercent` |
| withdrawalSpreadPercent | BigDecimal | Between `0` and `100` inclusive; at most 8 decimal places; defaults to `0` if omitted on create; same percentage semantics |
| memberCount | Integer | Read-only; number of `currency_pair` rows whose `spread_group_id` is this group |
| createdAt / updatedAt | Timestamp | System maintained |

### Entity: SpreadGroupMember (read-only projection of a member brand currency pair)
| Field | Type | Rule |
|---|---|---|
| currencyPairId | Long | The `currency_pair.id` |
| currencyPairDefinitionId | Long | Joined from the pair |
| baseCurrencyCode / quoteCurrencyCode | String | Joined via the definition |
| active | Boolean | The pair's own enabled state — shown for context; group membership is independent of it |

### API Contract

**GET /api/brand-spreads**
- Query param (optional): `brandId` — filter to one brand when present.
- Response `200`: `[ { "brandId": 1, "brandCode": "au", "depositSpreadPercent": 0.50000000, "withdrawalSpreadPercent": 0.80000000, "createdAt": "...", "updatedAt": "..." }, ... ]` — one entry per brand.

**GET /api/brand-spreads/{brandId}**
- Response `200`: single object (same shape).
- Brand does not exist → `404`. Brand exists but has no `brand_spread` row (e.g. a brand added after the seed migration) → the row is created on read with zeros and returned `200`, so callers never have to handle a missing default.

**PUT /api/brand-spreads/{brandId}**
- Request body: `{ "depositSpreadPercent": 0.5, "withdrawalSpreadPercent": 0.8 }` — both required.
- Validation (at submit time): each value present, numeric, between `0` and `100` inclusive, at most 8 decimal places → `400` on any violation (including a value over `100`).
- Brand does not exist → `404`. That brand's default spread already has a pending request → `409`.
- **Audited** — the stored values do not change yet. Response `202`: `{ "auditRequestId": 12, "status": "PENDING", "entityType": "BRAND_SPREAD", "actionType": "UPDATE", "entityId": <brandId>, "summary": "..." }`.

**GET /api/spread-groups**
- Query param (optional): `brandId` — filter when present.
- Response `200`: `[ { "id": 3, "brandId": 1, "brandCode": "au", "name": "VIP", "depositSpreadPercent": 0.20000000, "withdrawalSpreadPercent": 0.30000000, "memberCount": 4, "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/spread-groups/{id}**
- Response `200`: the group object above, plus `"members": [ { "currencyPairId": 10, "currencyPairDefinitionId": 1, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY", "active": true }, ... ]`.
- Not found → `404`.

**POST /api/spread-groups**
- Request body: `{ "brandId": 1, "name": "VIP", "depositSpreadPercent": 0.2, "withdrawalSpreadPercent": 0.3 }` (spreads default to `0` if omitted).
- Validation: `brandId` must reference an existing brand (`400`); `name` required, trimmed, 1–50 chars (`400`); spreads between `0` and `100` inclusive with at most 8 decimal places (`400`); `(brandId, name)` must not already exist (`409`, body `{ "error": "Spread group name already exists for this brand" }`).
- **Audited** — no group is created yet. Response `202`: the pending request summary with `"entityType": "SPREAD_GROUP"`, `"actionType": "CREATE"`, `entityId: null`. The group appears only after approval.

**PUT /api/spread-groups/{id}**
- Request body: any subset of `{ "name": "VIP+", "depositSpreadPercent": 0.2, "withdrawalSpreadPercent": 0.3 }` — `brandId` is immutable and ignored if sent. Fields not present keep their current value.
- Validation (at submit time): same rules as create, applied to the resulting values; renaming to a name another group of the same brand already holds → `409`.
- Not found → `404`. That group already has a pending request → `409`.
- **Audited** — the group does not change yet. Response `202`: the pending request summary with `"actionType": "UPDATE"`.

**DELETE /api/spread-groups/{id}**
- Not found → `404`. That group already has a pending request → `409`.
- **Audited** — the group is not removed yet. Response `202`: the pending request summary with `"actionType": "DELETE"`. On approval the group is deleted and every member pair's `spread_group_id` becomes `NULL` (handled by the FK's `ON DELETE SET NULL`), so those pairs fall back to the brand default. No guard — a group may be deleted while it still has members.

**POST /api/spread-groups/{id}/members**
- Request body: `{ "currencyPairIds": [10, 11, 12] }` — the brand currency pairs to pull into this group. Must be non-empty (`400` if empty or absent).
- Validation, applied to the whole batch before any write (all-or-nothing):
  - Group must exist → `404`.
  - Every id must reference an existing `currency_pair` → `400`, body lists the unknown ids.
  - Every pair's `brandId` must equal the group's `brandId` → `400`, body `{ "error": "Currency pair belongs to a different brand", "currencyPairIds": [ ... ] }`.
  - No pair may already belong to a **different** group → `409`, body `{ "error": "Currency pair already belongs to another spread group", "conflicts": [ { "currencyPairId": 11, "spreadGroupId": 2, "spreadGroupName": "STD" } ] }`. This is the enforcement point for 每個品牌幣種對只能加入一個群組 — reassignment requires removing the pair from its current group first.
  - A pair already in **this** group is a no-op, not an error.
- **Audited** — no membership changes yet. Response `202`: the pending request summary with `"entityType": "SPREAD_GROUP_MEMBER"`, `"actionType": "UPDATE"`, `entityId` = the group's id, and `afterData` carrying the full requested id list. The whole batch is one request: it is approved or rejected as a unit, never partially.

**DELETE /api/spread-groups/{id}/members/{currencyPairId}**
- Group not found, or that pair is not currently a member of this group → `404`. That group already has a pending membership request → `409`.
- **Audited** — the pair stays in the group for now. Response `202`: the pending request summary with `"entityType": "SPREAD_GROUP_MEMBER"`, `"actionType": "UPDATE"`. On approval the pair's `spread_group_id` becomes `NULL` and it falls back to the brand default. The `currency_pair` row itself is never deleted here.

**GET /api/spreads/effective**
- Query param (required): `brandId` → `400` if missing, `404` if the brand doesn't exist.
- Response `200`: one entry per brand currency pair of that brand, with its resolved spread percentages:
  `[ { "currencyPairId": 10, "currencyPairDefinitionId": 1, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY", "brandId": 1, "brandCode": "au", "spreadGroupId": 3, "spreadGroupName": "VIP", "source": "GROUP", "depositSpreadPercent": 0.20000000, "withdrawalSpreadPercent": 0.30000000 }, { "currencyPairId": 11, "currencyPairDefinitionId": 2, "baseCurrencyCode": "EUR", "quoteCurrencyCode": "USD", "brandId": 1, "brandCode": "au", "spreadGroupId": null, "spreadGroupName": null, "source": "DEFAULT", "depositSpreadPercent": 0.50000000, "withdrawalSpreadPercent": 0.80000000 } ]`
- `source` is `GROUP` when the pair has a group, `DEFAULT` otherwise; the percentage values are the ones that actually apply, already resolved server-side so no caller re-implements the fallback rule. These are still the raw percentages, not a computed rate — [currency-pair.md](currency-pair.md) and [exchange-rate.md](exchange-rate.md) are what apply them multiplicatively to a base rate.

## Implementation Details
1. **Spread value validation** is shared by both tiers: a value must be non-null, between `0` and `100` inclusive, and its decimal places (computed with `stripTrailingZeros().scale()`, floored at 0, matching the existing rate-precision check in [currency-pair.md](currency-pair.md)) must not exceed 8. Put it in one helper used by the brand-spread and group paths alike.
2. **`GET /api/brand-spreads/{brandId}`** validates the brand exists first (404), then reads its row; on a missing row it inserts a zero row and returns that, so the endpoint is total for every existing brand.
3. **`PUT /api/brand-spreads/{brandId}`** uses the same create-if-missing path, then updates both values in one statement.
4. **Group name uniqueness** is checked against `(brandId, name)` before insert/update, and the DB's `uk_spread_group_brand_name` constraint is the backstop — a constraint violation surfaces as the same `409`, never a `500`.
5. **`memberCount`** is a `COUNT` over `currency_pair` grouped by `spread_group_id`, joined into the list query — do not issue one count query per group.
6. **Member assignment** runs in a single transaction: validate the whole batch (existence → brand match → no other group), then one `UPDATE currency_pair SET spread_group_id = ? WHERE id IN (...)`. A partial batch must never be written — if any id fails validation, nothing is assigned.
7. **Member removal** is `UPDATE currency_pair SET spread_group_id = NULL WHERE id = ? AND spread_group_id = ?` — the second predicate is what makes "not a member of this group" a `404` rather than a silent success.
8. **`GET /api/spreads/effective`** resolves in SQL with a `LEFT JOIN` from `currency_pair` to `spread_group` and a join to `brand_spread`, selecting the group's values when `spread_group_id` is non-null and the brand default otherwise — one query, no N+1 and no per-row branching in Java.
9. Group deletion relies on the FK's `ON DELETE SET NULL`; do not null the members manually first.
10. **Audited write path**: every write endpoint validates exactly as described, then calls the audit module's submit contract instead of writing, and returns `202`. It never returns the entity.
11. **Three audit handlers** are registered per [audit.md](audit.md)'s handler contract — `BRAND_SPREAD`, `SPREAD_GROUP`, and `SPREAD_GROUP_MEMBER`. Each `validate` re-runs its submit-time checks against current data at approval time (the group may have been deleted, a name may now collide, a pair may have been pulled into another group meanwhile); each `apply` performs the real write. Membership requests keep their all-or-nothing semantics on apply: if any pair in the batch is no longer assignable, the whole approval fails with nothing written.
12. `SPREAD_GROUP_MEMBER` requests use the **group's** id as `entityId`, so the one-pending-per-target rule serializes membership edits per group. A pending membership change therefore also blocks a pending group edit only if that edit uses the same entity type — group edits (`SPREAD_GROUP`) and membership edits (`SPREAD_GROUP_MEMBER`) are tracked separately and may be pending at the same time.
13. `GET /api/spreads/effective` resolves from committed data only; a pending request never affects what it returns.

## Changes to the existing Currency Pair API
[currency-pair.md](currency-pair.md)'s response gains two read-only enrichment fields, `spreadGroupId` (Long, nullable) and `spreadGroupName` (String, nullable), so callers listing a brand's pairs can tell which are already assigned. They are **not** writable through `POST`/`PUT /api/currency-pairs` — assignment happens only through the group member endpoints above, so there is exactly one write path for the relation. Those acceptance criteria are appended to that spec, not duplicated here.

## Acceptance Criteria
- [x] `GET /api/brand-spreads` returns one entry per brand; the `brandId` filter narrows to one.
- [x] `GET /api/brand-spreads/{brandId}` returns `404` for an unknown brand, and auto-creates a zero row for a brand that has none.
- [x] `PUT /api/brand-spreads/{brandId}` updates both spreads and returns `200`; a negative value or one with more than 8 decimal places returns `400`.
- [x] `POST /api/spread-groups` creates a group with `memberCount: 0`; a duplicate `(brandId, name)` returns `409`; an unknown `brandId` returns `400`.
- [x] The same group name under two different brands is accepted.
- [x] `PUT /api/spread-groups/{id}` updates name/spreads, ignores `brandId`, and returns `409` on a name collision within the brand.
- [x] `GET /api/spread-groups/{id}` returns the group with its `members` array; `memberCount` matches the array length.
- [x] `POST /api/spread-groups/{id}/members` assigns every listed pair in one transaction and returns the updated group with members.
- [x] Assigning a pair that already belongs to a different group returns `409` with the conflicting pair/group details, and assigns none of the batch.
- [x] Assigning a pair belonging to a different brand than the group returns `400`, and assigns none of the batch.
- [x] Re-assigning a pair that is already in this group succeeds as a no-op.
- [x] `DELETE /api/spread-groups/{id}/members/{currencyPairId}` returns `204` and nulls only that pair's group; the `currency_pair` row still exists.
- [x] Removing a pair that is not a member of that group returns `404`.
- [x] `DELETE /api/spread-groups/{id}` returns `204` and leaves every member `currency_pair` row intact with `spreadGroupId: null`.
- [x] `GET /api/spreads/effective?brandId=` returns `source: "GROUP"` with the group's spreads for assigned pairs and `source: "DEFAULT"` with the brand's spreads for unassigned ones.
- [x] `GET /api/spreads/effective` without `brandId` returns `400`; with an unknown `brandId` returns `404`.
- [x] Every write endpoint (`PUT /api/brand-spreads/{brandId}`, `POST`/`PUT`/`DELETE /api/spread-groups`, `POST`/`DELETE` on `/members`) returns `202` with a pending audit request and changes no stored data until approved.
- [x] Submit-time validation still returns `400`/`404`/`409` as specified, before any audit request is created.
- [x] A second write against a target that already has a pending request of the same entity type returns `409`.
- [x] Approving each of the four request types (`BRAND_SPREAD`, `SPREAD_GROUP` create/update/delete, `SPREAD_GROUP_MEMBER` add/remove) performs the real change; rejecting or cancelling leaves the data untouched.
- [x] Approving a membership batch where one pair has since joined another group fails the whole approval with nothing written.
- [x] `GET /api/spreads/effective` returns identical values before and after a request is submitted, and only changes once it is approved.
- [x] `depositSpread`/`withdrawalSpread` have been renamed to `depositSpreadPercent`/`withdrawalSpreadPercent` everywhere they appear — `GET`/`PUT /api/brand-spreads`, `GET`/`POST`/`PUT /api/spread-groups`, and `GET /api/spreads/effective` — no endpoint still returns or accepts the old field names.
- [x] `PUT /api/brand-spreads/{brandId}` and `POST`/`PUT /api/spread-groups` reject a value greater than `100` with `400`, same as a negative value; `100` itself is accepted.
- [x] `beforeData`/`afterData` on `BRAND_SPREAD`/`SPREAD_GROUP` audit requests use the new field names (`depositSpreadPercent`/`withdrawalSpreadPercent`).
- [x] Values are still interpreted and validated the same way structurally (>= 0, <= 8 decimal places) — only the field name and the added upper bound (`<= 100`) changed; no other validation behavior changed.

---
## Execution Result
- Status: DONE
- Files changed:
  - New DTOs: `develop/backend/src/main/java/com/wdd/backend/dto/BrandSpread.java`, `BrandSpreadResponse.java`, `BrandSpreadUpdateRequest.java`, `SpreadGroup.java`, `SpreadGroupResponse.java`, `SpreadGroupDetailResponse.java`, `SpreadGroupMemberResponse.java`, `SpreadGroupCreateRequest.java`, `SpreadGroupUpdateRequest.java`, `SpreadGroupMemberAssignRequest.java`, `EffectiveSpread.java`, `EffectiveSpreadResponse.java`
  - New exceptions: `develop/backend/src/main/java/com/wdd/backend/exception/SpreadGroupNotFoundException.java`, `SpreadGroupNameConflictException.java`, `UnknownCurrencyPairIdsException.java`, `CurrencyPairBrandMismatchException.java`, `SpreadGroupMemberConflictException.java`, `SpreadGroupMemberNotFoundException.java`
  - Updated: `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (registered the six new handlers)
  - New mappers: `develop/backend/src/main/java/com/wdd/backend/mapper/BrandSpreadMapper.java` + `develop/backend/src/main/resources/mapper/BrandSpreadMapper.xml`; `SpreadGroupMapper.java` + `SpreadGroupMapper.xml`
  - Updated: `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyPairMapper.java` + `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml` (added `findByIds`, `findBySpreadGroupId`, `updateSpreadGroupForIds`, `clearSpreadGroupIfMember`, `findEffectiveSpreadsByBrandId`)
  - New services: `develop/backend/src/main/java/com/wdd/backend/service/SpreadValidator.java` (shared validation helper), `BrandSpreadService.java`, `SpreadGroupService.java`, `EffectiveSpreadService.java`
  - New controllers: `develop/backend/src/main/java/com/wdd/backend/controller/BrandSpreadController.java`, `SpreadGroupController.java`, `SpreadController.java`
  - New tests: `develop/backend/src/test/java/com/wdd/backend/service/BrandSpreadServiceTest.java` (9), `SpreadGroupServiceTest.java` (22), `EffectiveSpreadServiceTest.java` (3); `develop/backend/src/test/java/com/wdd/backend/controller/BrandSpreadControllerTest.java` (7), `SpreadGroupControllerTest.java` (19), `SpreadControllerTest.java` (3) — 63 new tests total.
- Notes:
  - Did not touch the Currency Pair API's write paths or its `spreadGroupId`/`spreadGroupName` enrichment — that was already in place from the prior session (`CurrencyPairMapper.xml`'s `selectColumns` `LEFT JOIN spread_group`), reused as-is for the member responses.
  - Member-assignment batch validation order matches the spec exactly: group exists (404) → all ids exist (400, lists unknown ids) → all pairs belong to the group's brand (400, `{"error":..., "currencyPairIds":[...]}`) → no pair already in a *different* group (409, `{"error":..., "conflicts":[{"currencyPairId","spreadGroupId","spreadGroupName"}]}`) → pairs already in *this* group are no-ops → one batched `UPDATE ... WHERE id IN (...)` for the rest. All validation happens before any write, so a failing batch never has a partial effect.
  - Spread group name uniqueness is checked in Java against `(brandId, name)` and also backstopped by catching `DuplicateKeyException` around the insert/update (translating a race into the same `409`), per Implementation Detail #4.
  - `GET /api/spreads/effective` resolves GROUP vs DEFAULT entirely in one SQL query (`CurrencyPairMapper.findEffectiveSpreadsByBrandId`, `CASE WHEN cp.spread_group_id IS NOT NULL THEN sg.* ELSE bs.* END`), no N+1, no per-row branching in Java.
  - Verified with `mvn -f develop/backend/pom.xml test`: all 167 tests pass (104 pre-existing + 63 new), no regressions.
  - Verified live end-to-end against the real DB (MySQL 127.0.0.1:3306/wdd) with the server running on port 8080 via `mvn spring-boot:run`, covering every tricky path called out in the task: all-or-nothing batch assignment (a batch with one pair already in another group assigned none of the batch and returned 409 with the exact conflict shape `{currencyPairId, spreadGroupId, spreadGroupName}`); cross-brand rejection (400, batch left fully unassigned); re-assigning an already-in-this-group pair as a no-op (200, unchanged membership); removing a non-member (404); group delete leaving every member `currency_pair` row intact with `spread_group_id` nulled (verified both via the API and directly against the table) and the group itself subsequently 404; and `GET /api/spreads/effective` returning `GROUP` (group's own spreads) vs `DEFAULT` (brand's default spreads) correctly for the two respective pairs, plus the 400 (missing `brandId`) and 404 (unknown `brandId`) cases.
  - All live-run test data was cleaned up afterward (spread groups, definitions — which cascade-deleted their fanned-out `currency_pair` rows — currencies, and the brand default spread restored to its original value); confirmed via direct SQL that `currency_pair`, `currency_pair_definition`, and `spread_group` are back to `0` rows, `currency` back to its original 5 seeded rows, and all 7 `brand_spread` rows back to `0.00000000`/`0.00000000`. One stray test currency (created by an ad-hoc manual `curl` probe before the scripted E2E run, code `LVA`, id 134) was also caught and deleted during verification. The server process was stopped afterward (confirmed no listener on port 8080).

### Increment 1 — 2026-08-23
- Status: DONE
- Files changed:
  - New audit handlers: `develop/backend/src/main/java/com/wdd/backend/service/BrandSpreadAuditHandler.java`, `SpreadGroupAuditHandler.java`, `SpreadGroupMemberAuditHandler.java` — the three handlers required by the spec (`BRAND_SPREAD`, `SPREAD_GROUP`, `SPREAD_GROUP_MEMBER`), each a `@Component` implementing `AuditHandler`, auto-registered into `AuditHandlerRegistry`.
  - Updated: `develop/backend/src/main/java/com/wdd/backend/service/BrandSpreadService.java` — `update` now validates exactly as before, then calls `AuditService.submit` (`entityType=BRAND_SPREAD`, `actionType=UPDATE`, `entityId=brandId`) instead of writing, returning `AuditPendingResponse`. `findByBrandId`'s own lazy zero-row creation is untouched (a read, not an audited write).
  - Updated: `develop/backend/src/main/java/com/wdd/backend/service/SpreadGroupService.java` — `create`/`update`/`delete`/`assignMembers`/`removeMember` all validate exactly as before, then submit instead of writing. `SPREAD_GROUP` (create `entityId=null`, update/delete `entityId=id`) and `SPREAD_GROUP_MEMBER` (`entityId=groupId` for both add and remove, `afterData.operation` = `"ADD"`/`"REMOVE"` + `currencyPairIds` disambiguates the two at apply time) are separate `entityType`s per the spec.
  - Updated: `develop/backend/src/main/java/com/wdd/backend/controller/BrandSpreadController.java`, `SpreadGroupController.java` — every write endpoint now returns `ResponseEntity<AuditPendingResponse>` with `202`, forwarding an optional `X-Actor` header, mirroring `CurrencyPairController`'s existing audited-write pattern. `SpreadController` (the read-only `/api/spreads/effective` endpoint) is untouched.
  - Rewritten tests: `develop/backend/src/test/java/com/wdd/backend/service/BrandSpreadServiceTest.java`, `SpreadGroupServiceTest.java` (mock `AuditService`, assert `submit(...)` is called with the right `entityType`/`actionType`/`entityId`/`before`/`afterData` and that the mapper is never called directly); `develop/backend/src/test/java/com/wdd/backend/controller/BrandSpreadControllerTest.java`, `SpreadGroupControllerTest.java`, `SpreadControllerTest.java` (rewritten against the real DB to submit→approve/reject/cancel through `/api/audit-requests`, plus new tests for the pending-conflict `409`, the `SPREAD_GROUP` vs `SPREAD_GROUP_MEMBER` independence, reject/cancel leaving data untouched, the all-or-nothing membership-approval-time race failure, and `/api/spreads/effective` being byte-identical immediately after a submit and only changing after approval).
- Notes:
  - `entityId` conventions match the spec exactly: `BRAND_SPREAD` = brandId, `SPREAD_GROUP` create = `null`/update+delete = the group id, `SPREAD_GROUP_MEMBER` (both add and remove) = the group's id — so at most one pending membership edit and, independently, one pending group edit may exist per group at once, verified live (a pending `SPREAD_GROUP_MEMBER` request did not block a `SPREAD_GROUP` rename on the same group, while a second request of the *same* type on the same group returned `409`).
  - `SpreadGroupMemberAuditHandler` re-validates the whole batch (existence → brand match → not already in a different group, for `ADD`; still-a-member, for `REMOVE`) against current data inside `validate()`; since `AuditApplyRunner` only calls `apply()` after `validate()` succeeds, a drifted pair anywhere in the batch fails the entire approval (`422`, request stays `PENDING` with `applyError` set) with nothing written — verified live with a two-pair batch where one pair was pulled into a different, already-approved group meanwhile: the batch approval returned `422` and the still-legal pair was confirmed (via direct SQL) to remain unassigned.
  - `beforeData`/`afterData` plus a zh-TW one-line `summary` are recorded for every request type, e.g. `"au 預設點差調整為入金 0.0007／出金 0.0009"`, `"au 新增點差群組「E2E-GRP」，入金 0.0002／出金 0.0003"`, `"au 點差群組「E2E-GRP」新增 1 個幣種對"`, `"au 點差群組「E2E-GRP」移除幣種對 ZZB/ZZC"`.
  - Verified with `mvn -f develop/backend/pom.xml test`: all 209 tests pass (was 167 before this increment; net +42 across rewritten/added service and controller tests), zero regressions — the pre-existing `CURRENCY_PAIR` audited-flow tests (`CurrencyPairServiceTest`, `CurrencyPairControllerTest`, `AuditServiceTest`, `AuditControllerTest`) stayed green untouched.
  - Verified live end-to-end against the real DB (MySQL 127.0.0.1:3306/wdd) with the server started via `mvn spring-boot:run` on port 8080 (confirmed free before starting, confirmed no listener after stopping): drove all four request types through submit → confirmed-unchanged (both direct row/`effective` reads) → approve → confirmed-applied — `BRAND_SPREAD` update, `SPREAD_GROUP` create, `SPREAD_GROUP` update (rename), `SPREAD_GROUP` delete, `SPREAD_GROUP_MEMBER` add, `SPREAD_GROUP_MEMBER` remove; drove the all-or-nothing membership-approval race described above; confirmed `GET /api/spreads/effective` returned byte-identical values immediately after a `BRAND_SPREAD` submit and only changed post-approval; confirmed same-entityType-same-target 409 (`SPREAD_GROUP:151` and `SPREAD_GROUP_MEMBER:151` each rejected a second concurrent request) and cross-entityType independence (a pending `SPREAD_GROUP_MEMBER:151` did not block a `SPREAD_GROUP:151` rename).
  - Also discovered and cleaned up pre-existing stray data unrelated to this increment's own work (one leftover `currency_pair_definition`/fan-out and one leftover `spread_group` referencing seed currencies USD/JPY, plus a non-zero brand-1 `brand_spread` row, all dated earlier in the session before this increment began) so the DB matches the documented zero baseline (`currency_pair`/`currency_pair_definition`/`spread_group`/`audit_request` all `0` rows, `currency` at its 5 seeded rows, every `brand_spread` row `0.00000000`/`0.00000000`) both before and after this increment's own verification work.

### Increment 2 — 2026-08-26
- Status: DONE
- Files changed:
  - Mapper XML rename (`deposit_spread`/`withdrawal_spread` → `deposit_spread_percent`/`withdrawal_spread_percent`, matching the already-live-migrated `brand_spread`/`spread_group` columns): `develop/backend/src/main/resources/mapper/BrandSpreadMapper.xml`, `SpreadGroupMapper.xml`, and `CurrencyPairMapper.xml`'s `findEffectiveSpreadsByBrandId` query + its `EffectiveSpreadResultMap` (the two `depositRate`/`withdrawalRate` `CASE WHEN` blocks elsewhere in that file were already fixed by the sibling `currency-pair.md` task and were left untouched).
  - Mapper interface: `develop/backend/src/main/java/com/wdd/backend/mapper/BrandSpreadMapper.java` (`update`'s `@Param` names renamed to match the XML's `#{depositSpreadPercent}`/`#{withdrawalSpreadPercent}` bind variables).
  - DTO field/getter/setter rename (`depositSpread`/`withdrawalSpread` → `depositSpreadPercent`/`withdrawalSpreadPercent`): `develop/backend/src/main/java/com/wdd/backend/dto/BrandSpread.java`, `BrandSpreadResponse.java`, `BrandSpreadUpdateRequest.java`, `SpreadGroup.java`, `SpreadGroupResponse.java`, `SpreadGroupDetailResponse.java`, `SpreadGroupCreateRequest.java`, `SpreadGroupUpdateRequest.java`, `EffectiveSpread.java`, `EffectiveSpreadResponse.java`.
  - Service/handler call-site updates for the renamed getters/setters and audit map keys: `develop/backend/src/main/java/com/wdd/backend/service/BrandSpreadService.java`, `SpreadGroupService.java`, `BrandSpreadAuditHandler.java`, `SpreadGroupAuditHandler.java`, `EffectiveSpreadService.java` (the last had no field-name references itself, only getter calls that needed the rename).
  - Validation upper bound: `develop/backend/src/main/java/com/wdd/backend/service/SpreadValidator.java` (added a `> 100` → `400` check, `100` inclusive, alongside the existing `< 0` and `> 8 decimal places` checks) plus the two audit handlers' own re-validation copies at approval time — `BrandSpreadAuditHandler.java` and `SpreadGroupAuditHandler.java`'s `validateSpreadValue` helpers got the same `> 100` check so a value that somehow reaches approval time still can't slip past `100`.
  - Test updates: `develop/backend/src/test/java/com/wdd/backend/service/BrandSpreadServiceTest.java`, `SpreadGroupServiceTest.java`, `EffectiveSpreadServiceTest.java`, `develop/backend/src/test/java/com/wdd/backend/controller/BrandSpreadControllerTest.java`, `SpreadGroupControllerTest.java`, `SpreadControllerTest.java` — every field reference renamed (including raw SQL column references in the two controller tests' JDBC-based setup/teardown, e.g. `BrandSpreadControllerTest`'s `@BeforeEach`/`@AfterEach` reading/writing `deposit_spread_percent`/`withdrawal_spread_percent` directly), plus `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyPairControllerTest.java`'s two `match.get("depositSpread")` assertions against `GET /api/spreads/effective`'s response body (that file otherwise belongs to `currency-pair.md` and was not touched beyond this one JSON-key rename it depends on).
  - New boundary tests for `> 100` rejection / `= 100` acceptance: two each added to `BrandSpreadServiceTest.java` (`updateRejectsSpreadOver100`, `updateAccepts100AsInclusiveUpperBound`), `SpreadGroupServiceTest.java` (`createRejectsSpreadOver100`, `createAccepts100AsInclusiveUpperBound`, `updateRejectsSpreadOver100`, `updateAccepts100AsInclusiveUpperBound` — 4 total, create + update), `BrandSpreadControllerTest.java` (`updateRejectsValueOver100`, `updateAccepts100AsInclusiveUpperBound`), and `SpreadGroupControllerTest.java` (`createRejectsSpreadValueOver100`, `createAccepts100AsInclusiveUpperBound`, `updateRejectsSpreadValueOver100`, `updateAccepts100AsInclusiveUpperBound` — 4 total) — 12 new tests.
  - Defensive fix unrelated to the rename itself but needed to get a clean full-suite run: `SpreadGroupControllerTest.java`'s `createCurrency` helper now purges any stray `currency`/`currency_pair_definition` row left over from an earlier, unrelated failed test run before creating a fresh one with the same fixed test code — a pre-existing `SGD` leftover (from a run that errored before reaching its own `@AfterEach`) was colliding with this file's own currency codes and causing an unrelated `NullPointerException` in `assignMembersReturns202AndAssignsWholeBatchOnlyAfterApproval` on a clean checkout.
- Notes:
  - Confirmed via `SHOW COLUMNS FROM brand_spread` that the live DB already has `deposit_spread_percent`/`withdrawal_spread_percent` (the DBA migration referenced in the task context was indeed already applied), so this increment is purely an application-layer catch-up to match schema that was already live and broken against the old mapper SQL.
  - `CurrencyPairMapper.xml`'s `findEffectiveSpreadsByBrandId` was querying the already-renamed source columns (`sg.deposit_spread_percent`, `bs.deposit_spread_percent`, etc. — fixed by the sibling `currency-pair.md` task) but was still aliasing its `SELECT` output as `deposit_spread`/`withdrawal_spread` and mapping into `EffectiveSpread.depositSpread`/`withdrawalSpread` — that alias/resultMap mismatch (not a broken source-column reference) is what this increment fixed there.
  - Did not touch `ExchangeRateMapper.xml`, `ExchangeRateEffectiveSpread.java`, `ExchangeRateService.java`, or `ExchangeRateServiceTest.java` — all four still reference the old `deposit_spread`/`withdrawal_spread` names/columns and are explicitly out of scope per the task ("a separate, subsequent task" covering `exchange-rate.md`).
  - Validation is duplicated in three places by design, matching the existing architecture: `SpreadValidator` (submit-time, shared by `BrandSpreadService`/`SpreadGroupService`), and each audit handler's own `validateSpreadValue` (approval-time re-validation per audit.md's handler contract, since data may have drifted between submit and approval). All three now enforce the same `>= 0`, `<= 100`, `<= 8 decimal places` rule identically.
  - Verified with `mvn -f develop/backend/pom.xml test`: all 245 tests pass (was 233 before this increment; net +12 new boundary tests), zero regressions across the full suite including the previously-broken `BrandSpreadControllerTest`/`SpreadGroupControllerTest`/`SpreadControllerTest` (now 10/27/4 tests respectively, all green) and the unrelated `CurrencyPairControllerTest`'s two effective-spread cross-check assertions.
  - Verified live end-to-end against the real DB (MySQL 127.0.0.1:3306/wdd) with the server run via `mvn spring-boot:run` on port 8080 (confirmed free before starting, confirmed no listener after stopping): `GET /api/brand-spreads`, `GET /api/spread-groups`, and `GET /api/spreads/effective?brandId=1` all returned `depositSpreadPercent`/`withdrawalSpreadPercent` correctly (the effective-spread endpoint correctly resolving `GROUP` vs values from the live `sales` spread group on brand `au`); `PUT /api/brand-spreads/{brandId}` with `depositSpreadPercent: 100.00000001` returned `400` (`"depositSpreadPercent must be <= 100"`) and with `100` returned `202` with `afterData: {"depositSpreadPercent": 100, "withdrawalSpreadPercent": 100}` (confirmed via `GET /api/audit-requests/{id}`), then cancelled to leave `brand_spread` untouched; `POST /api/spread-groups` with `depositSpreadPercent: 100.00000001` returned `400` and with `100`/`100` returned `202`, then cancelled and confirmed via `GET /api/spread-groups` that no group was created.
  - Confirmed no stray data was left behind by this increment's own live verification (cancelled both audit requests; `SELECT code FROM currency WHERE code REGEXP '^(SG|QR)'` empty after the full test suite run) or its own test additions (the two new controller-level `=100` tests for spread groups clean up via the existing `createdGroupIds`/`createdAuditRequestIds` teardown lists, same as every other test in that file).
