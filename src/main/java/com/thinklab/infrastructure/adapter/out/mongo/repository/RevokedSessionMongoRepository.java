package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.infrastructure.adapter.out.mongo.entity.RevokedSessionEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@MongoRepository
public interface RevokedSessionMongoRepository extends ReactorCrudRepository<RevokedSessionEntity, UUID> {

    Mono<Boolean> existsBySessionId(String sessionId);

    Flux<RevokedSessionEntity> findByRevokedAtGreaterThanEquals(Instant since);
}
