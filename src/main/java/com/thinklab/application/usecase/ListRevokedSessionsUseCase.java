package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.RevokedSessionsResponse;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.kit.security.SecurityProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

/**
 * Publishes the revoked sessions other services must refuse. A revocation only matters until the latest access token
 * of that session has expired, so entries older than one access-token lifetime are left out.
 */
@Singleton
public class ListRevokedSessionsUseCase {

    private final SessionRepository sessionRepository;
    private final SecurityProperties properties;
    private final Clock clock;

    @Inject
    public ListRevokedSessionsUseCase(SessionRepository sessionRepository, SecurityProperties properties) {
        this(sessionRepository, properties, Clock.systemUTC());
    }

    ListRevokedSessionsUseCase(SessionRepository sessionRepository, SecurityProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public Mono<RevokedSessionsResponse> execute() {
        Instant since = clock.instant().minusSeconds(properties.getTtlSeconds());
        return sessionRepository.findRevokedSince(since)
                .map(revoked -> new RevokedSessionsResponse.Entry(revoked.sessionId(), revoked.revokedAt().plusSeconds(properties.getTtlSeconds()).getEpochSecond()))
                .collectList()
                .map(RevokedSessionsResponse::new);
    }
}
