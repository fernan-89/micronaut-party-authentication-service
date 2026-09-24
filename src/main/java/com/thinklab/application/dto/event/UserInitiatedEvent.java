package com.thinklab.application.dto.event;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-service event payload published on {@code thinklab.party-authentication.user.initiated}
 * (kit ADR-003) whenever a new User is created. This is a public contract: any consuming service
 * (starting with notification-dispatch) parses this exact shape.
 */
@Serdeable
public record UserInitiatedEvent(UUID id, UUID organisationId, String email, String fullName, Instant occurredAt) {
}
