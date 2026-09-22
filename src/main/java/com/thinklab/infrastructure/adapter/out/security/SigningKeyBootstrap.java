package com.thinklab.infrastructure.adapter.out.security;

import com.thinklab.kit.security.JwtSigner;
import io.micronaut.context.annotation.Context;

/**
 * Forces the signer (and so the signing key) to exist at startup, so the public key is served from the first request
 * and a misconfigured private key fails the boot instead of the first login.
 */
@Context
public class SigningKeyBootstrap {

    public SigningKeyBootstrap(JwtSigner jwtSigner) {
        // Construction is the point: JwtSigner loads or creates the key in its constructor.
    }
}
