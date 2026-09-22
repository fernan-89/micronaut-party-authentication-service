package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.RevokedSession;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;

/** Persisted revocation of a login session. */
@Serdeable
@Introspected
@MappedEntity("revoked_sessions")
public record RevokedSessionEntity(
        @Id
        String sessionId,
        Instant revokedAt
) {
    public RevokedSessionEntity {
        Objects.requireNonNull(sessionId, "Persistence Invariant Violation: Session ID cannot be null.");
        Objects.requireNonNull(revokedAt, "Persistence Invariant Violation: RevokedAt cannot be null.");
    }

    public RevokedSession toDomain() {
        return new RevokedSession(sessionId, revokedAt);
    }
}
