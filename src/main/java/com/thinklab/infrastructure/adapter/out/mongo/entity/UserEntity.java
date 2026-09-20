package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Entity: Persistence model for the User aggregate mapped to MongoDB.
 *
 * <p><b>Architectural Role:</b> Mirrors the Hash Token Registry Service Domain's
 * {@code HashTokenEntity} pattern (Micronaut Data Mongo {@code @MappedEntity} +
 * {@code ReactorCrudRepository}) rather than the raw reactive driver + manual
 * {@code PojoCodecProvider} approach — a deliberate choice to avoid the codec-registration class
 * of bug the Party Reference Data Directory Service Domain hit during its own E2E validation.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@MappedEntity("users")
@Indexes({
        @Index(columns = {"organisationId", "email"}),
        @Index(columns = {"organisationId", "status"})
})
public record UserEntity(

        @Id
        UUID id,

        UUID organisationId,

        String fullName,

        String email,

        UserRole role,

        UserStatus status,

        Instant createdAt,

        Instant updatedAt,

        @Version
        Long version
) {

    public UserEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: User Entity ID cannot be null.");
        Objects.requireNonNull(organisationId, "Persistence Invariant Violation: Organisation ID cannot be null.");
        Objects.requireNonNull(fullName, "Persistence Invariant Violation: Full Name cannot be null.");
        Objects.requireNonNull(email, "Persistence Invariant Violation: Email cannot be null.");
        Objects.requireNonNull(status, "Persistence Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(createdAt, "Persistence Invariant Violation: CreatedAt timestamp cannot be null.");

        if (fullName.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Full Name cannot be blank.");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Email cannot be blank.");
        }
    }

    public static UserEntity fromDomain(User domain) {
        Objects.requireNonNull(domain, "Infrastructure constraint violated: Domain aggregate cannot be null for entity mapping.");

        return new UserEntity(
                domain.getId(),
                domain.getOrganisationId(),
                domain.getFullName(),
                domain.getEmail(),
                domain.getRole(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                null
        );
    }

    public User toDomain() {
        return User.reconstitute(id, organisationId, fullName, email, role, status, createdAt, updatedAt);
    }
}
