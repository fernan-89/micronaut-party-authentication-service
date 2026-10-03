package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload of {@code session/federated}: the organisation and the user a federation service (identity-federation) has already
 * authenticated against an external identity provider. Only a service may send it (ADR-022).
 */
@Serdeable
public record FederatedSessionRequest(
        @NotNull(message = "Organisation ID is required")
        UUID organisationId,

        @NotNull(message = "User ID is required")
        UUID userId
) {}
