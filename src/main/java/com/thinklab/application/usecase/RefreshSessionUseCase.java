package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Behavior Qualifier {@code session/refresh}: exchanges a refresh token for a new access token and a new refresh token
 * (rotation). A refresh token works once. Presenting one that was already used means it leaked, so the whole session
 * is revoked. Unknown, expired, revoked and used tokens, and tokens of users that are no longer ACTIVE, all yield the
 * same generic 401.
 */
@Singleton
public class RefreshSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionUseCase.class);

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionIssuer sessionIssuer;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;

    @Inject
    public RefreshSessionUseCase(SessionRepository sessionRepository, UserRepository userRepository,
                                 SessionIssuer sessionIssuer, SessionRevoker sessionRevoker) {
        this(sessionRepository, userRepository, sessionIssuer, sessionRevoker, Clock.systemUTC());
    }

    RefreshSessionUseCase(SessionRepository sessionRepository, UserRepository userRepository,
                          SessionIssuer sessionIssuer, SessionRevoker sessionRevoker, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sessionIssuer = sessionIssuer;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
    }

    public Mono<SessionResponse> execute(String refreshToken) {
        String tokenHash = SessionIssuer.hash(refreshToken);
        return sessionRepository.findRefreshToken(tokenHash)
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(record -> sessionRepository.isSessionRevoked(record.sessionId())
                        .flatMap(revoked -> rotate(record, revoked, tokenHash)));
    }

    private Mono<SessionResponse> rotate(RefreshTokenRecord record, boolean sessionRevoked, String tokenHash) {
        if (sessionRevoked || !record.expiresAt().isAfter(clock.instant())) {
            return Mono.error(new InvalidCredentialsException());
        }
        return sessionRepository.markRefreshTokenUsed(tokenHash).flatMap(firstUse -> {
            if (!firstUse) {
                log.warn("[SECURITY] Refresh token reuse detected for session {}: revoking the session.", record.sessionId());
                return sessionRevoker.revoke(record.sessionId()).then(Mono.<SessionResponse>error(new InvalidCredentialsException()));
            }
            return userRepository.findById(record.userId())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .switchIfEmpty(Mono.defer(() -> sessionRevoker.revoke(record.sessionId()).then(Mono.error(new InvalidCredentialsException()))))
                    .flatMap(user -> sessionIssuer.issue(user, record.sessionId()));
        });
    }
}
