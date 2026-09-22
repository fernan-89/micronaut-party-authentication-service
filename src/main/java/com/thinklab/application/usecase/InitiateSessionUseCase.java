package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.port.PasswordHasher;
import com.thinklab.domain.repository.CredentialRepository;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.security.JwtService;
import com.thinklab.kit.security.Role;
import com.thinklab.kit.security.SecurityProperties;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Behavior Qualifier {@code session/initiate}: authenticates an ACTIVE user with email and password and issues an
 * access token. Every failure (unknown user, no credential, wrong password, inactive user) is the same generic 401,
 * and an unknown user still pays the hashing cost so response time does not reveal which emails exist.
 */
@Singleton
public class InitiateSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateSessionUseCase.class);
    private static final String DUMMY_PASSWORD = "thinklab-timing-equalisation";

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private volatile String dummyHash;

    public InitiateSessionUseCase(UserRepository userRepository, CredentialRepository credentialRepository,
                                  PasswordHasher passwordHasher, JwtService jwtService, SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    public Mono<SessionResponse> execute(InitiateSessionRequest request) {
        return userRepository.findByOrganisationIdAndEmail(request.organisationId(), request.email())
                .flatMap(user -> credentialRepository.findHash(user.getId())
                        .flatMap(hash -> verify(request.password(), hash)
                                .filter(matches -> matches && user.getStatus() == UserStatus.ACTIVE)
                                .map(matches -> user)))
                .switchIfEmpty(Mono.defer(() -> verify(request.password(), dummyHash()).then(Mono.<User>empty())))
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .map(this::toSession)
                .doOnError(error -> log.warn("[USE CASE] Session initiation rejected for organisation {}", request.organisationId()));
    }

    private Mono<Boolean> verify(String rawPassword, String hash) {
        return Mono.fromCallable(() -> passwordHasher.matches(rawPassword, hash)).subscribeOn(Schedulers.boundedElastic());
    }

    private String dummyHash() {
        String current = dummyHash;
        if (current == null) {
            current = passwordHasher.hash(DUMMY_PASSWORD);
            dummyHash = current;
        }
        return current;
    }

    private SessionResponse toSession(User user) {
        UserRole userRole = user.getRole() != null ? user.getRole() : UserRole.VIEWER;
        String token = jwtService.issue(user.getId().toString(), user.getOrganisationId().toString(), Role.valueOf(userRole.name()));
        return new SessionResponse(token, "Bearer", securityProperties.getTtlSeconds());
    }
}
