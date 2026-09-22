package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.repository.CredentialRepository;
import com.thinklab.infrastructure.adapter.out.mongo.entity.CredentialEntity;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mongo adapter for the credential store: one document per user, replaced on every password change. */
@Singleton
public class CredentialRepositoryAdapter implements CredentialRepository {

    private final CredentialMongoRepository repository;

    public CredentialRepositoryAdapter(CredentialMongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Infrastructure constraint violated: CredentialMongoRepository cannot be null.");
    }

    @Override
    public Mono<Void> save(UUID userId, String encodedHash) {
        Objects.requireNonNull(userId, "Infrastructure constraint violated: User ID is mandatory.");
        Objects.requireNonNull(encodedHash, "Infrastructure constraint violated: Hash is mandatory.");
        CredentialEntity entity = new CredentialEntity(userId, encodedHash, Instant.now());
        return repository.existsById(userId)
                .flatMap(exists -> exists ? repository.update(entity) : repository.save(entity))
                .then();
    }

    @Override
    public Mono<String> findHash(UUID userId) {
        Objects.requireNonNull(userId, "Infrastructure constraint violated: User ID is mandatory.");
        return repository.findById(userId).map(CredentialEntity::encodedHash);
    }
}
