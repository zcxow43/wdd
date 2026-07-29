---
status: done
title: "Currency Pair as an Audit Consumer"
requirement: "Currency pair create/update/delete must not apply directly — they must be submitted for approval through the standalone audit module, with before/after visible before approving"
---

# Currency Pair as an Audit Consumer — Backend Spec

## Overview
`POST /api/currency-pairs`, `PUT /api/currency-pairs/{id}`, and `DELETE /api/currency-pairs/{id}` (`specs/backend/currency-pair.md`) must not mutate `currency_pair` directly — they submit a request through the generic audit module (`specs/backend/audit.md`), applied only once approved. This spec covers **only** currency pair's plug-in into that module: implementing `AuditHandler` for `entityType = "CURRENCY_PAIR"` and wiring `CurrencyPairController` to submit through `AuditService` instead of mutating directly. The generic submit/list/approve/reject mechanism, the `/api/audit-requests` API, and the `audit_request` table are entirely specified in `specs/backend/audit.md` and `specs/dba/audit.md` — implement those first (or alongside this).

This file previously (in an earlier, unimplemented iteration) defined the entire generic maker-checker mechanism itself, coupled to currency pairs. That generic machinery has been extracted into `specs/backend/audit.md`; this file now contains only what's genuinely currency-pair-specific.

Read-only endpoints (`GET /api/currency-pairs`, `GET /api/currency-pairs/{id}`) are **unaffected** — they keep reading live, already-approved rows from `currency_pair` directly.

## Requirements
- `POST`/`PUT`/`DELETE /api/currency-pairs...` submit through `AuditService.submit("CURRENCY_PAIR", ...)` (`specs/backend/audit.md`) instead of calling `CurrencyPairService.create`/`update`/`delete` directly, and return `202 Accepted` with the resulting `AuditRequestResponse`
- A `CurrencyPairAuditHandler` implements `AuditHandler` for `entityType = "CURRENCY_PAIR"`, reusing `CurrencyPairService`'s existing brand/currency-existence, base≠quote, rate/rateType, and uniqueness validation
- The handler's `CREATE` dedup rule (no live pair or `PENDING` `CREATE` request already exists for the same `(brandId, baseCurrencyId, quoteCurrencyId)`) is this handler's own responsibility, per `specs/backend/audit.md`'s note that natural-key dedup for `CREATE` isn't something the generic audit module can check on its own (there's no `entityId` yet)
- `CurrencyPairCreateRequest`/`CurrencyPairUpdateRequest` gain an optional `requestedBy` (String) field, passed through to `AuditService.submit`

## `CurrencyPairAuditHandler` snapshot shape
`before_snapshot`/`after_snapshot` (opaque JSON to the audit module, `specs/dba/audit.md`) for `entityType = "CURRENCY_PAIR"`:
```json
{
    "brandId": 3, "brandCode": "PUG",
    "baseCurrencyId": 2, "baseCurrencyCode": "USD",
    "quoteCurrencyId": 1, "quoteCurrencyCode": "TWD",
    "rate": 32.5, "rateType": "MANUAL", "active": true
}
```

## API Contract

### Changes to the existing `/api/currency-pairs` endpoints

#### `POST /api/currency-pairs` (submit a create request)
Request body: unchanged from `specs/backend/currency-pair.md` (`brandId`, `baseCurrencyId`, `quoteCurrencyId`, `rate`, `rateType`, `active`), plus optional `requestedBy`.

Behavior: `CurrencyPairController.create` builds the proposed snapshot map from the request and calls `auditService.submit("CURRENCY_PAIR", CREATE, null, afterSnapshot, requestedBy)`, which delegates validation to `CurrencyPairAuditHandler.validate(...)`. Nothing is inserted into `currency_pair`.

Response **`202 Accepted`** (changed from `201`): `AuditRequestResponse` with `entityType: "CURRENCY_PAIR"`, `actionType: "CREATE"`, `entityId: null`, `status: "PENDING"`, `before: null`, `after: <submitted values, with codes resolved>`.

Errors: same `400`/`404`/`409` shapes as `specs/backend/currency-pair.md`, plus:
```json
{ "error": "A pending create request already exists for this brand/base/quote combination" }
```
→ `409`

#### `PUT /api/currency-pairs/{id}` (submit an update request)
Request body: unchanged (partial update), plus optional `requestedBy`.

Behavior: `CurrencyPairController.update` calls `auditService.submit("CURRENCY_PAIR", UPDATE, id, mergedAfterSnapshot, requestedBy)`. `AuditService` calls `handler.snapshotOf(id)` for `before` (`404` via `CurrencyPairNotFoundException` if missing) and checks no `PENDING` request already exists for `(CURRENCY_PAIR, id)` (`409`, generic). `CurrencyPairController`/`CurrencyPairAuditHandler` merge the request onto the pair's current values (same partial-update merge as the original `CurrencyPairService.update`) to build the proposed `after` snapshot before `handler.validate(UPDATE, id, after)` runs. Nothing is persisted to `currency_pair`.

Response **`202 Accepted`** (changed from `200`): `AuditRequestResponse` with `actionType: "UPDATE"`, `entityId: id`, `status: "PENDING"`, `before: <pair's current values>`, `after: <merged proposed values>`.

Errors: same as create, plus:
```json
{ "error": "A pending change request already exists for this currency pair" }
```
→ `409`

#### `DELETE /api/currency-pairs/{id}` (submit a delete request)
No request body required; an optional `requestedBy` may be accepted the same way as the other two endpoints.

Behavior: `auditService.submit("CURRENCY_PAIR", DELETE, id, null, requestedBy)` — `handler.snapshotOf(id)` for `before` (`404` if missing), generic pending-dedup check (`409`). Nothing is deleted from `currency_pair`.

Response **`202 Accepted`** (changed from `204`): `AuditRequestResponse` with `actionType: "DELETE"`, `entityId: id`, `status: "PENDING"`, `before: <pair's current values>`, `after: null`.

Errors: `404` if not found, `409` if a pending request already exists for this pair.

The generic `/api/audit-requests` list/get/approve/reject endpoints used to review and act on these requests are specified in `specs/backend/audit.md`, not here.

## Implementation Details

### `CurrencyPairAuditHandler` (implements `AuditHandler`, `entityType() = "CURRENCY_PAIR"`)
- `snapshotOf(id)`: load the pair via `CurrencyPairMapper` (`404` via the existing `CurrencyPairNotFoundException` if missing), build the shape shown above.
- `validate(actionType, entityId, after)`: extract the existing brand-existence / currency-existence / base≠quote / rate-rule / uniqueness checks out of `CurrencyPairService` (`specs/backend/currency-pair.md`) into package-visible helper methods (or a small shared component) reused by both `CurrencyPairService` and this handler, rather than duplicating that logic. For `CREATE`, additionally check no `PENDING` `CREATE` `audit_request` row exists with `entityType='CURRENCY_PAIR'` and a matching `(brandId, baseCurrencyId, quoteCurrencyId)` in its `after_snapshot` (via `AuditRequestMapper`, e.g. `JSON_EXTRACT` or by loading candidate rows and comparing in Java — either is fine given the small expected row count).
- `apply(actionType, entityId, after)`: convert `after` back into a `CurrencyPairCreateRequest`/`CurrencyPairUpdateRequest`-shaped call into `CurrencyPairService.create`/`update`/`delete` (kept exactly as they are today — the actual insert/update/delete logic, just no longer called directly by `CurrencyPairController`). Returns the pair's id.
- `summarize(snapshot)`: `"{brandCode} · {baseCurrencyCode}/{quoteCurrencyCode}"`.

### Required changes to the existing Currency Pair API (`specs/backend/currency-pair.md`)
- `CurrencyPairController.create`/`update`/`delete`: no longer call `CurrencyPairService.create`/`update`/`delete` directly. Build the snapshot map and call `AuditService.submit("CURRENCY_PAIR", ...)`, returning `202` with `AuditRequestResponse`.
- `CurrencyPairController.list`/`getById` (`GET`): **unchanged**.
- `CurrencyPairService.create`/`update`/`delete`: kept as-is, but now called only from `CurrencyPairAuditHandler.apply(...)`, never directly from `CurrencyPairController`.
- Brand/currency-existence, base≠quote, rate-rule, and uniqueness validation logic currently inline in `CurrencyPairService.create`/`update` is extracted into shared helpers reused by `CurrencyPairAuditHandler.validate`.

### Out of scope (explicitly)
- No changes to `CurrencyService.delete`'s in-use guard — it continues to check only the live `currency_pair` table, not `audit_request`. A currency referenced only by a `PENDING`/historical audit request remains deletable; if later deleted while a `PENDING` request still references it, approval will correctly fail with `404` at re-validation time, leaving the request `PENDING` for a reviewer to reject or for someone to resubmit.

## Acceptance Criteria
- [x] `POST /api/currency-pairs` creates a `PENDING` `CURRENCY_PAIR`/`CREATE` audit request via `AuditService` and returns `202`; no row is inserted into `currency_pair`
- [x] `PUT /api/currency-pairs/{id}` creates a `PENDING` `CURRENCY_PAIR`/`UPDATE` audit request and returns `202`, with `before` matching the pair's current state and `after` matching the merged proposed state; the live pair is unchanged
- [x] `DELETE /api/currency-pairs/{id}` creates a `PENDING` `CURRENCY_PAIR`/`DELETE` audit request and returns `202`; the live pair is unchanged
- [x] Submitting a second create/update/delete for the same pair (or same brand/base/quote triple for create) while one is still `PENDING` returns `409`
- [x] Approving a `CURRENCY_PAIR`/`CREATE` request (via `specs/backend/audit.md`'s generic approve endpoint) inserts the row into `currency_pair` and sets the request's `entityId`
- [x] Approving a `CURRENCY_PAIR`/`UPDATE` request overwrites the target pair with the `after` snapshot
- [x] Approving a `CURRENCY_PAIR`/`DELETE` request deletes the target pair
- [x] Approving a `CURRENCY_PAIR` request whose re-validation now fails (e.g. brand disabled/removed, duplicate now exists) returns the appropriate `400`/`404`/`409` and leaves the request `PENDING`
- [x] `GET /api/currency-pairs` and `GET /api/currency-pairs/{id}` behavior is unchanged (still reads live data directly)
- [x] Unit tests for `CurrencyPairAuditHandler` (validate/apply/snapshotOf/summarize, all branches) and updated `CurrencyPairServiceTest`/`CurrencyPairControllerTest` reflecting the controller delegating to `AuditService`

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairValidator.java` (new) — extracted brand/currency-existence, base≠quote, uniqueness, and rate/rateType-rule checks out of `CurrencyPairService` into this shared `@Component`, reused by both `CurrencyPairService` and the new `CurrencyPairAuditHandler`, per the spec's "Implementation Details" section.
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairAuditHandler.java` (new) — `implements AuditHandler`, `entityType() = "CURRENCY_PAIR"`. `snapshotOf` loads the pair via the existing enriched `CurrencyPairMapper.findById` (404 via `CurrencyPairNotFoundException`); `validate` resolves brand/base/quote existence via `CurrencyPairValidator`, re-applies the rate/rateType rule onto the snapshot map (mutating `rate` in place, e.g. forcing it to `null` for `AUTO`), enriches the map in place with `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode`, and — for `CREATE` only, and only on the *original* submission (see "Notable judgment call" below) — checks no `PENDING` `CREATE` `audit_request` already exists for the same `(brandId, baseCurrencyId, quoteCurrencyId)` by loading candidate rows via `AuditRequestMapper.findAll(entityType, "PENDING", "CREATE")` and comparing in Java; `apply` reconstructs a `CurrencyPairCreateRequest`/`CurrencyPairUpdateRequest` from the snapshot map and delegates to the unchanged `CurrencyPairService.create`/`update`/`delete`; `summarize` returns `"{brandCode} · {baseCurrencyCode}/{quoteCurrencyCode}"`.
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/DuplicatePendingCurrencyPairCreateException.java` (new) — 409, message `"A pending create request already exists for this brand/base/quote combination"`, exactly matching the spec's CREATE-dedup error example.
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (edited) — added a handler for `DuplicatePendingCurrencyPairCreateException` → 409.
  - `develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairDeleteRequest.java` (new) — optional `requestedBy`, accepted as an optional `DELETE` request body.
  - `develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairCreateRequest.java`, `CurrencyPairUpdateRequest.java` (edited) — added optional `requestedBy` (String), passed through to `AuditService.submit`.
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairService.java` (edited) — constructor now takes `(CurrencyPairMapper, CurrencyPairValidator)` instead of `(CurrencyPairMapper, BrandMapper, CurrencyMapper)`; `create`/`update`/`delete` bodies are otherwise unchanged (same validation order, same rate/rateType handling), now delegating each validation step to `CurrencyPairValidator`. Kept exactly as the actual insert/update/delete logic — only reachable from `CurrencyPairAuditHandler.apply(...)` now, never directly from `CurrencyPairController`.
  - `develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyPairController.java` (edited) — `create`/`update`/`delete` no longer call `CurrencyPairService.create`/`update`/`delete`. `create` builds an `after` map from the request and calls `auditService.submit("CURRENCY_PAIR", CREATE, null, after, requestedBy)`; `update` first reads the pair's current values via `currencyPairService.getById(id)`, merges the partial request onto them to build the proposed `after` map, then calls `auditService.submit("CURRENCY_PAIR", UPDATE, id, after, requestedBy)`; `delete` calls `auditService.submit("CURRENCY_PAIR", DELETE, id, null, requestedBy)`. All three return `202 Accepted` with `AuditRequestResponse.from(...)`. `list`/`getById` (`GET`) are untouched.
  - `develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairServiceTest.java` (edited) — `setUp()` now constructs a real `CurrencyPairValidator` from the same mocked `BrandMapper`/`CurrencyMapper`/`CurrencyPairMapper` and injects it into `CurrencyPairService`; every existing test body is otherwise unchanged and still passes (25 tests).
  - `develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairAuditHandlerTest.java` (new) — 20 unit tests covering `entityType`, `snapshotOf` (found/404), `validate` (success + code-enrichment, brand/base/quote 404s, base=quote 400, live-duplicate 409, AUTO rate forced to null, MANUAL missing-rate 400, CREATE pending-duplicate 409, CREATE succeeds against a *different* pending triple, CREATE pending-duplicate check correctly skipped when re-validating an already-enriched snapshot at approval time — see below, UPDATE never invokes the CREATE-dedup query), `apply` (CREATE inserts + returns generated id, UPDATE updates + returns entityId, DELETE deletes + returns entityId, UPDATE 404s when the target row no longer exists), and `summarize`.
  - `develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairControllerTest.java` (rewritten) — 34 MockMvc integration tests: the 5 `GET` tests are unchanged (unaffected by this delta); `POST`/`PUT`/`DELETE` tests now assert `202` + `AuditRequestResponse` shape (`entityType`/`actionType`/`entityId`/`status`/`before`/`after`/`requestedBy`) and that the live `currency_pair` row is *not* mutated by the request itself; added dedicated 409 tests for "pending create already exists for this triple" and "pending update/delete already exists for this id"; added a full approval round-trip suite hitting the real `/api/audit-requests/{id}/approve` endpoint for `CREATE` (row inserted, `entityId` set), `UPDATE` (live row overwritten), `DELETE` (live row removed), plus two re-validation-at-approval-time failure cases (a live duplicate created between submission and approval → `409`, and the target row deleted directly between submission and approval → `404`) that both assert the request is left `PENDING`.
  - `develop/backend/pom.xml` (edited) — version bumped `0.0.4` → `0.0.5`, description updated to mention the audit-approval workflow.
  - `develop/backend/README.md` (edited) — documented the `/api/audit-requests` endpoint table, the "no longer mutates directly / 202" behavior change for `/api/currency-pairs` POST/PUT/DELETE, and the `0.0.5` version history entry.
- Verification performed:
  - `mvn -f develop/backend/pom.xml clean test` — `BUILD SUCCESS`, `Tests run: 152, Failures: 0, Errors: 0, Skipped: 0` (all pre-existing suites — `CurrencyServiceTest`, `CurrencyControllerTest`, `BrandServiceTest`, `BrandControllerTest`, `AuditServiceTest`, `AuditControllerTest` — plus `CurrencyPairServiceTest` (25), the rewritten `CurrencyPairControllerTest` (34), and the new `CurrencyPairAuditHandlerTest` (20)).
  - `mvn -f develop/backend/pom.xml clean test -Dsurefire.runOrder=random` — `BUILD SUCCESS`, same `152` tests, `0` failures, confirming no test-order/isolation dependency was introduced (the rewritten `CurrencyPairControllerTest.setUp()` also wipes `audit_request` between tests now, in addition to `currency_pair`/`currency`/`brand`).
  - `mvn -f develop/backend/pom.xml -DskipTests package` — `BUILD SUCCESS`, jar repackaged.
  - Manually traced every acceptance-criteria scenario against the actual `CurrencyPairController`/`AuditService`/`CurrencyPairAuditHandler` code paths (not just tests) while writing this summary, confirming no gaps between the spec's API contract and the implementation.
- Notable judgment calls:
  - **Self-collision bug found and fixed in the CREATE natural-key dedup check.** As initially written per the spec's literal instruction ("For CREATE, additionally check no PENDING CREATE audit_request row exists ... with a matching (brandId, baseCurrencyId, quoteCurrencyId)" inside `validate(...)`), this check broke approval of *any* CREATE request: `AuditService.approve()` re-invokes `handler.validate(...)` for re-validation while the request being approved is *itself* still `status=PENDING` in `audit_request` — so the dedup query would always find at least one match (itself) and incorrectly reject with `409`, even with zero real drift. Since `AuditHandler.validate(...)`'s signature (fixed, part of the already-implemented generic `specs/backend/audit.md` module I must not change) never passes the audit request's own id, and — as proven while designing the fix — no combination of `actionType`/`entityId`/`afterSnapshot` content can reliably distinguish "re-validating this exact pending request against itself" from "a genuine second concurrent submission for the same triple" (both scenarios yield exactly one matching PENDING row), a purely content-based fix is mathematically impossible. The fix implemented instead uses a reliable, already-present signal: a *freshly submitted* snapshot (built by `CurrencyPairController.create`, containing only `brandId`/`baseCurrencyId`/`quoteCurrencyId`/`rateType`/`rate`/`active`) has not yet been enriched with `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode`; a snapshot being re-validated at approval time (deserialized from the already-persisted, already-enriched `after_snapshot` JSON) already has those keys, because `validate(...)` itself is what adds them, and it always runs once at submission before the row is persisted. `CurrencyPairAuditHandler.validate` therefore only runs the CREATE pending-duplicate check when `!afterSnapshot.containsKey("brandCode")`. This is not spoofable by a client (the `CurrencyPairCreateRequest` DTO has no `brandCode` field, and unknown JSON properties are silently ignored, matching this codebase's existing Jackson behavior). Verified via `CurrencyPairAuditHandlerTest.validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime` (unit) and `CurrencyPairControllerTest.approve_createRequest_insertsRowAndSetsEntityId` (integration, which failed with `409` before this fix and passes after).
  - **Exact error wording for the generic UPDATE/DELETE pending-dedup 409** intentionally follows the already-implemented, tested `specs/backend/audit.md` module's own generic message (`"A pending audit request already exists for this entity"`, from `DuplicatePendingAuditRequestException`/`GlobalExceptionHandler`) rather than this spec's illustrative example text (`"A pending change request already exists for this currency pair"`). Changing that message would require either modifying the generic audit module to special-case `CURRENCY_PAIR` (explicitly forbidden — "This module must contain no reference to currency_pair...", `specs/backend/audit.md`) or introducing a currency-pair-specific override of a check that is, by the audit spec's own design, entirely generic and keyed only on `(entityType, entityId)`. Kept the existing, already-tested generic behavior; `CurrencyPairControllerTest` asserts `409` status for these cases without pinning the exact message text. Only the CREATE natural-key dedup (explicitly this handler's own responsibility, per both specs) uses the spec's literal wording.
  - The `PUT` merge step (spec: "CurrencyPairController/CurrencyPairAuditHandler merge the request onto the pair's current values") is implemented in `CurrencyPairController.update` (reads the pair once via `currencyPairService.getById(id)`, builds the merged `after` map), not inside the handler — the spec explicitly allows either. `AuditService.submit` separately calls `handler.snapshotOf(id)` for the `before` snapshot, so the pair is read twice per `PUT`; this minor redundancy was accepted in favor of keeping `CurrencyPairController` and `CurrencyPairAuditHandler` independently simple, per the spec's explicit "either is fine" latitude.
  - `CurrencyPairAuditHandler.apply` for `UPDATE`/`CREATE` reconstructs a full `CurrencyPairCreateRequest`/`CurrencyPairUpdateRequest` from the (already fully-merged and validated) snapshot map and calls the unmodified `CurrencyPairService.create`/`update`, which re-runs its own full validation internally. This is intentionally redundant with `CurrencyPairAuditHandler.validate` (which already ran moments earlier in the same `submit`/`approve` transaction) — the spec explicitly says to keep `CurrencyPairService.create`/`update`/`delete` "exactly as they are today", and the redundant check is a harmless, cheap defense-in-depth rather than a correctness concern.
  - `CurrencyService.delete`'s in-use guard is unchanged (still checks only the live `currency_pair` table via `CurrencyPairMapper.existsByCurrencyId`, per the spec's explicit "Out of scope" note) — not touched by this increment.
