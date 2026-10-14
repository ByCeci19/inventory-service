# Tasks: Reserve Stock for Order

**Input**: Design documents from `/specs/001-reserve-stock-order/` (plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md)

**Prerequisites**: plan.md (required) ✅, spec.md (required) ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: INCLUDED — explicitly required by FR-010 ("Each acceptance criterion AC-001..AC-006 MUST be covered by at least one automated test") and SC-006. TDD: write tests first, verify FAIL, then implement.

**Organization**: Grouped by user story (US1–US4, priorities from spec.md). Base package `com.example.inventory_service` → `src/main/java/com/example/inventory_service/...`, tests under `src/test/java/...`.

**Remediation note (v2)**: Addresses `/speckit.analyze` findings E1–E4 (HIGH/MEDIUM coverage), F1 (parallel-marker conflict), C1 (DDL strategy), F2/F3/B1/B2 (wording). New tasks T019, T020, T027, T031, T015; T030 drops invalid `[P]`; T032 reworded; T001/T003/T011 clarified.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable (different files, no dependencies)
- **[Story]**: US-label for user-story phase tasks only
- Exact file paths in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Build alignment (R-1), package skeleton, messaging/persistence config, contract snapshots

- [X] T001 Align build.gradle to Spring Boot 3.x (pin exact minor at implement time and record it in specs/001-reserve-stock-order/plan.md Technical Context) + Spring Cloud BOM + spring-cloud-stream binder-kafka 4.x + springdoc-openapi 2.x + flyway-core + test deps (H2, karate-junit5) in build.gradle
- [ ] T002 [P] Create DDD package skeleton domain/application/infrastructure under src/main/java/com/example/inventory_service/
- [ ] T003 [P] Consolidate configuration into a single src/main/resources/application.yml (retire src/main/resources/application.properties to avoid dual sources): SCS bindings (maxAttempts=4, exponential backOff intervals, enableDlq, DLQ topic inventory.reservation.requests.dlq per contracts/asyncapi.yaml), datasource profiles, data-mongo, flyway (validate in prod, disabled in tests where H2 + JPA create-drop applies)
- [ ] T004 [P] Snapshot reviewed contracts to src/main/resources/contracts/openapi.yaml and src/main/resources/contracts/asyncapi.yaml
- [ ] T005 [P] Configure orderId-correlated logging (MDC %X{orderId} in pattern) in src/main/resources/logback-spring.xml
- [ ] T006 Verify baseline green with ./gradlew test (existing contextLoads must pass before further work)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain core, ports, persistence + validation infrastructure every story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T007 [P] Create StockItem domain class enforcing `availableQty >= 0` (verbatim invariant) in src/main/java/com/example/inventory_service/domain/StockItem.java
- [ ] T008 [P] Create value objects ReservationItem (`quantity minimum: 1`), MissingSku (`reason enum INSUFFICIENT_STOCK, UNKNOWN_SKU, INVALID_QUANTITY, INVALID_REQUEST`), ReservationOutcome (`eventVersion const: v1`, `decidedAt format: date-time`) in src/main/java/com/example/inventory_service/domain/
- [ ] T009 Create Reservation aggregate (all-or-nothing evaluate, insert-once immutable outcome, `orderId maxLength: 64`) in src/main/java/com/example/inventory_service/domain/Reservation.java (depends on T007, T008)
- [ ] T010 [P] Create ports ReserveStockUseCase, InventoryPort, OutcomePublisher, AuditStore in src/main/java/com/example/inventory_service/application/port/
- [ ] T011 [P] Create JPA entities + repositories (ReservationEntity with UNIQUE `orderId` indexed, StockItemEntity; JPQL only, no native queries; DDL owns the UNIQUE constraint — conflict re-read handling belongs to T032) in src/main/java/com/example/inventory_service/infrastructure/persistence/jpa/
- [ ] T012 Create InventoryPort JPA adapter (load stock snapshot, save decrements + reservation row in one transaction) in src/main/java/com/example/inventory_service/infrastructure/persistence/jpa/JpaInventoryAdapter.java (depends on T010, T011)
- [ ] T013 Create test fixtures (seeded SKU-A x10, SKU-B x5 builders) in src/test/java/com/example/inventory_service/fixtures/InventoryFixtures.java
- [ ] T014 [P] Create validation/exception wiring (empty items and non-positive qty map to FAILED outcome with INVALID_REQUEST, no stock change) in src/main/java/com/example/inventory_service/infrastructure/web/ReservationExceptionHandler.java
- [ ] T015 Create Flyway V1 baseline migration (stock_item, reservation + lines/missing tables incl. UNIQUE constraint on reservation.order_id; prod profile validates/migrates, tests stay on H2 create-drop) in src/main/resources/db/migration/V1__baseline.sql (depends on T011 table design)

**Checkpoint**: Foundation ready — domain, ports, JPA adapter, migration, fixtures compile; user stories can now proceed

---

## Phase 3: User Story 1 — Reserve stock when available (Priority: P1) 🎯 MVP

**Goal**: Happy path — sufficient stock decrements, reservation recorded, InventoryReserved v1 published to the decisions topic + audited

**Independent Test**: POST order-1 (3x SKU-A, 2x SKU-B) → 200 RESERVED, stock 7/3, one reservation row, one audit doc, one InventoryReserved event consumed from `inventory.reservation.decisions` (QS-1)

### Tests for User Story 1 ⚠️ (write FIRST, verify FAIL)

- [ ] T016 [P] [US1] Unit test aggregate happy-path decrement and single reservation record (AC-001) in src/test/java/com/example/inventory_service/unit/ReservationTest.java
- [ ] T017 [P] [US1] Service integration test on H2 for success path incl. audit write with mocked AuditStore (AC-001) in src/test/java/com/example/inventory_service/integration/ReserveStockServiceTest.java
- [ ] T018 [P] [US1] Karate happy-path contract test asserting contracts/openapi.yaml shape incl. `eventVersion: v1`, items, timestamp (AC-001, AC-002) in src/test/resources/karate/reserve-success.feature
- [ ] T019 [P] [US1] EmbeddedKafka publication test — POST via REST, consume inventory.reservation.decisions, assert InventoryReserved payload (`eventVersion: v1`, orderId, items, decidedAt) per contracts/asyncapi.yaml (AC-002) in src/test/java/com/example/inventory_service/integration/ReservationOutcomePublishTest.java
- [ ] T020 [P] [US1] MongoAuditStore unit test with mocked MongoTemplate — immutable doc per orderId, indexed orderId, `eventVersion v1` (FR-008) in src/test/java/com/example/inventory_service/unit/MongoAuditStoreTest.java

### Implementation for User Story 1

- [ ] T021 [US1] Implement ReserveStockService success branch (lookup-miss → evaluate → decrement → record RESERVED → AFTER_COMMIT publish+audit) in src/main/java/com/example/inventory_service/application/ReserveStockService.java (depends on T016–T020 failing first)
- [ ] T022 [P] [US1] Implement OutcomePublisher SCS adapter publishing InventoryReserved per contracts/asyncapi.yaml in src/main/java/com/example/inventory_service/infrastructure/messaging/kafka/KafkaOutcomePublisher.java
- [ ] T023 [P] [US1] Implement AuditStore Mongo adapter (immutable doc per orderId, indexed orderId, `eventVersion v1`) in src/main/java/com/example/inventory_service/infrastructure/persistence/mongo/MongoAuditStore.java
- [ ] T024 [US1] Implement POST /api/v1/reservations controller per contracts/openapi.yaml (200 returns stored outcome; orderId into MDC) in src/main/java/com/example/inventory_service/infrastructure/web/ReservationController.java (depends on T021)

**Checkpoint**: US1 fully functional and independently testable — QS-1 passes, `./gradlew test` green

---

## Phase 4: User Story 2 — Reject when stock insufficient (Priority: P1)

**Goal**: All-or-nothing rejection — zero stock mutation, FAILED outcome naming every missing SKU; invalid shapes rejected without a reservation row

**Independent Test**: POST order-2 (999x SKU-A) → 200 FAILED with `missingSkus=[SKU-A/INSUFFICIENT_STOCK]`, stock unchanged (QS-2); multi-item partial-shortage mutates nothing; empty-items/zero-qty → FAILED INVALID_REQUEST with no row

### Tests for User Story 2 ⚠️ (write FIRST, verify FAIL)

- [ ] T025 [P] [US2] Unit test all-or-nothing rejection incl. unknown-SKU-as-missing and no-mutation assertion (AC-003) in src/test/java/com/example/inventory_service/unit/ReservationRejectionTest.java
- [ ] T026 [P] [US2] Karate rejection contract test incl. multi-item partial shortage (AC-003) in src/test/resources/karate/reserve-failure.feature
- [ ] T027 [P] [US2] Invalid-request tests — empty item list and zero/negative quantity yield FAILED with INVALID_REQUEST, no stock change, no reservation row (FR-007) in src/test/java/com/example/inventory_service/unit/ReservationValidationTest.java plus scenarios in src/test/resources/karate/reserve-failure.feature

### Implementation for User Story 2

- [ ] T028 [US2] Implement rejection branch in src/main/java/com/example/inventory_service/application/ReserveStockService.java (any-insufficient → no StockItem touched, record FAILED + InventoryReservationFailed with each missing sku/reason) (depends on T025–T027 failing first)

**Checkpoint**: US1 + US2 both work independently — QS-1 and QS-2 pass

---

## Phase 5: User Story 3 — Idempotent retry by orderId (Priority: P2)

**Goal**: Repeats of a recorded orderId return the verbatim stored outcome (same decidedAt) with no extra stock movement and no second row — including under concurrent duplicate inserts and cross-order contention

**Independent Test**: Repeat QS-1 byte-for-byte → identical body, stock still 7/3, one row (QS-3); conflicting payload (same orderId, different items) returns original, mutates nothing, logs mismatch; two orderIds racing for last units → exactly one wins

### Tests for User Story 3 ⚠️ (write FIRST, verify FAIL)

- [ ] T029 [P] [US3] Retry test — success and failure replays return stored outcome verbatim with zero stock delta (AC-004) in src/test/java/com/example/inventory_service/integration/ReservationIdempotencyTest.java
- [ ] T030 [US3] Concurrent duplicate-insert test — unique backstop triggers re-read, still one row and one stock movement (AC-004) in src/test/java/com/example/inventory_service/integration/ReservationIdempotencyTest.java
- [ ] T031 [P] [US3] Cross-order contention test — two distinct orderIds racing for the last units of one SKU: exactly one RESERVED, one FAILED, stock never negative (FR-009, SC-002) in src/test/java/com/example/inventory_service/integration/ReservationContentionTest.java

### Implementation for User Story 3

- [ ] T032 [US3] Implement conflict re-read in src/main/java/com/example/inventory_service/application/ReserveStockService.java (catch DataIntegrityViolationException → rollback → return stored outcome; DDL UNIQUE constraint itself owned by T011/T015) (depends on T029–T031 failing first)

**Checkpoint**: All idempotency scenarios green — QS-3 passes

---

## Phase 6: User Story 4 — Redelivery-safe consumer + DLQ (Priority: P2)

**Goal**: Kafka intake shares US1–US3 semantics; redelivery causes no duplicate effect; poison messages get 3 exponentially-backed-off retries then a DLQ record preserving orderId

**Independent Test**: Same ReservationRequested twice on EmbeddedKafka → one stock movement (QS-4); poison message → 3 retries with exponentially increasing delays → one DLQ record with orderId header, retryCount 3 (QS-5)

### Tests for User Story 4 ⚠️ (write FIRST, verify FAIL)

- [ ] T033 [P] [US4] EmbeddedKafka redelivery test — duplicate message, single reservation effect (AC-005) in src/test/java/com/example/inventory_service/integration/ReservationConsumerTest.java
- [ ] T034 [P] [US4] EmbeddedKafka poison-message test — 3 retries with exponentially increasing delays then DLQ with orderId header and reason (AC-006) in src/test/java/com/example/inventory_service/integration/ReservationConsumerDlqTest.java

### Implementation for User Story 4

- [ ] T035 [US4] Implement SCS consumer function on inventory.reservation.requests delegating to ReserveStockUseCase with maxAttempts=4 + exponential backoff + enableDlq in src/main/java/com/example/inventory_service/infrastructure/messaging/kafka/ReservationConsumer.java (depends on T033–T034 failing first)
- [ ] T036 [US4] Implement DeadLetter mapping (orderId nullable, failureReason, retryHistory of 3, parkedAt) per data-model.md in src/main/java/com/example/inventory_service/infrastructure/messaging/kafka/DeadLetterMapper.java

**Checkpoint**: All user stories independently functional — QS-4 and QS-5 pass

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Non-functional gates, traceability proof, contract snapshot, full validation

- [ ] T037 [P] Run p95 < 200 ms check at low-load baseline (single node, tens rps, small orders) per quickstart.md QS-6 and record result in specs/001-reserve-stock-order/quickstart.md
- [ ] T038 [P] Verify orderId log correlation (grep orderId across logs) and refresh checked-in snapshot against /v3/api-docs vs specs/001-reserve-stock-order/contracts/openapi.yaml
- [ ] T039 Record AC-001..AC-006 → test mapping matrix (FR-010/SC-006) in specs/001-reserve-stock-order/checklists/requirements.md notes
- [ ] T040 Run ./gradlew check fully green with zero Docker/testcontainer dependencies (constitution V gate)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–6)**: Depend on Foundational; runnable in parallel once it completes, or sequentially P1 → P2
- **Polish (Phase 7)**: Depends on all desired stories complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — no story dependencies (MVP)
- **US2 (P1)**: After Foundational — extends T021 branch; independently testable via QS-2
- **US3 (P2)**: After Foundational — hardens T021/T028 with re-read; independently testable via QS-3
- **US4 (P2)**: After Foundational — reuses service via consumer; independently testable via QS-4/QS-5

### Within Each User Story

- Tests MUST be written and FAIL before implementation (T016–T020 → T021–T024, etc.)
- Domain/ports before service; service before endpoint/consumer; DLQ mapper after consumer contract fixed
- Same-file tasks are sequential: T029 → T030 share ReservationIdempotencyTest.java (no joint `[P]`)

### AC Coverage (FR-010 compliance)

- AC-001 → T016, T017, T018 | AC-002 → T018, T019, T022 | AC-003 → T025, T026, T028
- AC-004 → T029, T030, T032 | AC-005 → T033, T035 | AC-006 → T034, T035, T036
- FR-007 → T014, T027 | FR-009/SC-002 → T007, T031 | Matrix proof → T039

### Parallel Opportunities

- Setup: T002–T005 in parallel (different files)
- Foundational: T007, T008, T010, T011, T014 in parallel; then T009, T012, T013; T015 after T011
- Per story: all test tasks [P] launch together before implementation (except same-file T030, which follows T029)
- US1 impl: T022 + T023 in parallel; US1 tests T016–T020 (5 files) all parallel
- Post-Foundational: US1–US4 implementable in parallel by different developers (shared-file merges on ReserveStockService.java need sequencing: T021 → T028 → T032)

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (TDD — expect FAIL before T021):
Task: "Unit test aggregate happy-path in src/test/java/com/example/inventory_service/unit/ReservationTest.java"
Task: "Service H2 integration test in src/test/java/com/example/inventory_service/integration/ReserveStockServiceTest.java"
Task: "Karate happy-path contract test in src/test/resources/karate/reserve-success.feature"
Task: "EmbeddedKafka publication test in src/test/java/com/example/inventory_service/integration/ReservationOutcomePublishTest.java"
Task: "MongoAuditStore unit test in src/test/java/com/example/inventory_service/unit/MongoAuditStoreTest.java"

# Launch US1 adapters together (different files, no mutual deps):
Task: "OutcomePublisher SCS adapter in infrastructure/messaging/kafka/KafkaOutcomePublisher.java"
Task: "AuditStore Mongo adapter in infrastructure/persistence/mongo/MongoAuditStore.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1
2. **STOP and VALIDATE**: QS-1 green (incl. decisions-topic assertion T019), `./gradlew test` green
3. Deploy/demo the reserving increment

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → QS-1 → demo (MVP) → US2 → QS-2 → US3 → QS-3 → US4 → QS-4/QS-5
3. Each phase keeps all prior QS scenarios green (no regressions)

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1+US2 (service forks)
   - Developer B: US3 (constraint/idempotency + contention)
   - Developer C: US4 (consumer/DLQ)
3. Coordinate merges on ReserveStockService.java (T021 → T028 → T032 sequence)

---

## Notes

- One task at a time in `/speckit.implement` (constitution workflow); commit after each task
- No Docker/testcontainers at any point — H2 + EmbeddedKafka + Mockito + Karate RANDOM_PORT only
- Stop at any checkpoint to validate the story independently via its QS scenario
