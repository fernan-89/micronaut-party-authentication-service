package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.mapper.UserMapper;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the retrieval of a single User (BIAN Behavior Qualifier: {@code retrieve}).
 */
@Singleton
public class RetrieveUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveUserUseCase.class);

    private final UserRepository userRepository;

    public RetrieveUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<UserResponse> execute(UUID id) {
        log.info("[USE CASE] Retrieving user with ID: {}", id);

        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .map(UserMapper::toResponse);
    }
}
