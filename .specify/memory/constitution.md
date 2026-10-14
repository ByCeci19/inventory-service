<!--
Sync Impact Report
Version change: scaffold (unratified) -> 1.0.0
Modified principles: (all newly defined from project input)
Added sections:
  - Core Principles (I-VI)
  - Technology Stack & Architecture Constraints
  - Development Workflow & Quality Gates
  - Governance
Removed sections: (none, scaffold placeholders replaced)
Follow-up TODOs:
  - Stack version discrepancy: user input states Spring Boot 3.x while build.gradle pins 4.1.1; reconcile before relying on pin.
Deferred non-governance intents (not executed here):
  - /speckit.specify stock reservation feature (idempotency, eventual consistency)
  - /speckit.clarify (optional)
  - /speckit.plan (aggregates, events, contracts, test strategy, risks)
  - /speckit.tasks
  - /speckit.analyze
  - /speckit.implement
-->

# Inventory Service Constitution

## Core Principles

### I. Domain-Driven Design & Aggregate Invariants (NON-NEGOTIABLE)

Business logic lives in the domain layer. Aggregates own and enforce their invariants (e.g. stock never goes below zero; valid reservation state transitions). Domain rules MUST NOT leak into adapters or infrastructure. Value objects are immutable and entities are identified by their aggregate root. Invariants are validated on construction and every state change, not in controllers or repositories.

### II. Ports & Adapters for Persistence and Messaging

Application and domain code depend only on port interfaces. JPA (PostgreSQL), MongoDB, and Kafka adapters live in infrastructure and implement those ports. No adapter-specific types (JPA entities, Mongo documents, Kafka messages) MAY appear in domain or application layers. Storage and messaging technologies remain swappable behind the ports, which is what makes non-Docker testing possible.

### III. Idempotent Stock Reservation by orderId (NON-NEGOTIABLE)

Every stock reservation is keyed by `orderId` and MUST be idempotent: a repeated request with the same `orderId` returns the previously recorded result and performs no additional stock decrement. The reservation outcome (status, reserved quantity, rejecting reason) MUST be persisted and looked up before mutating stock. Retries of the messaging layer therefore cannot cause double reservation.

### IV. Event-Driven Consistency with Versioned Events (NON-NEGOTIABLE)

State changes publish versioned domain events over Kafka via Spring Cloud Stream (AsyncAPI). Consumers assume at-least-once delivery and MUST be idempotent and tolerate duplicates and out-of-order messages according to their declared ordering key. Producers and consumers MUST declare event versions; breaking schema changes require a new version. Consumers configure retries; poison messages MUST be routed to a DLQ, never silently dropped.

### V. Test Without Docker (NON-NEGOTIABLE for CI)

No test depends on Docker. JPA tests run against H2 in the PostgreSQL profile; messaging tests use EmbeddedKafka; MongoDB tests use Flapdoodle embedded or mocks when preferred; Karate end-to-end tests run against the local application on a `RANDOM_PORT`. Tests MUST be reproducible offline and MUST NOT require external services or container runtimes.

### VI. Contracts First: OpenAPI & AsyncAPI

REST endpoints are described via OpenAPI and events via AsyncAPI. Breaking contract changes require a version bump and a migration note. Contract-level integration tests (Karate) guard endpoint and event shapes. Implementation MUST not diverge from the published contract.

## Technology Stack & Architecture Constraints

- Java 21 (Temurin); Gradle 8.x; Spring Boot 3.x
- PostgreSQL via JPA (runtime driver `org.postgresql:postgresql`) for relational/transactional data
- MongoDB via Spring Data MongoDB for document storage
- Kafka via Spring Cloud Stream for event-driven messaging
- REST contracts via OpenAPI; event contracts via AsyncAPI
- Validation via `spring-boot-starter-validation`; observability via actuator; hot reload via devtools in development only
- No feature code may bypass the ports from Principle II to reach a datastore or broker directly

## Development Workflow & Quality Gates

- Tests (unit, integration, and Karate contract) MUST be green before a change is considered complete
- TDD is preferred: write the failing test, then implement, then refactor
- New aggregates require invariant tests; new endpoints/events require contract coverage (OpenAPI/AsyncAPI plus Karate)
- Idempotency of stock reservation (Principle III) MUST be covered by an explicit test per aggregate
- Messaging changes MUST declare retry and DLQ behavior and be exercised against EmbeddedKafka
- Karate suites MUST target `RANDOM_PORT` and pass without Docker

## Governance

This constitution supersedes all other practice documents in this repository. Amendments require a documented proposal, review approval, a migration/compatibility note, and a version bump following semantic versioning: MAJOR for backward-incompatible governance or principle redefinitions, MINOR for new or materially expanded principles/sections, PATCH for clarifications and wording. All PRs and reviews MUST verify compliance with these principles; deviations require explicit, tracked justification. Runtime development guidance is provided through the project's specification and plan documents under `.specify/`. Compliance is reviewed on every constitution amendment and on every feature plan.

**Version**: 1.0.0 | **Ratified**: 2026-10-14 | **Last Amended**: 2026-10-14
