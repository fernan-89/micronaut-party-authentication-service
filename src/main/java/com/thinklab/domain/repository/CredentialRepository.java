package com.thinklab.domain.repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound port: storage of the password hash, kept apart from the User aggregate so it never leaks into projections. */
public interface CredentialRepository {

    /** Creates or replaces the credential hash of the user. */
    Mono<Void> save(UUID userId, String encodedHash);

    /** Emits the stored hash, or completes empty when the user has no credential yet. */
    Mono<String> findHash(UUID userId);
}
