---
status: done
title: "Audit Module — Generic Approval Service and API"
requirement: "Factor the approval/审核 mechanism out into its own independent audit module, so that any action needing approval can plug into it directly without adding anything to the audit module itself"
---

# Audit Module — Generic Approval Service and API — Backend Spec

## Overview
Provide a standalone, entity-agnostic **audit module**: a service and REST API implementing the submit → `PENDING` → approve/reject workflow, backed by the `audit_request` table (`specs/dba/audit.md`). Any feature in this codebase that needs its create/update/delete gated behind review plugs into this module by implementing one interface (`AuditHandler`) and registering it — **the audit module itself never changes** when a new consumer is added.

This spec was previously generalized (but not yet implemented) as a `ChangeRequestService`/`/api/change-requests` design living inside `specs/backend/currency-pair-approval.md`. This spec extracts it fully into its own standalone module, renamed for a clear, independent identity: `AuditHandler`, `AuditService`, `AuditController`, `/api/audit-requests`. `specs/backend/currency-pair-approval.md` no longer defines any of this generic machinery — it is now a thin consumer spec that implements `AuditHandler` for `currency_pair` and depends on this spec for everything generic.

**This module must contain no reference to `currency_pair`, `brand`, or any other specific entity.** If a future edit to `AuditService`/`AuditController`/`AuditHandler` needs to special-case a particular `entityType`, that is a sign the logic belongs in that entity's own handler instead.

## Requirements
- One interface, `AuditHandler`, that any approval-gated entity implements once to plug into the audit module
- One generic service, `AuditService`, holding a registry of `AuditHandler`s keyed by `entityType`, exposing `submit`/`list`/`getById`/`approve`/`reject` — none of which reference any specific entity type in their own code
- One generic REST API, `/api/audit-requests`, for listing requests and approving/rejecting them — usable by a review UI for *any* entity type without the UI or API needing entity-specific endpoints
- Adding a new approval-gated feature must require: implement `AuditHandler`, register it as a bean, have that feature's own controller call `AuditService.submit(...)`. Nothing else — no new endpoint, no change to `AuditService`, `AuditController`, or `audit_request`
- Approving/rejecting is only valid while a request is `PENDING` — reviewing an already-reviewed request returns `409`
- At most one `PENDING` request may exist per `(entityType, entityId)` at a time (generic, enforced by `AuditService` itself, not per-handler) — an entity-specific natural-key duplicate rule for `CREATE` (where `entityId` doesn't exist yet to dedup on) is each handler's own responsibility inside `validate(...)`
- Re-validate a request's `after` snapshot via its handler at approval time, not just at submission time (state may have drifted)

## The `AuditHandler` interface

```
interface AuditHandler {
    String entityType();                    // e.g. "CURRENCY_PAIR" — must be unique across all registered handlers

    // Build the "before" snapshot from the live entity, for an UPDATE/DELETE submission.
    // Throws the entity's own not-found exception if entityId doesn't exist.
    Map<String, Object> snapshotOf(Long entityId);

    // Validate a proposed "after" snapshot for CREATE/UPDATE. Also responsible for any
    // entity-specific dedup/natural-key rule (e.g. a CREATE colliding with another live
    // row, or with another PENDING CREATE request of this same entityType).
    // Throws the entity's own validation exceptions (400/404/409) on failure. Not called
    // for DELETE (deleting has no field-level business rules beyond existence, which
    // AuditService already checks generically via snapshotOf).
    void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    // Apply an approved change to the real entity table: insert/update/delete.
    // Returns the entity's id (the new id for CREATE; entityId unchanged otherwise).
    Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    // Short human-readable label for list rendering, e.g. "PUG · USD/TWD"
    String summarize(Map<String, Object> snapshot);
}
```

`AuditService` holds `Map<String, AuditHandler>` (populated by Spring from all `AuditHandler` beans, keyed by each bean's `entityType()`) and never imports or references any specific entity's classes.

## API Contract

Base path: `/api/audit-requests`

### 1. List Audit Requests
```
GET /api/audit-requests
```
Query parameters:
| Param      | Type   | Required | Description                                              |
|------------|--------|----------|-------------------------------------------------------------|
| entityType | String | No       | e.g. `CURRENCY_PAIR` — any value a registered handler uses    |
| status     | String | No       | `PENDING` / `APPROVED` / `REJECTED`                            |
| actionType | String | No       | `CREATE` / `UPDATE` / `DELETE`                                  |

Response `200`: array of `AuditRequestResponse` (shape below), newest `requestedAt` first.

### 2. Get Audit Request by ID
```
GET /api/audit-requests/{id}
```
Response `200`: single `AuditRequestResponse`. Response `404`:
```json
{ "error": "Audit request not found", "id": 999 }
```

### 3. Approve an Audit Request
```
POST /api/audit-requests/{id}/approve
```
Request body:
```json
{ "reviewedBy": "Bob" }
```
`reviewedBy` is optional (no auth system exists in this app — free-text, not tied to a logged-in user).

Behavior: load the request (`404` if missing); `409` via `AuditRequestAlreadyReviewedException` if `status != PENDING`; look up the `AuditHandler` for `request.entityType` (an unrecognized `entityType` — which shouldn't be possible for rows this service itself created — is a `500`, not a normal error path, and indicates a handler was removed/renamed without a data migration); call `handler.validate(actionType, entityId, afterSnapshot)` again (re-validation); call `handler.apply(actionType, entityId, afterSnapshot)`; set `status=APPROVED`, `reviewedBy`, `reviewedAt=now`, and (for `CREATE`) `entityId` to whatever `apply` returned.

Response `200`: updated `AuditRequestResponse` (`status: "APPROVED"`).

Response `404`: request not found, or (for `UPDATE`/`DELETE`) the handler reports the target entity no longer exists.

Response `409`:
```json
{ "error": "Audit request has already been reviewed", "id": 10, "status": "APPROVED" }
```
(also returned if `status` is already `REJECTED`)

Response `400`/`404`/`409` from re-validation: whatever the handler's `validate` throws — **the request stays `PENDING`**, it is not auto-rejected.

### 4. Reject an Audit Request
```
POST /api/audit-requests/{id}/reject
```
Request body:
```json
{ "reviewedBy": "Bob", "rejectReason": "匯率過高，請重新確認" }
```
Validation: `rejectReason` required (non-blank). `reviewedBy` optional.

Behavior: entirely generic — no handler involvement whatsoever. Set `status = REJECTED`, `reviewedBy`, `reviewedAt = now`, `rejectReason`. The target entity is never touched.

Response `200`: updated `AuditRequestResponse` (`status: "REJECTED"`). Response `404`: not found. Response `409`: already reviewed. Response `400`: `rejectReason` missing/blank.

### `AuditRequestResponse` shape
```json
{
    "id": 10,
    "entityType": "CURRENCY_PAIR",
    "actionType": "UPDATE",
    "entityId": 3,
    "status": "PENDING",
    "summary": "PUG · USD/TWD",
    "before": { "...": "entity-specific, opaque to this module" },
    "after": { "...": "entity-specific, opaque to this module" },
    "requestedBy": "Alice",
    "requestedAt": "2026-07-29T10:00:00",
    "reviewedBy": null,
    "reviewedAt": null,
    "rejectReason": null,
    "createdAt": "2026-07-29T10:00:00",
    "updatedAt": "2026-07-29T10:00:00"
}
```
`before`/`after` are the raw stored JSON, deserialized to a generic `Map<String,Object>`/JSON node and returned as-is — the module never inspects their contents beyond passing them to the relevant handler. `before` is `null` for `CREATE`; `after` is `null` for `DELETE`.

## Implementation Details

### Layer Structure
`pl.piomin.services.backend.audit` (a new, self-contained package): `AuditController`, `AuditService`, `AuditHandler` (interface), `AuditRequestMapper` (+ XML), `AuditRequest` (entity), `AuditRequestResponse`/`ApproveAuditRequestRequest`/`RejectAuditRequestRequest` (DTOs), `AuditRequestNotFoundException`/`AuditRequestAlreadyReviewedException`/`DuplicatePendingAuditRequestException`. Keeping this in its own package (rather than scattered across the existing `controller`/`service`/`dto` packages) makes the "independent module" boundary explicit and easy to verify by inspection. Consumer-specific handlers (e.g. `CurrencyPairAuditHandler`, `specs/backend/currency-pair-approval.md`) live in their own feature's package and depend on `pl.piomin.services.backend.audit`, never the other way around.

### Entity: `AuditRequest`
Fields map to `audit_request` 1:1: `id`, `entityType`, `actionType`, `entityId`, `beforeSnapshot`, `afterSnapshot`, `summary`, `status`, `requestedBy`, `requestedAt`, `reviewedBy`, `reviewedAt`, `rejectReason`, `createdAt`, `updatedAt`. `beforeSnapshot`/`afterSnapshot` are mapped as plain `String` fields holding raw JSON text — MyBatis treats the `JSON` column as an opaque string with no custom `TypeHandler` needed; `AuditService` parses/serializes to/from `Map<String,Object>` via the already-available Jackson `ObjectMapper`, keeping the mapper layer simple.

### DTOs
- `AuditRequestResponse`: as shown above; `before`/`after` are generic `Object`/`Map<String,Object>`.
- `ApproveAuditRequestRequest`: `reviewedBy` (String, optional).
- `RejectAuditRequestRequest`: `reviewedBy` (String, optional), `rejectReason` (String, `@NotBlank`).

### Service logic: `AuditService`
- `list(entityType, status, actionType)`: read all, optionally filtered; order by `requestedAt` descending.
- `getById(id)`: `404` via `AuditRequestNotFoundException` if missing.
- `submit(entityType, actionType, entityId, afterSnapshot, requestedBy)`: look up the handler for `entityType` (called by the consuming feature's own controller — see `specs/backend/currency-pair-approval.md` for an example call site); for `UPDATE`/`DELETE`, call `handler.snapshotOf(entityId)` for `before`, and check no `PENDING` request exists for `(entityType, entityId)` — `409` via `DuplicatePendingAuditRequestException` (this dedup check is fully generic: keyed only on `entityType`+`entityId`, no entity knowledge needed); for `CREATE`/`UPDATE`, call `handler.validate(actionType, entityId, afterSnapshot)` (entity-specific — may itself throw a `409` for a natural-key dedup conflict on `CREATE`, since there's no `entityId` yet to key the generic dedup check on); compute `summary` via `handler.summarize(...)` on whichever of before/after is non-null; serialize both snapshots to JSON text; insert with `status=PENDING`.
- `approve(id, reviewedBy)` / `reject(id, reviewedBy, rejectReason)`: as described in the API Contract above.
- `findPendingByEntity(entityType, entityId)`: generic `AuditRequestMapper` query filtering `status='PENDING'`, backing the dedup check.

### `AuditController`
`GET /api/audit-requests`, `GET /api/audit-requests/{id}`, `POST /api/audit-requests/{id}/approve`, `POST /api/audit-requests/{id}/reject`. Contains no entity-specific logic whatsoever — this is the file to check when verifying the module stayed generic.

### New exceptions
- `AuditRequestNotFoundException` → `404`
- `AuditRequestAlreadyReviewedException` → `409`
- `DuplicatePendingAuditRequestException` → `409`
- Add handlers for all three in `GlobalExceptionHandler`, following the existing pattern.

### How a consumer plugs in (informational — the actual work is in that consumer's own spec)
1. Implement `AuditHandler` for the entity type (e.g. `CurrencyPairAuditHandler`, `entityType() = "CURRENCY_PAIR"`), reusing that feature's existing service for `snapshotOf`/`validate`/`apply`.
2. Register it as a Spring bean (component scan picks it up automatically as long as it's `@Component`/`@Service`-annotated in a scanned package — no manual registration list to maintain).
3. In that feature's own controller, replace direct calls to its service's `create`/`update`/`delete` with a call to `AuditService.submit(entityType, actionType, entityId, afterSnapshot, requestedBy)`, and return `202` with the resulting `AuditRequestResponse`.
4. Nothing in this spec (`audit_request` table, `AuditHandler` interface, `AuditService`, `AuditController`) changes.

### Out of scope (explicitly)
- No entity-specific handler implementations — those belong to each consumer's own spec (e.g. `specs/backend/currency-pair-approval.md`).
- No notification/email system for reviewers.
- No real authentication/authorization — `requestedBy`/`reviewedBy` are free-text fields; any caller can currently call both submit and approve/reject. Restricting who may approve, and which handler beans exist, remains a code-level concern.

## Acceptance Criteria
- [x] `AuditController`, `AuditService`, and `AuditHandler` compile and contain zero references to `currency_pair`, `brand`, `Currency`, or any other specific domain entity — verified by inspection, not just tests
- [x] `GET /api/audit-requests?entityType=X&status=PENDING` and `GET /api/audit-requests/{id}` work against a test-only fake `AuditHandler` registered purely for the test, proving the module works without any real consumer wired in
- [x] `POST /api/audit-requests/{id}/approve` on a `PENDING` request calls the correct handler's `validate` then `apply`, sets `status=APPROVED`, `reviewedBy`, `reviewedAt`, and (for `CREATE`) `entityId` from `apply`'s return value
- [x] Approving a request whose re-validation now fails returns the handler's error and leaves the request `PENDING`
- [x] `POST /api/audit-requests/{id}/reject` with a `rejectReason` marks the request `REJECTED`; missing `rejectReason` returns `400`
- [x] Approving or rejecting an already-`APPROVED`/`REJECTED` request returns `409`
- [x] Submitting a second request for the same `(entityType, entityId)` while one is `PENDING` returns `409` without any handler-specific code needed to make that check work
- [x] Unit tests for `AuditService` using a fake/test `AuditHandler` (not `CurrencyPairAuditHandler`) covering submit/approve/reject and all generic validation/dedup branches
- [x] Integration tests for `AuditController` endpoints

---
## Execution Result
- Status: DONE
- Files changed (all new unless noted):
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditActionType.java` — enum `CREATE`/`UPDATE`/`DELETE`
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditHandler.java` — the plug-in interface (`entityType`, `snapshotOf`, `validate`, `apply`, `summarize`)
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditRequest.java` — entity mapped 1:1 to `audit_request` (`actionType`/`status` kept as plain `String`, matching this project's no-enum-type-handler convention seen in `CurrencyPair.rateType`)
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditRequestMapper.java` + `develop/backend/src/main/resources/mapper/AuditRequestMapper.xml` — `findAll` (filtered, `requested_at DESC`), `findById`, `findPendingByEntity`, `insert`, `update`, plus `deleteById`/`findAllIds` for test cleanup only
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditRequestResponse.java`, `ApproveAuditRequestRequest.java`, `RejectAuditRequestRequest.java` — DTOs; `before`/`after` parsed from the raw JSON text columns to `Map<String,Object>` via a private static Jackson `ObjectMapper`, matching the existing static `from(entity)` factory convention
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditRequestNotFoundException.java`, `AuditRequestAlreadyReviewedException.java`, `DuplicatePendingAuditRequestException.java`
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditService.java` — generic `submit`/`list`/`getById`/`approve`/`reject`, holding `Map<String, AuditHandler>` built from all injected `AuditHandler` beans (empty list is fine — no consumer exists yet)
  - `develop/backend/src/main/java/pl/piomin/services/backend/audit/AuditController.java` — `GET /api/audit-requests`, `GET /api/audit-requests/{id}`, `POST /api/audit-requests/{id}/approve`, `POST /api/audit-requests/{id}/reject`
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (modified) — added handlers for the three new audit exceptions (404/409/409), following the existing pattern; an unrecognized `entityType` at approve time deliberately has no handler and falls through to Spring's default 500
  - `develop/backend/src/test/resources/schema.sql` (modified) — added `audit_request` for the H2 test DB (snapshot columns as `VARCHAR(4000)` rather than H2's `JSON` type, since MyBatis treats them as opaque strings either way and this avoids H2-JSON-literal binding quirks; functionally equivalent to the MySQL `JSON` columns from `V005__create_audit_request_table.sql`)
  - `develop/backend/src/test/java/pl/piomin/services/backend/audit/AuditServiceTest.java` — unit tests (17) against a Mockito-mocked `AuditHandler`
  - `develop/backend/src/test/java/pl/piomin/services/backend/audit/TestAuditHandler.java` — test-only fake `AuditHandler` (`entityType = "TEST_ENTITY"`), a `@Component` living only on the test classpath under the scanned base package, so Spring wires it as a real bean during `@SpringBootTest` runs without ever shipping in production
  - `develop/backend/src/test/java/pl/piomin/services/backend/audit/AuditControllerTest.java` — integration tests (14) driving the real `AuditController`/`AuditService`/H2 DB via `TestAuditHandler`
- Verification performed:
  - `mvn -f develop/backend/pom.xml compile` — `BUILD SUCCESS`
  - `mvn -f develop/backend/pom.xml test` — `BUILD SUCCESS`, `Tests run: 125, Failures: 0, Errors: 0, Skipped: 0` (all pre-existing suites plus the 31 new audit tests)
  - `mvn -f develop/backend/pom.xml -DskipTests package` — `BUILD SUCCESS`, jar repackaged
  - Inspected `AuditController.java`, `AuditService.java`, `AuditHandler.java`: zero imports or references to `currency_pair`/`CurrencyPair`/`Brand`/`Currency` or any other domain entity (verified via `grep`); the only string `"CURRENCY_PAIR"` anywhere in the module is an illustrative example inside a Javadoc comment on `AuditHandler.entityType()`, copied verbatim from this spec's own interface definition — no import, class reference, or branching logic on it
- Notes on judgment calls:
  - `actionType`/`status` are stored as plain `String` on the `AuditRequest` entity (not a MyBatis enum type handler), matching this codebase's existing convention (`CurrencyPair.rateType`); `AuditActionType` is a real Java enum used at the `AuditHandler`/`AuditService` boundary per the spec's own interface signature, and is converted to/from `String` at the mapper boundary
  - `approve()` re-validates via `handler.validate(...)` for `CREATE`/`UPDATE` only (never `DELETE`, per the interface contract) and calls `handler.apply(...)` unconditionally; if re-validation throws, the exception propagates before `auditRequestMapper.update(...)` is ever called, so the row is left untouched (still `PENDING`) — covered by `AuditServiceTest.approve_leavesRequestPending_whenRevalidationFails` and `AuditControllerTest.approve_returns400_andLeavesRequestPending_whenRevalidationFails`
  - A missing/unregistered handler for a request's `entityType` at approve time throws a plain `IllegalStateException`, deliberately left unmapped in `GlobalExceptionHandler` so Spring's default machinery returns `500` — per the spec, this is a data/deployment bug, not a normal API error path
  - `AuditController.approve` accepts a `null`/missing request body (`@RequestBody(required = false)`) since `reviewedBy` is fully optional; `reject` requires a JSON body because `rejectReason` is mandatory via `@NotBlank`
- Acceptance Criteria:
  - [x] `AuditController`, `AuditService`, and `AuditHandler` compile and contain zero references to `currency_pair`, `brand`, `Currency`, or any other specific domain entity — verified by inspection
  - [x] `GET /api/audit-requests?entityType=X&status=PENDING` and `GET /api/audit-requests/{id}` work against a test-only fake `AuditHandler` (`TestAuditHandler`) registered purely for the test
  - [x] `POST /api/audit-requests/{id}/approve` on a `PENDING` request calls the handler's `validate` then `apply`, sets `status=APPROVED`, `reviewedBy`, `reviewedAt`, and (for `CREATE`) `entityId` from `apply`'s return value
  - [x] Approving a request whose re-validation now fails returns the handler's error (`400` from `TestAuditHandler`'s forced `ResponseStatusException`) and leaves the request `PENDING`
  - [x] `POST /api/audit-requests/{id}/reject` with a `rejectReason` marks the request `REJECTED`; missing `rejectReason` returns `400`
  - [x] Approving or rejecting an already-`APPROVED`/`REJECTED` request returns `409`
  - [x] Submitting a second request for the same `(entityType, entityId)` while one is `PENDING` returns `409` without any handler-specific code
  - [x] Unit tests for `AuditService` using a fake/test `AuditHandler` covering submit/approve/reject and all generic validation/dedup branches (17 tests in `AuditServiceTest`)
  - [x] Integration tests for `AuditController` endpoints (14 tests in `AuditControllerTest`)
