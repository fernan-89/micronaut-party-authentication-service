package com.thinklab.application.usecase;

import com.thinklab.application.dto.event.UserInitiatedEvent;
import com.thinklab.domain.model.User;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * Persists a new {@link User} and its {@code user.initiated} outbox event as a single atomic write (kit
 * ADR-003's 0.4.2 addendum: the local MongoDB replica set now supports real multi-document transactions).
 * Split out of {@link InitiateUserUseCase} because Micronaut's {@code @Transactional} interceptor only
 * triggers through the bean proxy — a private method on that same class, called via {@code this::}, would
 * bypass it entirely (self-invocation).
 */
@Singleton
public class UserCreationWriter {

    private static final String USER_INITIATED_SUBJECT = "thinklab.party-authentication.user.initiated";

    private final UserRepository userRepository;
    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    public UserCreationWriter(UserRepository userRepository, OutboxStore outboxStore, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Mono<User> createAndPublish(User user) {
        return userRepository.create(user)
                .flatMap(created -> outboxStore.append(toOutboxEvent(created)).thenReturn(created));
    }

    private OutboxEvent toOutboxEvent(User user) {
        UserInitiatedEvent payload = new UserInitiatedEvent(
                user.getId(), user.getOrganisationId(), user.getEmail(), user.getFullName(), Instant.now());
        try {
            return OutboxEvent.newEvent(USER_INITIATED_SUBJECT, objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
