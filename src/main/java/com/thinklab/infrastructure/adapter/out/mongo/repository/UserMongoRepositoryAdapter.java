package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.infrastructure.adapter.out.mongo.entity.UserEntity;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Adapter: Implementation of the {@link UserRepository} outbound port,
 * delegating to the Micronaut Data {@link UserMongoRepository}.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Slf4j
@Singleton
public class UserMongoRepositoryAdapter implements UserRepository {

    private final UserMongoRepository repository;

    public UserMongoRepositoryAdapter(UserMongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Infrastructure constraint violated: UserMongoRepository cannot be null.");
    }

    @Override
    public Mono<User> create(User user) {
        Objects.requireNonNull(user, "Infrastructure constraint violated: User aggregate cannot be null for persistence.");

        return Mono.just(user)
                .map(UserEntity::fromDomain)
                .flatMap(repository::save)
                .map(UserEntity::toDomain)
                .doOnSuccess(saved -> log.debug("[ACTION: PERSIST_USER] [ID: {}] - User successfully committed to database.", saved.getId()))
                .doOnError(e -> log.error("[ACTION: PERSIST_USER] [ORG: {}] - CRITICAL: Failed to save user. Error: {}", user.getOrganisationId(), e.getMessage(), e));
    }

    @Override
    public Mono<User> findById(UUID id) {
        Objects.requireNonNull(id, "Infrastructure constraint violated: Identifier UUID is mandatory for retrieval.");

        return repository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Flux<User> findAllByOrganisationId(UUID organisationId, @Nullable UserStatus status) {
        Objects.requireNonNull(organisationId, "Infrastructure constraint violated: Organisation ID is mandatory.");

        Flux<UserEntity> source = status != null
                ? repository.findByOrganisationIdAndStatus(organisationId, status)
                : repository.findByOrganisationId(organisationId);

        return source.map(UserEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsByOrganisationIdAndEmail(UUID organisationId, String email) {
        Objects.requireNonNull(organisationId, "Infrastructure constraint violated: Organisation ID is mandatory.");
        Objects.requireNonNull(email, "Infrastructure constraint violated: Email is mandatory.");

        return repository.existsByOrganisationIdAndEmail(organisationId, email)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> updateBasicInfo(UUID id, String fullName, UserRole role) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(entity -> repository.update(new UserEntity(
                        entity.id(), entity.organisationId(), fullName, entity.email(), role,
                        entity.status(), entity.createdAt(), Instant.now(), entity.version()
                )))
                .then();
    }

    @Override
    public Mono<Void> updateStatus(UUID id, UserStatus status) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(entity -> repository.update(new UserEntity(
                        entity.id(), entity.organisationId(), entity.fullName(), entity.email(), entity.role(),
                        status, entity.createdAt(), Instant.now(), entity.version()
                )))
                .then();
    }
}
