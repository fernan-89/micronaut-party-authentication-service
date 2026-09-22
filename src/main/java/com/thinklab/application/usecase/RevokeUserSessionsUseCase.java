package com.thinklab.application.usecase;

import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Behavior Qualifier {@code session/control/revoke}: revokes every session of a user (forced logout). Allowed for an
 * administrator, a service or the user themselves; a user of another tenant is reported as not found. When no role is
 * supplied (security disabled in local development) the authorisation check is skipped.
 */
@Singleton
public class RevokeUserSessionsUseCase {

    private final UserRepository userRepository;
    private final SessionRevoker sessionRevoker;

    public RevokeUserSessionsUseCase(UserRepository userRepository, SessionRevoker sessionRevoker) {
        this.userRepository = userRepository;
        this.sessionRevoker = sessionRevoker;
    }

    public Mono<Void> execute(UUID tenantId, UUID userId, String executor, String role) {
        if (role != null && !"ADMIN".equals(role) && !"SERVICE".equals(role) && !userId.toString().equals(executor)) {
            return Mono.error(new OperationForbiddenException("Only an administrator or the user themselves may revoke these sessions."));
        }
        return userRepository.findById(userId)
                .filter(user -> user.getOrganisationId().equals(tenantId))
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
                .flatMap(user -> sessionRevoker.revokeAllOf(userId));
    }
}
