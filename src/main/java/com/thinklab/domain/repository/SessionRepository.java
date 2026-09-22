package com.thinklab.domain.repository;

import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.RevokedSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound port: refresh tokens and revoked sessions. */
public interface SessionRepository {

    Mono<Void> saveRefreshToken(RefreshTokenRecord record);

    /** Emits the token record, or completes empty when the hash is unknown. */
    Mono<RefreshTokenRecord> findRefreshToken(String tokenHash);

    /** Atomically flags the token as used; emits true only for the call that flipped it, false if it was already used. */
    Mono<Boolean> markRefreshTokenUsed(String tokenHash);

    /** Idempotently records the revocation of a session. */
    Mono<Void> revokeSession(String sessionId, Instant revokedAt);

    Mono<Boolean> isSessionRevoked(String sessionId);

    /** The distinct session ids that were issued to the user. */
    Flux<String> findSessionIdsByUserId(UUID userId);

    /** Sessions revoked at or after the given instant. */
    Flux<RevokedSession> findRevokedSince(Instant since);
}
