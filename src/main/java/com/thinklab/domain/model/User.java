package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidUserStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core Domain Model representing the User Aggregate Root.
 *
 * <p><b>BIAN Alignment (ADR-013):</b> This is the Control Record of the
 * {@code party-authentication} Service Domain — the platform's authoritative IAM record for a
 * tenant operator, scoped to an Organisation from the Party Reference Data Directory.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class User {

    private final UUID id;
    private final UUID organisationId;
    private String fullName;
    private String email;
    private UserRole role;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UUID id, UUID organisationId, String fullName, String email, UserRole role) {
        this.id = id;
        this.organisationId = organisationId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = UserStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private User(
            UUID id,
            UUID organisationId,
            String fullName,
            String email,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organisationId = organisationId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status != null ? status : UserStatus.PENDING;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Static factory method for aggregate creation (BIAN Behavior Qualifier: {@code initiate}).
     * The UUID must be provided by the orchestration layer after calling the Hash Token Registry.
     */
    public static User createNew(UUID id, UUID organisationId, String fullName, String email, UserRole role) {
        if (id == null || organisationId == null || fullName == null || email == null || role == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Full Name, Email, and Role are mandatory for User creation.");
        }
        return new User(id, organisationId, fullName, email, role);
    }

    /**
     * Reconstitutes an existing User aggregate from persistence layer.
     */
    public static User reconstitute(
            UUID id,
            UUID organisationId,
            String fullName,
            String email,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null || organisationId == null || fullName == null || email == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Full Name, and Email are mandatory to reconstitute a User.");
        }
        return new User(id, organisationId, fullName, email, role, status, createdAt, updatedAt);
    }

    // --- Domain Behaviors (State Mutations) ---

    public void updateProfile(String fullName, UserRole role) {
        if (fullName == null || fullName.isBlank() || role == null) {
            throw new IllegalArgumentException("Full Name and Role cannot be empty.");
        }
        this.fullName = fullName;
        this.role = role;
        this.updatedAt = Instant.now();
    }

    /**
     * Behavior Qualifier: {@code control}. Transitions the User to the given target status,
     * enforcing the {@link UserStatus} state machine.
     */
    public void changeStatus(UserStatus newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null.");
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        changeStatus(UserStatus.ACTIVE);
    }

    public void suspend() {
        changeStatus(UserStatus.SUSPENDED);
    }

    /**
     * Terminal transition (BIAN Behavior Qualifier: {@code control/deactivate}). No physical
     * DELETE anywhere in this Service Domain — the User is never removed, only closed.
     */
    public void deactivate() {
        changeStatus(UserStatus.DEACTIVATED);
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // --- Nested Value Objects ---

    public enum UserRole {
        ADMIN, OPERATOR, VIEWER
    }

    /**
     * Formal lifecycle state machine for the User Control Record, mirroring the
     * {@code HashStatus}/{@code OrganisationStatus} pattern (ADR-013).
     */
    public enum UserStatus {
        PENDING, ACTIVE, SUSPENDED, DEACTIVATED;

        /**
         * Validates if the transition from the current state to the target state is legally permitted.
         *
         * @throws InvalidUserStatusException if the transition violates business compliance rules
         *                                    or is unnecessarily idempotent.
         */
        public void validateTransitionTo(UserStatus targetStatus) {
            Objects.requireNonNull(targetStatus, "Target UserStatus must not be null for transition validation.");

            if (this == targetStatus) {
                throw new InvalidUserStatusException(String.format(
                        "Idempotency Violation: The User is already in the [%s] state.", this));
            }
            if (!canTransitionTo(targetStatus)) {
                throw new InvalidUserStatusException(String.format(
                        "Compliance Violation: Illegal state transition from [%s] to [%s].", this, targetStatus));
            }
        }

        public boolean canTransitionTo(UserStatus targetStatus) {
            if (targetStatus == null) {
                return false;
            }
            return switch (this) {
                case PENDING -> targetStatus == ACTIVE || targetStatus == DEACTIVATED;
                case ACTIVE -> targetStatus == SUSPENDED || targetStatus == DEACTIVATED;
                case SUSPENDED -> targetStatus == ACTIVE || targetStatus == DEACTIVATED;
                case DEACTIVATED -> false;
            };
        }
    }
}
