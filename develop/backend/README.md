# backend

Version: 0.0.10

Spring Boot REST API for currency, brand, currency pair and spread management (`pl.piomin.services`).

## Stack
- Java 17, Spring Boot 3.5.16, Maven
- MyBatis (mapper interface + XML) over MySQL 8
- Bean Validation (jakarta.validation)
- Tests: JUnit 5, Mockito, MockMvc against an in-memory H2 database

## Run

```
mvn -f develop/backend/pom.xml spring-boot:run
```

Requires MySQL reachable at `127.0.0.1:3306`, database `wdd`, user `app` (see `env.md`).
The `currency`, `brand` and `currency_pair` tables must already exist (see `specs/dba/currency.md`, `specs/dba/brand.md`, `specs/dba/currency-pair.md`).

## Build & Test

```
mvn -f develop/backend/pom.xml compile
mvn -f develop/backend/pom.xml test
```

## API

Base path: `/api/currencies`

| Method | Path                | Description                        |
|--------|---------------------|-------------------------------------|
| GET    | /api/currencies      | List all currencies                 |
| GET    | /api/currencies/{id} | Get one currency                    |
| POST   | /api/currencies      | Create a currency                   |
| PUT    | /api/currencies/{id} | Partially update a currency         |
| DELETE | /api/currencies/{id} | Delete a currency                   |

See `specs/backend/currency.md` for the full contract, validation rules, and error responses.

Base path: `/api/brands`

| Method | Path              | Description                              |
|--------|-------------------|--------------------------------------------|
| GET    | /api/brands        | List brands (optional `?active=`)          |
| GET    | /api/brands/{id}   | Get one brand                              |
| PUT    | /api/brands/{id}   | Toggle a brand's `active` flag              |

Brands are a fixed, seeded set (AU, MONETA, PUG, STAR, UM, VJP, VT) — there is no create or delete endpoint. See `specs/backend/brand.md` for the full contract, validation rules, and error responses.

Base path: `/api/currency-pairs`

| Method | Path                     | Description                                    |
|--------|--------------------------|--------------------------------------------------|
| GET    | /api/currency-pairs       | List currency pairs (optional `?brandId=`, `?active=`) |
| GET    | /api/currency-pairs/{id}  | Get one currency pair                             |
| PUT    | /api/currency-pairs/{id}  | Partially update a currency pair                  |
| DELETE | /api/currency-pairs/{id}  | Delete a currency pair                            |

Each currency pair belongs to exactly one brand and has a `rate`/`rateType` (`MANUAL` or `AUTO`); (brand, base, quote) must be unique, and base/quote currencies must differ. **There is no `POST /api/currency-pairs`** — a brand's `currency_pair` row can only come into existence via `/api/currency-pair-definitions`' per-brand fan-out (a global definition must exist first). See `specs/backend/currency-pair.md` for the full contract, validation rules, and error responses.

Two related changes ship alongside this feature:
- `PUT /api/currencies/{id}` no longer accepts/changes `code` — a currency's code is immutable once created.
- `DELETE /api/currencies/{id}` now returns `409` if the currency is still referenced (as base or quote) by any currency pair.

### Audit / approval workflow

**`PUT`/`DELETE /api/currency-pairs...` no longer mutate `currency_pair` directly** (there is no `POST` at all — see above). Each now submits a `PENDING` change request through the generic audit module and returns `202 Accepted` with an `AuditRequestResponse` (`before`/`after` snapshots, `status`). The change only lands once approved. `CurrencyPairAuditHandler` handles `UPDATE`/`DELETE` only. See `specs/backend/currency-pair-approval.md` and `specs/backend/audit.md`.

Base path: `/api/audit-requests` (generic — works for any registered `entityType`, e.g. `CURRENCY_PAIR`)

| Method | Path                          | Description                                             |
|--------|-------------------------------|-----------------------------------------------------------|
| GET    | /api/audit-requests            | List requests (optional `?entityType=`, `?status=`, `?actionType=`) |
| GET    | /api/audit-requests/{id}       | Get one request                                            |
| POST   | /api/audit-requests/{id}/approve | Approve a `PENDING` request (re-validates, then applies)  |
| POST   | /api/audit-requests/{id}/reject  | Reject a `PENDING` request (requires `rejectReason`)      |

`CurrencyPairAuditHandler` plugs `currency_pair` into this module as `entityType = "CURRENCY_PAIR"`, reusing `CurrencyPairService`/`CurrencyPairValidator` for validation and for actually applying an approved change.

Base path: `/api/spread-defaults`

| Method | Path                       | Description                                       |
|--------|----------------------------|-----------------------------------------------------|
| GET    | /api/spread-defaults        | List default spreads (optional `?brandId=`)         |
| GET    | /api/spread-defaults/{id}   | Get one default spread                               |
| PUT    | /api/spread-defaults/{id}   | Submit a `PENDING` update request (`202`)            |

One `spread_default` row exists per brand from the moment that brand is seeded (`specs/dba/spread.md`) — there is no create/delete endpoint. `SpreadDefaultAuditHandler` plugs `spread_default` updates into the audit module as `entityType = "SPREAD_DEFAULT"`.

Base path: `/api/spread-groups`

| Method | Path                                    | Description                                                    |
|--------|------------------------------------------|-------------------------------------------------------------------|
| GET    | /api/spread-groups                        | List custom spread groups with members (optional `?brandId=`)     |
| GET    | /api/spread-groups/{id}                   | Get one spread group with members                                  |
| POST   | /api/spread-groups                        | Submit a `PENDING` create request (`202`)                          |
| PUT    | /api/spread-groups/{id}                   | Submit a `PENDING` update request (`202`)                          |
| DELETE | /api/spread-groups/{id}                   | Submit a `PENDING` delete request (`202`)                          |
| GET    | /api/spread-groups/resolve/{currencyPairId} | Resolve the effective spread for a pair (live data, unaffected by pending requests) |

A currency pair belongs to at most one spread group at a time; assigning it to a different group in a create/update proposal moves it there once approved. Unassigned pairs fall back to their brand's default spread. `SpreadGroupAuditHandler` plugs `spread_group`/`spread_group_member` create/update/delete into the audit module as `entityType = "SPREAD_GROUP"`, reusing `SpreadGroupService`/`SpreadGroupValidator`. See `specs/backend/spread.md` for the full contract, validation rules, and error responses.

Base path: `/api/currency-pair-definitions`

| Method | Path                                 | Description                                                     |
|--------|---------------------------------------|---------------------------------------------------------------------|
| GET    | /api/currency-pair-definitions         | List definitions (optional `?baseCurrencyId=`, `?quoteCurrencyId=`) |
| GET    | /api/currency-pair-definitions/{id}    | Get one definition                                                   |
| POST   | /api/currency-pair-definitions         | Create a definition — applies immediately (`201`)                   |
| PUT    | /api/currency-pair-definitions/{id}    | Update `forwardPrecision`/`reversePrecision` — applies immediately (`200`) |
| DELETE | /api/currency-pair-definitions/{id}    | Delete a definition — applies immediately (`204`)                   |

A brand-agnostic currency pair master: creating a definition for a (base, quote) direction provisions an `AUTO`/`rate=null`/`active=true` `currency_pair` row for every brand that doesn't already have a live row for that exact triple (existing brand rows are never touched). If a definition already exists for either this exact direction or its reverse, creation is rejected with `409` (backed by a DB-level unique constraint on `specs/dba/currency-pair-definition.md`'s generated `pair_key_low`/`pair_key_high` columns). `baseCurrencyId`/`quoteCurrencyId` are immutable after creation — only precision is editable. Deleting a definition removes only the definition row; previously-provisioned `currency_pair` rows are left untouched and independently editable via the existing `/api/currency-pairs` API/audit flow. **Unlike `/api/currency-pairs`/`/api/spread-groups`, this feature does not go through the audit-approval workflow — mutations apply immediately.** See `specs/backend/currency-pair-definition.md` for the full contract, validation rules, and error responses.

**Delete guard**: `DELETE /api/currency-pair-definitions/{id}` first checks every `currency_pair` row for that (base, quote) direction across all brands; if any brand still has an `active = true` row, deletion is rejected with `409` and `activeBrandCodes` listing every brand that still needs to be disabled first (via the existing per-brand `currency_pair` edit/audit flow). Zero rows for that pair (e.g. all brands' rows were independently deleted) never blocks deletion — only a live active row does.

## Version History
- 0.0.1 — Initial Currency CRUD API (list/get/create/update/delete), validation, error handling, unit + integration tests.
- 0.0.2 — Added Brand API (list/get/toggle-active), reusing the `brand` table seeded by `specs/dba/brand.md`; unit + integration tests.
- 0.0.3 — Added Currency Pair CRUD API (`/api/currency-pairs`), scoped per brand with brand/currency joins for enriched responses; made currency `code` immutable on update; currency delete now blocked (`409`) while referenced by a currency pair; unit + integration tests.
- 0.0.4 — Currency Pair rate/rateType rule: `MANUAL` requires rate (non-null, >0); `AUTO` forces rate to null (ignores supplied values). Enforced at service layer on create/update; comprehensive unit + integration tests covering all branches.
- 0.0.5 — Generic audit/approval module (`/api/audit-requests`, `AuditHandler`/`AuditService`/`AuditController`) and `CurrencyPairAuditHandler` plugging `currency_pair` into it: `POST`/`PUT`/`DELETE /api/currency-pairs...` now submit a `PENDING` change request (`202`) instead of mutating directly; a change only lands once approved. Validation logic shared between `CurrencyPairService` and `CurrencyPairAuditHandler` via new `CurrencyPairValidator`. Unit + integration tests covering submit/validate/apply/approve/reject and re-validation-at-approval branches.
- 0.0.6 — Spread (點差) API: `/api/spread-defaults` (one row per brand, read + audited update, no create/delete) and `/api/spread-groups` (full CRUD, all mutations audited) over the new `spread_default`/`spread_group`/`spread_group_member` tables (`specs/dba/spread.md`). A currency pair belongs to at most one spread group; unassigned pairs use their brand's default spread; `GET /api/spread-groups/resolve/{currencyPairId}` resolves the effective spread from live, already-approved data. `SpreadDefaultAuditHandler`/`SpreadGroupAuditHandler` plug both concepts into the audit module as `entityType = "SPREAD_DEFAULT"`/`"SPREAD_GROUP"`, reusing new `SpreadDefaultService`/`SpreadGroupService`/`SpreadGroupValidator`. Unit + integration tests covering submit/validate/apply/approve/reject, membership move/detach semantics, and re-validation-at-approval branches.
- 0.0.7 — Currency Pair Definition (Global Master) API: `/api/currency-pair-definitions` full CRUD over the new `currency_pair_definition` table (`specs/dba/currency-pair-definition.md`). Creating a definition for a (base, quote) direction fans out an `AUTO`/`rate=null`/`active=true` `currency_pair` row to every brand missing a live row for that exact triple, by calling the existing, unmodified `CurrencyPairService.create` directly — skipping brands that already have a row. Rejects the reverse direction or an exact duplicate with `409`. Applies immediately — explicitly does **not** go through the audit-approval workflow, unlike `currency-pair`/`spread`. Deleting a definition never touches previously-provisioned `currency_pair` rows and frees up its reverse direction for future use. Unit + integration tests covering create/list/get/update/delete, fan-out (including the "skip brand with existing row" case), and all validation/conflict branches.
- 0.0.8 — Removed `POST /api/currency-pairs` entirely, per `specs/backend/currency-pair.md`/`specs/backend/currency-pair-approval.md`'s "no CREATE" delta: a brand's `currency_pair` row can now only ever come into existence via `/api/currency-pair-definitions`' fan-out. `CurrencyPairController` no longer has a `create` method/route (Spring's default 405 applies). `CurrencyPairAuditHandler` no longer has a `CREATE` case in `validate`/`apply` (`apply(CREATE, ...)` now throws `UnsupportedOperationException`); removed the now-dead `DuplicatePendingCurrencyPairCreateException` and its `GlobalExceptionHandler` mapping. `CurrencyPairService.create`/`CurrencyPairCreateRequest` are unchanged and still used internally by `CurrencyPairDefinitionService`'s fan-out. Removed/repurposed the CREATE-specific tests in `CurrencyPairControllerTest`/`CurrencyPairAuditHandlerTest`; `CurrencyPairDefinitionServiceTest`/`CurrencyPairDefinitionControllerTest` pass unmodified, proving the fan-out path still works.
- 0.0.9 — `DELETE /api/currency-pair-definitions/{id}` now guards against deleting a definition while any brand's `currency_pair` row for that direction is still `active = true`: rejected with `409` and `activeBrandCodes` listing every still-active brand, deleting nothing. Backed by a new, purely-additive `CurrencyPairMapper.findActiveByBaseQuote` read method and a new `CurrencyPairDefinitionInUseException` mapped in `GlobalExceptionHandler`. A pair with zero rows (or all rows inactive) for that direction still deletes normally. No change to any existing `CurrencyPairMapper`/`CurrencyPairService`/`CurrencyPairController` behavior. Unit + integration tests covering blocked-with-one/multiple-active-brands, allowed-once-all-inactive, and allowed-with-zero-rows.
- 0.0.10 — Removed the `active` enable/disable concept from the `Currency` feature entirely, per `specs/backend/currency.md`'s delta (the `currency.active` column was already dropped from the live schema by migration `V010`). `Currency`/`CurrencyCreateRequest`/`CurrencyUpdateRequest`/`CurrencyResponse` no longer have an `active` field; `GET /api/currencies` no longer accepts an `?active=` filter (always returns the full list); `CurrencyMapper`/`CurrencyMapper.xml` `findAll` takes no parameters and `insert`/`update` no longer reference the `active` column; `src/test/resources/schema.sql`'s H2 `currency` table definition no longer has an `active` column. `Brand.active`/`CurrencyPair.active` are unrelated and untouched. Removed/updated the now-obsolete `active`-filtering tests in `CurrencyServiceTest`/`CurrencyControllerTest`; fixed compile-only call sites in other test files (`CurrencyPairControllerTest`, `CurrencyPairDefinitionControllerTest`, `SpreadControllerTest`, `CurrencyPairAuditHandlerTest`, `CurrencyPairServiceTest`) that constructed a `Currency` via `setActive`/`findAll(null)` for unrelated test fixtures.
