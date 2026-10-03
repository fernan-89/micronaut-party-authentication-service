# ADR-022: A Federated Session Is Opened by the Federation Service, Through the Same Issuer

## Status
Accepted

## Context
Signing in through an external identity provider (OIDC) must give the person the same kind of session as a password login. The
platform deliberately has ONE issuer of tokens (ADR-021: only this service holds the ES256 private key; everything else verifies
through the JWKS), with refresh rotation, theft detection and revocation built around it.

## Decision
- A new internal Behavior Qualifier `POST /session/federated` `{organisationId, userId}` opens a session for a user that the
  identity-federation service has ALREADY authenticated against the external provider. It returns the same `SessionResponse` a
  password login returns, minted by the same `SessionIssuer`, so the refresh token is single-use and rotating, a replay revokes the
  session, and forced logout and revocation polling all work without any change.
- **Only a SERVICE may call it**: the verified `X-Role` must be `SERVICE` (otherwise 403); with security off there is no role and
  nothing is enforced, like every other role check. The identity-federation service calls it with its own client-credentials
  token (`thinklab-identity-federation-service` is a registered service client).
- The path is **not public** here and is on the gateway's denied paths (404 from outside), like `token/service`.
- The user must exist in that organisation and be `ACTIVE`; unknown, inactive and foreign users are the same generic 401 as a wrong
  password, so the answer never says which part was wrong.
- Nothing about the external identity is stored here: no provider token, no claims. The link between an external subject and a user
  lives in the identity-federation service.

## Consequences
- Positive: federation adds no second issuer, no kit change and no new session mechanism; a stolen federated refresh token is caught
  like any other.
- Negative: this service trusts the SERVICE caller to have authenticated the person: the federation service is a privileged component
  and its credentials must be protected like the signing key's neighbours.
