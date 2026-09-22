package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Payload of {@code session/initiate}: the organisation, the user email and the password. */
@Serdeable
public record InitiateSessionRequest(
        @NotNull(message = "Organisation ID is required")
        UUID organisationId,
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        @NotBlank(message = "Password is required")
        String password
) {}
