package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates updates to basic User info (BIAN Behavior Qualifier: {@code update}).
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

        return userRepository.updateBasicInfo(id, request.fullName(), request.role());
    }
}
