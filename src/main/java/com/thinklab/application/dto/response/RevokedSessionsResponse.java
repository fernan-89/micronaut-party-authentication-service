package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** The sessions whose access tokens must be refused, each until the latest access token of that session has expired. */
@Serdeable
public record RevokedSessionsResponse(List<Entry> revoked) {

    @Serdeable
    public record Entry(String sid, long until) {
    }
}
