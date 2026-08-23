---
status: done
title: "Audit / Approval API (generic module)"
requirement: "品牌幣種對與點差的新增/修改/刪除需要審核通過才會執行；此規格提供通用的送審、審核、套用機制"
depends_on: []
---

# Audit — Backend Spec

## Overview
The generic approval module. An audited change is not applied when it is submitted — it is recorded as a **pending request** (see [audit-request.md](../dba/audit-request.md)) and only performed when a reviewer approves it. This spec owns the request store, the review endpoints, and the contract that entity code plugs into.

**This module knows nothing about currency pairs or spreads.** It never imports their services, never validates their fields, and never interprets `before_data`/`after_data` — those are opaque JSON to it. Everything entity-specific lives behind the handler contract below, registered by the entity's own spec ([currency-pair.md](currency-pair.md), [spread.md](spread.md)). That is what lets a future audited entity be added without touching this spec. `depends_on` is deliberately empty for the same reason: this module is buildable on its own, and the entities depend on *it*, not the reverse.

## Requirements

### Entity: AuditRequest
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| entityType | String | Which kind of thing this changes — set by the submitting entity code, opaque here |
| actionType | String | `CREATE`, `UPDATE`, or `DELETE` |
| entityId | Long | Identifies the target, `null` for `CREATE`. It is whatever key the entity's own API addresses that target by — usually its primary key, but e.g. a brand default spread is addressed by `brandId`, and a membership change by its group's id. The audit module never interprets it; only the entity's handler does. Its only meaning here is uniqueness: `(entityType, entityId)` is what the one-pending-per-target rule keys on |
| brandId | Long | The brand this change belongs to, for filtering/display; `null` if not brand-scoped |
| summary | String | One-line human-readable description, supplied by the submitting entity code (max 200 chars) |
| beforeData | JSON object | The target's values when the request was raised; `null` for `CREATE` |
| afterData | JSON object | The requested values; `null` for `DELETE` |
| status | String | `PENDING` → `APPROVED` / `REJECTED` / `CANCELLED`. Terminal once it leaves `PENDING` |
| requestedBy / requestedAt | String / Timestamp | Who raised it and when |
| reviewedBy / reviewedAt | String / Timestamp | Who resolved it and when; `null` while pending |
| reviewComment | String | Reviewer's note (max 500); required when rejecting, optional when approving |
| applyError | String | Why the last approval attempt failed to apply; `null` normally |

### The actor, and what it is not
Every write carries an actor string, taken from an `X-Actor` request header and falling back to `system` when absent. **This project has no authentication, so the actor is whatever the caller says it is — it is a display/attribution field, not a security control.** Two consequences to state plainly rather than pretend away:
- Nothing prevents the same person approving their own request; enforcing maker–checker separation requires real identity, which does not exist here yet.
- `requestedBy`/`reviewedBy` are not evidence of who actually acted.
When authentication is introduced, the actor should come from the authenticated principal and this fallback must be removed.

### The submit contract (used by audited entities, not exposed over HTTP)
Audited entity code calls a single submit operation instead of writing its change:

`submit(entityType, actionType, entityId, brandId, summary, beforeData, afterData, requestedBy) → AuditRequest`

- Validates that no `PENDING` request already exists for `(entityType, entityId)` — if one does, the caller's request is rejected as a conflict so two people cannot queue contradictory edits to one row.
- Persists the request as `PENDING` and returns it.
- Performs **no** entity validation of its own — the caller validates before submitting (see "Validation happens twice" below).

### The handler contract (implemented per audited entity)
Each `entityType` registers one handler with the module:

- `validate(request)` — re-check the request against **current** data; throws if it is no longer legal.
- `apply(request)` — perform the real change.

The module resolves a handler by `entityType` at approval time. An unknown `entityType` is a server-side wiring error (`500`), not a client error. Handlers are the only place entity knowledge lives.

### Validation happens twice, deliberately
Once at **submit** time, by the entity's own endpoint, so a caller gets an immediate `400`/`404`/`409` for an obviously bad change instead of a request that is doomed to fail review. Once again at **approval** time, via the handler's `validate`, because the world may have moved since: the target may have been deleted, a name may now collide, a parent's precision may have tightened. Skipping the second check would let approval apply a change that is no longer valid.

### API Contract

**GET /api/audit-requests**
- Query params (all optional): `status`, `entityType`, `brandId`, `entityId`.
- Response `200`: array of requests, newest `requestedAt` first:
  `[ { "id": 12, "entityType": "CURRENCY_PAIR", "actionType": "UPDATE", "entityId": 10, "brandId": 1, "summary": "au USD/JPY 改為手動匯率 150.25", "status": "PENDING", "requestedBy": "alice", "requestedAt": "...", "reviewedBy": null, "reviewedAt": null, "reviewComment": null, "applyError": null }, ... ]`
- `beforeData`/`afterData` are omitted from the list response — they are only returned by the detail endpoint, so the queue stays light.

**GET /api/audit-requests/{id}**
- Response `200`: the full request **including** `beforeData` and `afterData`.
- Not found → `404`.

**POST /api/audit-requests/{id}/approve**
- Request body: `{ "comment": "optional note" }`; actor from `X-Actor`.
- Behavior: load the request → must be `PENDING` (else `409`) → resolve its handler → `validate` → `apply` → mark `APPROVED` with reviewer, timestamp, and comment. The validate+apply+status update are one transaction: if apply fails, nothing is half-written.
- If `validate`/`apply` rejects the change because the underlying data drifted → `422`, body `{ "error": "<why>", "auditRequestId": 12 }`. The request **stays `PENDING`** with `applyError` recorded, so it can be retried after the conflict is resolved, or withdrawn. It is not auto-rejected — a stale request is not the reviewer's decision.
- Response `200`: the updated request.

**POST /api/audit-requests/{id}/reject**
- Request body: `{ "comment": "why" }` — required, 1–500 chars (`400` if missing/blank); actor from `X-Actor`.
- Must be `PENDING` (else `409`). Nothing is applied to the target.
- Response `200`: the updated request, `status: "REJECTED"`.

**POST /api/audit-requests/{id}/cancel**
- Withdraws a request the submitter no longer wants. Request body: `{ "comment": "optional" }`; actor from `X-Actor`.
- Must be `PENDING` (else `409`).
- Response `200`: the updated request, `status: "CANCELLED"`.
- This exists because of the one-pending-per-target rule: without a way to withdraw, a mistaken request would block every further change to that row until someone approved or rejected it. It is not a bypass — cancelling applies nothing.

## Implementation Details
1. **Status transitions** are centralized in one place: only `PENDING` → `APPROVED`/`REJECTED`/`CANCELLED` is legal, and any attempt to resolve an already-resolved request is a `409`. Never mutate a terminal row.
2. **The one-pending-per-target rule** is checked in the service before insert *and* backed by the database's `uk_audit_request_pending` unique index — a constraint violation surfaces as the same conflict error, never a `500`.
3. **Approval is transactional**: handler `validate` → handler `apply` → status update commit together. A failure anywhere rolls the whole thing back and leaves the request `PENDING` with `applyError` set (the `applyError` write is a separate, committed statement so the reason survives the rollback).
4. **`beforeData`/`afterData` are stored and returned verbatim** as JSON. This module never parses them for meaning; only the entity's handler does.
5. **Handler registry**: handlers are looked up by `entityType` string. Registration is per-entity, so adding an audited entity touches only that entity's code and never this module.
6. **List ordering and filtering** happen in the query, not in memory.
7. Reads of the audited entities themselves are unaffected by this module — a pending request changes nothing that any `GET` returns.

## Acceptance Criteria
- [x] `GET /api/audit-requests` returns requests newest-first and narrows correctly on `status`, `entityType`, `brandId`, and `entityId`.
- [x] The list response omits `beforeData`/`afterData`; `GET /api/audit-requests/{id}` includes both.
- [x] `GET /api/audit-requests/{id}` returns `404` for an unknown id.
- [x] Submitting a change for a target that already has a `PENDING` request is rejected as a conflict (`409`), and the database's unique index rejects it too if the service check is bypassed.
- [x] A second `PENDING` request is accepted once the first is approved, rejected, or cancelled.
- [x] `approve` runs the registered handler's `validate` then `apply`, marks the request `APPROVED` with reviewer and timestamp, and the target row actually changes.
- [x] `approve` on a request whose handler rejects it (data drifted) returns `422`, leaves the request `PENDING` with `applyError` populated, and leaves the target row untouched.
- [x] `approve`/`reject`/`cancel` on a non-`PENDING` request return `409`.
- [x] `reject` with a missing or blank comment returns `400`; with a comment it marks `REJECTED` and changes nothing on the target.
- [x] `cancel` marks `CANCELLED` and changes nothing on the target.
- [x] `X-Actor` is recorded as `requestedBy`/`reviewedBy`, defaulting to `system` when the header is absent.
- [x] The audit module contains no import of, or reference to, any audited entity's service — verified by inspection; entity knowledge exists only in handlers.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/AuditRequest.java` (persistence model), `AuditRequestSummaryResponse.java` (list, omits before/afterData), `AuditRequestDetailResponse.java` (detail + approve/reject/cancel responses, includes before/afterData), `AuditActionRequest.java` (`{comment}` body)
  - `develop/backend/src/main/java/com/wdd/backend/service/AuditHandler.java` (the `validate`/`apply`/`entityType()` contract), `AuditHandlerRegistry.java` (resolves by `entityType`; built from `List<AuditHandler>` auto-injected by Spring — new entities register by adding a `@Component`, no change to this module), `AuditApplyRunner.java` (separate `@Transactional` bean running validate→apply→status-update as one unit; kept separate from `AuditService` to avoid the Spring self-invocation proxy pitfall), `AuditService.java` (submit/findAll/findById/approve/reject/cancel)
  - `develop/backend/src/main/java/com/wdd/backend/controller/AuditController.java` (`GET /api/audit-requests`, `GET /api/audit-requests/{id}`, `POST .../approve|reject|cancel`, reads `X-Actor`)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/AuditRequestMapper.java` + `develop/backend/src/main/resources/mapper/AuditRequestMapper.xml` (`findAll` with `<where>`/`<if>` filters and `ORDER BY requested_at DESC, id DESC`, `findById`, `findPending`, `insert`, `updateResolved`, `updateApplyError`)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/JsonObjectTypeHandler.java` (MyBatis `BaseTypeHandler<Object>` serializing/deserializing `beforeData`/`afterData` to/from the `JSON` columns via Jackson, referenced explicitly via `typeHandler=` — not globally registered, since `Object` is too broad a Java type to bind automatically)
  - `develop/backend/src/main/java/com/wdd/backend/exception/AuditRequestNotFoundException.java` (404), `AuditRequestConflictException.java` (409, both "pending already exists" and "not PENDING" cases), `AuditHandlerException.java` (thrown by handler `validate`/`apply` to signal a legal-but-now-stale change; caught only inside the approve path), `AuditApplyFailedException.java` (422, carries `auditRequestId`)
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` — added handlers for the four exceptions above (404/409/422)
  - `develop/backend/src/test/java/com/wdd/backend/service/StubAuditHandler.java` — **test-only** `@Component` implementing `AuditHandler` for `entityType() == "TEST_STUB"` (not a real entity name), picked up by Spring's component scan only because it's on the test classpath when `@SpringBootTest` boots; "applies" onto an in-memory `ConcurrentHashMap` (`TARGET_STATE`) standing in for a real target row, and throws `AuditHandlerException` when `afterData.forceFail == true` to exercise the 422 path on demand. Nothing like it exists under `src/main`.
  - `develop/backend/src/test/java/com/wdd/backend/service/AuditServiceTest.java` (17 tests, mocked `AuditRequestMapper`/`AuditHandlerRegistry`/`AuditApplyRunner`) and `develop/backend/src/test/java/com/wdd/backend/controller/AuditControllerTest.java` (14 tests, `@SpringBootTest` + `TestRestTemplate` + real MySQL, seeding via `AuditService.submit(...)` since submit is not an HTTP endpoint)
- Notes:
  - Design: this module has zero knowledge of currency-pair/spread — verified both by code review and by `grep -inE "currencypair|brandspread|spreadgroup|currency|spread"` across every file listed above under `src/main`, which returned no matches in any of them. `AuditHandler` is a pure contract; `AuditHandlerRegistry` is populated purely from Spring-injected `List<AuditHandler>`, so a future entity spec registers a handler `@Component` and this module is untouched.
  - `beforeData`/`afterData` are typed `Object` end-to-end (DTO → JSON response), and MyBatis reads/writes them via `JsonObjectTypeHandler` against the `JSON` columns, always returned verbatim (round-tripped through Jackson, never interpreted).
  - Approve is split across two Spring beans (`AuditService.approve()` calling out to `AuditApplyRunner.run()`) specifically so `@Transactional` on the validate→apply→status-update sequence is honored via the Spring AOP proxy — a private/self-invoked method on the same bean would silently run non-transactionally. On `AuditHandlerException` from the handler, `AuditApplyRunner`'s transaction rolls back completely (including any partial `apply()` writes), then `AuditService.approve()` performs `updateApplyError` as its own separate, immediately-committed statement outside that rolled-back transaction, and throws `AuditApplyFailedException` (422). Verified live (see below) that after a forced 422, the row is `PENDING` with `apply_error` populated and the stub's `TARGET_STATE` untouched.
  - An unknown `entityType` (`AuditHandlerRegistry.resolve` finds no match) throws a plain `IllegalStateException`, deliberately **not** registered in `GlobalExceptionHandler`, so it falls through to Spring's default handler and surfaces as `500` — confirmed live against the real running app (no handler is registered in production code, only the test-scope stub), the request stayed untouched (still `PENDING`, `apply_error` still `NULL`) since the exception is thrown before `AuditApplyRunner`'s transaction even starts.
  - `mvn -f develop/backend/pom.xml test`: **198 tests, 0 failures, 0 errors** (167 pre-existing + 14 new `AuditControllerTest` + 17 new `AuditServiceTest`). No regressions. `AuditControllerTest` runs against the real MySQL DB exactly like the other controller tests in this project (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`), so its 14 cases already constitute one live-DB run of: the one-pending-per-target conflict (service-level `AuditRequestConflictException` **and** a direct-mapper bypass hitting `uk_audit_request_pending` and getting `DataIntegrityViolationException`), a second request accepted after the first is cancelled, approve running the stub handler's `validate` then `apply` transactionally and mutating `TARGET_STATE`, the 422 drift path leaving the row `PENDING`/`applyError`-populated/target-untouched, 409 on approve/reject/cancel of an already-resolved row, 400 on reject with blank/missing comment, and `X-Actor` defaulting to `system`.
  - On top of that, ran a genuine separate live verification: started the actual app via `mvn -f develop/backend/pom.xml spring-boot:run` on port 8080 (confirmed free beforehand via `Get-NetTCPConnection`), and drove the full HTTP surface with `curl` plus direct `mysql` inserts (standing in for what an entity's future `submit()` call will do, since `submit` is intentionally not an HTTP endpoint): confirmed `GET` list/detail/filtering, the DB unique-index rejection (`ERROR 1062 ... uk_audit_request_pending`) when inserting a second `PENDING` row directly, `POST .../cancel` succeeding and freeing the target for a new `PENDING` row, `POST .../approve` on an entity type with no runtime handler returning `500` and leaving the row untouched, `POST .../reject` returning `400` for missing/blank comment and `200`+`REJECTED` with a valid one (`reviewedBy` correctly defaulted to `"system"` with no `X-Actor` header sent), and `409` on approve/reject/cancel of that now-`REJECTED` row. Afterward: deleted all inserted rows (`DELETE FROM audit_request WHERE id IN (...)`, confirmed `COUNT(*) = 0`), stopped the `spring-boot:run` process, confirmed port 8080 free again, and removed the scratch log file — nothing left running or behind.
  - Not covered live (by design, not an oversight): a genuine successful `approve` against a *real* handler in the running production app — no real `AuditHandler` exists yet in `src/main` (that's the point of this spec: `currency-pair.md`/`spread.md` register their own next). That path **is** exercised live against MySQL by `AuditControllerTest` via the test-scope `StubAuditHandler`, which is the mechanism the spec explicitly calls for ("verify the approve path with a test-only stub handler registered in test scope").
