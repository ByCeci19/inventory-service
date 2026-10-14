# Implementation Plan: Reserve Stock for Order

**Branch**: `001-reserve-stock-order` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-reserve-stock-order/spec.md` (incl. `## Clarifications` session 2026-10-14)

## Summary

Idempotent, all-or-nothing stock reservation keyed by `orderId`. Intake is a synchronous REST endpoint (`POST /api/v1/reservations`, OpenAPI) backed by a domain aggregate that looks up the stored outcome by `orderId` before mutating stock; every decision is persisted (JPA) and published as a versioned outcome event (`InventoryReserved` / `InventoryReservationFailed`, `eventVersion: v1`, AsyncAPI) over Kafka via Spring Cloud Stream. A Kafka consumer offers the same operation for event-driven intake and is idempotent under redelivery; poison messages get 3 retries with exponential backoff then DLQ. Audit outcomes are persisted per orderId in MongoDB. All tests run without Docker (H2, EmbeddedKafka, mocked Mongo port, Karate on `RANDOM_PORT`).

## Technical Context

**Language/Version**: Java 21 (Temurin 21.0.4, verified on host)

**Primary Dependencies**: Spring Boot 3.5.16 (pinned 2026-10-14, T001) + Spring Cloud BOM 2025.0.3 (`spring-cloud-stream`/`binder-kafka` 4.3.3), Spring Data JPA, Spring Data MongoDB, Flyway, Bean Validation, springdoc-openapi 2.9.1, Spring for Apache Kafka (transitive via binder), Spring for Apache Kafka (transitive via binder)

**Storage**: PostgreSQL via JPA (production); H2 (tests, default mode, JPQL only — no native queries). MongoDB via Spring Data MongoDB for audit/event history (production); mocked audit port in tests (no embedded Mongo dependency).

**Testing**: JUnit 5 + AssertJ, `spring-kafka-test` EmbeddedKafka, H2, Mockito, Karate JUnit 5 (`RANDOM_PORT`). Mapping FR↔AC enforced by FR-010/SC-006.

**Target Platform**: Linux server (JVM)

**Project Type**: Event-driven microservice (REST intake + Kafka messaging)

**Performance Goals**: p95 reservation decision < 200 ms at low-load baseline (single node, tens of reservations/sec, small multi-item orders) per clarified SC-004

**Constraints**: No Docker in dev/tests (constitution V); idempotency by orderId (III); versioned events + 3 retries exp-backoff + DLQ (IV); contracts first — OpenAPI + AsyncAPI (VI); trusted cluster network, no auth in v1 (clarified)

**Scale/Scope**: v1 single-node; excludes cancellation, replenishment, Saga compensations (spec Out of scope)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Design compliance | Status |
|---|-----------|-------------------|--------|
| I | DDD invariants in domain | `Reservation` aggregate owns all-or-nothing check, non-negative stock, immutable-once-recorded outcome; no logic in controllers/adapters | PASS |
| II | Ports & adapters | Application depends on `InventoryPort`, `OutcomePublisher`, `AuditStore` ports; JPA/Mongo/Kafka types confined to `infrastructure/` | PASS |
| III | Idempotency by orderId | Outcome lookup precedes mutation in one transaction + UNIQUE constraint on `reservation.order_id` as concurrent backstop (conflict → re-read stored outcome) | PASS |
| IV | Versioned events + retry + DLQ via SCS | Payload `eventVersion: v1`; binder `maxAttempts=4` (1+3), exponential backoff, `enableDlq` with preserved `orderId` header | PASS |
| V | No-Docker tests | H2 (JPA), EmbeddedKafka (messaging), mocked Mongo port, Karate on `RANDOM_PORT`; zero container/testcontainer dependencies | PASS |
| VI | Contracts first | `contracts/openapi.yaml` + `contracts/asyncapi.yaml` authored in this plan; Karate guards REST shape | PASS |

Post-Phase-1 re-check: no violations introduced. Complexity Tracking: none required.

Known discrepancy resolved in plan (not a gate failure): `build.gradle` pins Spring Boot 4.1.1 and Gradle wrapper is 9.8.1, while the constitution says Boot 3.x / Gradle 8.x. Decision R-1 aligns the build to Boot 3.x (proven SCS-4.x matrix); Gradle wrapper 9.8.1 is retained (builds Boot 3 + Java 21) with a follow-up constitution PATCH. See `research.md` R-1.

## Project Structure

### Documentation (this feature)

```text
specs/001-reserve-stock-order/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── openapi.yaml     # POST /api/v1/reservations
│   └── asyncapi.yaml    # InventoryReserved / InventoryReservationFailed v1
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Single Gradle project, DDD layers under `com.example.inventory_service`:

```text
src/main/java/com/example/inventory_service/
├── domain/
│   ├── Reservation.java            # aggregate root: items, outcome, idempotency + invariant checks
│   ├── StockItem.java              # SKU + availableQty (availableQty >= 0 invariant)
│   ├── ReservationItem.java        # value object: sku + quantity (> 0)
│   ├── MissingSku.java             # value object: sku + reason
│   └── ReservationOutcome.java     # value object: status, eventVersion=v1, decidedAt
├── application/
│   ├── port/in/ReserveStockUseCase.java
│   ├── port/out/InventoryPort.java      # load/save stock + find/store reservation
│   ├── port/out/OutcomePublisher.java   # publish versioned outcome events
│   ├── port/out/AuditStore.java         # persist outcome per orderId
│   └── ReserveStockService.java         # lookup-by-orderId → evaluate → mutate → record → publish+audit
├── infrastructure/
│   ├── web/ReservationController.java   # REST adapter (OpenAPI)
│   ├── persistence/jpa/                # JPA entities, repositories, InventoryPort adapter
│   ├── persistence/mongo/              # audit documents, AuditStore adapter
│   └── messaging/kafka/                # SCS functions, consumer, DLQ config, OutcomePublisher adapter
└── InventoryServiceApplication.java

src/main/resources/
├── application.yml                 # SCS bindings (retry/DLQ), datasource, data-mongo
└── contracts/ (copies of specs/.../contracts for springdoc/asyncapi reference)

src/test/java/.../
├── unit/           # aggregate invariant + idempotency tests (AC-001, AC-003, AC-004)
├── integration/    # JPA/H2, EmbeddedKafka consumer redelivery + DLQ (AC-005, AC-006), audit port
└── contract/       # Karate features vs RANDOM_PORT (AC-001..AC-004 + OpenAPI shape)

src/test/resources/
└── karate/         # *.feature files (see quickstart.md)
```

**Structure Decision**: Single-project DDD layout; no frontend/mobile options apply. Test tree mirrors the no-Docker strategy (unit / integration / contract).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

None — all gates pass; no violations to justify.
