package com.thinklab.application.usecase;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.infrastructure.adapter.out.mongo.Infrastructure;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserCreationWriter} with the event backbone on, against a real MongoDB replica set and NATS
 * JetStream: the user and its {@code user.initiated} outbox event are written in one transaction.
 */
// packages = "com.thinklab": @MicronautTest otherwise uses this test's own package as the application
// package, and Micronaut Data MongoDB then stops mapping the entities' @Id to _id (it stores an "id"
// field next to a generated ObjectId _id, so findById finds nothing). Production runs from com.thinklab.
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserCreationIT implements TestPropertyProvider {

    private static final String DATABASE = "party_auth_creation_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "mongodb.uri", Infrastructure.mongoUri(DATABASE),
                "thinklab.events.enabled", "true",
                "thinklab.events.nats-url", Infrastructure.natsUrl());
    }

    @Inject
    UserCreationWriter writer;

    @Inject
    UserRepository users;

    @Inject
    MongoClient mongoClient;

    @Test
    @DisplayName("creating a user commits the user and its user.initiated outbox event together")
    void userAndEventAreCommitted() {
        User user = User.createNew(UUID.randomUUID(), UUID.randomUUID(), "Ada Lovelace", "ada-" + UUID.randomUUID() + "@thinklab.com", UserRole.ADMIN);

        User created = writer.createAndPublish(user).block();

        assertEquals(user.getId(), created.getId());
        assertNotNull(users.findById(user.getId()).block());
        List<Document> events = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("outbox_events")
                .find(Filters.eq("subject", "thinklab.party-authentication.user.initiated"))).collectList().block();
        assertTrue(events.stream().anyMatch(e -> e.getString("payloadJson").contains(user.getId().toString())),
                () -> "no user.initiated event for " + user.getId() + " in " + events);
    }
}
