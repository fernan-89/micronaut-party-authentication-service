package com.thinklab.application.usecase;

import com.thinklab.application.dto.event.UserInitiatedEvent;
import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.mapper.UserMapper;
import com.thinklab.domain.exception.DuplicateUserException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the business flow for User creation (BIAN Behavior Qualifier: {@code initiate}).
 */
@Singleton
public class InitiateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateUserUseCase.class);
    private static final String USER_INITIATED_SUBJECT = "thinklab.party-authentication.user.initiated";

    private final HashServicePort hashServicePort;
    private final UserRepository userRepository;
    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    public InitiateUserUseCase(HashServicePort hashServicePort, UserRepository userRepository,
                                OutboxStore outboxStore, ObjectMapper objectMapper) {
        this.hashServicePort = hashServicePort;
        this.userRepository = userRepository;
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
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
                            .flatMap(userRepository::create)
                            .flatMap(this::publishUserInitiated)
                            .map(UserMapper::toResponse);
                });
    }

    private Mono<User> publishUserInitiated(User user) {
        return Mono.defer(() -> {
                    UserInitiatedEvent payload = new UserInitiatedEvent(
                            user.getId(), user.getOrganisationId(), user.getEmail(), user.getFullName(), java.time.Instant.now());
                    String payloadJson;
                    try {
                        payloadJson = objectMapper.writeValueAsString(payload);
                    } catch (java.io.IOException e) {
                        return Mono.<OutboxEvent>error(e);
                    }
                    return outboxStore.append(OutboxEvent.newEvent(USER_INITIATED_SUBJECT, payloadJson));
                })
                .onErrorResume(e -> {
                    log.error("[EVENTS] Failed to append user.initiated outbox event for user [{}]: {}", user.getId(), e.getMessage(), e);
                    return Mono.empty();
                })
                .thenReturn(user);
    }
}
