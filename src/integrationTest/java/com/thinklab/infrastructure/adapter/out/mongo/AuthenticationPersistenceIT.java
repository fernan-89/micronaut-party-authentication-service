package com.thinklab.infrastructure.adapter.out.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.CredentialRepository;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.domain.repository.UserRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Users, credentials and sessions against a real MongoDB: the generated Micronaut Data queries, the
 * refresh-token rotation race, idempotent revocation, and the declared (unique) indexes.
 *
 * <p>{@code packages = "com.thinklab"}: see {@code UserCreationIT}.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "party_auth_persistence_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", Infrastructure.mongoUri(DATABASE));
    }

    @Inject
    UserRepository users;

    @Inject
    CredentialRepository credentials;

    @Inject
    SessionRepository sessions;

    @Inject
    MongoClient mongoClient;

    private static User newUser(UUID organisationId) {
        return User.createNew(UUID.randomUUID(), organisationId, "Ada Lovelace", "ada-" + UUID.randomUUID() + "@thinklab.com", UserRole.ADMIN);
    }

    private static RefreshTokenRecord token(String sessionId) {
        return new RefreshTokenRecord("hash-" + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sessionId,
                Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS), false);
    }

    private Set<Document> indexes(String collection) {
        return Flux.from(mongoClient.getDatabase(DATABASE).getCollection(collection).listIndexes()).collect(Collectors.toSet()).block();
    }

    private boolean hasIndex(String collection, Document keys, boolean unique) {
        return indexes(collection).stream().anyMatch(i -> keys.equals(i.get("key", Document.class))
                && unique == Boolean.TRUE.equals(i.getBoolean("unique")));
    }

    @Test
    @DisplayName("users: create, find by id and by tenant+email, tenant listing with status filter, partial updates")
    void users() {
        UUID organisation = UUID.randomUUID();
        User ada = users.create(newUser(organisation)).block();
        User other = users.create(newUser(organisation)).block();
        users.create(newUser(UUID.randomUUID())).block();

        assertEquals(ada.getEmail(), users.findById(ada.getId()).block().getEmail());
        assertTrue(users.existsByOrganisationIdAndEmail(organisation, ada.getEmail()).block());
        assertFalse(users.existsByOrganisationIdAndEmail(UUID.randomUUID(), ada.getEmail()).block());
        assertEquals(ada.getId(), users.findByOrganisationIdAndEmail(organisation, ada.getEmail()).block().getId());

        users.updateStatus(other.getId(), UserStatus.ACTIVE).block();
        users.updateBasicInfo(ada.getId(), "Ada King", UserRole.VIEWER).block();

        assertEquals(Set.of(ada.getId(), other.getId()),
                users.findAllByOrganisationId(organisation, null).map(User::getId).collect(Collectors.toSet()).block());
        assertEquals(List.of(other.getId()),
                users.findAllByOrganisationId(organisation, UserStatus.ACTIVE).map(User::getId).collectList().block());
        User updated = users.findById(ada.getId()).block();
        assertEquals("Ada King", updated.getFullName());
        assertEquals(UserRole.VIEWER, updated.getRole());
    }

    @Test
    @DisplayName("credentials: the first save inserts, a later save replaces the hash")
    void credentials() {
        UUID userId = UUID.randomUUID();

        assertNull(credentials.findHash(userId).block());
        credentials.save(userId, "$argon2id$first").block();
        credentials.save(userId, "$argon2id$second").block();

        assertEquals("$argon2id$second", credentials.findHash(userId).block());
    }

    @Test
    @DisplayName("refresh tokens: stored and found by hash, listed per user, and a used token cannot be used again")
    void refreshTokens() {
        RefreshTokenRecord record = token("session-" + UUID.randomUUID());
        sessions.saveRefreshToken(record).block();

        assertEquals(record, sessions.findRefreshToken(record.tokenHash()).block());
        assertEquals(List.of(record.sessionId()), sessions.findSessionIdsByUserId(record.userId()).collectList().block());
        assertTrue(sessions.markRefreshTokenUsed(record.tokenHash()).block());
        assertFalse(sessions.markRefreshTokenUsed(record.tokenHash()).block());
        assertFalse(sessions.markRefreshTokenUsed("hash-unknown").block());
    }

    @Test
    @DisplayName("concurrent rotations of the same refresh token: exactly one wins (theft detection depends on it)")
    void concurrentRotationHasOneWinner() {
        RefreshTokenRecord record = token("session-" + UUID.randomUUID());
        sessions.saveRefreshToken(record).block();

        List<Boolean> outcomes = Flux.fromStream(IntStream.range(0, 8).boxed())
                .flatMap(i -> sessions.markRefreshTokenUsed(record.tokenHash()).subscribeOn(Schedulers.parallel()))
                .collectList().block();

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count(), () -> "outcomes: " + outcomes);
    }

    @Test
    @DisplayName("a second refresh token with the same hash is rejected by the unique index")
    void duplicateTokenHashIsRejected() {
        RefreshTokenRecord record = token("session-" + UUID.randomUUID());
        sessions.saveRefreshToken(record).block();

        RefreshTokenRecord clash = new RefreshTokenRecord(record.tokenHash(), UUID.randomUUID(), UUID.randomUUID(),
                "session-" + UUID.randomUUID(), record.expiresAt(), false);
        assertThrows(RuntimeException.class, () -> sessions.saveRefreshToken(clash).block());
    }

    @Test
    @DisplayName("revocation is idempotent, also under concurrency, and revoked sessions are listed since a point in time")
    void revocation() {
        String sessionId = "session-" + UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);

        Flux.range(0, 6)
                .flatMap(i -> sessions.revokeSession(sessionId, Instant.now()).subscribeOn(Schedulers.parallel()))
                .then().block();
        sessions.revokeSession(sessionId, Instant.now()).block();

        assertTrue(sessions.isSessionRevoked(sessionId).block());
        assertFalse(sessions.isSessionRevoked("session-" + UUID.randomUUID()).block());
        assertEquals(1, sessions.findRevokedSince(before).filter(r -> r.sessionId().equals(sessionId)).count().block());
    }

    @Test
    @DisplayName("the declared indexes exist, including the unique ones on tokenHash and sessionId")
    void declaredIndexesExist() {
        assertTrue(hasIndex("users", new Document("organisationId", 1).append("email", 1), false), () -> "users: " + indexes("users"));
        assertTrue(hasIndex("users", new Document("organisationId", 1).append("status", 1), false), () -> "users: " + indexes("users"));
        assertTrue(hasIndex("refresh_tokens", new Document("tokenHash", 1), true), () -> "refresh_tokens: " + indexes("refresh_tokens"));
        assertTrue(hasIndex("revoked_sessions", new Document("sessionId", 1), true), () -> "revoked_sessions: " + indexes("revoked_sessions"));
    }
}
