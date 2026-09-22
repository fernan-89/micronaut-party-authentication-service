package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CaptureCredentialRequest;
import com.thinklab.application.dto.request.InitiateSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.application.usecase.CaptureCredentialUseCase;
import com.thinklab.application.usecase.InitiateSessionUseCase;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Behavior Qualifiers {@code session/initiate} (login, public) and {@code {id}/credential/update} (password change). */
@Controller("/party-authentication/v1")
public class CredentialController {

    private static final Logger log = LoggerFactory.getLogger(CredentialController.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String EXECUTOR_HEADER = "X-Executor";
    private static final String ROLE_HEADER = "X-Role";

    private final InitiateSessionUseCase initiateSessionUseCase;
    private final CaptureCredentialUseCase captureCredentialUseCase;

    public CredentialController(InitiateSessionUseCase initiateSessionUseCase, CaptureCredentialUseCase captureCredentialUseCase) {
        this.initiateSessionUseCase = initiateSessionUseCase;
        this.captureCredentialUseCase = captureCredentialUseCase;
    }

    @Post("/session/initiate")
    public Mono<HttpResponse<SessionResponse>> initiateSession(@Body @Valid InitiateSessionRequest request) {
        log.info("[ACTION: INITIATE_SESSION] Login attempt for organisation {}", request.organisationId());
        return initiateSessionUseCase.execute(request).map(HttpResponse::ok);
    }

    @Put("/{id}/credential/update")
    public Mono<HttpResponse<Void>> updateCredential(
            @PathVariable UUID id,
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid CaptureCredentialRequest request
    ) {
        log.info("[ACTION: UPDATE_CREDENTIAL] [ID: {}] [EXECUTOR: {}]", id, executor);
        return Mono.defer(() -> captureCredentialUseCase.execute(UUID.fromString(tenantId), id, executor, role, request))
                .then(Mono.just(HttpResponse.<Void>noContent()));
    }
}
