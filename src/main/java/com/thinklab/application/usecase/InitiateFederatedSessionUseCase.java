package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.FederatedSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Behavior Qualifier {@code session/federated}: opens a login session for a user that the identity-federation service has
 * authenticated against an external identity provider (ADR-022). The platform keeps ONE issuer: the access token and the
 * refresh token are the same ones a password login produces, so rotation, theft detection and revocation work unchanged.
 *
 * <p>Only a SERVICE may call it (the verified {@code X-Role}; with security off there is no role and nothing is enforced, like
 * every other role check). The user must exist in that organisation and be ACTIVE; every failure is the same generic 401 as a
 * wrong password, so the answer never says which part was wrong.
 */
@Singleton
public class InitiateFederatedSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateFederatedSessionUseCase.class);

    private final UserRepository userRepository;
    private final SessionIssuer sessionIssuer;

    public InitiateFederatedSessionUseCase(UserRepository userRepository, SessionIssuer sessionIssuer) {
        this.userRepository = userRepository;
        this.sessionIssuer = sessionIssuer;
    }

    public Mono<SessionResponse> execute(FederatedSessionRequest request, String role) {
        if (role != null && !"SERVICE".equals(role)) {
            return Mono.error(new OperationForbiddenException("Only a service may open a federated session."));
        }
        return userRepository.findById(request.userId())
                .filter(user -> user.getOrganisationId().equals(request.organisationId()) && user.getStatus() == UserStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(user -> sessionIssuer.issue(user, UUID.randomUUID().toString()))
                .doOnError(error -> log.warn("[USE CASE] Federated session rejected for organisation {}", request.organisationId()));
    }
}
