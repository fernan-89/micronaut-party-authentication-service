package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserMapperTest {

    @Test
    @DisplayName("toDomain should create a PENDING user from the request, sovereign ID and tenant")
    void toDomain() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();

        User user = UserMapper.toDomain(new InitiateUserRequest("Ada", "ada@thinklab.com", UserRole.ADMIN), id, org);

        assertEquals(id, user.getId());
        assertEquals(org, user.getOrganisationId());
        assertEquals("Ada", user.getFullName());
        assertEquals("ada@thinklab.com", user.getEmail());
        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(UserStatus.PENDING, user.getStatus());
    }

    @Test
    @DisplayName("toResponse should flatten enums to names and copy every field")
    void toResponse() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-01-02T00:00:00Z");
        User user = User.reconstitute(id, org, "Ada", "ada@thinklab.com", UserRole.VIEWER, UserStatus.SUSPENDED, created, updated);

        UserResponse response = UserMapper.toResponse(user);

        assertEquals(id, response.id());
        assertEquals(org, response.organisationId());
        assertEquals("Ada", response.fullName());
        assertEquals("ada@thinklab.com", response.email());
        assertEquals("VIEWER", response.role());
        assertEquals("SUSPENDED", response.status());
        assertEquals(created, response.createdAt());
        assertEquals(updated, response.updatedAt());
    }

    @Test
    @DisplayName("toResponse should tolerate a reconstituted user without a role")
    void toResponseNullRole() {
        User user = User.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Ada", "a@b.c", null, null, null, null);

        assertEquals(null, UserMapper.toResponse(user).role());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<UserMapper> constructor = UserMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
