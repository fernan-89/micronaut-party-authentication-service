package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.infrastructure.adapter.out.mongo.entity.RevokedSessionEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

@MongoRepository
public interface RevokedSessionMongoRepository extends ReactorCrudRepository<RevokedSessionEntity, String> {

    Flux<RevokedSessionEntity> findByRevokedAtGreaterThanEquals(Instant since);
}
