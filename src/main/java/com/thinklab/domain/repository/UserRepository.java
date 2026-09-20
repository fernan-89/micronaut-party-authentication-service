package com.thinklab.domain.repository;

import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for User persistence operations (Party Authentication Service Domain).
 * Part of the pure Domain Layer.
 *
 * ARCHITECTURAL RULE: Partial State Mutations.
 * Monolithic save operations are strictly reserved for aggregate creation.
 * All state transitions must be handled via specific, granular update methods
 * to prevent race conditions and optimize I/O operations at the database level.
 *
 * <p>There is no {@code deleteById} — the terminal {@code control/deactivate} Behavior Qualifier
 * transitions the User to {@link UserStatus#DEACTIVATED} via {@link #updateStatus}, never a
 * physical deletion (ADR-013).
 */
public interface UserRepository {

    Mono<User> create(User user);

    Mono<User> findById(UUID id);

    /**
     * Tenant-scoped listing of all Users belonging to a given Organisation.
     *
     * @param organisationId The UUID v4 of the parent Organisation (tenant boundary).
     * @param status         Optional status filter; {@code null} returns Users in any status.
     * @return A {@link Flux} emitting the matching User aggregates.
     */
    Flux<User> findAllByOrganisationId(UUID organisationId, UserStatus status);

    Mono<Void> updateStatus(UUID id, UserStatus status);

    Mono<Void> updateBasicInfo(UUID id, String fullName, UserRole role);

    /**
     * Duplicate-prevention check, called explicitly from the use case before creating a new User —
     * never relied upon solely as a DB unique index side effect.
     *
     * @param organisationId The tenant boundary (Organisation) to scope the check to.
     * @param email          The candidate email address.
     * @return A {@link Mono} emitting {@code true} if a User with this email already exists for this Organisation.
     */
    Mono<Boolean> existsByOrganisationIdAndEmail(UUID organisationId, String email);
}
