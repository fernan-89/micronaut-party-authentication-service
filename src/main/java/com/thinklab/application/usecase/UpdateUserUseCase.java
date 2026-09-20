package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates updates to basic User info (BIAN Behavior Qualifier: {@code update}).
 *
 * <p>Loads the aggregate first and lets the domain validate the mutation (a DEACTIVATED user is
 * terminal and cannot be edited) before issuing the granular persistence update — never a blind
 * partial write.
 */
@Singleton
public class UpdateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateUserUseCase.class);

    private final UserRepository userRepository;

    public UpdateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<Void> execute(UUID id, UpdateUserRequest request) {
        log.info("[USE CASE] Updating basic info for user ID: {}", id);

        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(user -> {
                    user.updateProfile(request.fullName(), request.role());
                    return userRepository.updateBasicInfo(id, user.getFullName(), user.getRole());
                });
    }
}
