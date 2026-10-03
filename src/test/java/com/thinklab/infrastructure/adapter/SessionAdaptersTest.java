package com.thinklab.infrastructure.adapter;

import com.thinklab.application.dto.request.FederatedSessionRequest;
import com.thinklab.application.dto.request.RefreshTokenRequest;
import com.thinklab.application.dto.response.RevokedSessionsResponse;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.application.usecase.InitiateFederatedSessionUseCase;
import com.thinklab.application.usecase.IssueServiceTokenUseCase;
import com.thinklab.application.usecase.ListRevokedSessionsUseCase;
import com.thinklab.application.usecase.RefreshSessionUseCase;
import com.thinklab.application.usecase.RevokeSessionUseCase;
import com.thinklab.application.usecase.RevokeUserSessionsUseCase;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.infrastructure.adapter.in.web.SessionController;
import com.thinklab.infrastructure.adapter.out.mongo.entity.RefreshTokenEntity;
import com.thinklab.infrastructure.adapter.out.mongo.entity.RevokedSessionEntity;
import com.thinklab.infrastructure.adapter.out.mongo.repository.RefreshTokenMongoRepository;
import com.thinklab.infrastructure.adapter.out.mongo.repository.RevokedSessionMongoRepository;
import com.thinklab.infrastructure.adapter.out.mongo.repository.SessionRepositoryAdapter;
import com.thinklab.infrastructure.adapter.out.security.LocalServiceTokenProvider;
import com.thinklab.infrastructure.adapter.out.security.SigningKeyBootstrap;
import com.thinklab.kit.security.JwtSigner;
import com.thinklab.kit.security.JwtVerifier;
import com.thinklab.kit.security.KeyProvider;
import com.thinklab.kit.security.LocalKeyStore;
import com.thinklab.kit.security.RevocationList;
import com.thinklab.kit.security.Role;
import com.thinklab.kit.security.SecurityProperties;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAdaptersTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    private final RefreshTokenMongoRepository tokens = mock(RefreshTokenMongoRepository.class);
    private final RevokedSessionMongoRepository revoked = mock(RevokedSessionMongoRepository.class);
    private final SessionRepositoryAdapter adapter = new SessionRepositoryAdapter(tokens, revoked);

    private RefreshTokenEntity entity(boolean used) {
        return new RefreshTokenEntity(ENTITY_ID, "hash", ID, ID, "sess", NOW.plusSeconds(60), used, 1L);
    }

    // ------------------------------------------------------------------------------ entities

    @Test
    @DisplayName("the entities reject null fields and map to and from the domain")
    void entities() {
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(null, "h", ID, ID, "s", NOW, false, null));
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(ENTITY_ID, null, ID, ID, "s", NOW, false, null));
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(ENTITY_ID, "h", null, ID, "s", NOW, false, null));
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(ENTITY_ID, "h", ID, null, "s", NOW, false, null));
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(ENTITY_ID, "h", ID, ID, null, NOW, false, null));
        assertThrows(NullPointerException.class, () -> new RefreshTokenEntity(ENTITY_ID, "h", ID, ID, "s", null, false, null));
        assertThrows(NullPointerException.class, () -> new RevokedSessionEntity(null, "s", NOW));
        assertThrows(NullPointerException.class, () -> new RevokedSessionEntity(ENTITY_ID, null, NOW));
        assertThrows(NullPointerException.class, () -> new RevokedSessionEntity(ENTITY_ID, "s", null));

        RefreshTokenRecord record = new RefreshTokenRecord("h", ID, ID, "s", NOW, false);
        assertEquals(record, RefreshTokenEntity.fromDomain(record).toDomain());
        assertTrue(RefreshTokenEntity.fromDomain(record).asUsed().used());
        assertEquals("s", RevokedSessionEntity.of("s", NOW).toDomain().sessionId());
    }

    // ------------------------------------------------------------------------------ adapter

    @Test
    @DisplayName("the adapter saves and finds refresh tokens by their hash, not by the Mongo id")
    void saveAndFind() {
        RefreshTokenRecord record = new RefreshTokenRecord("hash", ID, ID, "sess", NOW, false);
        when(tokens.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tokens.findByTokenHash("hash")).thenReturn(Mono.just(entity(false)));

        StepVerifier.create(adapter.saveRefreshToken(record)).verifyComplete();
        StepVerifier.create(adapter.findRefreshToken("hash")).assertNext(found -> assertEquals("sess", found.sessionId())).verifyComplete();
    }

    @Test
    @DisplayName("marking a token used succeeds once, then reports reuse; unknown tokens and lost races are reported as false")
    void markUsed() {
        when(tokens.findByTokenHash("fresh")).thenReturn(Mono.just(entity(false)));
        when(tokens.findByTokenHash("used")).thenReturn(Mono.just(entity(true)));
        when(tokens.findByTokenHash("gone")).thenReturn(Mono.empty());
        when(tokens.findByTokenHash("race")).thenReturn(Mono.just(entity(false)));
        when(tokens.update(any(RefreshTokenEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)))
                .thenReturn(Mono.error(new IllegalStateException("optimistic lock")));

        StepVerifier.create(adapter.markRefreshTokenUsed("fresh")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.markRefreshTokenUsed("used")).expectNext(false).verifyComplete();
        StepVerifier.create(adapter.markRefreshTokenUsed("gone")).expectNext(false).verifyComplete();
        StepVerifier.create(adapter.markRefreshTokenUsed("race")).expectNext(false).verifyComplete();
        verify(tokens, org.mockito.Mockito.times(2)).update(any(RefreshTokenEntity.class));
    }

    private static com.mongodb.MongoWriteException writeError(int code) {
        return new com.mongodb.MongoWriteException(new com.mongodb.WriteError(code, "E" + code, new org.bson.BsonDocument()),
                new com.mongodb.ServerAddress());
    }

    @Test
    @DisplayName("a concurrent revocation losing the unique-index race still succeeds; other write errors propagate")
    void revocationRace() {
        when(revoked.existsBySessionId(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(false));
        when(revoked.save(any(RevokedSessionEntity.class)))
                .thenReturn(Mono.error(writeError(11000)))
                .thenReturn(Mono.error(new IllegalStateException("wrapped", writeError(11000))))
                .thenReturn(Mono.error(writeError(2)))
                .thenReturn(Mono.error(new IllegalStateException("not a write error")));

        StepVerifier.create(adapter.revokeSession("raced", NOW)).verifyComplete();
        StepVerifier.create(adapter.revokeSession("raced-wrapped", NOW)).verifyComplete();
        StepVerifier.create(adapter.revokeSession("bad", NOW)).expectError(com.mongodb.MongoWriteException.class).verify();
        StepVerifier.create(adapter.revokeSession("worse", NOW)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("revoking is idempotent and revocation state is readable, keyed by sessionId not the Mongo id")
    void revocation() {
        when(revoked.existsBySessionId("new")).thenReturn(Mono.just(false));
        when(revoked.existsBySessionId("old")).thenReturn(Mono.just(true));
        when(revoked.save(any(RevokedSessionEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(revoked.findByRevokedAtGreaterThanEquals(NOW)).thenReturn(Flux.just(RevokedSessionEntity.of("new", NOW)));

        StepVerifier.create(adapter.revokeSession("new", NOW)).verifyComplete();
        StepVerifier.create(adapter.revokeSession("old", NOW)).verifyComplete();
        StepVerifier.create(adapter.isSessionRevoked("old")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.findRevokedSince(NOW)).assertNext(r -> assertEquals("new", r.sessionId())).verifyComplete();
        verify(revoked).save(any(RevokedSessionEntity.class));
    }

    @Test
    @DisplayName("a user's session ids are distinct")
    void sessionIds() {
        when(tokens.findByUserId(ID)).thenReturn(Flux.just(entity(false), entity(true)));

        StepVerifier.create(adapter.findSessionIdsByUserId(ID)).expectNext("sess").verifyComplete();
    }

    @Test
    @DisplayName("the adapter null-checks every argument")
    void guards() {
        assertThrows(NullPointerException.class, () -> new SessionRepositoryAdapter(null, revoked));
        assertThrows(NullPointerException.class, () -> new SessionRepositoryAdapter(tokens, null));
        assertThrows(NullPointerException.class, () -> adapter.saveRefreshToken(null));
        assertThrows(NullPointerException.class, () -> adapter.findRefreshToken(null));
        assertThrows(NullPointerException.class, () -> adapter.markRefreshTokenUsed(null));
        assertThrows(NullPointerException.class, () -> adapter.revokeSession(null, NOW));
        assertThrows(NullPointerException.class, () -> adapter.revokeSession("s", null));
        assertThrows(NullPointerException.class, () -> adapter.isSessionRevoked(null));
        assertThrows(NullPointerException.class, () -> adapter.findSessionIdsByUserId(null));
        assertThrows(NullPointerException.class, () -> adapter.findRevokedSince(null));
    }

    // ------------------------------------------------------------------------------ controller

    private final RefreshSessionUseCase refresh = mock(RefreshSessionUseCase.class);
    private final RevokeSessionUseCase revoke = mock(RevokeSessionUseCase.class);
    private final RevokeUserSessionsUseCase revokeAll = mock(RevokeUserSessionsUseCase.class);
    private final ListRevokedSessionsUseCase list = mock(ListRevokedSessionsUseCase.class);
    private final IssueServiceTokenUseCase serviceToken = mock(IssueServiceTokenUseCase.class);
    private final InitiateFederatedSessionUseCase federated = mock(InitiateFederatedSessionUseCase.class);
    private final LocalKeyStore keyStore = new LocalKeyStore(new SecurityProperties());
    private final SessionController controller = new SessionController(refresh, revoke, revokeAll, list, serviceToken, keyStore, federated);

    @Test
    @DisplayName("refresh, revoke and the revocation list map to 200, 204 and 200")
    void sessionEndpoints() {
        SessionResponse response = new SessionResponse("a", "Bearer", 600, "r", 3600);
        when(refresh.execute("tok")).thenReturn(Mono.just(response)).thenReturn(Mono.error(new InvalidCredentialsException()));
        when(revoke.execute("tok")).thenReturn(Mono.empty());
        when(list.execute()).thenReturn(Mono.just(new RevokedSessionsResponse(List.of())));

        StepVerifier.create(controller.refresh(new RefreshTokenRequest("tok"))).assertNext(r -> assertEquals("a", r.body().accessToken())).verifyComplete();
        StepVerifier.create(controller.refresh(new RefreshTokenRequest("tok"))).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(controller.revoke(new RefreshTokenRequest("tok"))).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.revoked()).assertNext(r -> assertTrue(r.body().revoked().isEmpty())).verifyComplete();
    }

    @Test
    @DisplayName("session/federated passes the verified role to the use case and maps to 200, propagating a refusal")
    void federatedEndpoint() {
        FederatedSessionRequest request = new FederatedSessionRequest(ID, ID);
        when(federated.execute(request, "SERVICE")).thenReturn(Mono.just(new SessionResponse("a", "Bearer", 600, "r", 3600)));
        when(federated.execute(request, "ADMIN")).thenReturn(Mono.error(new InvalidCredentialsException()));

        StepVerifier.create(controller.federated(request, "SERVICE")).assertNext(r -> assertEquals("a", r.body().accessToken())).verifyComplete();
        StepVerifier.create(controller.federated(request, "ADMIN")).expectError(InvalidCredentialsException.class).verify();
    }

    @Test
    @DisplayName("forced logout returns 204, rejects a malformed tenant and propagates failures")
    void forcedLogoutEndpoint() {
        when(revokeAll.execute(any(), any(), any(), any())).thenReturn(Mono.empty()).thenReturn(Mono.error(new InvalidCredentialsException()));

        StepVerifier.create(controller.revokeAllSessions(ID, ID.toString(), "admin", "ADMIN")).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.revokeAllSessions(ID, ID.toString(), "admin", "ADMIN")).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(controller.revokeAllSessions(ID, "not-a-uuid", "admin", "ADMIN")).expectError(IllegalArgumentException.class).verify();
        verify(revoke, never()).execute(any());
    }

    @Test
    @DisplayName("the service-token endpoint returns the token, and the JWKS endpoint only ever exposes the public key")
    void serviceTokenAndJwks() {
        when(serviceToken.execute("svc", "s")).thenReturn(Mono.just(new SessionResponse("t", "Bearer", 600, null, 0)));

        StepVerifier.create(controller.serviceToken("svc", "s")).assertNext(r -> assertEquals("t", r.body().accessToken())).verifyComplete();
        assertThrows(IllegalStateException.class, controller::jwks);
        keyStore.signingKey();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> key = ((List<java.util.Map<String, Object>>) controller.jwks().get("keys")).get(0);
        assertEquals("EC", key.get("kty"));
        assertFalse(key.containsKey("d"));
    }

    // ------------------------------------------------------------------------------ security beans

    @Test
    @DisplayName("the local service-token provider signs a SERVICE token named after this service, and the bootstrap builds")
    void localProvider() {
        SecurityProperties properties = new SecurityProperties();
        properties.setServiceName("thinklab-party-authentication-service");
        LocalKeyStore store = new LocalKeyStore(properties);
        JwtSigner signer = new JwtSigner(properties, store);
        JwtVerifier verifier = new JwtVerifier(properties, new KeyProvider(properties, store), new RevocationList());

        var principal = verifier.verify(new LocalServiceTokenProvider(signer, properties).token());

        assertEquals("thinklab-party-authentication-service", principal.subject());
        assertEquals(Role.SERVICE, principal.role());
        assertNotNull(new SigningKeyBootstrap(signer));
    }
}
