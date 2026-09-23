package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.RefreshTokenRecord;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted refresh token. {@code id} is the Mongo primary key (a UUID, like every other entity in this
 * service); lookups go through the unique {@code tokenHash} index rather than {@code id} because the hash is
 * a 64-character SHA-256 hex string, not the 24-character hex Micronaut Data expects when a plain
 * {@code String} is the {@code @Id} of a MongoDB entity.
 */
@Serdeable
@Introspected
@MappedEntity("refresh_tokens")
@Indexes(@Index(columns = {"tokenHash"}, unique = true))
public record RefreshTokenEntity(
        @Id
        UUID id,
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
        Objects.requireNonNull(id, "Persistence Invariant Violation: ID cannot be null.");
        Objects.requireNonNull(tokenHash, "Persistence Invariant Violation: Token hash cannot be null.");
        Objects.requireNonNull(userId, "Persistence Invariant Violation: User ID cannot be null.");
        Objects.requireNonNull(organisationId, "Persistence Invariant Violation: Organisation ID cannot be null.");
        Objects.requireNonNull(sessionId, "Persistence Invariant Violation: Session ID cannot be null.");
        Objects.requireNonNull(expiresAt, "Persistence Invariant Violation: ExpiresAt cannot be null.");
    }

    public static RefreshTokenEntity fromDomain(RefreshTokenRecord record) {
        return new RefreshTokenEntity(UUID.randomUUID(), record.tokenHash(), record.userId(), record.organisationId(),
                record.sessionId(), record.expiresAt(), record.used(), null);
    }

    public RefreshTokenRecord toDomain() {
        return new RefreshTokenRecord(tokenHash, userId, organisationId, sessionId, expiresAt, used);
    }

    public RefreshTokenEntity asUsed() {
        return new RefreshTokenEntity(id, tokenHash, userId, organisationId, sessionId, expiresAt, true, version);
    }
}
