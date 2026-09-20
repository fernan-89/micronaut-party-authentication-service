package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for User Output Payload (Party Authentication Control Record).
 * Enforces the DTO Isolation Pattern by preventing the pure Domain Model from bleeding out
 * into the HTTP/External boundaries.
 */
@Serdeable
public record UserResponse(
        UUID id,
        UUID organisationId,
        String fullName,
        String email,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
