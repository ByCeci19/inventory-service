# Research: Reserve Stock for Order

**Feature**: `001-reserve-stock-order` | **Date**: 2026-10-14

All Technical Context unknowns resolved — no `NEEDS CLARIFICATION` remains. Each item: Decision / Rationale / Alternatives considered.

## R-1: Spring Boot line for the build (3.x vs pinned 4.1.1)

- **Decision**: Align `build.gradle` to Spring Boot 3.x (latest 3.x patch) with the matching Spring Cloud BOM (2024.x line) for `spring-cloud-stream` + `binder-kafka` 4.x; amend the file during implementation. Keep Gradle wrapper 9.8.1.
- **Rationale**: Constitution, spec, and clarifications all normatively say Boot 3.x; Spring Cloud Stream 4.3.x (verified latest, Jun 2026) is the proven Boot-3-compatible binder line, and springdoc 2.x ↔ Boot 3 is a documented matrix. The 4.1.1 pin is an Initializr scaffold default, not a deliberate choice; no code depends on Boot 4 APIs (repo is one `@SpringBootApplication` + one context test). Constitution therefore needs no amendment on this point.
- **Alternatives considered**: (a) Keep Boot 4.1.1 + adopt SCS 5.x-equivalent — rejected: Boot-4-compatible SCS line unverified at plan time; risks an unbuildable dependency set. (b) Keep Boot 4.1.1 and use `spring-kafka` directly without SCS — rejected: violates constitution Principle IV (Kafka via Spring Cloud Stream) and loses binder-managed retry/DLQ.
- **Follow-up**: constitution says "Gradle 8.x" but wrapper pins 9.8.1, which builds Boot 3 + Java 21 fine — file a constitution PATCH (`Gradle 8.x+ / wrapper-pinned`) separately.

## R-2: Intake shape — REST primary + Kafka consumer on the same use case

- **Decision**: Primary intake is synchronous `POST /api/v1/reservations` returning the stored outcome (200, reserved or failed — failure is a business outcome, not an HTTP error); a Kafka consumer on `inventory.reservation.requests` invokes the same `ReserveStockUseCase`, giving the event-driven path identical idempotency semantics.
- **Rationale**: Satisfies constitution VI (REST/OpenAPI testable by Karate) and IV (event-driven/AsyncAPI) simultaneously; one service method means AC-004 and AC-005 are the same guarantee tested twice, not two implementations.
- **Alternatives considered**: (a) Kafka-only intake — rejected: leaves no Karate-testable REST surface and contradicts the OpenAPI principle. (b) Separate code paths per intake — rejected: duplicates invariant/idempotency logic, doubling defect surface.

## R-3: Idempotency mechanism (lookup-first + unique backstop)

- **Decision**: `ReserveStockService` runs in a single transaction: `findByOrderId` → if present return stored outcome verbatim; else evaluate, mutate, insert with UNIQUE constraint on `reservation.order_id`; on `DataIntegrityViolationException` (concurrent duplicate insert) roll back, re-read, return stored outcome. Event publish follows commit (`AFTER_COMMIT` transactional listener); redelivered publishes are safe because consumers are idempotent on `orderId`.
- **Rationale**: Lookup-first covers retries/redelivery (AC-004/AC-005); the unique constraint covers the race window between concurrent duplicates that SELECT-then-INSERT alone cannot close (spec edge case: two orders, last units — plus same-orderId races). No distributed lock needed at v1 scale.
- **Alternatives considered**: (a) SELECT-then-INSERT without constraint — rejected: race window under concurrency. (b) Pessimistic locking on stock rows for all requests — rejected: heavier contention, unnecessary at tens-of-rps baseline. (c) Transactional outbox table + relay — rejected for v1 as over-engineering; `AFTER_COMMIT` + idempotent consumers meets at-least-once needs; revisit if outcome loss is ever observed.

## R-4: Retry + DLQ via SCS Kafka binder (clarified: 3 retries, exp backoff)

- **Decision**: Consumer binding `maxAttempts=4` (initial + 3 retries), `backOffInitialInterval`/`backOffMultiplier`/`backOffMaxInterval` exponential schedule, `enableDlq=true` with dedicated DLQ topic, `orderId` propagated as record header and preserved onto the DLQ record; `DeadLetterRecord` keeps failure reason + retry history for operators.
- **Rationale**: Binder-native retry/DLQ is the Spring Cloud Stream idiom (no hand-rolled retry loops), directly implements clarified FR-006, and keeps poison messages out of the main flow without silent drops.
- **Alternatives considered**: (a) Spring Retry annotation in consumer code — rejected: duplicates binder capability, harder to observe/configure. (b) Unbounded retry — rejected: violates FR-006 and risks wedged partitions.

## R-5: No-Docker test stack (H2 + EmbeddedKafka + mocked Mongo port + Karate)

- **Decision**: JPA slice/integration tests on H2 (default mode; JPQL only, zero native queries so PostgreSQL parity holds); messaging tests on EmbeddedKafka from the already-declared `spring-kafka-test`; Mongo audit port mocked with Mockito in service tests plus a thin adapter unit test against mocked `MongoTemplate` (no Flapdoodle/embedded-Mongo dependency); Karate JUnit 5 suite against `@SpringBootTest(webEnvironment=RANDOM_PORT)` with the Kafka binder pointed at EmbeddedKafka.
- **Rationale**: Meets constitution V with the smallest version-sensitive dependency footprint: H2 and EmbeddedKafka are version-agnostic; skipping Flapdoodle avoids the Boot-3/Boot-4 starter-matrix risk entirely while the constitution explicitly permits mocks for Mongo. Karate on `RANDOM_PORT` guards the OpenAPI shape.
- **Alternatives considered**: (a) Flapdoodle embedded Mongo — rejected: version-matrix risk for zero v1 benefit (audit port is a thin store/load). (b) Testcontainers — rejected: violates constitution V outright. (c) H2 `MODE=PostgreSQL` — rejected unless a native query forces it; default mode + JPQL is sufficient and less brittle.

## R-6: Contracts tooling (springdoc + hand-authored AsyncAPI)

- **Decision**: REST contract from code via springdoc-openapi 2.x served at `/v3/api-docs` and checked into `contracts/openapi.yaml` as the reviewed snapshot (Karate asserts the shape); AsyncAPI 3.0 document hand-authored in `contracts/asyncapi.yaml` as the normative event contract (codegen deferred).
- **Rationale**: springdoc 2.x ↔ Boot 3 matrix is documented (v3.x targets Boot 4); snapshot-checking the generated OpenAPI makes contract drift a failing test. AsyncAPI has no equally frictionless Boot generator, so the hand-authored file is normative and reviewed like code.
- **Alternatives considered**: (a) Hand-write both contracts — rejected: OpenAPI would drift from the controller. (b) AsyncAPI codegen plugin — rejected for v1: plugin/version churn exceeds the value for two messages.

## R-7: Audit durability model

- **Decision**: `AuditStore.save(outcome)` is called in the same application flow after the reservation record commits (best-effort ordering: reservation truth first, audit second); audit documents are immutable, keyed by `orderId`, and include `eventVersion`, decision, items/missing SKUs, and timestamp — directly satisfying clarified FR-008/SC-005 traceability.
- **Rationale**: Reservation correctness never depends on the audit write (spec assumption), but every outcome is still queryable by `orderId` for operators and tests.
- **Alternatives considered**: (a) Same-transaction dual-write JPA+Mongo — rejected: no XA; couples availability of the two stores. (b) Audit from consumed outcome events — deferred: valid evolution, unnecessary for v1.
