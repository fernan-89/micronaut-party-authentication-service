package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.infrastructure.adapter.out.mongo.entity.RefreshTokenEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@MongoRepository
public interface RefreshTokenMongoRepository extends ReactorCrudRepository<RefreshTokenEntity, UUID> {

    Mono<RefreshTokenEntity> findByTokenHash(String tokenHash);

    Flux<RefreshTokenEntity> findByUserId(UUID userId);
}
