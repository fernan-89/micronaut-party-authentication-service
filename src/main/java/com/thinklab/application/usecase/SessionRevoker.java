package com.thinklab.application.usecase;

import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.kit.security.RevocationList;
import com.thinklab.kit.security.SecurityProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Revokes login sessions. The revocation is persisted (so refresh stops working and other services learn of it by
 * polling) and applied to this service's own {@link RevocationList} at once.
 */
@Singleton
public class SessionRevoker {

    private final SessionRepository sessionRepository;
    private final RevocationList revocationList;
    private final SecurityProperties properties;
    private final Clock clock;

    @Inject
    public SessionRevoker(SessionRepository sessionRepository, RevocationList revocationList, SecurityProperties properties) {
        this(sessionRepository, revocationList, properties, Clock.systemUTC());
    }

    SessionRevoker(SessionRepository sessionRepository, RevocationList revocationList, SecurityProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.revocationList = revocationList;
        this.properties = properties;
        this.clock = clock;
    }

    /** Revokes one session. */
    public Mono<Void> revoke(String sessionId) {
        Instant now = clock.instant();
        return sessionRepository.revokeSession(sessionId, now)
                .doOnSuccess(ignored -> revocationList.revoke(sessionId, now.plusSeconds(properties.getTtlSeconds())));
    }

    /** Revokes every session ever issued to the user. */
    public Mono<Void> revokeAllOf(UUID userId) {
        return sessionRepository.findSessionIdsByUserId(userId).concatMap(this::revoke).then();
    }
}
