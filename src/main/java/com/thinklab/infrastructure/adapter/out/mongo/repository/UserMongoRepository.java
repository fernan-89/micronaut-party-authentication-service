package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.infrastructure.adapter.out.mongo.entity.UserEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Infrastructure Adapter: Reactive repository for {@link UserEntity} persistence.
 * Leverages Micronaut Data's Ahead-of-Time (AOT) compilation for reflection-free,
 * non-blocking query routines against MongoDB.
 *
 * @author ThinkLab
 * @since 1.0
 */
@MongoRepository
public interface UserMongoRepository extends ReactorCrudRepository<UserEntity, UUID> {

    /** The only by-id lookup of the service: a user of another organisation is the same as none (ADR-023). */
    Mono<UserEntity> findByIdAndOrganisationId(UUID id, UUID organisationId);


    Mono<Boolean> existsByOrganisationIdAndEmail(UUID organisationId, String email);

    Mono<UserEntity> findByOrganisationIdAndEmail(UUID organisationId, String email);

    Flux<UserEntity> findByOrganisationId(UUID organisationId);

    Flux<UserEntity> findByOrganisationIdAndStatus(UUID organisationId, UserStatus status);
}
