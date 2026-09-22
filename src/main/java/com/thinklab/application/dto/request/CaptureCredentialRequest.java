package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload of {@code credential/update}: the new password (never logged, never returned). */
@Serdeable
public record CaptureCredentialRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128, message = "Password must have between 12 and 128 characters")
        String password
) {}
