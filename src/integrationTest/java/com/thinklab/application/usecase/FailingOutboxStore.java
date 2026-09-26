package com.thinklab.application.usecase;

import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxMongoStore;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Only in {@link UserCreationRollbackIT}: an outbox whose append always fails, to prove the rollback. */
@Singleton
@Replaces(OutboxMongoStore.class)
@Requires(property = "it.outbox.fail", value = "true")
class FailingOutboxStore implements OutboxStore {

    @Override
    public Mono<OutboxEvent> append(OutboxEvent event) {
        return Mono.error(new IllegalStateException("outbox unavailable"));
    }

    @Override
    public Flux<OutboxEvent> findUnpublished(int batchSize) {
        return Flux.empty();
    }

    @Override
    public Mono<Void> markPublished(UUID eventId) {
        return Mono.empty();
    }
}
