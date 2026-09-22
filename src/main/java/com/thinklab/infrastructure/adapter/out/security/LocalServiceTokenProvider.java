package com.thinklab.infrastructure.adapter.out.security;

import com.thinklab.kit.security.ClientCredentialsTokenProvider;
import com.thinklab.kit.security.JwtSigner;
import com.thinklab.kit.security.Role;
import com.thinklab.kit.security.SecurityProperties;
import com.thinklab.kit.security.ServiceTokenProvider;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * The authentication service signs its own service tokens locally (no network hop, and it can start before it can
 * reach itself), replacing the kit client-credentials provider.
 */
@Singleton
@Replaces(ClientCredentialsTokenProvider.class)
@Requires(property = "thinklab.security.enabled", value = "true")
public class LocalServiceTokenProvider implements ServiceTokenProvider {

    private final JwtSigner jwtSigner;
    private final SecurityProperties properties;

    public LocalServiceTokenProvider(JwtSigner jwtSigner, SecurityProperties properties) {
        this.jwtSigner = jwtSigner;
        this.properties = properties;
    }

    @Override
    public String token() {
        return jwtSigner.issue(properties.getServiceName(), "platform", Role.SERVICE, null);
    }
}
