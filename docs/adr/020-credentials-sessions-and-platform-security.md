# ADR-020: Credentials, sessions and platform security

## Status
Accepted

## Context
Until now every service trusted the `X-Tenant-Id` and `X-Executor` headers, so any caller could act as any
tenant or user. Catalog tasks USR-02 (credential hashing) and USR-03 (RBAC scopes) and journey 3 of the roadmap
require real authentication before any API is exposed.

## Decision
1. **Passwords** are hashed with **Argon2id** (RFC 9106; 19 MiB, 2 iterations, 1 lane, 16-byte random salt) through
   Bouncy Castle. The encoded hash carries its parameters, so cost can be raised without invalidating credentials.
   Hashes live in their own `credentials` collection (`CredentialRepository`), never in the User aggregate, so
   they cannot leak into a projection.
2. **Behavior Qualifiers**: `PUT /{id}/credential/update` sets or replaces the password (12-128 characters);
   `POST /session/initiate` authenticates an ACTIVE user by organisation, email and password and returns an
   access token. Both fit the BIAN control-record model (`credential`, `session`).
3. **Tokens** are HS256 JWTs issued by `JwtService` in the shared kit (claims `sub` user, `tid` organisation,
   `role`, `iss`, `exp`). The algorithm is pinned, so `none` and other algorithms are rejected. The signing secret
   (`THINKLAB_JWT_SECRET`, at least 32 bytes) is shared by all services; asymmetric keys are a later hardening step.
4. **Enforcement** is a kit `SecurityFilter` in every service, enabled with `THINKLAB_SECURITY_ENABLED=true`
   (off by default for local development): bearer token required outside health and documentation paths,
   role-based method authorisation (VIEWER read-only, OPERATOR no delete, ADMIN and SERVICE everything), and
   `X-Tenant-Id` / `X-Executor` / `X-Role` **derived from the token**. A conflicting tenant header is rejected.
5. **Service-to-service**: outbound calls to the Hash Token Registry carry a short-lived `SERVICE` token
   (`ServiceTokenClientFilter`); a `SERVICE` token may act for any tenant.
6. **Failure hygiene**: every login failure (unknown user, no credential, wrong password, inactive user) is the same
   `ERR-USR-00401`, and an unknown user still pays the hashing cost. A password change by anyone other than an admin
   or the user themselves is `ERR-USR-00403`; a user of another tenant is reported as not found.

## Consequences
- Positive: tenant and executor can no longer be forged; the audit trail's executor is trustworthy; RBAC is one
  rule set in one place.
- Negative: a shared symmetric secret means any service could mint tokens (acceptable inside the trust boundary;
  revisit with asymmetric keys and a gateway). The first administrator must be bootstrapped with an operator-minted
  `SERVICE` token (see `thinklab-platform/scripts/e2e/secured-smoke.ps1`). Token revocation and refresh are not
  implemented yet; tokens simply expire.
