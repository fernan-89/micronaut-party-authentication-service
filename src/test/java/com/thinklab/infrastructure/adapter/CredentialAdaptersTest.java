package com.thinklab.infrastructure.adapter;

import com.thinklab.application.dto.request.CaptureCredentialRequest;
import com.thinklab.application.dto.request.InitiateSessionRequest;
import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.application.usecase.CaptureCredentialUseCase;
import com.thinklab.application.usecase.InitiateSessionUseCase;
import com.thinklab.domain.exception.InvalidCredentialsException;
import com.thinklab.domain.exception.OperationForbiddenException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.infrastructure.adapter.in.web.CredentialController;
import com.thinklab.infrastructure.adapter.in.web.handler.GlobalExceptionHandler;
import com.thinklab.infrastructure.adapter.out.mongo.entity.CredentialEntity;
import com.thinklab.infrastructure.adapter.out.mongo.repository.CredentialMongoRepository;
import com.thinklab.infrastructure.adapter.out.mongo.repository.CredentialRepositoryAdapter;
import com.thinklab.infrastructure.adapter.out.mongo.repository.UserMongoRepository;
import com.thinklab.infrastructure.adapter.out.mongo.repository.UserMongoRepositoryAdapter;
import com.thinklab.infrastructure.adapter.out.mongo.entity.UserEntity;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialAdaptersTest {

    private static final UUID ID = UUID.randomUUID();

    // ------------------------------------------------------------------ entity and repository adapter

    @Test
    @DisplayName("CredentialEntity rejects null and blank fields")
    void entityGuards() {
        assertThrows(NullPointerException.class, () -> new CredentialEntity(null, "h", Instant.now()));
        assertThrows(NullPointerException.class, () -> new CredentialEntity(ID, null, Instant.now()));
        assertThrows(NullPointerException.class, () -> new CredentialEntity(ID, "h", null));
        assertThrows(IllegalArgumentException.class, () -> new CredentialEntity(ID, " ", Instant.now()));
    }

    @Test
    @DisplayName("the credential adapter creates the first hash, replaces an existing one and reads it back")
    void credentialAdapter() {
        CredentialMongoRepository repository = mock(CredentialMongoRepository.class);
        CredentialRepositoryAdapter adapter = new CredentialRepositoryAdapter(repository);
        when(repository.existsById(ID)).thenReturn(Mono.just(false)).thenReturn(Mono.just(true));
        when(repository.save(any(CredentialEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(repository.update(any(CredentialEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(repository.findById(ID)).thenReturn(Mono.just(new CredentialEntity(ID, "stored", Instant.now())));

        StepVerifier.create(adapter.save(ID, "first")).verifyComplete();
        StepVerifier.create(adapter.save(ID, "second")).verifyComplete();
        StepVerifier.create(adapter.findHash(ID)).expectNext("stored").verifyComplete();

        verify(repository).save(any(CredentialEntity.class));
        verify(repository).update(any(CredentialEntity.class));
    }

    @Test
    @DisplayName("the credential adapter null-checks its arguments")
    void credentialAdapterGuards() {
        CredentialRepositoryAdapter adapter = new CredentialRepositoryAdapter(mock(CredentialMongoRepository.class));

        assertThrows(NullPointerException.class, () -> new CredentialRepositoryAdapter(null));
        assertThrows(NullPointerException.class, () -> adapter.save(null, "h"));
        assertThrows(NullPointerException.class, () -> adapter.save(ID, null));
        assertThrows(NullPointerException.class, () -> adapter.findHash(null));
    }

    @Test
    @DisplayName("the user adapter finds a user by organisation and email and null-checks its arguments")
    void userLookup() {
        UserMongoRepository repository = mock(UserMongoRepository.class);
        UserMongoRepositoryAdapter adapter = new UserMongoRepositoryAdapter(repository);
        UserEntity entity = UserEntity.fromDomain(User.createNew(ID, ID, "Ada", "ada@x.com", UserRole.ADMIN));
        when(repository.findByOrganisationIdAndEmail(ID, "ada@x.com")).thenReturn(Mono.just(entity));

        StepVerifier.create(adapter.findByOrganisationIdAndEmail(ID, "ada@x.com"))
                .assertNext(user -> assertEquals("Ada", user.getFullName())).verifyComplete();
        assertThrows(NullPointerException.class, () -> adapter.findByOrganisationIdAndEmail(null, "e"));
        assertThrows(NullPointerException.class, () -> adapter.findByOrganisationIdAndEmail(ID, null));
    }

    // ------------------------------------------------------------------ controller

    @Test
    @DisplayName("the login endpoint returns 200 with the session and propagates failures")
    void loginEndpoint() {
        InitiateSessionUseCase login = mock(InitiateSessionUseCase.class);
        CredentialController controller = new CredentialController(login, mock(CaptureCredentialUseCase.class));
        InitiateSessionRequest request = new InitiateSessionRequest(ID, "ada@x.com", "pw");
        when(login.execute(request)).thenReturn(Mono.just(new SessionResponse("tok", "Bearer", 60)))
                .thenReturn(Mono.error(new InvalidCredentialsException()));

        StepVerifier.create(controller.initiateSession(request))
                .assertNext(r -> assertEquals("tok", r.body().accessToken())).verifyComplete();
        StepVerifier.create(controller.initiateSession(request)).expectError(InvalidCredentialsException.class).verify();
    }

    @Test
    @DisplayName("the credential endpoint returns 204, rejects a malformed tenant and propagates failures")
    void credentialEndpoint() {
        CaptureCredentialUseCase capture = mock(CaptureCredentialUseCase.class);
        CredentialController controller = new CredentialController(mock(InitiateSessionUseCase.class), capture);
        CaptureCredentialRequest body = new CaptureCredentialRequest("a-brand-new-password");
        when(capture.execute(any(), any(), any(), any(), any())).thenReturn(Mono.empty())
                .thenReturn(Mono.error(new OperationForbiddenException("no")));

        StepVerifier.create(controller.updateCredential(ID, ID.toString(), "op", "ADMIN", body))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.updateCredential(ID, ID.toString(), "op", "ADMIN", body))
                .expectError(OperationForbiddenException.class).verify();
        StepVerifier.create(controller.updateCredential(ID, "not-a-uuid", "op", "ADMIN", body))
                .expectError(IllegalArgumentException.class).verify();
    }

    // ------------------------------------------------------------------ error mapping

    @Test
    @DisplayName("the exception handler maps invalid credentials to 401 and forbidden operations to 403")
    void errorMapping() {
        HttpRequest<?> request = Mockito.mock(HttpRequest.class);
        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        when(request.getPath()).thenReturn("/party-authentication/v1/session/initiate");
        when(request.getAttribute("traceId", String.class)).thenReturn(Optional.empty());
        when(request.getHeaders()).thenReturn(headers);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        HttpResponse<Map<String, Object>> unauthorised = handler.handle(request, new InvalidCredentialsException());
        HttpResponse<Map<String, Object>> forbidden = handler.handle(request, new OperationForbiddenException("no"));

        assertEquals(HttpStatus.UNAUTHORIZED, unauthorised.getStatus());
        assertEquals("ERR-USR-00401", unauthorised.body().get("error_code"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
        assertEquals(UserStatus.PENDING, User.createNew(ID, ID, "a", "b", UserRole.VIEWER).getStatus());
    }
}
