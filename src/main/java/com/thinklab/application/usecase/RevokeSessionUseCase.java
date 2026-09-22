package com.thinklab.application.usecase;

import com.thinklab.domain.repository.SessionRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Behavior Qualifier {@code session/revoke}: logout. Revokes the session the refresh token belongs to. Idempotent and
 * silent: an unknown token completes normally so the endpoint cannot be used to probe for valid tokens.
 */
@Singleton
public class RevokeSessionUseCase {

    private final SessionRepository sessionRepository;
    private final SessionRevoker sessionRevoker;

    public RevokeSessionUseCase(SessionRepository sessionRepository, SessionRevoker sessionRevoker) {
        this.sessionRepository = sessionRepository;
        this.sessionRevoker = sessionRevoker;
    }

    public Mono<Void> execute(String refreshToken) {
        return sessionRepository.findRefreshToken(SessionIssuer.hash(refreshToken))
                .flatMap(record -> sessionRevoker.revoke(record.sessionId()));
    }
}
