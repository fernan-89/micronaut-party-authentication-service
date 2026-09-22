# Thinklab Party Authentication Service

**Version:** v1.0.0-BIAN

**Status:** Production-Ready (Mission-Critical)

## Overview

The Thinklab Party Authentication Service is the platform's authoritative IAM record for tenant
operators. It implements the BIAN-aligned `party-authentication` Service Domain (ADR-013): the
`User` is the Control Record, always scoped to an Organisation from the Party Reference Data
Directory (`X-Tenant-Id`), and every route follows the `/{control-record-id}/{behavior-qualifier}`
convention (`initiate`, `retrieve`, `update`, `control`) instead of ad-hoc REST verbs. It
consolidates the four competing user-model prototypes of the original Postman blueprint into one
contract (ADR-016).

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive
stack (Project Reactor, Micronaut Data MongoDB).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Micronaut Data MongoDB (`thinklab_party_authentication_db`, collection `users`) with optimistic locking (`@Version`)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (exhaustive FSM matrix, use cases, controller, adapter, entity, handler)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
User { id, organisationId, fullName, email, role, status, createdAt, updatedAt }
role:   ADMIN | OPERATOR | VIEWER
status: PENDING | ACTIVE | SUSPENDED | DEACTIVATED
```

### Lifecycle (ADR-016)

```text
PENDING -> ACTIVE <-> SUSPENDED
PENDING | ACTIVE | SUSPENDED -> DEACTIVATED (terminal, no exit, no DELETE)
```

* Every control operation loads the aggregate and lets the domain validate the transition before
  persisting — never a blind partial write.
* A `DEACTIVATED` user cannot be updated.
* Email is unique per Organisation (checked explicitly before creation).
* Credentials are never stored or logged by this service; hashing is delegated to the Hash Token Registry.

## BIAN Behavior Qualifier Contract (`/party-authentication/v1`)

`X-Tenant-Id` (Organisation UUID) is mandatory on `initiate` and the collection `retrieve`;
`X-Executor` is mandatory on every mutation. There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /party-authentication/v1/initiate` |
| retrieve (single) | `GET /party-authentication/v1/{id}/retrieve` |
| retrieve (collection, optional `status`) | `GET /party-authentication/v1/retrieve` |
| update | `PUT /party-authentication/v1/{id}/update` |
| control/activate, suspend, deactivate | `PUT /party-authentication/v1/{id}/control/{action}` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-USR-00404` | 404 | User not found |
| `ERR-USR-00401` | 401 | Invalid credentials (generic: unknown user, no password, wrong password or inactive user) |
| `ERR-USR-00403` | 403 | Only an administrator or the user themselves may change a credential (ADR-020) |
| `ERR-USR-00409` | 409 | Duplicate email in the Organisation, or illegal/idempotent lifecycle transition |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure (including a malformed `X-Tenant-Id`) |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8082/party-authentication/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -H "X-Executor: admin-user-01" \
  -d '{"fullName":"Ada Lovelace","email":"ada@thinklab.com","role":"OPERATOR"}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8082)
./gradlew run

# Container image
docker build -t thinklab-party-authentication-service:latest .
```

* **Health:** `http://localhost:8082/health`
* **Swagger UI:** `http://localhost:8082/swagger-ui`
* **Postman suite:** `docs/postman/` (lifecycle + negative scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8082` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_party_authentication_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 016 party authentication domain model.
## Authentication (ADR-020)

| Route | Purpose |
|---|---|
| `POST /party-authentication/v1/session/initiate` | Public login: `{organisationId, email, password}` returns `{accessToken, tokenType, expiresIn}` |
| `PUT /party-authentication/v1/{id}/credential/update` | Sets or replaces the password (Argon2id, 12-128 characters); admin or the user themselves |

Security is enabled per environment with `THINKLAB_SECURITY_ENABLED=true` and a shared `THINKLAB_JWT_SECRET` (>= 32 bytes).

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.

