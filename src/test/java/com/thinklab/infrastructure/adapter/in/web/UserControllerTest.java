package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.usecase.ControlUserUseCase;
import com.thinklab.application.usecase.InitiateUserUseCase;
import com.thinklab.application.usecase.RetrieveUserUseCase;
import com.thinklab.application.usecase.RetrieveUsersUseCase;
import com.thinklab.application.usecase.UpdateUserUseCase;
import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String EXECUTOR = "iam-admin";

    @Mock private InitiateUserUseCase initiateUserUseCase;
    @Mock private RetrieveUserUseCase retrieveUserUseCase;
    @Mock private RetrieveUsersUseCase retrieveUsersUseCase;
    @Mock private UpdateUserUseCase updateUserUseCase;
    @Mock private ControlUserUseCase controlUserUseCase;

    @InjectMocks private UserController controller;

    private UUID organisationId;
    private UUID userId;
    private UserResponse sample;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sample = new UserResponse(userId, organisationId, "Ada Lovelace", "ada@thinklab.com", "OPERATOR", "PENDING",
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created and scopes the user to the tenant header")
    void initiate() {
        InitiateUserRequest request = new InitiateUserRequest("Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
        when(initiateUserUseCase.execute(organisationId, request)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.initiate(organisationId.toString(), EXECUTOR, request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatus());
                    assertEquals(userId, response.body().id());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("initiate rejects a malformed tenant header before reaching the use case")
    void initiateMalformedTenant() {
        InitiateUserRequest request = new InitiateUserRequest("n", "e@e.com", UserRole.VIEWER);

        assertThrows(IllegalArgumentException.class, () -> controller.initiate("not-a-uuid", EXECUTOR, request));
    }

    @Test
    @DisplayName("initiate propagates a domain error from the use case")
    void initiatePropagatesError() {
        when(initiateUserUseCase.execute(any(), any())).thenReturn(Mono.error(new UserNotFoundException("boom")));

        StepVerifier.create(controller.initiate(organisationId.toString(), EXECUTOR, new InitiateUserRequest("n", "e@e.com", UserRole.VIEWER)))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("retrieveById returns 200 OK")
    void retrieveById() {
        when(retrieveUserUseCase.execute(userId)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.retrieveById(userId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals("Ada Lovelace", response.body().fullName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveById surfaces not-found")
    void retrieveByIdNotFound() {
        when(retrieveUserUseCase.execute(userId)).thenReturn(Mono.error(new UserNotFoundException(userId)));

        StepVerifier.create(controller.retrieveById(userId)).expectError(UserNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveAll scopes by tenant with and without a status filter")
    void retrieveAll() {
        when(retrieveUsersUseCase.execute(organisationId, UserStatus.ACTIVE)).thenReturn(Flux.just(sample));
        when(retrieveUsersUseCase.execute(organisationId, null)).thenReturn(Flux.just(sample, sample));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), UserStatus.ACTIVE)).expectNext(sample).verifyComplete();
        StepVerifier.create(controller.retrieveAll(organisationId.toString(), null)).expectNextCount(2).verifyComplete();
    }

    @Test
    @DisplayName("retrieveAll rejects a malformed tenant header")
    void retrieveAllMalformedTenant() {
        assertThrows(IllegalArgumentException.class, () -> controller.retrieveAll("nope", null));
    }

    @Test
    @DisplayName("update returns 204 No Content")
    void update() {
        UpdateUserRequest request = new UpdateUserRequest("Ada L.", UserRole.ADMIN);
        when(updateUserUseCase.execute(userId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.update(userId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("each control endpoint dispatches its action and returns 204")
    void controlEndpoints() {
        when(controlUserUseCase.execute(eq(userId), any(ControlUserUseCase.Action.class))).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlActivate(userId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlSuspend(userId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlDeactivate(userId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();

        verify(controlUserUseCase).execute(userId, ControlUserUseCase.Action.ACTIVATE);
        verify(controlUserUseCase).execute(userId, ControlUserUseCase.Action.SUSPEND);
        verify(controlUserUseCase).execute(userId, ControlUserUseCase.Action.DEACTIVATE);
    }

    @Test
    @DisplayName("a control endpoint surfaces an illegal transition")
    void controlIllegal() {
        when(controlUserUseCase.execute(userId, ControlUserUseCase.Action.SUSPEND))
                .thenReturn(Mono.error(new InvalidUserStatusException("illegal")));

        StepVerifier.create(controller.controlSuspend(userId, EXECUTOR)).expectError(InvalidUserStatusException.class).verify();
    }
}
