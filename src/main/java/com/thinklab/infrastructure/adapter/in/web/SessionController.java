package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.RefreshTokenRequest;
import com.thinklab.application.dto.response.RevokedSessionsResponse;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.application.usecase.IssueServiceTokenUseCase;
import com.thinklab.application.usecase.ListRevokedSessionsUseCase;
import com.thinklab.application.usecase.RefreshSessionUseCase;
import com.thinklab.application.usecase.RevokeSessionUseCase;
import com.thinklab.application.usecase.RevokeUserSessionsUseCase;
import com.thinklab.kit.security.LocalKeyStore;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Session lifecycle beyond login (ADR-021): refresh rotation, logout, forced logout, the published revocation list,
 * the service-token endpoint (client credentials) and the public key set. All of these except the forced logout are
 * public paths: they authenticate with a refresh token, a client secret or nothing at all (public keys).
 */
@Controller("/party-authentication/v1")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final RefreshSessionUseCase refreshSessionUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;
    private final RevokeUserSessionsUseCase revokeUserSessionsUseCase;
    private final ListRevokedSessionsUseCase listRevokedSessionsUseCase;
    private final IssueServiceTokenUseCase issueServiceTokenUseCase;
    private final LocalKeyStore localKeyStore;

    public SessionController(RefreshSessionUseCase refreshSessionUseCase, RevokeSessionUseCase revokeSessionUseCase,
                             RevokeUserSessionsUseCase revokeUserSessionsUseCase, ListRevokedSessionsUseCase listRevokedSessionsUseCase,
                             IssueServiceTokenUseCase issueServiceTokenUseCase, LocalKeyStore localKeyStore) {
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.revokeSessionUseCase = revokeSessionUseCase;
        this.revokeUserSessionsUseCase = revokeUserSessionsUseCase;
        this.listRevokedSessionsUseCase = listRevokedSessionsUseCase;
        this.issueServiceTokenUseCase = issueServiceTokenUseCase;
        this.localKeyStore = localKeyStore;
    }

    @Post("/session/refresh")
    public Mono<HttpResponse<SessionResponse>> refresh(@Body @Valid RefreshTokenRequest request) {
        log.info("[ACTION: REFRESH_SESSION] Refresh requested");
        return refreshSessionUseCase.execute(request.refreshToken()).map(HttpResponse::ok);
    }

    @Post("/session/revoke")
    public Mono<HttpResponse<Void>> revoke(@Body @Valid RefreshTokenRequest request) {
        log.info("[ACTION: REVOKE_SESSION] Logout requested");
        return revokeSessionUseCase.execute(request.refreshToken()).then(Mono.just(HttpResponse.<Void>noContent()));
    }

    @Get("/session/revoked")
    public Mono<HttpResponse<RevokedSessionsResponse>> revoked() {
        return listRevokedSessionsUseCase.execute().map(HttpResponse::ok);
    }

    @Put("/{id}/session/control/revoke")
    public Mono<HttpResponse<Void>> revokeAllSessions(
            @PathVariable UUID id,
            @Header("X-Tenant-Id") @NotBlank String tenantId,
            @Header("X-Executor") @NotBlank String executor,
            @Header("X-Role") @Nullable String role
    ) {
        log.info("[ACTION: REVOKE_USER_SESSIONS] [ID: {}] [EXECUTOR: {}]", id, executor);
        return Mono.defer(() -> revokeUserSessionsUseCase.execute(UUID.fromString(tenantId), id, executor, role))
                .then(Mono.just(HttpResponse.<Void>noContent()));
    }

    @Post(value = "/token/service", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    public Mono<HttpResponse<SessionResponse>> serviceToken(@Body("client_id") @Nullable String clientId,
                                                            @Body("client_secret") @Nullable String clientSecret) {
        log.info("[ACTION: ISSUE_SERVICE_TOKEN] Client credentials requested by {}", clientId);
        return issueServiceTokenUseCase.execute(clientId, clientSecret).map(HttpResponse::ok);
    }

    @Get("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return localKeyStore.publicSet().orElseThrow(() -> new IllegalStateException("The signing key is not initialised.")).toJSONObject();
    }
}
