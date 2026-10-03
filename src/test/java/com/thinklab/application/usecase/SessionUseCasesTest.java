package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.FederatedSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.RevokedSession;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.domain.repository.UserRepository;
import com.thinklab.kit.security.JwtSigner;
import com.thinklab.kit.security.JwtVerifier;
import com.thinklab.kit.security.KeyProvider;
import com.thinklab.kit.security.LocalKeyStore;
import com.thinklab.kit.security.RevocationList;
import com.thinklab.kit.security.Role;
import com.thinklab.kit.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SecurityProperties properties = new SecurityProperties();
    private final LocalKeyStore keyStore = new LocalKeyStore(properties);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RevocationList revocations = new RevocationList(clock);
    private final UUID tenant = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private JwtSigner signer;
    private SessionIssuer issuer;
    private SessionRevoker revoker;

    @BeforeEach
    void setUp() {
        signer = new JwtSigner(properties, keyStore);
        issuer = new SessionIssuer(sessions, signer, properties, clock);
        revoker = new SessionRevoker(sessions, revocations, properties, clock);
        when(sessions.saveRefreshToken(any())).thenReturn(Mono.empty());
        when(sessions.revokeSession(anyString(), any())).thenReturn(Mono.empty());
    }

    private User user(UserStatus status, UserRole role) {
        return User.reconstitute(userId, tenant, "Ada", "ada@x.com", role, status, NOW, NOW);
    }

    // ------------------------------------------------------------------------------ SessionIssuer

    @Test
    @DisplayName("the issuer mints a session-bound ES256 access token and stores only the hash of a fresh refresh token")
    void issuesSession() {
        JwtVerifier verifier = new JwtVerifier(properties, new KeyProvider(properties, keyStore), revocations);

        SessionResponse response = issuer.issue(user(UserStatus.ACTIVE, UserRole.OPERATOR), "sess-1").block();

        var principal = verifier.verify(response.accessToken());
        assertEquals(userId.toString(), principal.subject());
        assertEquals(tenant.toString(), principal.tenantId());
        assertEquals(Role.OPERATOR, principal.role());
        assertEquals("sess-1", principal.sessionId());
        assertEquals("Bearer", response.tokenType());
        assertEquals(600, response.expiresIn());
        ArgumentCaptor<RefreshTokenRecord> stored = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(sessions).saveRefreshToken(stored.capture());
        assertEquals(SessionIssuer.hash(response.refreshToken()), stored.getValue().tokenHash());
        assertNotEquals(response.refreshToken(), stored.getValue().tokenHash());
        assertEquals(NOW.plusSeconds(properties.getRefreshTtlSeconds()), stored.getValue().expiresAt());
    }

    @Test
    @DisplayName("a user without a role gets the viewer role, and the public constructor works")
    void defaultRole() {
        SessionIssuer real = new SessionIssuer(sessions, signer, properties);
        JwtVerifier verifier = new JwtVerifier(properties, new KeyProvider(properties, keyStore), revocations);

        SessionResponse response = real.issue(user(UserStatus.ACTIVE, null), "s").block();

        assertEquals(Role.VIEWER, verifier.verify(response.accessToken()).role());
    }

    @Test
    @DisplayName("the token hash is SHA-256, and an unavailable digest is an infrastructure error")
    void hashing() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", SessionIssuer.hash("hello"));
        assertThrows(IllegalStateException.class, () -> SessionIssuer.hash("x", "NO-SUCH-DIGEST"));
    }

    // ------------------------------------------------------------------------------ SessionRevoker

    @Test
    @DisplayName("revoking a session persists it and applies it to the local revocation list at once")
    void revokes() {
        revoker.revoke("sess-1").block();

        verify(sessions).revokeSession("sess-1", NOW);
        assertTrue(revocations.isRevoked("sess-1"));
    }

    @Test
    @DisplayName("revoking all sessions of a user revokes each distinct session; the public constructor works")
    void revokesAll() {
        when(sessions.findSessionIdsByUserId(userId)).thenReturn(Flux.just("a", "b"));

        new SessionRevoker(sessions, revocations, properties).revokeAllOf(userId).block();

        verify(sessions, times(2)).revokeSession(anyString(), any());
    }

    // ------------------------------------------------------------------------------ RefreshSessionUseCase

    private RefreshSessionUseCase refresh() {
        return new RefreshSessionUseCase(sessions, users, issuer, revoker, clock);
    }

    private RefreshTokenRecord stored(String raw, Instant expiresAt) {
        return new RefreshTokenRecord(SessionIssuer.hash(raw), userId, tenant, "sess-1", expiresAt, false);
    }

    @Test
    @DisplayName("a valid refresh token is rotated into a new access token and a new refresh token of the same session")
    void rotates() {
        when(sessions.findRefreshToken(SessionIssuer.hash("raw"))).thenReturn(Mono.just(stored("raw", NOW.plusSeconds(60))));
        when(sessions.isSessionRevoked("sess-1")).thenReturn(Mono.just(false));
        when(sessions.markRefreshTokenUsed(SessionIssuer.hash("raw"))).thenReturn(Mono.just(true));
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));

        StepVerifier.create(refresh().execute("raw"))
                .assertNext(response -> assertNotEquals("raw", response.refreshToken()))
                .verifyComplete();

        verify(sessions).saveRefreshToken(any());
    }

    @Test
    @DisplayName("unknown, revoked and expired tokens are the same generic 401 and nothing is rotated")
    void rejects() {
        when(sessions.findRefreshToken(SessionIssuer.hash("unknown"))).thenReturn(Mono.empty());
        StepVerifier.create(refresh().execute("unknown")).expectError(InvalidCredentialsException.class).verify();

        when(sessions.findRefreshToken(SessionIssuer.hash("revoked"))).thenReturn(Mono.just(stored("revoked", NOW.plusSeconds(60))));
        when(sessions.isSessionRevoked("sess-1")).thenReturn(Mono.just(true));
        StepVerifier.create(refresh().execute("revoked")).expectError(InvalidCredentialsException.class).verify();

        when(sessions.findRefreshToken(SessionIssuer.hash("expired"))).thenReturn(Mono.just(stored("expired", NOW)));
        when(sessions.isSessionRevoked("sess-1")).thenReturn(Mono.just(false));
        StepVerifier.create(refresh().execute("expired")).expectError(InvalidCredentialsException.class).verify();

        verify(sessions, never()).markRefreshTokenUsed(anyString());
    }

    @Test
    @DisplayName("reusing a rotated refresh token is treated as theft: the whole session is revoked")
    void reuseDetection() {
        when(sessions.findRefreshToken(SessionIssuer.hash("stolen"))).thenReturn(Mono.just(stored("stolen", NOW.plusSeconds(60))));
        when(sessions.isSessionRevoked("sess-1")).thenReturn(Mono.just(false));
        when(sessions.markRefreshTokenUsed(SessionIssuer.hash("stolen"))).thenReturn(Mono.just(false));

        StepVerifier.create(refresh().execute("stolen")).expectError(InvalidCredentialsException.class).verify();

        verify(sessions).revokeSession("sess-1", NOW);
    }

    @Test
    @DisplayName("a user that is no longer ACTIVE (or gone) cannot refresh, and the session is revoked")
    void inactiveUser() {
        when(sessions.findRefreshToken(SessionIssuer.hash("raw"))).thenReturn(Mono.just(stored("raw", NOW.plusSeconds(60))));
        when(sessions.isSessionRevoked("sess-1")).thenReturn(Mono.just(false));
        when(sessions.markRefreshTokenUsed(SessionIssuer.hash("raw"))).thenReturn(Mono.just(true));
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.SUSPENDED, UserRole.ADMIN)));
        StepVerifier.create(refresh().execute("raw")).expectError(InvalidCredentialsException.class).verify();

        when(users.findById(userId)).thenReturn(Mono.empty());
        StepVerifier.create(refresh().execute("raw")).expectError(InvalidCredentialsException.class).verify();

        verify(sessions, times(2)).revokeSession("sess-1", NOW);
    }

    @Test
    @DisplayName("the refresh use case public constructor uses the system clock")
    void refreshPublicConstructor() {
        when(sessions.findRefreshToken(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(new RefreshSessionUseCase(sessions, users, issuer, revoker).execute("x"))
                .expectError(InvalidCredentialsException.class).verify();
    }

    // ------------------------------------------------------------------------------ InitiateFederatedSessionUseCase

    @Test
    @DisplayName("a service opens a session for an ACTIVE user of that organisation, with the same tokens a password login produces; with no role (security off) nothing is enforced")
    void federatedSession() {
        InitiateFederatedSessionUseCase useCase = new InitiateFederatedSessionUseCase(users, issuer);
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.OPERATOR)));
        FederatedSessionRequest request = new FederatedSessionRequest(tenant, userId);

        StepVerifier.create(useCase.execute(request, "SERVICE")).assertNext(session -> {
            assertEquals("Bearer", session.tokenType());
            assertTrue(session.refreshToken().length() > 20);
        }).verifyComplete();
        StepVerifier.create(useCase.execute(request, null)).expectNextCount(1).verifyComplete();
        verify(sessions, times(2)).saveRefreshToken(any());
    }

    @Test
    @DisplayName("only a SERVICE may open a federated session; an unknown, inactive or foreign user is the same generic 401, and nothing is stored")
    void federatedSessionRefusals() {
        InitiateFederatedSessionUseCase useCase = new InitiateFederatedSessionUseCase(users, issuer);
        FederatedSessionRequest request = new FederatedSessionRequest(tenant, userId);
        StepVerifier.create(useCase.execute(request, "ADMIN")).expectError(OperationForbiddenException.class).verify();
        StepVerifier.create(useCase.execute(request, "OPERATOR")).expectError(OperationForbiddenException.class).verify();

        when(users.findById(userId)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(request, "SERVICE")).expectError(InvalidCredentialsException.class).verify();
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.SUSPENDED, UserRole.ADMIN)));
        StepVerifier.create(useCase.execute(request, "SERVICE")).expectError(InvalidCredentialsException.class).verify();
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));
        StepVerifier.create(useCase.execute(new FederatedSessionRequest(UUID.randomUUID(), userId), "SERVICE")).expectError(InvalidCredentialsException.class).verify();

        verify(sessions, never()).saveRefreshToken(any());
    }

    // ------------------------------------------------------------------------------ RevokeSessionUseCase

    @Test
    @DisplayName("logout revokes the session of a known token and silently ignores an unknown one")
    void logout() {
        RevokeSessionUseCase logout = new RevokeSessionUseCase(sessions, revoker);
        when(sessions.findRefreshToken(SessionIssuer.hash("known"))).thenReturn(Mono.just(stored("known", NOW.plusSeconds(60))));
        when(sessions.findRefreshToken(SessionIssuer.hash("unknown"))).thenReturn(Mono.empty());

        StepVerifier.create(logout.execute("known")).verifyComplete();
        StepVerifier.create(logout.execute("unknown")).verifyComplete();

        verify(sessions, times(1)).revokeSession("sess-1", NOW);
    }

    // ------------------------------------------------------------------------------ RevokeUserSessionsUseCase

    @Test
    @DisplayName("an administrator, a service, the user themselves or a dev caller may force a logout")
    void forcedLogout() {
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.OPERATOR)));
        when(sessions.findSessionIdsByUserId(userId)).thenReturn(Flux.just("a"));
        RevokeUserSessionsUseCase useCase = new RevokeUserSessionsUseCase(users, revoker);

        StepVerifier.create(useCase.execute(tenant, userId, "admin", "ADMIN")).verifyComplete();
        StepVerifier.create(useCase.execute(tenant, userId, "svc", "SERVICE")).verifyComplete();
        StepVerifier.create(useCase.execute(tenant, userId, userId.toString(), "OPERATOR")).verifyComplete();
        StepVerifier.create(useCase.execute(tenant, userId, "dev", null)).verifyComplete();

        verify(sessions, times(4)).revokeSession("a", NOW);
    }

    @Test
    @DisplayName("another non-admin user cannot force a logout; other tenants and unknown users are not found")
    void forcedLogoutRejections() {
        RevokeUserSessionsUseCase useCase = new RevokeUserSessionsUseCase(users, revoker);
        StepVerifier.create(useCase.execute(tenant, userId, "someone", "OPERATOR")).expectError(OperationForbiddenException.class).verify();

        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.OPERATOR)));
        StepVerifier.create(useCase.execute(UUID.randomUUID(), userId, "admin", "ADMIN")).expectError(UserNotFoundException.class).verify();

        when(users.findById(userId)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(tenant, userId, "admin", "ADMIN")).expectError(UserNotFoundException.class).verify();
    }

    // ------------------------------------------------------------------------------ ListRevokedSessionsUseCase

    @Test
    @DisplayName("the published list carries each revoked session until its last access token can expire")
    void publishesRevocations() {
        when(sessions.findRevokedSince(NOW.minusSeconds(600))).thenReturn(Flux.just(new RevokedSession("s1", NOW.minusSeconds(100))));

        StepVerifier.create(new ListRevokedSessionsUseCase(sessions, properties, clock).execute())
                .assertNext(response -> {
                    assertEquals(1, response.revoked().size());
                    assertEquals("s1", response.revoked().get(0).sid());
                    assertEquals(NOW.minusSeconds(100).plusSeconds(600).getEpochSecond(), response.revoked().get(0).until());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("the list use case public constructor uses the system clock")
    void listPublicConstructor() {
        when(sessions.findRevokedSince(any())).thenReturn(Flux.empty());

        StepVerifier.create(new ListRevokedSessionsUseCase(sessions, properties).execute())
                .assertNext(response -> assertTrue(response.revoked().isEmpty())).verifyComplete();
    }

    @Test
    @DisplayName("suspending or deactivating a user revokes all of their sessions; activating does not")
    void controlRevokesSessions() {
        SessionRevoker mockRevoker = mock(SessionRevoker.class);
        when(mockRevoker.revokeAllOf(userId)).thenReturn(Mono.empty());
        when(users.updateStatus(any(), any())).thenReturn(Mono.empty());
        ControlUserUseCase control = new ControlUserUseCase(users, mockRevoker);

        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));
        StepVerifier.create(control.execute(userId, ControlUserUseCase.Action.SUSPEND)).verifyComplete();
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));
        StepVerifier.create(control.execute(userId, ControlUserUseCase.Action.DEACTIVATE)).verifyComplete();
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.PENDING, UserRole.ADMIN)));
        StepVerifier.create(control.execute(userId, ControlUserUseCase.Action.ACTIVATE)).verifyComplete();

        verify(mockRevoker, times(2)).revokeAllOf(userId);
    }

    // ------------------------------------------------------------------------------ IssueServiceTokenUseCase

    @Test
    @DisplayName("a registered client with the right secret gets a SERVICE token without a refresh token")
    void serviceToken() {
        IssueServiceTokenUseCase useCase = new IssueServiceTokenUseCase(signer, Map.of("svc", "s3cret"));
        JwtVerifier verifier = new JwtVerifier(properties, new KeyProvider(properties, keyStore), revocations);

        SessionResponse response = useCase.execute("svc", "s3cret").block();

        var principal = verifier.verify(response.accessToken());
        assertEquals("svc", principal.subject());
        assertEquals("platform", principal.tenantId());
        assertEquals(Role.SERVICE, principal.role());
        assertEquals(null, response.refreshToken());
    }

    @Test
    @DisplayName("unknown clients, wrong, missing and blank secrets are the same generic 401")
    void serviceTokenRejections() {
        IssueServiceTokenUseCase useCase = new IssueServiceTokenUseCase(signer, Map.of("svc", "s3cret", "disabled", " "));

        StepVerifier.create(useCase.execute("nobody", "s3cret")).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(useCase.execute("svc", "wrong")).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(useCase.execute("svc", null)).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(useCase.execute(null, "s3cret")).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(useCase.execute("disabled", " ")).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(new IssueServiceTokenUseCase(signer, null).execute("svc", "s3cret")).expectError(InvalidCredentialsException.class).verify();
    }
}
