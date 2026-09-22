package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

/**
 * The issued credentials: a short-lived access token and a long-lived, single-use refresh token. Presenting a used
 * refresh token again revokes the whole session.
 */
@Serdeable
public record SessionResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, long refreshExpiresIn) {
}
