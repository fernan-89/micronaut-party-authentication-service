# ADR 023: The by-id routes are scoped to the tenant

## Status
Accepted (2026-11-20). Part of the platform tenant audit, group B (`thinklab-platform/docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037 and the nine services of group A.

## Context
A user belongs to an organisation, but the five by-id routes of users (retrieve, update, activate, suspend, deactivate) took only the id: a tenant that knew a UUID could read the name, email and role of another tenant's user, or suspend and deactivate it. The credential and session-revoke routes already took the tenant and compared it after loading; the refresh and federated-session flows loaded by id and compared (or did not compare) afterwards.

## Decision
- Every by-id route takes `X-Tenant-Id` (required, 400 if blank) and finds the user only in that tenant: another tenant's user is the same as none (404 `UserNotFoundException`), nothing is read or written.
- `UserRepository` has no unscoped `findById`: it is `findById(id, organisationId)`, answered by a derived `findByIdAndOrganisationId` query. The credential and revoke use cases, the federated session (organisation of the request) and the refresh flow (organisation stored in the refresh-token record) use it too, so the comparison no longer lives in each use case.
- Writes that follow a scoped load keep using the id.

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's user; one lookup, no use case that forgets to compare.
- Negative: a client that sends no tenant on a user by-id route now gets a 400 (the web app sends it on every call; identity-federation already sends it on its calls).
