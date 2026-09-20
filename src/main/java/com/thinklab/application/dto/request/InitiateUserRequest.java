package com.thinklab.application.dto.request;

import com.thinklab.domain.model.User.UserRole;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for User Creation Request (BIAN Behavior Qualifier: {@code initiate}).
 * Acts as a protective barrier to the Domain Layer. organisationId travels via the
 * {@code X-Tenant-Id} header, not the body.
 */
@Serdeable
public record InitiateUserRequest(

        @NotBlank(message = "Full Name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotNull(message = "Role is required")
        UserRole role
) {}
