package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.domain.exception.DuplicateUserException;
import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HashServicePort hashServicePort;

    @Mock
    private OutboxStore outboxStore;

    @Mock
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID organisationId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        user = User.createNew(userId, organisationId, "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
    }

    @Test
    void testInitiateUserUseCase() throws Exception {
        InitiateUserUseCase useCase = new InitiateUserUseCase(hashServicePort, userRepository, outboxStore, objectMapper);
        InitiateUserRequest request = new InitiateUserRequest("Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);

        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "ada@thinklab.com")).thenReturn(Mono.just(false));
        when(hashServicePort.generateSovereignId("user-creation")).thenReturn(Mono.just(userId));
        when(userRepository.create(any(User.class))).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(res -> {
                    assertEquals(userId, res.id());
                    assertEquals("Ada Lovelace", res.fullName());
                })
                .verifyComplete();
    }

    @Test
    void testInitiateUserUseCasePublishesEventEvenWhenOutboxAppendFails() throws Exception {
        InitiateUserUseCase useCase = new InitiateUserUseCase(hashServicePort, userRepository, outboxStore, objectMapper);
        InitiateUserRequest request = new InitiateUserRequest("Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);

        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "ada@thinklab.com")).thenReturn(Mono.just(false));
        when(hashServicePort.generateSovereignId("user-creation")).thenReturn(Mono.just(userId));
        when(userRepository.create(any(User.class))).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(any(OutboxEvent.class))).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(res -> assertEquals(userId, res.id()))
                .verifyComplete();
    }

    @Test
    void testInitiateUserUseCaseStillCompletesWhenPayloadSerializationFails() throws Exception {
        InitiateUserUseCase useCase = new InitiateUserUseCase(hashServicePort, userRepository, outboxStore, objectMapper);
        InitiateUserRequest request = new InitiateUserRequest("Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);

        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "ada@thinklab.com")).thenReturn(Mono.just(false));
        when(hashServicePort.generateSovereignId("user-creation")).thenReturn(Mono.just(userId));
        when(userRepository.create(any(User.class))).thenReturn(Mono.just(user));
        when(objectMapper.writeValueAsString(any())).thenThrow(new java.io.IOException("bad payload"));

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(res -> assertEquals(userId, res.id()))
                .verifyComplete();
    }

    @Test
    void testInitiateUserUseCaseRejectsDuplicateEmail() {
        InitiateUserUseCase useCase = new InitiateUserUseCase(hashServicePort, userRepository, outboxStore, objectMapper);
        InitiateUserRequest request = new InitiateUserRequest("Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);

        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "ada@thinklab.com")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(organisationId, request))
                .expectError(DuplicateUserException.class)
                .verify();
    }

    @Test
    void testRetrieveUserUseCaseSuccess() {
        RetrieveUserUseCase useCase = new RetrieveUserUseCase(userRepository);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));

        StepVerifier.create(useCase.execute(userId))
                .assertNext(res -> assertEquals("Ada Lovelace", res.fullName()))
                .verifyComplete();
    }

    @Test
    void testRetrieveUserUseCaseNotFound() {
        RetrieveUserUseCase useCase = new RetrieveUserUseCase(userRepository);
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void testRetrieveUsersUseCase() {
        RetrieveUsersUseCase useCase = new RetrieveUsersUseCase(userRepository);
        when(userRepository.findAllByOrganisationId(organisationId, null)).thenReturn(Flux.just(user));

        StepVerifier.create(useCase.execute(organisationId, null))
                .assertNext(res -> assertEquals("Ada Lovelace", res.fullName()))
                .verifyComplete();
    }

    @Test
    void testUpdateUserUseCase() {
        UpdateUserUseCase useCase = new UpdateUserUseCase(userRepository);
        UpdateUserRequest request = new UpdateUserRequest("Ada L.", UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.updateBasicInfo(userId, "Ada L.", UserRole.ADMIN)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, request))
                .verifyComplete();
    }

    @Test
    void testControlUserUseCaseActivate() {
        ControlUserUseCase useCase = new ControlUserUseCase(userRepository, noopRevoker());
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.updateStatus(userId, UserStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, ControlUserUseCase.Action.ACTIVATE))
                .verifyComplete();
    }

    @Test
    void testControlUserUseCaseRejectsIllegalTransition() {
        ControlUserUseCase useCase = new ControlUserUseCase(userRepository, noopRevoker());
        user.activate();
        user.deactivate();
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));

        StepVerifier.create(useCase.execute(userId, ControlUserUseCase.Action.ACTIVATE))
                .expectError(InvalidUserStatusException.class)
                .verify();
    }

    private static SessionRevoker noopRevoker() {
        SessionRevoker revoker = org.mockito.Mockito.mock(SessionRevoker.class);
        org.mockito.Mockito.lenient().when(revoker.revokeAllOf(org.mockito.ArgumentMatchers.any())).thenReturn(reactor.core.publisher.Mono.empty());
        return revoker;
    }
}
