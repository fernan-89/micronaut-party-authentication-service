package com.thinklab.domain.model;

import java.time.Instant;

/** A login session that has been revoked at the given instant. */
public record RevokedSession(String sessionId, Instant revokedAt) {
}
