package com.thinklab.infrastructure.adapter.out.mongo.entity;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted password hash, keyed by the user id. Never mapped into any response. */
@Serdeable
@Introspected
@MappedEntity("credentials")
public record CredentialEntity(
        @Id
        UUID userId,
        String encodedHash,
        Instant updatedAt
) {
    public CredentialEntity {
        Objects.requireNonNull(userId, "Persistence Invariant Violation: User ID cannot be null.");
        Objects.requireNonNull(encodedHash, "Persistence Invariant Violation: Hash cannot be null.");
        Objects.requireNonNull(updatedAt, "Persistence Invariant Violation: UpdatedAt cannot be null.");
        if (encodedHash.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Hash cannot be blank.");
        }
    }
}
