package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.infrastructure.adapter.out.mongo.entity.RefreshTokenEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@MongoRepository
public interface RefreshTokenMongoRepository extends ReactorCrudRepository<RefreshTokenEntity, String> {

    Flux<RefreshTokenEntity> findByUserId(UUID userId);
}
