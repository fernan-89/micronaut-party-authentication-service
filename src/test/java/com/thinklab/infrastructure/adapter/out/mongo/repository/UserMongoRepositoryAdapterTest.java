package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.infrastructure.adapter.out.mongo.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMongoRepositoryAdapterTest {

    @Mock private UserMongoRepository repository;

    private UserMongoRepositoryAdapter adapter;
    private UUID userId;
    private UUID organisationId;
    private UserEntity entity;

    @BeforeEach
    void setUp() {
        adapter = new UserMongoRepositoryAdapter(repository);
        userId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        entity = new UserEntity(userId, organisationId, "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR,
                UserStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), 3L);
    }

    @Test
    @DisplayName("the constructor rejects a null repository")
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new UserMongoRepositoryAdapter(null));
    }

    @Test
    @DisplayName("create maps the aggregate to an entity, saves it and maps the result back")
    void create() {
        User user = User.createNew(userId, organisationId, "Ada Lovelace", "ada@thinklab.com", UserRole.OPERATOR);
        when(repository.save(any(UserEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.create(user))
                .expectNextMatches(saved -> saved.getId().equals(userId) && saved.getStatus() == UserStatus.PENDING)
                .verifyComplete();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("ada@thinklab.com", captor.getValue().email());
        assertEquals(null, captor.getValue().version());
    }

    @Test
    @DisplayName("create rejects a null aggregate and propagates a save failure")
    void createFailures() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));

        when(repository.save(any(UserEntity.class))).thenReturn(Mono.error(new IllegalStateException("mongo down")));
        User user = User.createNew(userId, organisationId, "Ada", "ada@thinklab.com", UserRole.VIEWER);

        StepVerifier.create(adapter.create(user)).expectErrorMessage("mongo down").verify();
    }

    @Test
    @DisplayName("findById maps the entity to the aggregate, or completes empty")
    void findById() {
        when(repository.findById(userId)).thenReturn(Mono.just(entity)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(userId))
                .expectNextMatches(user -> user.getId().equals(userId) && user.getStatus() == UserStatus.ACTIVE
                        && user.getRole() == UserRole.OPERATOR)
                .verifyComplete();
        StepVerifier.create(adapter.findById(userId)).verifyComplete();
    }

    @Test
    @DisplayName("findById requires an identifier")
    void findByIdRequiresId() {
        assertThrows(NullPointerException.class, () -> adapter.findById(null));
    }

    @Test
    @DisplayName("findAllByOrganisationId uses the status-scoped query only when a status is given")
    void findAll() {
        when(repository.findByOrganisationIdAndStatus(organisationId, UserStatus.ACTIVE)).thenReturn(Flux.just(entity));
        when(repository.findByOrganisationId(organisationId)).thenReturn(Flux.just(entity, entity));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, UserStatus.ACTIVE)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).expectNextCount(2).verifyComplete();

        verify(repository).findByOrganisationIdAndStatus(organisationId, UserStatus.ACTIVE);
        verify(repository).findByOrganisationId(organisationId);
    }

    @Test
    @DisplayName("findAllByOrganisationId requires the tenant")
    void findAllRequiresTenant() {
        assertThrows(NullPointerException.class, () -> adapter.findAllByOrganisationId(null, null));
    }

    @Test
    @DisplayName("existsByOrganisationIdAndEmail delegates and defaults an empty result to false")
    void exists() {
        when(repository.existsByOrganisationIdAndEmail(organisationId, "a@b.c")).thenReturn(Mono.just(true));
        when(repository.existsByOrganisationIdAndEmail(organisationId, "z@z.z")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.existsByOrganisationIdAndEmail(organisationId, "a@b.c")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.existsByOrganisationIdAndEmail(organisationId, "z@z.z")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("existsByOrganisationIdAndEmail requires tenant and email")
    void existsRequiresArguments() {
        assertThrows(NullPointerException.class, () -> adapter.existsByOrganisationIdAndEmail(null, "a@b.c"));
        assertThrows(NullPointerException.class, () -> adapter.existsByOrganisationIdAndEmail(organisationId, null));
    }

    @Test
    @DisplayName("updateBasicInfo rewrites name/role, refreshes updatedAt and keeps status, email and version")
    void updateBasicInfo() {
        when(repository.findById(userId)).thenReturn(Mono.just(entity));
        when(repository.update(any(UserEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.updateBasicInfo(userId, "Ada L.", UserRole.ADMIN)).verifyComplete();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).update(captor.capture());
        UserEntity updated = captor.getValue();
        assertEquals("Ada L.", updated.fullName());
        assertEquals(UserRole.ADMIN, updated.role());
        assertEquals(UserStatus.ACTIVE, updated.status());
        assertEquals("ada@thinklab.com", updated.email());
        assertEquals(3L, updated.version());
        assertTrue(updated.updatedAt().isAfter(entity.updatedAt()));
    }

    @Test
    @DisplayName("updateStatus rewrites only the status and refreshes updatedAt")
    void updateStatus() {
        when(repository.findById(userId)).thenReturn(Mono.just(entity));
        when(repository.update(any(UserEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.updateStatus(userId, UserStatus.SUSPENDED)).verifyComplete();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).update(captor.capture());
        assertEquals(UserStatus.SUSPENDED, captor.getValue().status());
        assertEquals("Ada Lovelace", captor.getValue().fullName());
        assertEquals(UserRole.OPERATOR, captor.getValue().role());
    }

    @Test
    @DisplayName("both updates fail with UserNotFoundException when the user is missing and never write")
    void updatesFailWhenMissing() {
        when(repository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.updateBasicInfo(userId, "x", UserRole.ADMIN)).expectError(UserNotFoundException.class).verify();
        StepVerifier.create(adapter.updateStatus(userId, UserStatus.SUSPENDED)).expectError(UserNotFoundException.class).verify();

        verify(repository, never()).update(any(UserEntity.class));
    }
}
