package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserBehaviorTest {

    private static User newUser() {
        return User.createNew(UUID.randomUUID(), UUID.randomUUID(), "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
    }

    @Test
    @DisplayName("createNew should reject every missing mandatory field")
    void createNewValidation() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> User.createNew(null, org, "n", "e", UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> User.createNew(id, null, "n", "e", UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> User.createNew(id, org, null, "e", UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> User.createNew(id, org, "n", null, UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> User.createNew(id, org, "n", "e", null));
    }

    @Test
    @DisplayName("createNew should expose every attribute and start with equal created/updated timestamps")
    void createNewAttributes() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();

        User user = User.createNew(id, org, "Grace Hopper", "grace@thinklab.com", UserRole.ADMIN);

        assertEquals(id, user.getId());
        assertEquals(org, user.getOrganisationId());
        assertEquals("Grace Hopper", user.getFullName());
        assertEquals("grace@thinklab.com", user.getEmail());
        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(UserStatus.PENDING, user.getStatus());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstitute should restore persisted state and default a missing status/timestamps")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");

        User restored = User.reconstitute(id, org, "n", "e", UserRole.VIEWER, UserStatus.SUSPENDED, created, updated);
        User defaults = User.reconstitute(id, org, "n", "e", UserRole.VIEWER, null, null, null);

        assertEquals(UserStatus.SUSPENDED, restored.getStatus());
        assertEquals(created, restored.getCreatedAt());
        assertEquals(updated, restored.getUpdatedAt());
        assertEquals(UserStatus.PENDING, defaults.getStatus());
        assertNotNull(defaults.getCreatedAt());
        assertEquals(defaults.getCreatedAt(), defaults.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstitute should reject missing identity, tenant, name or email")
    void reconstituteValidation() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> User.reconstitute(null, org, "n", "e", UserRole.ADMIN, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> User.reconstitute(id, null, "n", "e", UserRole.ADMIN, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> User.reconstitute(id, org, null, "e", UserRole.ADMIN, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> User.reconstitute(id, org, "n", null, UserRole.ADMIN, null, null, null));
    }

    @Test
    @DisplayName("updateProfile should replace name and role and refresh updatedAt")
    void updateProfile() throws InterruptedException {
        User user = newUser();
        Instant before = user.getUpdatedAt();
        Thread.sleep(2);

        user.updateProfile("Ada L.", UserRole.ADMIN);

        assertEquals("Ada L.", user.getFullName());
        assertEquals(UserRole.ADMIN, user.getRole());
        assertTrue(user.getUpdatedAt().isAfter(before));
    }

    @Test
    @DisplayName("updateProfile should reject blank names and null roles")
    void updateProfileValidation() {
        User user = newUser();
        assertThrows(IllegalArgumentException.class, () -> user.updateProfile(null, UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> user.updateProfile(" ", UserRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> user.updateProfile("ok", null));
        assertEquals("Ada Lovelace", user.getFullName());
    }

    @Test
    @DisplayName("updateProfile should be rejected once the user is DEACTIVATED (terminal)")
    void updateProfileRejectedWhenDeactivated() {
        User user = newUser();
        user.deactivate();

        InvalidUserStatusException ex = assertThrows(InvalidUserStatusException.class, () -> user.updateProfile("x", UserRole.ADMIN));

        assertEquals("ERR-USR-00409", ex.getErrorCode());
        assertEquals("Ada Lovelace", user.getFullName());
    }

    @Test
    @DisplayName("changeStatus should reject a null target")
    void changeStatusNull() {
        assertThrows(NullPointerException.class, () -> newUser().changeStatus(null));
    }

    @Test
    @DisplayName("PENDING users can be deactivated directly (rejected onboarding)")
    void pendingToDeactivated() {
        User user = newUser();

        user.deactivate();

        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
    }

    @Test
    @DisplayName("Illegal-transition messages distinguish idempotency from compliance violations")
    void transitionMessages() {
        User user = newUser();
        user.activate();

        InvalidUserStatusException idempotent = assertThrows(InvalidUserStatusException.class, user::activate);
        assertTrue(idempotent.getMessage().contains("Idempotency Violation"));

        User pending = newUser();
        InvalidUserStatusException illegal = assertThrows(InvalidUserStatusException.class, pending::suspend);
        assertTrue(illegal.getMessage().contains("Compliance Violation"));
    }

    @Test
    @DisplayName("the three roles are ADMIN, OPERATOR and VIEWER")
    void roles() {
        assertEquals(3, UserRole.values().length);
        assertEquals(UserRole.VIEWER, UserRole.valueOf("VIEWER"));
    }

    @TestFactory
    @DisplayName("UserStatus transition matrix is exhaustive and matches the documented FSM")
    Stream<DynamicTest> transitionMatrix() {
        Map<UserStatus, Set<UserStatus>> allowed = Map.of(
                UserStatus.PENDING, EnumSet.of(UserStatus.ACTIVE, UserStatus.DEACTIVATED),
                UserStatus.ACTIVE, EnumSet.of(UserStatus.SUSPENDED, UserStatus.DEACTIVATED),
                UserStatus.SUSPENDED, EnumSet.of(UserStatus.ACTIVE, UserStatus.DEACTIVATED),
                UserStatus.DEACTIVATED, EnumSet.noneOf(UserStatus.class)
        );

        return Stream.of(UserStatus.values()).flatMap(from -> Stream.of(UserStatus.values()).map(to ->
                DynamicTest.dynamicTest(from + " -> " + to, () -> {
                    boolean expected = allowed.get(from).contains(to);
                    assertEquals(expected, from.canTransitionTo(to));
                    if (expected) {
                        from.validateTransitionTo(to);
                    } else {
                        assertThrows(InvalidUserStatusException.class, () -> from.validateTransitionTo(to));
                    }
                })));
    }

    @Test
    @DisplayName("canTransitionTo(null) is false and validateTransitionTo(null) is a programming error")
    void nullTargets() {
        assertFalse(UserStatus.ACTIVE.canTransitionTo(null));
        assertThrows(NullPointerException.class, () -> UserStatus.ACTIVE.validateTransitionTo(null));
    }
}
