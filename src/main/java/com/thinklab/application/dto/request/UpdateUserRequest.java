package com.thinklab.application.dto.request;

import com.thinklab.domain.model.User.UserRole;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating basic User info (BIAN Behavior Qualifier: {@code update}).
 */
@Serdeable
public record UpdateUserRequest(
        @NotBlank(message = "Full Name is required")
        String fullName,

        @NotNull(message = "Role is required")
        UserRole role
) {}
