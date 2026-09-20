# ADR-016: Party Authentication Domain Model, State Machine, and Persistence Strategy

## Status
Accepted

## Context
The Party Authentication Service Domain is the platform's authoritative IAM record for tenant
operators, scoped to an Organisation from the Party Reference Data Directory Service Domain. It
consolidates the four competing user-model prototypes found in the original Postman-derived
platform blueprint (userIdStandard, userHash, userId+tenantId, last_user_id) into a single
contract: `userId` addressing with mandatory `organisationId` (tenant) scoping, matching the
blueprint's own recommendation.

## Decision

### Domain model
`User` is the Control Record: `id, organisationId, fullName, email, role(UserRole), status(UserStatus)`.
`UserRole` is a flat enum (`ADMIN`, `OPERATOR`, `VIEWER`) rather than a granular scopes list, kept
intentionally simple for v1 — a richer RBAC model (per-permission scopes) is deferred until a real
consumer requires it (YAGNI).

### State machine
`UserStatus`: `PENDING -> ACTIVE`; `ACTIVE <-> SUSPENDED`; `ACTIVE|SUSPENDED -> DEACTIVATED`
(terminal, no exit path). Formalized as `UserStatus.validateTransitionTo(...)`, mirroring the
`HashStatus`/`OrganisationStatus` pattern established by the Hash Token Registry and Party
Reference Data Directory Service Domains (ADR-013). Every mutation in `ControlUserUseCase` loads
the aggregate first and calls the domain transition method before persisting — never a blind
partial update. This directly avoids the bug class found in the Party Reference Data Directory
Service Domain's own E2E validation, where `ControlOrganisationUseCase` originally issued a raw
Mongo update without loading the aggregate, silently allowing illegal transitions (e.g.
cancelling an already-CANCELED Organisation returned 204 instead of 409).

### Persistence
Uses **Micronaut Data Mongo** (`@MappedEntity` + `ReactorCrudRepository`), following the Hash
Token Registry Service Domain's pattern — not the Party Reference Data Directory's original raw
reactive-driver + manual `PojoCodecProvider` approach. The raw-driver approach was the root cause
of a `CodecConfigurationException` found when the Party Reference Data Directory was first run
against a live MongoDB instance (the driver's default codec registry has no codec for arbitrary
POJOs). Micronaut Data Mongo registers entity codecs automatically via AOT compilation, so this
class of bug cannot occur here.

### No physical DELETE
Consistent with every other Service Domain on the platform: `control/deactivate` is the only path
to a terminal state. There is no `deleteById` on `UserRepository`.

## Consequences
- Positive: no codec-registration risk; state machine enforced uniformly; consistent RFC 7807
  error shape (`ERR-USR-00404`/`ERR-USR-00409`) across the platform.
- Negative: `UserRole` is coarse-grained; a future ADR will be needed if fine-grained permission
  scopes become a real requirement.
