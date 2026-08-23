---
status: done
title: "Currency Pair API (Brand-Scoped)"
requirement: "品牌幣種對可以 CRUD，設定自動匯率或手動匯率，可以開啟關閉，手動匯率必須填上匯率，精度受幣種對限制"
depends_on: [currency-pair-definition, brand, audit]
---

# Currency Pair — Backend Spec

## Overview
CRUD for each brand's own currency pair settings (see [currency-pair.md](../dba/currency-pair.md)). Most rows are created by [currency-pair-definition.md](currency-pair-definition.md)'s fan-out when a definition is created, but this API also supports creating/deleting individual rows directly (e.g. to recreate one that was deleted). A pair's rate is either `AUTO` (system-derived elsewhere, stored as `null` here) or `MANUAL` (an admin-entered value, required and validated against the parent definition's `precision`).

**Every create, update, and delete on this API is audited: it is not applied when called, but recorded as a pending request that only takes effect once a reviewer approves it** (see [audit.md](audit.md)). Reads are unaffected and always return currently-effective data. The one exception is system-generated writes — the fan-out that creates a row per brand when a definition is created, and the cascade that removes rows when a definition is deleted — which apply directly, because they are consequences of an action on the definition (itself not audited), not user actions on a brand currency pair. Auditing them would make creating a definition impossible to complete.

## Requirements

### Entity: CurrencyPair
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| currencyPairDefinitionId | Long | Required on create; must reference an existing definition; immutable after creation |
| baseCurrencyCode / quoteCurrencyCode | String | Read-only enrichment (joined via the definition) |
| brandId | Long | Required on create; must reference an existing brand; immutable after creation |
| brandCode | String | Read-only enrichment (joined from `brand.code`) |
| rateType | String | `AUTO` or `MANUAL`; defaults to `AUTO` if omitted |
| rate | BigDecimal | Required (and must be `> 0`) when `rateType` is `MANUAL`; forced to `null` when `rateType` is `AUTO` regardless of what's sent; when `MANUAL`, decimal places must not exceed the parent definition's `precision` |
| active | Boolean | Defaults to `false`; freely togglable |
| spreadGroupId | Long | Read-only enrichment (`currency_pair.spread_group_id`), `null` when the pair uses its brand default spread. Not writable here — assignment happens only through the spread group member endpoints in [spread.md](spread.md); ignored if sent on `POST`/`PUT` |
| spreadGroupName | String | Read-only enrichment (joined from `spread_group.name`), `null` when unassigned |
| createdAt / updatedAt | Timestamp | System maintained |

### API Contract

**GET /api/currency-pairs**
- Query params (all optional): `currencyPairDefinitionId`, `brandId`, `active` (boolean) — filter when present.
- Response `200`: `[ { "id": 10, "currencyPairDefinitionId": 1, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY", "brandId": 1, "brandCode": "au", "rateType": "AUTO", "rate": null, "active": false, "spreadGroupId": 3, "spreadGroupName": "VIP", "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/currency-pairs/{id}**
- Response `200`: single object (same shape). Not found → `404`.

**POST /api/currency-pairs**
- Request body: `{ "currencyPairDefinitionId": 1, "brandId": 1, "rateType": "MANUAL", "rate": 150.25, "active": false }` (`rateType` defaults `"AUTO"`, `active` defaults `false` if omitted).
- Validation (at submit time): `currencyPairDefinitionId` must reference an existing definition, `brandId` must reference an existing brand (`400` if either doesn't exist); the `(currencyPairDefinitionId, brandId)` combination must not already exist (`409` if it does); `rateType` must be `AUTO`/`MANUAL`; if `MANUAL`, `rate` is required, `> 0`, and its decimal places must not exceed the definition's `precision` (`400` on any violation). If `AUTO`, any `rate` sent is ignored and stored as `null`.
- **Audited** — nothing is created yet. Response `202`: `{ "auditRequestId": 12, "status": "PENDING", "entityType": "CURRENCY_PAIR", "actionType": "CREATE", "entityId": null, "summary": "..." }`. The row appears only after the request is approved.

**PUT /api/currency-pairs/{id}**
- Request body: any subset of `{ "rateType": "MANUAL", "rate": 150.25, "active": true }` — `currencyPairDefinitionId`/`brandId` are immutable and ignored if sent. Fields not present keep their current value.
- Validation (at submit time): same rate/precision rules as create, applied to the resulting `rateType`+`rate` combination — `400` on violation.
- Not found → `404`. A pair that already has a pending request → `409` (one pending change per row).
- **Audited** — the row is not changed yet. Response `202`: the pending request summary, same shape as `POST`'s with `"actionType": "UPDATE"` and `entityId` set.

**DELETE /api/currency-pairs/{id}**
- Not found → `404`. A pair that already has a pending request → `409`.
- **Audited** — the row is not removed yet. Response `202`: the pending request summary with `"actionType": "DELETE"`. On approval the row is removed. No guard — a brand currency pair can be deleted regardless of its `active` state (the guard lives on the parent definition's delete, not here).

## Implementation Details
1. `GET` endpoints read the live table directly, joining `brand` and (via the definition) `currency` for the enrichment fields, applying the optional filters at the query level.
2. `POST`: validate `currencyPairDefinitionId`/`brandId` exist (400) → check `(definition, brand)` uniqueness (409) → validate `rateType`/`rate` against the definition's `precision` (400) → insert → `201`.
3. `PUT`: load existing row (404 if missing) → merge the request's fields onto the current values → validate the resulting `rateType`/`rate` combination against the parent definition's `precision` (400) → update → `200`.
4. `DELETE`: load existing row (404 if missing) → delete → `204`.
5. Rate/precision validation reads the parent `currency_pair_definition.precision` (via `currencyPairDefinitionId`, unchanged across the row's lifetime) — reuse the definition lookup already required for validation, don't duplicate the query.
6. `spreadGroupId`/`spreadGroupName` come from the same read query via a `LEFT JOIN` to `spread_group` — no extra round trip, and both are dropped from any write path so a client cannot reassign a group through this API.
7. **Audited write path**: `POST`/`PUT`/`DELETE` validate exactly as described, then call the audit module's submit contract instead of writing — passing `entityType: "CURRENCY_PAIR"`, the action, the target id, the pair's `brandId`, a Traditional-Chinese one-line `summary` (e.g. `au USD/JPY 改為手動匯率 150.25`), the current row as `beforeData` (null for create), and the merged requested values as `afterData` (null for delete). They return `202`, never the entity.
8. **`CURRENCY_PAIR` audit handler**: registered with the audit module per [audit.md](audit.md)'s handler contract. `validate` re-runs the same existence/uniqueness/rate-precision checks against current data (the definition's `precision` may have changed, the row may have been deleted, the `(definition, brand)` slot may have been taken). `apply` performs the real insert/update/delete. This handler is the only place the audit module learns anything about currency pairs.
9. The fan-out on definition create and the cascade on definition delete bypass the audit path entirely and write directly — they are not user actions on this entity.

## Acceptance Criteria
- [x] `GET /api/currency-pairs` returns all pairs; `currencyPairDefinitionId`/`brandId`/`active` filters narrow the results correctly.
- [x] `GET /api/currency-pairs/{id}` returns `404` for a non-existent id.
- [x] `POST` with `rateType: "AUTO"` creates a row with `rate: null` even if a `rate` value was sent in the body.
- [x] `POST` with `rateType: "MANUAL"` and no `rate` (or `rate <= 0`) returns `400`.
- [x] `POST` with `rateType: "MANUAL"` and a `rate` with more decimal places than the definition's `precision` returns `400`.
- [x] `POST` with a duplicate `(currencyPairDefinitionId, brandId)` returns `409`.
- [x] `POST` with a non-existent `currencyPairDefinitionId` or `brandId` returns `400`.
- [x] `PUT` can toggle `active` independently of `rateType`/`rate`.
- [x] `PUT` switching `rateType` from `MANUAL` to `AUTO` clears `rate` to `null`.
- [x] `PUT`/`DELETE` for a non-existent id return `404`.
- [x] `DELETE` succeeds regardless of the row's `active` value (no guard at this level).
- [x] `POST`/`PUT`/`DELETE` return `202` with a pending audit request and change no `currency_pair` row until the request is approved.
- [x] Submit-time validation still returns `400`/`404`/`409` as specified, before any audit request is created.
- [x] A second mutation on a pair that already has a pending request returns `409`.
- [x] Approving a `CURRENCY_PAIR` request performs the create/update/delete for real; rejecting or cancelling it leaves the row untouched.
- [x] The handler's `validate` rejects an approval whose change is no longer legal (e.g. the parent definition's `precision` tightened below the pending manual rate's decimal places), and the row is left unchanged.
- [x] The definition fan-out and the definition-delete cascade still write directly, creating no audit requests.
- [x] Every `GET` response includes `spreadGroupId` and `spreadGroupName`, populated for a pair assigned to a spread group and `null` for an unassigned one.
- [x] `POST`/`PUT` ignore `spreadGroupId`/`spreadGroupName` if sent — a created pair is always unassigned, and an update never changes a pair's group.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPair.java` (extended: added `baseCurrencyCode`/`quoteCurrencyCode` fields)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairResponse.java` (extended: added `baseCurrencyCode`/`quoteCurrencyCode` fields; constructor signature updated)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairCreateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairUpdateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyPairMapper.java` (extended: added `findAll`, `findById`, `findByDefinitionAndBrand`, `update`, `deleteById`)
  - `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml` (extended: shared `selectColumns` SQL fragment joining `brand` and, via `currency_pair_definition`, `currency` twice for base/quote codes; added `findAll` with optional filters, `findById`, `findByDefinitionAndBrand`, `update`, `deleteById`)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairNotFoundException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairConflictException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (extended: handlers for the two new exceptions, 404/409)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairService.java` (new: findAll/findById/create/update/delete + rate/precision validation)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairDefinitionService.java` (updated `toCurrencyPairResponse` call site for the new `CurrencyPairResponse` constructor shape)
  - `develop/backend/src/main/java/com/wdd/backend/controller/CurrencyPairController.java` (new: `GET/POST /api/currency-pairs`, `GET/PUT/DELETE /api/currency-pairs/{id}`)
  - `develop/backend/src/test/java/com/wdd/backend/service/CurrencyPairServiceTest.java` (new, 19 unit tests, mocked mappers)
  - `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyPairControllerTest.java` (new, 13 integration tests, live DB via `TestRestTemplate`)
- Notes:
  - Extended the sibling `currency-pair-definition` spec's lightweight `CurrencyPair`/`CurrencyPairResponse`/`CurrencyPairMapper` starting point rather than creating parallel classes, per instructions.
  - `findByDefinitionId` (used by the definition's fan-out response) now also returns `baseCurrencyCode`/`quoteCurrencyCode` via the same joins — purely additive, verified the full existing suite (`CurrencyPairDefinitionServiceTest`/`CurrencyPairDefinitionControllerTest`) still passes unmodified.
  - Rate precision check uses `rate.stripTrailingZeros().scale()` (floored at 0) so trailing zeros in a submitted rate (e.g. `150.2500` against `precision: 2`) don't cause a false-positive rejection.
  - `PUT` merge semantics: any field omitted (`null`) from the request body keeps the row's current value; when the resulting `rateType` is `AUTO`, `rate` is always forced to `null` regardless of what was sent or previously stored.
  - Verified with `mvn -f develop/backend/pom.xml compile` and `mvn -f develop/backend/pom.xml test` — full suite green: 104 tests, 0 failures, 0 errors, 0 skipped (includes the 19 new service unit tests and 13 new controller integration tests, plus all pre-existing tests with no regressions).
  - Additionally ran a live smoke test against the real MySQL-backed app (`mvn spring-boot:run` on port 8080): created currencies + a `precision: 2` definition, then exercised `GET /api/currency-pairs` (list + `currencyPairDefinitionId`/`brandId`/`active` filters), `GET /{id}`, `POST` (AUTO ignoring a sent rate, MANUAL success, MANUAL exceeding precision → 400, duplicate `(definitionId, brandId)` → 409, non-existent definition/brand → 400), `PUT` (toggle `active` alone, `MANUAL`→`AUTO` clearing `rate`, over-precision rate → 400, unknown id → 404), and `DELETE` (unknown id → 404; success on an `active: true` row with no guard) — all responses matched the spec's contract exactly. All smoke-test rows were cleaned up (currency pairs deactivated, definition deleted cascading its fanned-out pairs, both test currencies deleted) and the server process was stopped afterward.

---
### Increment 1 — 2026-08-23
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPair.java` (added read-only `spreadGroupId`/`spreadGroupName` fields + getters/setters)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairResponse.java` (added `spreadGroupId`/`spreadGroupName` fields + getters/setters; constructor signature extended with the two new params)
  - `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml` (`selectColumns` shared SQL fragment: added `LEFT JOIN spread_group sg ON sg.id = cp.spread_group_id`, selecting `cp.spread_group_id` and `sg.name AS spread_group_name`; `CurrencyPairResultMap` extended with the two new column mappings — feeds `findAll`/`findById`/`findByDefinitionAndBrand`/`findByDefinitionId` uniformly, no extra query)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairService.java` (`toResponse` now passes `spreadGroupId`/`spreadGroupName` through to the response)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairDefinitionService.java` (`toCurrencyPairResponse` — same shared `CurrencyPairResponse` constructor — updated call site for the new params; the definition fan-out response now also surfaces these fields, always `null` for a freshly created pair)
- Notes:
  - `CurrencyPairCreateRequest`/`CurrencyPairUpdateRequest` were not touched — neither ever declared a `spreadGroupId`/`spreadGroupName` field, and Jackson's default (fail-on-unknown-properties is not enabled anywhere in this project) silently ignores any such field sent in a `POST`/`PUT` body. Verified this live (see below) rather than assuming it.
  - No new round trip: `spreadGroupId`/`spreadGroupName` are populated by the same shared `selectColumns` SQL fragment (`LEFT JOIN spread_group`) used by every read path in this mapper, per spec Implementation Detail #6.
  - Verified with `mvn -f develop/backend/pom.xml compile` and `mvn -f develop/backend/pom.xml test` (JDK 17 via `/c/Users/user/.jdks/corretto-17.0.11`, required since the shell's default `java` was 11) — full suite green: 104 tests, 0 failures, 0 errors, 0 skipped, no regressions.
  - Live-verified against the real MySQL-backed app (`mvn spring-boot:run` on port 8080, DB `wdd`):
    - Created a `USD/JPY precision:2` currency pair definition (id 31), fanning out to 7 brand pairs (ids 182–188); confirmed the fan-out response itself and a subsequent `GET /api/currency-pairs?currencyPairDefinitionId=31` both returned `spreadGroupId: null`, `spreadGroupName: null` for every pair.
    - Inserted a `spread_group` row directly via SQL (`name='VIP-verify'`, id 6, `brand_id=1`) and set `currency_pair.spread_group_id = 6` for pair 182 directly via SQL.
    - Confirmed `GET /api/currency-pairs/182` and the list-filtered `GET` now returned `spreadGroupId: 6`, `spreadGroupName: "VIP-verify"` for pair 182 and `null`/`null` for the other 6 sibling pairs (183–188).
    - `PUT /api/currency-pairs/182` with body `{"active": true, "spreadGroupId": 999}` returned `active: true` but `spreadGroupId` unchanged at `6` — confirming the sent `spreadGroupId` was ignored.
    - Deleted pair 183, then `POST /api/currency-pairs` with `{"currencyPairDefinitionId":31,"brandId":2,"spreadGroupId":6,"spreadGroupName":"hacked"}` returned a new row (id 189) with `spreadGroupId: null`, `spreadGroupName: null` — confirming `POST` ignores both fields and a created pair is always unassigned.
  - Cleanup: deactivated pair 182 (it had been toggled `active: true` during the `PUT` test, which the parent definition's delete-guard requires), deleted `spread_group` id 6 directly via SQL, then `DELETE /api/currency-pair-definitions/31` (cascades all its fanned-out pairs, `204`). Confirmed via SQL afterward: `spread_group`, `currency_pair`, and `currency_pair_definition` all back to `0` rows. Server process (PID 27676, launched via `spring-boot:run`) was killed with `taskkill /F /T` and port 8080 confirmed free afterward.
  - Nothing left unverified — both new acceptance criteria were exercised end-to-end against the live DB in addition to the automated suite.

---
### Increment 2 — 2026-08-23
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/AuditPendingResponse.java` (new: generic 202 response shape — auditRequestId/status/entityType/actionType/entityId/summary — built via `AuditPendingResponse.from(AuditRequest)`; not currency-pair-specific, reusable by any future audited controller)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairAuditHandler.java` (new: the CURRENCY_PAIR AuditHandler @Component — validate/apply for CREATE/UPDATE/DELETE, re-running existence/uniqueness/rate-precision checks against current data at approval time; normalizes beforeData/afterData Map values — which arrive as JSON-native types Double/Integer/Long/String after the round trip through the audit_request JSON columns — back to BigDecimal/Long/Boolean)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairService.java` (rewritten: create/update/delete now validate exactly as before at submit time — same 400/404/409 — then call AuditService.submit(...) instead of writing, returning AuditPendingResponse; builds beforeData/afterData as Map<String,Object> and a Traditional-Chinese one-line summary per action; no longer calls currencyPairMapper.insert/update/deleteById directly — those now live only in CurrencyPairAuditHandler)
  - `develop/backend/src/main/java/com/wdd/backend/controller/CurrencyPairController.java` (POST/PUT/DELETE now return 202 ACCEPTED with AuditPendingResponse; read X-Actor header and pass through to the service, matching AuditController's convention)
  - `develop/backend/src/test/java/com/wdd/backend/service/CurrencyPairServiceTest.java` (rewritten: mocks AuditService instead of asserting on direct mapper writes; verifies submit-time validation still short-circuits before any AuditService.submit call, and that a successful create/update/delete submits the correct entityType/actionType/entityId/brandId/beforeData/afterData and never touches currencyPairMapper.insert/update/deleteById)
  - `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyPairControllerTest.java` (rewritten: every create/update/delete test now asserts 202 + row-unchanged, then approves via POST /api/audit-requests/{id}/approve and asserts the row actually changed; added secondMutationOnPairWithPendingRequestReturns409, rejectingOrCancellingAnAuditRequestLeavesRowUntouched, approvalRevalidatesAgainstCurrentDataAndRejectsIfPrecisionTightened, definitionFanOutAndDeleteCascadeWriteDirectlyWithNoAuditRequests)
  - `specs/backend/currency-pair.md` (this file: checked off the six new acceptance criteria, added this section)
- Notes:
  - Submit-time validation in CurrencyPairService is untouched logic-wise (same existence/uniqueness/rate-precision checks, same exception types, so the same 400/404/409); only the final step changed from "write" to "submit for audit". CurrencyPairAuditHandler.validate duplicates the rate/precision check with its own small private method (throwing AuditHandlerException instead of InvalidRequestException) rather than sharing code with the service, deliberately, per audit.md's "Validation happens twice" design: submit-time and approval-time validation are separate concerns with separate exception semantics (400 vs 422), and the duplication is about 10 lines.
  - CURRENCY_PAIR's one-pending-per-target conflict (409 on a second PUT/DELETE while one is PENDING) is enforced entirely by AuditService.submit's existing findPending(entityType, entityId) check — CurrencyPairService does not duplicate this check itself.
  - The definition fan-out (CurrencyPairDefinitionService.create) and delete cascade (CurrencyPairDefinitionService.delete, backed by the DB's ON DELETE CASCADE) were not touched at all in this increment — both call CurrencyPairMapper/CurrencyPairDefinitionMapper directly and never go through CurrencyPairService or AuditService, so they remain synchronous and unaudited by construction. Verified this holds both by code inspection (no new imports/calls added to CurrencyPairDefinitionService) and live (see below).
  - spreadGroupId/spreadGroupName enrichment on GET is unaffected — no changes to the read path, mapper XML, or CurrencyPairResponse.
  - Verified with `mvn -f develop/backend/pom.xml compile` and `mvn -f develop/backend/pom.xml test` (JDK 17 via `/c/Users/user/.jdks/corretto-17.0.11`) — full suite green: **202 tests, 0 failures, 0 errors, 0 skipped** (198 pre-existing + 4 net new: CurrencyPairServiceTest stayed at 19 tests, rewritten for the audited flow; CurrencyPairControllerTest grew from 13 to 17). No regressions anywhere else, including CurrencyPairDefinitionServiceTest/CurrencyPairDefinitionControllerTest (fan-out/cascade untouched) and AuditServiceTest/AuditControllerTest/StubAuditHandler-based tests (generic module untouched).
  - Live-verified end-to-end against the real MySQL-backed app (`mvn spring-boot:run` on port 8080, confirmed free before and after): created two fresh currencies (LVA/LVB) and a precision:2 definition (id 231) — fan-out created 7 currency_pair rows directly with **zero** audit_request rows created (`SELECT COUNT(*) FROM audit_request WHERE entity_type='CURRENCY_PAIR'` stayed 0 through the fan-out). Then:
    - PUT on a fanned-out pair (rateType: MANUAL, rate: 1.55, active: true) → 202 with summary "au LVA/LVB 改為手動匯率 1.55，啟用"; confirmed the row was still AUTO/null/false immediately after; a second PUT and a DELETE on the same pair both returned 409 ("A PENDING audit request already exists for CURRENCY_PAIR:1619"); approved the request (POST .../approve → 200, beforeData/afterData correctly populated) and confirmed the row was now MANUAL/1.55/true.
    - DELETE on that same (now-approved) pair → 202, row still 200 OK on GET immediately after; approved → row 404 afterward.
    - Precision-drift rejection: PUT a different pair to MANUAL, rate: 2.34 under precision:2 (legal, 202), then tightened the definition's precision to 1 via PUT /api/currency-pair-definitions/231, then approved the still-pending request → **422** (`{"error":"rate must not exceed 1 decimal places","auditRequestId":123}`), confirmed the row was still untouched (AUTO/null/false) and the request detail showed status: "PENDING", applyError: "rate must not exceed 1 decimal places" — then cancelled it to clean up.
    - CREATE flow: deleted one fanned-out pair directly via SQL to open a slot, POST /api/currency-pairs for that (definition, brand) → 202, entityId: null; confirmed no row existed yet; approved → row created for real with the submitted MANUAL/9.9 values.
    - Cascade: DELETE /api/currency-pair-definitions/231 → 204, confirmed all remaining currency_pair rows for that definition (including the one created via the audited CREATE above) were gone — direct cascade, not routed through audit.
    - Cleanup: deleted the test audit_request rows (121–124) and the two test currencies via DELETE /api/currencies/{id}; confirmed the DB's currency_pair/currency_pair_definition/audit_request counts returned to the exact pre-test baseline (one pre-existing, unrelated USD/JPY definition with 7 fanned pairs and 0 audit requests — present before this task started and deliberately left untouched, not created by this work). Server process was killed and port 8080 confirmed free afterward.
  - Nothing left unverified against the spec's six new acceptance criteria — all were exercised both by the automated test suite and live against the real database.
