package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.domain.model.User;

import java.util.UUID;

/**
 * Static factory mapper for User DTOs and Domain Entities.
 * Enforces strict DTO Isolation Pattern.
 */
public final class UserMapper {

    private UserMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static User toDomain(InitiateUserRequest request, UUID sovereignId, UUID organisationId) {
        return User.createNew(sovereignId, organisationId, request.fullName(), request.email(), request.role());
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getOrganisationId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
