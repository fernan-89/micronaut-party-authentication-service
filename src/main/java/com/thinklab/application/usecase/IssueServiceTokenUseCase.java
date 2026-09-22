package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.kit.security.JwtSigner;
import com.thinklab.kit.security.Role;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Behavior Qualifier {@code token/service}: OAuth2-style client credentials for other ThinkLab services and operators.
 * The registered client id becomes the token subject; the token carries the SERVICE role and no session. Unknown
 * clients, wrong secrets and blank secrets are the same generic 401.
 */
@Singleton
public class IssueServiceTokenUseCase {

    private static final String PLATFORM_TENANT = "platform";

    private final JwtSigner jwtSigner;
    private final Map<String, String> clients;

    @Inject
    public IssueServiceTokenUseCase(JwtSigner jwtSigner, @Nullable @Property(name = "thinklab.security.service-clients") Map<String, String> clients) {
        this.jwtSigner = jwtSigner;
        this.clients = clients != null ? clients : Map.of();
    }

    public Mono<SessionResponse> execute(String clientId, String clientSecret) {
        String expected = clientId == null ? null : clients.get(clientId);
        boolean valid = expected != null && !expected.isBlank() && clientSecret != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), clientSecret.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            return Mono.error(new InvalidCredentialsException());
        }
        return Mono.just(new SessionResponse(jwtSigner.issue(clientId, PLATFORM_TENANT, Role.SERVICE, null), "Bearer", jwtSigner.ttlSeconds(), null, 0));
    }
}
