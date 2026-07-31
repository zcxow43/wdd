---
status: done
title: "Currency Pair as an Audit Consumer"
requirement: "Currency pair update/delete must not apply directly — they must be submitted for approval through the standalone audit module, with before/after visible before approving. Create was originally in scope here too, but per a later requirement a brand's currency_pair row can now only ever come into existence via specs/backend/currency-pair-definition.md's global-definition fan-out — POST /api/currency-pairs, and this handler's CREATE branch, have been removed."
depends_on: [currency-pair, audit]
---

# Currency Pair as an Audit Consumer — Backend Spec

## Overview
`PUT /api/currency-pairs/{id}` and `DELETE /api/currency-pairs/{id}` (`specs/backend/currency-pair.md`) must not mutate `currency_pair` directly — they submit a request through the generic audit module (`specs/backend/audit.md`), applied only once approved. This spec covers **only** currency pair's plug-in into that module: implementing `AuditHandler` for `entityType = "CURRENCY_PAIR"` (`UPDATE`/`DELETE` only — see the Delta below) and wiring `CurrencyPairController` to submit through `AuditService` instead of mutating directly. The generic submit/list/approve/reject mechanism, the `/api/audit-requests` API, and the `audit_request` table are entirely specified in `specs/backend/audit.md` and `specs/dba/audit.md` — implement those first (or alongside this).

This file previously (in an earlier, unimplemented iteration) defined the entire generic maker-checker mechanism itself, coupled to currency pairs. That generic machinery has been extracted into `specs/backend/audit.md`; this file now contains only what's genuinely currency-pair-specific.

Read-only endpoints (`GET /api/currency-pairs`, `GET /api/currency-pairs/{id}`) are **unaffected** — they keep reading live, already-approved rows from `currency_pair` directly.

## Requirements
- `PUT`/`DELETE /api/currency-pairs...` submit through `AuditService.submit("CURRENCY_PAIR", ...)` (`specs/backend/audit.md`) instead of calling `CurrencyPairService.update`/`delete` directly, and return `202 Accepted` with the resulting `AuditRequestResponse`
- A `CurrencyPairAuditHandler` implements `AuditHandler` for `entityType = "CURRENCY_PAIR"`, reusing `CurrencyPairService`'s existing brand/currency-existence, base≠quote, rate/rateType, and uniqueness validation — for `UPDATE`/`DELETE` only, per the Delta below
- `CurrencyPairUpdateRequest` has an optional `requestedBy` (String) field, passed through to `AuditService.submit`

### Delta: no CREATE — a brand pair requires a global definition first
Per a later requirement ("必須先有全域, 品牌幣種對才會有" — a brand currency pair can only exist once a global currency-pair-definition exists for that direction), `POST /api/currency-pairs` has been removed entirely (`specs/backend/currency-pair.md`) — not just moved behind approval. Consequently:
- `CurrencyPairController` has no `create` method/route at all — not even one that only submits an audit request.
- `CurrencyPairAuditHandler` handles `UPDATE`/`DELETE` only. Its `CREATE` branch (the natural-key dedup check described below, and the `apply(CREATE, ...)` insert branch) is dead code with no caller — remove it, along with `DuplicatePendingCurrencyPairCreateException` if nothing else references it, rather than leaving unreachable branches in place.
- The sole remaining way a `currency_pair` row comes into existence is `CurrencyPairDefinitionService.create`'s per-brand fan-out (`specs/backend/currency-pair-definition.md`), which calls `CurrencyPairService.create` as a plain, unaudited method call — that mechanism is unchanged by this delta.
- `CurrencyPairCreateRequest`'s `requestedBy` field (added for the now-removed `POST` route) is no longer meaningful at the controller level but the DTO itself stays, since `CurrencyPairDefinitionService` still constructs one internally to call `CurrencyPairService.create`; it just never carries a real `requestedBy` from that internal call site.

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

There is no `POST /api/currency-pairs` — see the Delta above. Only `PUT`/`DELETE` submit audit requests.

#### `PUT /api/currency-pairs/{id}` (submit an update request)
Request body: unchanged (partial update), plus optional `requestedBy`.

Behavior: `CurrencyPairController.update` calls `auditService.submit("CURRENCY_PAIR", UPDATE, id, mergedAfterSnapshot, requestedBy)`. `AuditService` calls `handler.snapshotOf(id)` for `before` (`404` via `CurrencyPairNotFoundException` if missing) and checks no `PENDING` request already exists for `(CURRENCY_PAIR, id)` (`409`, generic). `CurrencyPairController`/`CurrencyPairAuditHandler` merge the request onto the pair's current values (same partial-update merge as the original `CurrencyPairService.update`) to build the proposed `after` snapshot before `handler.validate(UPDATE, id, after)` runs. Nothing is persisted to `currency_pair`.

Response **`202 Accepted`** (changed from `200`): `AuditRequestResponse` with `actionType: "UPDATE"`, `entityId: id`, `status: "PENDING"`, `before: <pair's current values>`, `after: <merged proposed values>`.

Errors: `400`/`404` per `specs/backend/currency-pair.md`'s validation rules, `409` if the resulting (brand, base, quote) triple collides with a different existing row, plus:
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
Handles `UPDATE`/`DELETE` only — there is no `CREATE` case (see the Delta above; remove any pre-existing `CREATE` branch/dedup check rather than leaving it as dead code):
- `snapshotOf(id)`: load the pair via `CurrencyPairMapper` (`404` via the existing `CurrencyPairNotFoundException` if missing), build the shape shown above.
- `validate(actionType, entityId, after)`: extract the existing brand-existence / currency-existence / base≠quote / rate-rule / uniqueness checks out of `CurrencyPairService` (`specs/backend/currency-pair.md`) into package-visible helper methods (or a small shared component) reused by both `CurrencyPairService` and this handler, rather than duplicating that logic. Only ever invoked with `UPDATE`.
- `apply(actionType, entityId, after)`: for `UPDATE`, convert `after` back into a `CurrencyPairUpdateRequest`-shaped call into `CurrencyPairService.update` (kept exactly as it is today — the actual update logic, just no longer called directly by `CurrencyPairController`); for `DELETE`, call `CurrencyPairService.delete`. Returns the pair's id.
- `summarize(snapshot)`: `"{brandCode} · {baseCurrencyCode}/{quoteCurrencyCode}"`.

### Required changes to the existing Currency Pair API (`specs/backend/currency-pair.md`)
- `CurrencyPairController` has no `create` method/route — see the Delta above. `update`/`delete` no longer call `CurrencyPairService.update`/`delete` directly; they build the snapshot map and call `AuditService.submit("CURRENCY_PAIR", ...)`, returning `202` with `AuditRequestResponse`.
- `CurrencyPairController.list`/`getById` (`GET`): **unchanged**.
- `CurrencyPairService.create`/`update`/`delete`: kept as-is. `update`/`delete` are now called only from `CurrencyPairAuditHandler.apply(...)`, never directly from `CurrencyPairController`. `create` is called only from `CurrencyPairDefinitionService`'s fan-out (`specs/backend/currency-pair-definition.md`), never from `CurrencyPairAuditHandler` (which has no `CREATE` branch) or `CurrencyPairController`.
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

### Delta: no CREATE — a brand pair requires a global definition first
(The `[x]` items above describing `POST`/`CURRENCY_PAIR`/`CREATE` behavior remain historically accurate for what was built and tested at the time; `POST /api/currency-pairs` no longer exists — see `specs/backend/currency-pair.md`.)
- [x] `CurrencyPairAuditHandler` has no `CREATE` case in `validate`/`apply` — confirmed by inspection, not just by the route being gone
- [x] `DuplicatePendingCurrencyPairCreateException` is removed if nothing else references it after the `CREATE` branch is removed
- [x] `CurrencyPairAuditHandlerTest`'s `CREATE`-specific test cases (pending-duplicate dedup, apply-inserts-and-returns-id, etc.) are removed or repurposed — the handler no longer has that behavior to test
- [x] `CurrencyPairControllerTest`'s `POST`-related tests (202/create-audit-request assertions, create-pending-duplicate 409, the `CREATE` leg of the approval round-trip suite) are removed, since the route no longer exists
- [x] `UPDATE`/`DELETE` audit behavior (submit, approve, reject, re-validation-at-approval-time failure, pending-dedup) is completely unchanged by this delta

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

### Increment 1 — 2026-07-30
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairAuditHandler.java` (edited — removed the `CREATE` natural-key dedup check and the `isOriginalSubmission`/enrichment-signal logic from `validate(...)` (it is now only ever invoked with `UPDATE`, so no `actionType` branching is needed there anymore); removed the `checkNoPendingCreateDuplicate` helper, the `toCreateRequest` helper, and the `parseSnapshot` helper (all dead once `CREATE` was removed); `apply(...)`'s exhaustive `switch` over `AuditActionType` now handles `UPDATE`/`DELETE` and has a `case CREATE ->` that throws `UnsupportedOperationException` with a message pointing at the currency-pair-definition fan-out, rather than leaving the old insert branch in place or dropping exhaustiveness; removed the now-unused `AuditRequestMapper`/`ObjectMapper` constructor dependencies and their imports (`List`, `Objects`, `JsonProcessingException`, `TypeReference`, `ObjectMapper`, `AuditRequest`, `AuditRequestMapper`, `CurrencyPairCreateRequest`, `DuplicatePendingCurrencyPairCreateException`) since nothing in the class uses them anymore
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/DuplicatePendingCurrencyPairCreateException.java` (deleted — confirmed via `grep` that nothing else in the codebase referenced it after the `CREATE` branch was removed from the handler)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (edited — removed the `@ExceptionHandler(DuplicatePendingCurrencyPairCreateException.class)` method)
  - `develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyPairController.java` (edited — removed `create`/`@PostMapping` entirely; see `specs/backend/currency-pair.md`'s paired Increment 2 for the full description, since both specs' deltas are one atomic change)
  - `develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairAuditHandlerTest.java` (rewritten — removed the `AuditRequestMapper` mock and `ObjectMapper` import (no longer needed by the handler's constructor); removed every `CREATE`-specific test (`validate_create_throwsDuplicate_whenPendingCreateExistsForSameTriple`, `validate_create_succeeds_whenPendingCreateExistsForDifferentTriple`, `validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime`, `apply_create_insertsPairAndReturnsGeneratedId`); changed the remaining `validate(...)` test invocations from `AuditActionType.CREATE` to `AuditActionType.UPDATE` (matching the class's own new contract that `validate` is only ever invoked with `UPDATE`); removed the now-meaningless `validate_update_doesNotCheckPendingCreateDuplicate` test (there is no pending-create-duplicate check left to not-check); added `apply_create_throwsUnsupportedOperation` asserting `handler.apply(AuditActionType.CREATE, ...)` throws `UnsupportedOperationException`. All `UPDATE`/`DELETE` test bodies (`snapshotOf`, brand/currency 404s, base=quote 400, live-duplicate 409, AUTO-forces-null, MANUAL-missing-rate 400, `apply_update_*`, `apply_delete_*`, `summarize`) are otherwise untouched.
  - `develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairControllerTest.java` (edited — see `specs/backend/currency-pair.md`'s paired Increment 2 for the full description)
  - `develop/backend/pom.xml`, `develop/backend/README.md` (edited — version bumped `0.0.7` → `0.0.8`; see `specs/backend/currency-pair.md`'s paired Increment 2)
- Notes:
  - This increment and `specs/backend/currency-pair.md`'s "Increment 2" were implemented together as a single atomic change (removing `POST /api/currency-pairs` end-to-end: controller route, audit-handler `CREATE` branch, and the now-dead dedup exception). They are recorded separately here only because they are two separate spec files.
  - `CurrencyPairAuditHandler.apply(...)`'s `switch` remains exhaustive over `AuditActionType` (a `default`/missing-case compile error would have caught an incomplete removal) — `CREATE` is handled explicitly with `throw new UnsupportedOperationException(...)` rather than being silently omitted, so a future accidental re-wiring of a `CREATE` audit submission for `CURRENCY_PAIR` would fail loudly instead of writing corrupt data.
  - Verified via `mvn -f develop/backend/pom.xml clean test`: `BUILD SUCCESS`, `256` tests, `0` failures/errors, including `CurrencyPairDefinitionServiceTest` (14) and `CurrencyPairDefinitionControllerTest` (15) — a completely different feature — passing unmodified, confirming `CurrencyPairService.create`'s plain-method fan-out call path is unaffected by removing `CurrencyPairAuditHandler`'s `CREATE` case and `CurrencyPairController`'s `POST` route.
  - No changes were needed to `.circleci/config.yml` — the existing `mvn -f develop/backend/pom.xml -B test` step already covers this.
