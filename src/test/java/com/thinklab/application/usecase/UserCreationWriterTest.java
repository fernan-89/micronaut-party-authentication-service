package com.thinklab.application.usecase;

import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCreationWriterTest {

    @Mock private UserRepository userRepository;
    @Mock private OutboxStore outboxStore;
    @Mock private ObjectMapper objectMapper;

    private UserCreationWriter writer;
    private UUID userId;
    private UUID organisationId;
    private User user;

    @BeforeEach
    void setUp() {
        writer = new UserCreationWriter(userRepository, outboxStore, objectMapper);
        userId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        user = User.createNew(userId, organisationId, "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
    }

    @Test
    @DisplayName("createAndPublish persists the user then appends its outbox event, returning the created user")
    void createAndPublishSuccess() throws Exception {
        when(userRepository.create(user)).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(any(OutboxEvent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(writer.createAndPublish(user))
                .expectNext(user)
                .verifyComplete();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxStore).append(captor.capture());
        assertEquals("thinklab.party-authentication.user.initiated", captor.getValue().subject());
    }

    @Test
    @DisplayName("a failed outbox append propagates as an error - the write is now atomic, not best-effort")
    void createAndPublishPropagatesOutboxFailure() throws Exception {
        when(userRepository.create(user)).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(any(OutboxEvent.class))).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(writer.createAndPublish(user))
                .expectErrorMessage("mongo down")
                .verify();
    }

    @Test
    @DisplayName("a payload serialization failure propagates and the outbox is never called")
    void createAndPublishPropagatesSerializationFailure() throws Exception {
        when(userRepository.create(user)).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenThrow(new IOException("bad payload"));

        StepVerifier.create(writer.createAndPublish(user))
                .expectError(UncheckedIOException.class)
                .verify();

        verify(outboxStore, never()).append(any());
    }

    @Test
    @DisplayName("a repository failure propagates and the outbox is never called")
    void createAndPublishPropagatesRepositoryFailure() {
        when(userRepository.create(user)).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(writer.createAndPublish(user))
                .expectErrorMessage("mongo down")
                .verify();

        verify(outboxStore, never()).append(any());
    }
}
