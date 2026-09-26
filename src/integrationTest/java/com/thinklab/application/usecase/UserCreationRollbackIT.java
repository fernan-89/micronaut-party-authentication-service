package com.thinklab.application.usecase;

import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.infrastructure.adapter.out.mongo.Infrastructure;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** If the outbox append fails, the user write in the same transaction is rolled back (kit ADR-003, 0.4.2+). */
// packages = "com.thinklab": @MicronautTest otherwise uses this test's own package as the application
// package, and Micronaut Data MongoDB then stops mapping the entities' @Id to _id (it stores an "id"
// field next to a generated ObjectId _id, so findById finds nothing). Production runs from com.thinklab.
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserCreationRollbackIT implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "mongodb.uri", Infrastructure.mongoUri("party_auth_rollback_it"),
                "thinklab.events.enabled", "true",
                "thinklab.events.nats-url", Infrastructure.natsUrl(),
                "it.outbox.fail", "true");
    }

    @Inject
    UserCreationWriter writer;

    @Inject
    UserRepository users;

    @Test
    @DisplayName("a failing outbox append rolls the user creation back")
    void failedAppendRollsBackTheUser() {
        User user = User.createNew(UUID.randomUUID(), UUID.randomUUID(), "Grace Hopper", "grace-" + UUID.randomUUID() + "@thinklab.com", UserRole.OPERATOR);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> writer.createAndPublish(user).block());

        assertEquals("outbox unavailable", failure.getMessage());
        assertNull(users.findById(user.getId()).block());
    }
}
