# ADR-021: Asymmetric tokens, refresh rotation and session revocation

## Status
Accepted — supersedes the token scheme, the service-to-service token and the "no revocation/refresh"
consequence of [ADR-020](020-credentials-sessions-and-platform-security.md) (points 1, 2, 4, 6 of ADR-020
stand unchanged). Built on `thinklab-service-kit` 0.3.0
([ADR-002](https://github.com/fernan-89/thinklab-service-kit/blob/master/docs/adr/002-asymmetric-tokens-refresh-and-revocation.md)),
which carries the platform-wide mechanics (`JwtSigner`/`JwtVerifier`, `KeyProvider`, `RevocationList`); this
ADR is what party-authentication, as the issuer, does with them.

## Context
ADR-020 shipped a single HS256 secret shared by every service and admitted two open gaps: any service could
mint a token for any tenant, and there was no way to end a session or exchange a long-lived credential for a
fresh short-lived one without asking the user to log in again.

## Decision
1. **party-authentication is the only issuer.** It is the sole service wired to `JwtSigner` (backed by
   `LocalKeyStore`) and publishes its public key at `GET /party-authentication/v1/.well-known/jwks.json`
   (`SigningKeyBootstrap` forces the key to exist at startup, so the first request is never the one that
   creates it). `THINKLAB_JWT_PRIVATE_KEY` is a JWK; unset, a key is generated once at startup — every issued
   token becomes invalid on the next restart, acceptable outside production.
2. **Access tokens are short (10 minutes) and carry a session id (`sid`).** `SessionIssuer` mints the access
   token and, alongside it, a 256-bit random opaque refresh token; only the refresh token's SHA-256 hash is
   persisted (`RefreshTokenRecord` / `refresh_tokens` collection) — a database read never yields a usable
   credential.
3. **Refresh is single-use with theft detection.** `POST /session/refresh` exchanges a refresh token for a new
   access token and a new refresh token of the *same* session (`RefreshSessionUseCase`); presenting a token a
   second time does not just fail, it revokes the whole session, on the reasoning that a refresh token cannot
   legitimately be replayed — if it is, it leaked.
4. **Revocation is explicit and effective platform-wide.** `POST /session/revoke` (logout, silent on an
   unknown token — it must not let a caller probe for valid tokens), `PUT /{id}/session/control/revoke`
   (forced logout of every session of a user; same authorisation rule as `credential/update`: admin, service or
   the user themselves), and suspending or deactivating a user (`ControlUserUseCase`) all revoke every open
   session of that user. `GET /session/revoked` is the list every other service polls
   (`ListRevokedSessionsUseCase`); an entry is kept only until the session's last access token could have
   expired, so the list stays small. This endpoint and `POST /token/service` are on the platform gateway's
   `denied-paths` — they are for services, never a public client.
5. **Service-to-service tokens are signed locally.** `LocalServiceTokenProvider` replaces the kit's default
   `ClientCredentialsTokenProvider`: the issuer signs its own service tokens directly rather than calling
   itself over HTTP. `POST /token/service` (`IssueServiceTokenUseCase`) is the client-credentials endpoint the
   *other* services use, checked against `thinklab.security.service-clients` (client id → secret,
   constant-time comparison); every failure — unknown client, wrong secret, disabled client (blank secret) — is
   the same generic `ERR-USR-00401`.

## Consequences
- Positive: no other service can forge a token; a stolen refresh token is usable exactly once before it
  self-destructs the session; an administrator can force a logout that takes effect everywhere within one
  revocation-poll interval, not just on this service.
- Negative: party-authentication is now a harder single point of trust — its private key and its database of
  refresh-token hashes are both security-critical, and its own availability now gates every other service's
  ability to *verify* new logins and refresh in-flight sessions (already-issued access tokens keep working
  until they expire). Client-side session storage must handle a rotating refresh token, which is more state
  than a single static credential.
