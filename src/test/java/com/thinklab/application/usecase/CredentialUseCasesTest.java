package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureCredentialRequest;
import com.thinklab.application.dto.request.InitiateSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.exception.InvalidUserStatusException;
import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.port.PasswordHasher;
import com.thinklab.domain.repository.CredentialRepository;
import com.thinklab.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialUseCasesTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CredentialRepository credentials = mock(CredentialRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final SessionIssuer issuer = mock(SessionIssuer.class);

    @BeforeEach
    void setUp() {
        when(issuer.issue(any(), anyString())).thenReturn(Mono.just(new SessionResponse("access", "Bearer", 600, "refresh", 3600)));
        when(hasher.hash(anyString())).thenReturn("hash-of-password");
        when(credentials.save(any(), anyString())).thenReturn(Mono.empty());
    }

    private User user(UserStatus status, UserRole role) {
        return User.reconstitute(userId, tenant, "Ada", "ada@x.com", role, status, Instant.now(), Instant.now());
    }

    // ---------------------------------------------------------------------------- credential/update

    private CaptureCredentialUseCase capture() {
        return new CaptureCredentialUseCase(users, credentials, hasher);
    }

    private final CaptureCredentialRequest newPassword = new CaptureCredentialRequest("a-brand-new-password");

    @Test
    @DisplayName("an administrator, a service or the user themselves may set the credential; the hash is stored")
    void capturesCredential() {
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.OPERATOR)));

        StepVerifier.create(capture().execute(tenant, userId, "admin-1", "ADMIN", newPassword)).verifyComplete();
        StepVerifier.create(capture().execute(tenant, userId, "svc", "SERVICE", newPassword)).verifyComplete();
        StepVerifier.create(capture().execute(tenant, userId, userId.toString(), "OPERATOR", newPassword)).verifyComplete();
        StepVerifier.create(capture().execute(tenant, userId, "dev", null, newPassword)).verifyComplete();

        verify(credentials, times(4)).save(userId, "hash-of-password");
    }

    @Test
    @DisplayName("another non-admin user cannot change the credential")
    void forbidden() {
        StepVerifier.create(capture().execute(tenant, userId, "someone-else", "OPERATOR", newPassword))
                .expectError(OperationForbiddenException.class).verify();

        verify(credentials, never()).save(any(), anyString());
    }

    @Test
    @DisplayName("an unknown user or a user of another tenant is not found")
    void notFound() {
        when(users.findById(userId)).thenReturn(Mono.empty());
        StepVerifier.create(capture().execute(tenant, userId, "admin", "ADMIN", newPassword))
                .expectError(UserNotFoundException.class).verify();

        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));
        StepVerifier.create(capture().execute(UUID.randomUUID(), userId, "admin", "ADMIN", newPassword))
                .expectError(UserNotFoundException.class).verify();
    }

    @Test
    @DisplayName("a deactivated user cannot receive a credential")
    void deactivated() {
        when(users.findById(userId)).thenReturn(Mono.just(user(UserStatus.DEACTIVATED, UserRole.ADMIN)));

        StepVerifier.create(capture().execute(tenant, userId, "admin", "ADMIN", newPassword))
                .expectError(InvalidUserStatusException.class).verify();
    }

    // ---------------------------------------------------------------------------- session/initiate

    private InitiateSessionUseCase session() {
        return new InitiateSessionUseCase(users, credentials, hasher, issuer);
    }

    private InitiateSessionRequest login(String password) {
        return new InitiateSessionRequest(tenant, "ada@x.com", password);
    }

    @Test
    @DisplayName("valid credentials of an ACTIVE user open a session through the issuer")
    void loginSucceeds() {
        when(users.findByOrganisationIdAndEmail(tenant, "ada@x.com")).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.OPERATOR)));
        when(credentials.findHash(userId)).thenReturn(Mono.just("stored-hash"));
        when(hasher.matches("secret-password", "stored-hash")).thenReturn(true);

        StepVerifier.create(session().execute(login("secret-password")))
                .assertNext(response -> {
                    assertEquals("access", response.accessToken());
                    assertEquals("refresh", response.refreshToken());
                })
                .verifyComplete();

        verify(issuer).issue(any(), anyString());
    }

    @Test
    @DisplayName("every failure is the same generic 401 and still pays the hashing cost")
    void genericFailures() {
        // wrong password
        when(users.findByOrganisationIdAndEmail(tenant, "ada@x.com")).thenReturn(Mono.just(user(UserStatus.ACTIVE, UserRole.ADMIN)));
        when(credentials.findHash(userId)).thenReturn(Mono.just("stored-hash"));
        when(hasher.matches(anyString(), anyString())).thenReturn(false);
        StepVerifier.create(session().execute(login("wrong"))).expectError(InvalidCredentialsException.class).verify();

        // inactive user with the right password
        when(users.findByOrganisationIdAndEmail(tenant, "ada@x.com")).thenReturn(Mono.just(user(UserStatus.SUSPENDED, UserRole.ADMIN)));
        when(hasher.matches("right", "stored-hash")).thenReturn(true);
        StepVerifier.create(session().execute(login("right"))).expectError(InvalidCredentialsException.class).verify();

        // user without a credential
        when(credentials.findHash(userId)).thenReturn(Mono.empty());
        StepVerifier.create(session().execute(login("right"))).expectError(InvalidCredentialsException.class).verify();

        // unknown user
        when(users.findByOrganisationIdAndEmail(tenant, "ada@x.com")).thenReturn(Mono.empty());
        StepVerifier.create(session().execute(login("right"))).expectError(InvalidCredentialsException.class).verify();
    }

    @Test
    @DisplayName("the timing-equalisation hash is computed once and reused")
    void dummyHashIsCached() {
        when(users.findByOrganisationIdAndEmail(tenant, "ada@x.com")).thenReturn(Mono.empty());
        when(hasher.matches(anyString(), eq("hash-of-password"))).thenReturn(false);
        InitiateSessionUseCase useCase = session();

        StepVerifier.create(useCase.execute(login("a"))).expectError(InvalidCredentialsException.class).verify();
        StepVerifier.create(useCase.execute(login("b"))).expectError(InvalidCredentialsException.class).verify();

        verify(hasher, times(1)).hash(anyString());
    }
}
