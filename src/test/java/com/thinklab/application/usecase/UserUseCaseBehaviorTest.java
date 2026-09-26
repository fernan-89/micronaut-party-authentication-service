package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Complements {@link UserUseCaseTest} with failure paths, argument capture and every control action. */
@ExtendWith(MockitoExtension.class)
class UserUseCaseBehaviorTest {

    @Mock private UserRepository userRepository;
    @Mock private HashServicePort hashServicePort;
    @Mock private UserCreationWriter userCreationWriter;

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
    @DisplayName("Initiate: persists a PENDING aggregate built from the request, sovereign ID and tenant")
    void initiatePersistsPendingAggregate() {
        UUID sovereign = UUID.randomUUID();
        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "grace@thinklab.com")).thenReturn(Mono.just(false));
        when(hashServicePort.generateSovereignId("user-creation")).thenReturn(Mono.just(sovereign));
        when(userCreationWriter.createAndPublish(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateUserUseCase(hashServicePort, userRepository, userCreationWriter)
                        .execute(organisationId, new InitiateUserRequest("Grace Hopper", "grace@thinklab.com", UserRole.ADMIN)))
                .assertNext(res -> {
                    assertEquals(sovereign, res.id());
                    assertEquals(organisationId, res.organisationId());
                    assertEquals("PENDING", res.status());
                    assertEquals("ADMIN", res.role());
                })
                .verifyComplete();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userCreationWriter).createAndPublish(captor.capture());
        assertEquals("grace@thinklab.com", captor.getValue().getEmail());
    }

    @Test
    @DisplayName("Initiate: a duplicate email never reaches the hash service or the transactional writer")
    void initiateDuplicateShortCircuits() {
        when(userRepository.existsByOrganisationIdAndEmail(organisationId, "ada@thinklab.com")).thenReturn(Mono.just(true));

        StepVerifier.create(new InitiateUserUseCase(hashServicePort, userRepository, userCreationWriter)
                        .execute(organisationId, new InitiateUserRequest("Ada", "ada@thinklab.com", UserRole.VIEWER)))
                .expectErrorSatisfies(error -> assertEquals("ERR-USR-00409", ((com.thinklab.domain.exception.BusinessException) error).getErrorCode()))
                .verify();

        verifyNoInteractions(hashServicePort);
        verifyNoInteractions(userCreationWriter);
    }

    @Test
    @DisplayName("Initiate: a hash-service failure is propagated and nothing is persisted")
    void initiateHashFailure() {
        when(userRepository.existsByOrganisationIdAndEmail(any(), anyString())).thenReturn(Mono.just(false));
        when(hashServicePort.generateSovereignId(anyString())).thenReturn(Mono.error(new IllegalStateException("hash down")));

        StepVerifier.create(new InitiateUserUseCase(hashServicePort, userRepository, userCreationWriter)
                        .execute(organisationId, new InitiateUserRequest("Ada", "ada@thinklab.com", UserRole.VIEWER)))
                .expectErrorMessage("hash down")
                .verify();

        verifyNoInteractions(userCreationWriter);
    }

    @Test
    @DisplayName("Retrieve collection: forwards the status filter")
    void retrieveCollectionWithStatus() {
        when(userRepository.findAllByOrganisationId(organisationId, UserStatus.ACTIVE)).thenReturn(Flux.just(user, user));

        StepVerifier.create(new RetrieveUsersUseCase(userRepository).execute(organisationId, UserStatus.ACTIVE))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Update: loads the aggregate, applies the domain change, then issues the granular update")
    void updateLoadsThenPersists() {
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.updateBasicInfo(userId, "Ada L.", UserRole.ADMIN)).thenReturn(Mono.empty());

        StepVerifier.create(new UpdateUserUseCase(userRepository).execute(userId, new UpdateUserRequest("Ada L.", UserRole.ADMIN)))
                .verifyComplete();

        verify(userRepository).updateBasicInfo(userId, "Ada L.", UserRole.ADMIN);
    }

    @Test
    @DisplayName("Update: 404 when the user does not exist, and nothing is written")
    void updateNotFound() {
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(new UpdateUserUseCase(userRepository).execute(userId, new UpdateUserRequest("x", UserRole.ADMIN)))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(userRepository, never()).updateBasicInfo(any(), any(), any());
    }

    @Test
    @DisplayName("Update: a DEACTIVATED user cannot be edited and nothing is written")
    void updateRejectedForDeactivatedUser() {
        user.deactivate();
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));

        StepVerifier.create(new UpdateUserUseCase(userRepository).execute(userId, new UpdateUserRequest("x", UserRole.ADMIN)))
                .expectError(InvalidUserStatusException.class)
                .verify();

        verify(userRepository, never()).updateBasicInfo(any(), any(), any());
    }

    @Test
    @DisplayName("Control: SUSPEND and DEACTIVATE drive the matching persistence updates")
    void controlSuspendAndDeactivate() {
        user.activate();
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.updateStatus(userId, UserStatus.SUSPENDED)).thenReturn(Mono.empty());
        when(userRepository.updateStatus(userId, UserStatus.DEACTIVATED)).thenReturn(Mono.empty());
        ControlUserUseCase useCase = new ControlUserUseCase(userRepository, noopRevoker());

        StepVerifier.create(useCase.execute(userId, ControlUserUseCase.Action.SUSPEND)).verifyComplete();
        StepVerifier.create(useCase.execute(userId, ControlUserUseCase.Action.DEACTIVATE)).verifyComplete();

        verify(userRepository).updateStatus(userId, UserStatus.SUSPENDED);
        verify(userRepository).updateStatus(userId, UserStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("Control: 404 when the user does not exist")
    void controlNotFound() {
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlUserUseCase(userRepository, noopRevoker()).execute(userId, ControlUserUseCase.Action.ACTIVATE))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Control: an illegal move (SUSPEND a PENDING user) never reaches the repository")
    void controlIllegalMoveNeverWrites() {
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));

        StepVerifier.create(new ControlUserUseCase(userRepository, noopRevoker()).execute(userId, ControlUserUseCase.Action.SUSPEND))
                .expectError(InvalidUserStatusException.class)
                .verify();

        verify(userRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("Control: each action maps to its target status")
    void controlActionTargets() {
        assertEquals(UserStatus.ACTIVE, ControlUserUseCase.Action.ACTIVATE.targetStatus());
        assertEquals(UserStatus.SUSPENDED, ControlUserUseCase.Action.SUSPEND.targetStatus());
        assertEquals(UserStatus.DEACTIVATED, ControlUserUseCase.Action.DEACTIVATE.targetStatus());
    }

    private static SessionRevoker noopRevoker() {
        SessionRevoker revoker = org.mockito.Mockito.mock(SessionRevoker.class);
        org.mockito.Mockito.lenient().when(revoker.revokeAllOf(org.mockito.ArgumentMatchers.any())).thenReturn(reactor.core.publisher.Mono.empty());
        return revoker;
    }
}
