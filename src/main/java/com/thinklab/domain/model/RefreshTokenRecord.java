package com.thinklab.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored refresh token. Only the SHA-256 hash of the opaque token is kept, so a database leak does not yield usable
 * tokens. {@code used} marks a rotated token: presenting it again is treated as theft and revokes the whole session.
 */
public record RefreshTokenRecord(String tokenHash, UUID userId, UUID organisationId, String sessionId, Instant expiresAt, boolean used) {
}
