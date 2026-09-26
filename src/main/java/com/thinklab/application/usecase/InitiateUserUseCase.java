package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.mapper.UserMapper;
import com.thinklab.domain.exception.DuplicateUserException;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the business flow for User creation (BIAN Behavior Qualifier: {@code initiate}). The
 * persistence + outbox append is delegated to {@link UserCreationWriter} so it runs as a single atomic
 * write (kit ADR-003's 0.4.2 addendum); everything else here — duplicate check, the hash service call —
 * deliberately stays outside that transaction boundary.
 */
@Singleton
public class InitiateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateUserUseCase.class);

    private final HashServicePort hashServicePort;
    private final UserRepository userRepository;
    private final UserCreationWriter userCreationWriter;

    public InitiateUserUseCase(HashServicePort hashServicePort, UserRepository userRepository, UserCreationWriter userCreationWriter) {
        this.hashServicePort = hashServicePort;
        this.userRepository = userRepository;
        this.userCreationWriter = userCreationWriter;
    }

    public Mono<UserResponse> execute(UUID organisationId, InitiateUserRequest request) {
        log.info("[USE CASE] Initiating user creation for organisation: {} email: {}", organisationId, request.email());

        return userRepository.existsByOrganisationIdAndEmail(organisationId, request.email())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new DuplicateUserException(
                                String.format("A User already exists for organisation [%s] and email [%s].", organisationId, request.email())));
                    }
                    return hashServicePort.generateSovereignId("user-creation")
                            .map(sovereignId -> UserMapper.toDomain(request, sovereignId, organisationId))
                            .flatMap(userCreationWriter::createAndPublish)
                            .map(UserMapper::toResponse);
                });
    }
}
