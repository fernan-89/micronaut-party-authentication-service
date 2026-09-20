package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserEntityTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private static UserEntity entity(UUID id, UUID org, String name, String email, UserStatus status, Instant created) {
        return new UserEntity(id, org, name, email, UserRole.ADMIN, status, created, created, null);
    }

    @Test
    @DisplayName("fromDomain / toDomain round-trips the aggregate; a new entity has no @Version yet")
    void roundTrip() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        User user = User.reconstitute(id, org, "Ada", "ada@thinklab.com", UserRole.VIEWER, UserStatus.SUSPENDED, NOW, NOW.plusSeconds(60));

        UserEntity entity = UserEntity.fromDomain(user);
        User restored = entity.toDomain();

        assertNull(entity.version());
        assertEquals(id, restored.getId());
        assertEquals(org, restored.getOrganisationId());
        assertEquals("Ada", restored.getFullName());
        assertEquals("ada@thinklab.com", restored.getEmail());
        assertEquals(UserRole.VIEWER, restored.getRole());
        assertEquals(UserStatus.SUSPENDED, restored.getStatus());
        assertEquals(NOW, restored.getCreatedAt());
        assertEquals(NOW.plusSeconds(60), restored.getUpdatedAt());
    }

    @Test
    @DisplayName("fromDomain rejects a null aggregate")
    void fromDomainNull() {
        assertThrows(NullPointerException.class, () -> UserEntity.fromDomain(null));
    }

    @Test
    @DisplayName("the canonical constructor enforces every persistence invariant")
    void invariants() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> entity(null, org, "n", "e", UserStatus.ACTIVE, NOW));
        assertThrows(NullPointerException.class, () -> entity(id, null, "n", "e", UserStatus.ACTIVE, NOW));
        assertThrows(NullPointerException.class, () -> entity(id, org, null, "e", UserStatus.ACTIVE, NOW));
        assertThrows(NullPointerException.class, () -> entity(id, org, "n", null, UserStatus.ACTIVE, NOW));
        assertThrows(NullPointerException.class, () -> entity(id, org, "n", "e", null, NOW));
        assertThrows(NullPointerException.class, () -> entity(id, org, "n", "e", UserStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> entity(id, org, "  ", "e", UserStatus.ACTIVE, NOW));
        assertThrows(IllegalArgumentException.class, () -> entity(id, org, "n", "  ", UserStatus.ACTIVE, NOW));
    }
}
