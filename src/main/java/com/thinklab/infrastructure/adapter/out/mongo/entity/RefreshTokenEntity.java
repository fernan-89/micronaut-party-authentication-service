package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.RefreshTokenRecord;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted refresh token, keyed by the SHA-256 hash of the opaque token. */
@Serdeable
@Introspected
@MappedEntity("refresh_tokens")
public record RefreshTokenEntity(
        @Id
        String tokenHash,
        UUID userId,
        UUID organisationId,
        String sessionId,
        Instant expiresAt,
        boolean used,
        @Version
        Long version
) {
    public RefreshTokenEntity {
        Objects.requireNonNull(tokenHash, "Persistence Invariant Violation: Token hash cannot be null.");
        Objects.requireNonNull(userId, "Persistence Invariant Violation: User ID cannot be null.");
        Objects.requireNonNull(organisationId, "Persistence Invariant Violation: Organisation ID cannot be null.");
        Objects.requireNonNull(sessionId, "Persistence Invariant Violation: Session ID cannot be null.");
        Objects.requireNonNull(expiresAt, "Persistence Invariant Violation: ExpiresAt cannot be null.");
    }

    public static RefreshTokenEntity fromDomain(RefreshTokenRecord record) {
        return new RefreshTokenEntity(record.tokenHash(), record.userId(), record.organisationId(), record.sessionId(),
                record.expiresAt(), record.used(), null);
    }

    public RefreshTokenRecord toDomain() {
        return new RefreshTokenRecord(tokenHash, userId, organisationId, sessionId, expiresAt, used);
    }

    public RefreshTokenEntity asUsed() {
        return new RefreshTokenEntity(tokenHash, userId, organisationId, sessionId, expiresAt, true, version);
    }
}
