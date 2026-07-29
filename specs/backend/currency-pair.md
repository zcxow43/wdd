---
status: done
title: "Currency Pair API"
requirement: "Provide REST API for currency pair CRUD (rate manual/auto), scoped per brand; lock currency code on update; block currency delete when referenced by a pair. Delta: when rateType is AUTO, clear any supplied rate to null; when rateType is MANUAL, rate is required. Delta 2: create/update/delete no longer apply directly — see specs/backend/currency-pair-approval.md."
---

# Currency Pair API — Backend Spec

## Delta: create/update/delete now go through approval (implemented)
**`POST /api/currency-pairs`, `PUT /api/currency-pairs/{id}`, and `DELETE /api/currency-pairs/{id}` no longer mutate `currency_pair` directly.** They now submit an audit request (`202 Accepted` instead of `201`/`200`/`204`) that must be approved before it takes effect. Implemented per `specs/backend/currency-pair-approval.md`'s "Required changes to the existing Currency Pair API" section (`CurrencyPairAuditHandler` + `CurrencyPairController` delegating to `AuditService.submit`, `specs/backend/audit.md`) — see that spec's Execution Result for details. `GET /api/currency-pairs` and `GET /api/currency-pairs/{id}` are unaffected and keep working exactly as documented below; the Acceptance Criteria below (all `[x]`, describing the pre-delta 201/200/204 contract) remain historically accurate for what they tested at the time and are superseded for POST/PUT/DELETE response codes by `specs/backend/currency-pair-approval.md`, per that file's own Acceptance Criteria.

## Overview
Implement a REST API for managing currency pairs, each with an exchange rate that is either manually entered or automatically maintained, and each belonging to exactly one **brand**. Depends on the `currency_pair` table defined in `specs/dba/currency-pair.md`, the `brand` table/API (`specs/dba/brand.md`, `specs/backend/brand.md`), and the existing `currency` table/API (`specs/dba/currency.md`, `specs/backend/currency.md`).

This spec also requires two changes to the **existing** currency API (`pl.piomin.services.backend.*` currency classes) so the whole requirement is satisfied:
1. **Currency `code` becomes immutable after creation.** Remove `code` from `CurrencyUpdateRequest` entirely — it must not be possible to change a currency's code via `PUT /api/currencies/{id}`.
2. **Currency delete must be blocked while it is referenced by any currency pair.** Before deleting, `CurrencyService.delete` must check whether the currency is used as `base_currency_id` or `quote_currency_id` in `currency_pair`, and if so, reject with `409`.

## Requirements
- Full CRUD API for currency pairs
- Every currency pair belongs to exactly one brand (`brandId`), referencing the `brand` table (`specs/backend/brand.md`)
- `rate` field supports two modes via `rateType`: `MANUAL` (caller supplies the rate on every write) and `AUTO` (system-maintained; no automatic external rate-fetching job is in scope for this spec — that is future work)

### Delta: rate required for MANUAL, cleared for AUTO
- **`MANUAL`**: `rate` is required — must be present, numeric, and `> 0` on create. On update, `rate` may be omitted only if the pair already has a `rate` (i.e. is not switching from `AUTO` without also supplying a rate); switching an existing pair's `rateType` to `MANUAL` without a `rate` present (neither on the request nor already set) is rejected with `400`.
- **`AUTO`**: `rate` is no longer accepted as an initial/fallback value. Any `rate` supplied in the request body while `rateType` is (or is being changed to) `AUTO` is **ignored — the persisted `rate` is forced to `NULL`**. This applies on both create and update, including when a `MANUAL` pair is switched to `AUTO` (its previously configured rate is cleared).
- This supersedes the earlier "caller may still supply an initial/fallback value" behavior for `AUTO` pairs.
- Base and quote currency must reference existing, distinct currencies
- Duplicate (brand, base, quote) pairs are rejected — the same (base, quote) combination is allowed under different brands
- Currency `code` is immutable once created (enforced on the existing currency update endpoint)
- Currency delete is rejected with `409` while referenced by any currency pair (enforced on the existing currency delete endpoint)

## API Contract

Base path: `/api/currency-pairs`

### 1. List Currency Pairs

```
GET /api/currency-pairs
```

Query parameters:
| Param   | Type    | Required | Description                |
|---------|---------|----------|------------------------------|
| brandId | Long    | No       | Filter by owning brand       |
| active  | Boolean | No       | Filter by active status      |

Response `200`:
```json
[
    {
        "id": 1,
        "brandId": 3,
        "brandCode": "PUG",
        "baseCurrencyId": 2,
        "baseCurrencyCode": "USD",
        "quoteCurrencyId": 1,
        "quoteCurrencyCode": "TWD",
        "rate": 32.5,
        "rateType": "MANUAL",
        "active": true,
        "createdAt": "2025-01-01T00:00:00",
        "updatedAt": "2025-01-01T00:00:00"
    }
]
```

Note: `rate` is `null` for any row where `rateType` is `AUTO`.

### 2. Get Currency Pair by ID

```
GET /api/currency-pairs/{id}
```

Response `200`: single currency pair object (same shape as list item)

Response `404`:
```json
{
    "error": "Currency pair not found",
    "id": 999
}
```

### 3. Create Currency Pair

```
POST /api/currency-pairs
```

Request body:
```json
{
    "brandId": 3,
    "baseCurrencyId": 2,
    "quoteCurrencyId": 1,
    "rate": 32.5,
    "rateType": "MANUAL",
    "active": true
}
```

Validation:
| Field           | Rule                                                          |
|-----------------|-----------------------------------------------------------------|
| brandId         | Required, must reference an existing `brand.id`                 |
| baseCurrencyId  | Required, must reference an existing `currency.id`              |
| quoteCurrencyId | Required, must reference an existing `currency.id`, must differ from `baseCurrencyId` |
| rate            | Required and must be numeric, > 0 **when `rateType` is `MANUAL`**. Ignored — persisted as `null` — **when `rateType` is `AUTO`**, even if supplied |
| rateType        | Required, one of `MANUAL`, `AUTO`                                |
| active          | Optional, defaults to true                                      |

Response `201`: created currency pair object with generated `id`

Response `404`: if `brandId`, `baseCurrencyId`, or `quoteCurrencyId` does not exist
```json
{
    "error": "Currency not found",
    "id": 999
}
```
```json
{
    "error": "Brand not found",
    "id": 999
}
```

Response `409`: if the (brand, base, quote) pair already exists
```json
{
    "error": "Currency pair already exists for this brand",
    "brandId": 3,
    "baseCurrencyId": 2,
    "quoteCurrencyId": 1
}
```

Response `400`: if `baseCurrencyId` equals `quoteCurrencyId`, or other validation failure
```json
{
    "error": "Base and quote currency must differ"
}
```

Response `400`: if `rateType` is `MANUAL` and `rate` is missing, `<= 0`, or non-numeric
```json
{
    "error": "rate is required and must be greater than 0 when rateType is MANUAL"
}
```

### 4. Update Currency Pair

```
PUT /api/currency-pairs/{id}
```

Request body: same shape as create. All fields optional (partial update). If `brandId`/`baseCurrencyId`/`quoteCurrencyId` are changed, re-validate existence, distinctness, and (brand, base, quote) uniqueness against other rows.

`rate`/`rateType` interaction on update (see Delta above): resolve the effective `rateType` (request value, or the existing row's value if omitted), then:
- effective `rateType = MANUAL` → effective `rate` (request value, or existing row's value if the request didn't supply one) must be non-null and `> 0`, else `400`
- effective `rateType = AUTO` → persisted `rate` is forced to `NULL`, regardless of what the request supplied or what the row previously had

Response `200`: updated currency pair object

Response `404`: if `id`, `brandId`, `baseCurrencyId`, or `quoteCurrencyId` not found

Response `409`: if the resulting (brand, base, quote) pair collides with a different existing row

Response `400`: validation failure (e.g. effective `rateType = MANUAL` with no effective `rate` or `rate <= 0`, invalid `rateType`, base == quote)

### 5. Delete Currency Pair

```
DELETE /api/currency-pairs/{id}
```

Response `204`: no content

Response `404`: if id not found

## Implementation Details

### Layer Structure
Follow the existing currency feature's layering: Controller → Service → MyBatis Mapper (interface + XML), with request/response DTOs separate from the entity — same package structure as `pl.piomin.services.backend.{controller,service,mapper,model,dto,exception}`.

### Entity: `CurrencyPair`
Fields: `id`, `brandId`, `baseCurrencyId`, `quoteCurrencyId`, `rate` (`BigDecimal`, **nullable**), `rateType` (`String` or enum `RateType { MANUAL, AUTO }`), `active`, `createdAt`, `updatedAt`.

### Response enrichment
`CurrencyPairResponse` includes `brandCode` (joined from `brand`) and `baseCurrencyCode` / `quoteCurrencyCode` (joined from `currency`) in addition to the raw ids, so the frontend does not need extra lookups to render the table. Populate this via a mapper query that joins `currency_pair` to `brand` and to `currency` twice (aliased), rather than N+1 lookups.

### Service logic
- **Create**: validate brand existence (404 if missing), validate base/quote currency existence (404 if missing), validate base ≠ quote (400), check for existing pair with same (brand, base, quote) (409), apply the rate/rateType rule below, insert.
- **Update**: same validations as create, scoped to "any row other than this id" for the uniqueness check; rate/rateType rule below is applied against the *effective* (merged) `rateType`/`rate`.
- **Delete**: straightforward delete by id (no downstream references to check).
- **List/Get**: read-only, optional `brandId` and `active` filters on list.

### Rate / rateType rule (create and update)
Apply this immediately before persisting, after all other validations pass:
- effective `rateType == "AUTO"` → set `rate = null` on the entity, discarding whatever was supplied in the request (throw `InvalidCurrencyPairException` → `400` is **not** raised here; the value is simply cleared, never rejected)
- effective `rateType == "MANUAL"` → the effective `rate` (request value, falling back to the existing row's `rate` on update when the request omits it) must be non-null and `> 0`; otherwise throw `InvalidCurrencyPairException("rate is required and must be greater than 0 when rateType is MANUAL")` → `400`
- Because this rule needs the *combination* of `rate` and `rateType`, it cannot be expressed as independent per-field Bean Validation annotations on the DTOs — implement it as an explicit check in `CurrencyPairService.create`/`update`, mirroring how base≠quote is already handled as a cross-field business rule rather than a `@Pattern`/`@NotNull` annotation.

### Required DTO changes for the rate/rateType delta
- `CurrencyPairCreateRequest.rate` (`develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairCreateRequest.java`): remove `@NotNull` — `rate` is no longer unconditionally required at the DTO level (it becomes conditionally required based on `rateType`, enforced in the service layer per the rule above). Keep `@DecimalMin(value = "0.0", inclusive = false)` so that *if* a value is supplied, it's still validated as `> 0` before the service-layer rule runs.
- `CurrencyPairUpdateRequest.rate`: unchanged (already has no `@NotNull`, only `@DecimalMin`) — no DTO change needed here, just the new service-layer rule.
- `CurrencyPairResponse.rate`: type stays `BigDecimal`, now nullable — serializes as JSON `null` for `AUTO` pairs.

### Required changes to the existing Currency feature
- `CurrencyUpdateRequest` (`develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyUpdateRequest.java`): remove the `code` field and its getter/setter entirely. `code` is set only once, at creation, via `CurrencyCreateRequest`.
- `CurrencyService.update` (`develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyService.java`): remove the code-mutation branch (lines handling `request.getCode()`), since the field no longer exists on the request DTO.
- `CurrencyService.delete`: before calling `currencyMapper.deleteById(id)`, call a new `CurrencyPairMapper.existsByCurrencyId(id)` (checks both `base_currency_id` and `quote_currency_id`); if true, throw a new `CurrencyInUseException` mapped to `409`:
```json
{
    "error": "Currency is referenced by one or more currency pairs and cannot be deleted",
    "id": 1
}
```
- `GlobalExceptionHandler`: add a handler for `CurrencyInUseException` → `409`, and for the new `CurrencyPairNotFoundException` / `CurrencyPairExistsException` → `404` / `409`, following the existing pattern used for `CurrencyNotFoundException` / `CurrencyCodeExistsException`.

### Error Handling
- Return `404` with JSON body when a currency pair, or a referenced currency, is not found
- Return `409` with JSON body when the unique (base, quote) constraint is violated, or when deleting a currency still in use
- Return `400` with field-level validation errors (base == quote, rate ≤ 0, invalid rateType, or `rate` missing while effective `rateType` is `MANUAL`)

## Acceptance Criteria
- [x] `GET /api/currency-pairs` returns list of all pairs with brand/base/quote codes populated
- [x] `GET /api/currency-pairs?brandId=3` filters correctly
- [x] `GET /api/currency-pairs?active=true` filters correctly
- [x] `GET /api/currency-pairs/{id}` returns single pair or 404
- [x] `POST /api/currency-pairs` creates and returns 201
- [x] `POST /api/currency-pairs` with base == quote returns 400
- [x] `POST /api/currency-pairs` with nonexistent brand, base, or quote currency id returns 404
- [x] `POST /api/currency-pairs` with duplicate (brand, base, quote) returns 409
- [x] `POST /api/currency-pairs` with the same (base, quote) under a different brand succeeds (no false-positive 409)
- [x] `PUT /api/currency-pairs/{id}` updates and returns 200
- [x] `DELETE /api/currency-pairs/{id}` deletes and returns 204
- [x] `PUT /api/currencies/{id}` no longer accepts/changes `code` (field removed from update DTO)
- [x] `DELETE /api/currencies/{id}` returns 409 when the currency is referenced by a currency pair, and succeeds once no pair references it
- [x] Unit tests for `CurrencyPairService` (positive and negative cases) and updated `CurrencyServiceTest` covering the immutable-code and delete-guard behavior
- [x] Integration tests for `CurrencyPairController` endpoints and the updated currency delete/update endpoints
- [x] `POST /api/currency-pairs` with `rateType: MANUAL` and no `rate` returns 400
- [x] `POST /api/currency-pairs` with `rateType: MANUAL` and `rate: 0` or negative returns 400
- [x] `POST /api/currency-pairs` with `rateType: AUTO` and a `rate` supplied succeeds with 201, and the persisted/returned `rate` is `null`
- [x] `POST /api/currency-pairs` with `rateType: AUTO` and no `rate` succeeds with 201 and `rate: null`
- [x] `PUT /api/currency-pairs/{id}` switching an existing `MANUAL` pair to `rateType: AUTO` clears its `rate` to `null`, even if a `rate` value is also supplied in the same request
- [x] `PUT /api/currency-pairs/{id}` switching an existing `AUTO` pair (rate `null`) to `rateType: MANUAL` without supplying `rate` returns 400
- [x] `PUT /api/currency-pairs/{id}` switching to `MANUAL` while supplying a valid `rate` in the same request succeeds
- [x] `GET /api/currency-pairs` / `GET /api/currency-pairs/{id}` correctly serialize `rate: null` for `AUTO` pairs
- [x] Unit/integration tests updated to cover all of the above rate/rateType branches

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/src/main/java/pl/piomin/services/backend/model/CurrencyPair.java (new — entity, 1:1 with `currency_pair` table, plus `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode` fields populated only by enriched/joined read queries)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairCreateRequest.java (new — `brandId`/`baseCurrencyId`/`quoteCurrencyId`/`rate`/`rateType` required, `active` optional)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairUpdateRequest.java (new — all fields optional, same per-field constraints as create)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairResponse.java (new — includes joined `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode`)
  - develop/backend/src/main/java/pl/piomin/services/backend/mapper/CurrencyPairMapper.java (new — `findAll`, `findById` (enriched via joins to `brand`/`currency`), `findByBrandBaseQuote` (uniqueness check), `insert`, `update`, `deleteById`, `existsByCurrencyId` (used by the currency delete guard), `findAllIds` (test-only cleanup helper))
  - develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml (new — MyBatis SQL mapper; `findAll`/`findById` join `currency_pair` to `brand` and to `currency` twice (aliased `bc`/`qc`) in a single query rather than N+1 lookups, per spec)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairService.java (new — `list`, `getById`, `create`, `update`, `delete`, with brand/currency existence validation, base≠quote validation, and (brand, base, quote) uniqueness validation scoped to "any row other than this id" on update)
  - develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyPairController.java (new — `GET /api/currency-pairs` (optional `brandId`/`active`), `GET /api/currency-pairs/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyPairNotFoundException.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyPairExistsException.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyInUseException.java (new — thrown by `CurrencyService.delete` when the currency is still referenced by a currency pair)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/InvalidCurrencyPairException.java (new — 400 for the "base and quote currency must differ" business rule, which cannot be expressed as a simple per-field Bean Validation annotation)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java (edited — added handlers for `CurrencyInUseException` → 409, `CurrencyPairNotFoundException` → 404, `CurrencyPairExistsException` → 409, `InvalidCurrencyPairException` → 400; reused the existing `BrandNotFoundException` → 404 and `CurrencyNotFoundException` → 404 handlers as-is for the brand/currency-not-found cases raised while creating/updating a currency pair)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyUpdateRequest.java (edited — removed the `code` field and its getter/setter entirely; `code` is now set only once, at creation, via `CurrencyCreateRequest`)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyService.java (edited — constructor now also takes `CurrencyPairMapper`; removed the code-mutation branch from `update` (since the field no longer exists on the request DTO); `delete` now calls `currencyPairMapper.existsByCurrencyId(id)` before `currencyMapper.deleteById(id)` and throws `CurrencyInUseException` (409) if the currency is still referenced by any pair)
  - develop/backend/src/test/resources/schema.sql (edited — added an H2-compatible `currency_pair` table (with the `uk_currency_pair_brand_base_quote` unique constraint) alongside the existing `currency` and `brand` tables; intentionally without FK constraints, to keep each test class's independent `@BeforeEach` table-reset logic free of FK-ordering dependencies across test classes sharing the same H2 in-memory database)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairServiceTest.java (new — 16 unit tests, Mockito, covering list/get/create/update/delete, all validation branches, and both positive and negative cases)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyServiceTest.java (edited — injected a mocked `CurrencyPairMapper`; removed the now-uncompilable `update_throwsConflict_whenNewCodeUsedByAnotherRecord` test (the update DTO no longer has `code`); added `delete_throwsConflict_whenReferencedByCurrencyPair` and stubbed `existsByCurrencyId` in the existing delete test)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairControllerTest.java (new — 17 MockMvc integration tests against H2, covering list/get/create/update/delete, brand-scoped uniqueness (including the "same base/quote under a different brand succeeds" case), and 400/404/409 error paths)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyControllerTest.java (edited — autowired `BrandMapper`/`CurrencyPairMapper`; `@BeforeEach` now also resets `currency_pair` and `brand` tables for full test isolation; added `update_ignoresCodeField_evenWhenSuppliedInRequestBody` and `delete_returns409_whenReferencedByCurrencyPair` (which also verifies the delete succeeds once the referencing pair is removed))
  - develop/backend/pom.xml (edited — version bumped 0.0.2 → 0.0.3 per semantic versioning convention, description updated)
  - develop/backend/README.md (edited — documented the Currency Pair API endpoint table, the two related Currency API behavior changes, and the 0.0.3 version history entry)
- Notes:
  - Implemented full CRUD for `/api/currency-pairs` following the existing Controller → Service → MyBatis Mapper (interface + XML) layering and DTO conventions from the Currency/Brand features. No Lombok used.
  - `rateType` is validated as a plain `String` with `@Pattern(regexp = "^(MANUAL|AUTO)$")` (mirroring the existing `code` pattern-validation style on `CurrencyCreateRequest`) rather than introducing a Java enum, keeping the entity/DTO/MyBatis mapping simple and consistent with the rest of the codebase; no automatic external rate-fetching job was implemented for `AUTO` pairs, per the spec ("future work").
  - `CurrencyPairResponse` is enriched via a single joined MyBatis query (`currency_pair` LEFT... actually INNER JOIN to `brand` and to `currency` twice, aliased `bc`/`qc`) for both `findAll` and `findById`, avoiding N+1 lookups as required. The plain (non-enriched) `findByBrandBaseQuote` query is used only internally for the uniqueness check and does not populate the joined code fields (not needed there).
  - Business-rule validation order in `CurrencyPairService.create`/`update`: brand existence (404) → base currency existence (404) → quote currency existence (404) → base≠quote (400) → (brand, base, quote) uniqueness (409), matching the precedence implied by the spec's acceptance criteria. On update, only fields actually present in the request are re-validated for existence, but the merged (existing-or-new) triple is always re-checked for distinctness and uniqueness.
  - Reused the existing `BrandNotFoundException` and `CurrencyNotFoundException` (and their existing `GlobalExceptionHandler` mappings) as-is for the currency-pair 404 cases, since their error-body shape (`{"error": "Brand not found", "id": ...}` / `{"error": "Currency not found", "id": ...}`) already matches the spec's contract exactly — no need for new brand/currency-specific currency-pair exception types.
  - Currency `code` immutability: removed `code` from `CurrencyUpdateRequest` entirely (field + getter/setter). Since Spring's default Jackson config ignores unknown JSON properties, a client still sending `"code": "..."` in a `PUT /api/currencies/{id}` body is silently ignored rather than rejected — verified this behavior explicitly with `update_ignoresCodeField_evenWhenSuppliedInRequestBody`.
  - Currency delete guard: `CurrencyService.delete` now checks `currencyPairMapper.existsByCurrencyId(id)` (which checks both `base_currency_id` and `quote_currency_id` via `OR`) before deleting, throwing `CurrencyInUseException` (409) if in use. This is an application-level pre-check that returns a friendly, structured error; the DB-level `ON DELETE RESTRICT` FK from `specs/dba/currency-pair.md` remains as a defense-in-depth backstop in production (not present in the H2 test schema, by design — see below).
  - Test isolation across the three MockMvc controller test classes (`CurrencyControllerTest`, `BrandControllerTest`, `CurrencyPairControllerTest`), which share one H2 in-memory database instance (`DB_CLOSE_DELAY=-1`) for the lifetime of the test JVM: deliberately did **not** add FK constraints between `currency_pair` and `brand`/`currency` in `src/test/resources/schema.sql` (unlike the production migration), because each test class's own `@BeforeEach` independently wipes and re-seeds its own required tables, and cross-class FK enforcement would create ordering dependencies between otherwise-independent test classes. Instead, `CurrencyPairControllerTest` and (now) `CurrencyControllerTest` each reset `currency_pair`, `brand`, and `currency` at the start of every test to guarantee full isolation regardless of execution order. This was verified empirically: an initial implementation (before adding brand-table cleanup to `CurrencyControllerTest`) passed under the default (deterministic) Maven Surefire test order but failed intermittently (`DuplicateKeyException` on brand code `AU`) when run with `-Dsurefire.runOrder=random`; after adding the missing cleanup, 4 additional consecutive `-Dsurefire.runOrder=random` runs all passed (75/75 tests each), in addition to the standard `mvn test` run.
  - Ran `mvn -f develop/backend/pom.xml clean compile` (BUILD SUCCESS) and `mvn -f develop/backend/pom.xml clean test` (BUILD SUCCESS, 75 tests total: 12 CurrencyServiceTest + 14 CurrencyControllerTest + 7 BrandServiceTest + 9 BrandControllerTest + 16 CurrencyPairServiceTest + 17 CurrencyPairControllerTest, 0 failures/errors), plus 4 additional randomized-order runs (`-Dsurefire.runOrder=random`), all green. No changes were needed to `.circleci/config.yml` — the existing `build-and-test` job already runs `mvn test` against the whole backend module.
  - Bumped the Maven project version to `0.0.3` (PATCH bump per project convention) and updated `README.md` with the Currency Pair API table, the two Currency API behavior changes, and the version history entry.

### Increment 1 — 2026-07-27
- Status: DONE
- Files changed:
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairCreateRequest.java (edited — removed `@NotNull` from `rate` field; `rate` is now conditionally required based on `rateType`, enforced in service layer)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairService.java (edited — added `applyRateTypeRule` private method that enforces: AUTO → force rate = null (silently discarding any supplied value); MANUAL → rate must be non-null and > 0, else throw InvalidCurrencyPairException. This rule is applied immediately before persisting in both `create` and `update`, after all other validations pass)
  - develop/backend/src/test/resources/schema.sql (edited — changed `currency_pair.rate` from `NOT NULL` to `NULL` in the H2 test schema, aligning with production DB migration V004 which made rate nullable with a CHECK constraint)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairServiceTest.java (edited — added 9 new unit tests covering the rate/rateType rule: MANUAL w/o rate, MANUAL w/ rate zero, MANUAL w/ rate negative, AUTO w/ rate supplied, AUTO w/o rate, update MANUAL→AUTO clears rate, update MANUAL→AUTO clears rate even if rate supplied, update AUTO→MANUAL w/o rate fails, update AUTO→MANUAL w/ valid rate succeeds)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairControllerTest.java (edited — added 10 new integration tests covering the same scenarios as unit tests plus GET serialization of rate null for AUTO pairs)
  - develop/backend/pom.xml (edited — version bumped 0.0.3 → 0.0.4)
  - develop/backend/README.md (edited — version bumped to 0.0.4, added version history entry)
- Notes:
  - Implemented the "Delta: rate required for MANUAL, cleared for AUTO" behavior per the spec's "Rate / rateType rule" section and "Required DTO changes" section.
  - `CurrencyPairCreateRequest.rate` DTO field validation: removed `@NotNull`, kept `@DecimalMin(value = "0.0", inclusive = false)` per spec instruction. This means zero/negative values supplied in the request body are caught at the bean validation layer (returning `{"error": "Validation failed", "details": {"rate": "rate must be greater than 0"}}`) before the service-layer rule runs — the integration tests for zero/negative cases verify this layered behavior.
  - Service-layer `applyRateTypeRule` method resolves the effective `rateType` (request value, or existing row's value on update) and effective `rate` (request value, or existing row's value on update when the request omits it), then applies: AUTO → `pair.setRate(null)` (silent discard of any supplied value, no exception); MANUAL → if effective rate is null or ≤ 0, throw `InvalidCurrencyPairException("rate is required and must be greater than 0 when rateType is MANUAL")` → 400. This logic is invoked immediately before `currencyPairMapper.insert`/`update` in both `create` and `update` methods, after all other validations (brand/currency existence, base≠quote, uniqueness) pass.
  - H2 test schema (`src/test/resources/schema.sql`) now has `rate DECIMAL(18,8) NULL`, matching production. The DBA V004 migration's CHECK constraint `ck_currency_pair_rate_valid` enforcing (MANUAL→rate NOT NULL AND >0) OR (AUTO→rate IS NULL) was applied to the live MySQL db `wdd` prior to this increment; the H2 test schema does not replicate the CHECK (H2's CHECK syntax support is limited and test-only validation is already handled by the application layer), but the nullability change is critical for test data insertion and was applied.
  - All 9 unchecked acceptance-criteria items from the delta are now checked and verified: `POST` MANUAL w/o rate → 400; `POST` MANUAL w/ rate ≤ 0 → 400 (bean validation layer); `POST` AUTO w/ rate → 201 rate null; `POST` AUTO w/o rate → 201 rate null; `PUT` MANUAL→AUTO clears rate; `PUT` AUTO→MANUAL w/o rate → 400; `PUT` AUTO→MANUAL w/ rate → success; `GET` serializes rate null for AUTO.
  - Ran `mvn -f develop/backend/pom.xml clean test` — BUILD SUCCESS, 94 tests (12 CurrencyServiceTest + 14 CurrencyControllerTest + 7 BrandServiceTest + 9 BrandControllerTest + 25 CurrencyPairServiceTest + 27 CurrencyPairControllerTest), 0 failures/errors. Test count increased from 75 → 94 (+19 new tests: 9 unit + 10 integration for the rate/rateType rule branches).
  - `.circleci/config.yml` already covers this via the existing `mvn -f develop/backend/pom.xml -B test` step — no change needed.
