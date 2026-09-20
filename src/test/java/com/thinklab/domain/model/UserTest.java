package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User createPendingUser() {
        return User.createNew(UUID.randomUUID(), UUID.randomUUID(), "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
    }

    @Test
    @DisplayName("Should create a new User in PENDING state")
    void shouldCreateNewUserInPendingState() {
        User user = createPendingUser();

        assertEquals(UserStatus.PENDING, user.getStatus());
        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    @DisplayName("Should activate a PENDING user")
    void shouldActivatePendingUser() {
        User user = createPendingUser();

        user.activate();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("Should suspend and reactivate an ACTIVE user")
    void shouldSuspendAndReactivateActiveUser() {
        User user = createPendingUser();
        user.activate();

        user.suspend();
        assertEquals(UserStatus.SUSPENDED, user.getStatus());

        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("Should deactivate a user terminally and block any further transition")
    void shouldDeactivateTerminallyAndBlockFurtherTransitions() {
        User user = createPendingUser();
        user.activate();

        user.deactivate();

        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
        assertThrows(InvalidUserStatusException.class, user::activate,
                "Zero Trust Violation: no transition should be permitted out of terminal DEACTIVATED state.");
    }

    @Test
    @DisplayName("Should reject redundant self-transitions (idempotency violation)")
    void shouldRejectSelfTransition() {
        User user = createPendingUser();
        user.activate();

        assertThrows(InvalidUserStatusException.class, user::activate);
    }

    @Test
    @DisplayName("Should reject illegal PENDING -> SUSPENDED transition")
    void shouldRejectIllegalPendingToSuspended() {
        User user = createPendingUser();

        assertThrows(InvalidUserStatusException.class, user::suspend);
    }
}
