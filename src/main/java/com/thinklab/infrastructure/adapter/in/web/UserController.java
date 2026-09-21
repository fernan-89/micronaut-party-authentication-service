package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateUserRequest;
import com.thinklab.application.dto.request.UpdateUserRequest;
import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.usecase.ControlUserUseCase;
import com.thinklab.application.usecase.InitiateUserUseCase;
import com.thinklab.application.usecase.RetrieveUserUseCase;
import com.thinklab.application.usecase.RetrieveUsersUseCase;
import com.thinklab.application.usecase.UpdateUserUseCase;
import com.thinklab.domain.model.User.UserStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code party-authentication} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.User} is the
 * Control Record. Every route follows
 * {@code /party-authentication/v1/{control-record-id}/{behavior-qualifier}}. There is no
 * {@code DELETE}: {@code control/deactivate} is a terminal, soft status transition, never a
 * physical deletion.
 *
 * <p><b>Header-Sourced Forensics (ADR-013):</b> {@code X-Tenant-Id} (organisationId) is mandatory
 * on {@code initiate} and collection {@code retrieve}; {@code X-Executor} is mandatory on every
 * mutation.
 */
@Controller("/party-authentication/v1")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiateUserUseCase initiateUserUseCase;
    private final RetrieveUserUseCase retrieveUserUseCase;
    private final RetrieveUsersUseCase retrieveUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final ControlUserUseCase controlUserUseCase;

    public UserController(
            InitiateUserUseCase initiateUserUseCase,
            RetrieveUserUseCase retrieveUserUseCase,
            RetrieveUsersUseCase retrieveUsersUseCase,
            UpdateUserUseCase updateUserUseCase,
            ControlUserUseCase controlUserUseCase
    ) {
        this.initiateUserUseCase = initiateUserUseCase;
        this.retrieveUserUseCase = retrieveUserUseCase;
        this.retrieveUsersUseCase = retrieveUsersUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.controlUserUseCase = controlUserUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Creates a new User Control Record. */
    @Post("/initiate")
    public Mono<HttpResponse<UserResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateUserRequest request
    ) {
        log.info("[ACTION: INITIATE_USER] [EXECUTOR: {}] Received request to create user for organisation: {} email: {}", executor, tenantId, request.email());

        return initiateUserUseCase.execute(UUID.fromString(tenantId), request)
                .map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single User by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<UserResponse>> retrieveById(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_USER] Received request to get user by ID: {}", id);

        return retrieveUserUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists Users scoped to a tenant. */
    @Get("/retrieve")
    public Mono<List<UserResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable UserStatus status
    ) {
        log.info("[ACTION: RETRIEVE_USERS] Received request to list users for organisation: {} status: {}", tenantId, status);

        return Mono.defer(() -> retrieveUsersUseCase.execute(UUID.fromString(tenantId), status).collectList());
    }

    /** Behavior Qualifier: {@code update}. Updates basic User info. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid UpdateUserRequest request
    ) {
        log.info("[ACTION: UPDATE_USER] [EXECUTOR: {}] Received request to update user info for ID: {}", executor, id);

        return updateUserUseCase.execute(id, request).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/activate}. */
    @Put("/{id}/control/activate")
    public Mono<HttpResponse<Void>> controlActivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_USER] [EXECUTOR: {}] activate for ID: {}", executor, id);

        return controlUserUseCase.execute(id, ControlUserUseCase.Action.ACTIVATE).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/suspend}. */
    @Put("/{id}/control/suspend")
    public Mono<HttpResponse<Void>> controlSuspend(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_USER] [EXECUTOR: {}] suspend for ID: {}", executor, id);

        return controlUserUseCase.execute(id, ControlUserUseCase.Action.SUSPEND).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/deactivate}. Terminal, soft — no physical DELETE exists. */
    @Put("/{id}/control/deactivate")
    public Mono<HttpResponse<Void>> controlDeactivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_USER] [EXECUTOR: {}] deactivate for ID: {}", executor, id);

        return controlUserUseCase.execute(id, ControlUserUseCase.Action.DEACTIVATE).thenReturn(HttpResponse.noContent());
    }
}
