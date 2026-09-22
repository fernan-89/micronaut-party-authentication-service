package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

/** The issued access token. */
@Serdeable
public record SessionResponse(String accessToken, String tokenType, long expiresIn) {
}
