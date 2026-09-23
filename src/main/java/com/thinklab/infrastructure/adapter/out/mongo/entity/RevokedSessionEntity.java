package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.RevokedSession;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted revocation of a login session. {@code id} is the Mongo primary key; {@code sessionId} (an opaque
 * UUID string minted at login, not a Mongo id) is looked up through its own unique index — see
 * {@link RefreshTokenEntity} for why a non-ObjectId string cannot be the {@code @Id} itself.
 */
@Serdeable
@Introspected
@MappedEntity("revoked_sessions")
@Indexes(@Index(columns = {"sessionId"}, unique = true))
public record RevokedSessionEntity(
        @Id
        UUID id,
        String sessionId,
        Instant revokedAt
) {
    public RevokedSessionEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: ID cannot be null.");
        Objects.requireNonNull(sessionId, "Persistence Invariant Violation: Session ID cannot be null.");
        Objects.requireNonNull(revokedAt, "Persistence Invariant Violation: RevokedAt cannot be null.");
    }

    public static RevokedSessionEntity of(String sessionId, Instant revokedAt) {
        return new RevokedSessionEntity(UUID.randomUUID(), sessionId, revokedAt);
    }

    public RevokedSession toDomain() {
        return new RevokedSession(sessionId, revokedAt);
    }
}
