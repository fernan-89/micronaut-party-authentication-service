package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureCredentialRequest;
import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.port.PasswordHasher;
import com.thinklab.domain.repository.CredentialRepository;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * Behavior Qualifier {@code credential/update}: sets or replaces a user's password (USR-02).
 *
 * <p>Authorisation: only an ADMIN, a SERVICE or the user themselves may change a password. When no role is supplied
 * (security disabled in local development) the check is skipped. The user must belong to the caller's tenant; a user
 * of another tenant is reported as not found so its existence is not disclosed.
 */
@Singleton
public class CaptureCredentialUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureCredentialUseCase.class);

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;

    public CaptureCredentialUseCase(UserRepository userRepository, CredentialRepository credentialRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
    }

    public Mono<Void> execute(UUID tenantId, UUID userId, String executor, String role, CaptureCredentialRequest request) {
        log.info("[USE CASE] Updating credential of user {} by {}", userId, executor);
        if (role != null && !"ADMIN".equals(role) && !"SERVICE".equals(role) && !userId.toString().equals(executor)) {
            return Mono.error(new OperationForbiddenException("Only an administrator or the user themselves may change this credential."));
        }
        return userRepository.findById(userId)
                .filter(user -> user.getOrganisationId().equals(tenantId))
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
                .flatMap(user -> {
                    if (user.getStatus() == UserStatus.DEACTIVATED) {
                        return Mono.error(new InvalidUserStatusException("Compliance Violation: cannot set a credential for a DEACTIVATED user."));
                    }
                    return Mono.fromCallable(() -> passwordHasher.hash(request.password()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(hash -> credentialRepository.save(userId, hash));
                });
    }
}
