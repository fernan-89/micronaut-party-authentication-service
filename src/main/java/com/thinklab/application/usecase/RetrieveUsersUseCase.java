package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.UserResponse;
import com.thinklab.application.mapper.UserMapper;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.UserRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Orchestrates the tenant-scoped listing of Users (BIAN Behavior Qualifier: {@code retrieve} —
 * collection). Unlike some sibling Service Domains, this listing is never global: every query is
 * strictly bound to the {@code organisationId} supplied via the {@code X-Tenant-Id} header.
 */
@Singleton
public class RetrieveUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveUsersUseCase.class);

    private final UserRepository userRepository;

    public RetrieveUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Flux<UserResponse> execute(UUID organisationId, @Nullable UserStatus status) {
        log.info("[USE CASE] Retrieving users for organisation: {} status: {}", organisationId, status);

        return userRepository.findAllByOrganisationId(organisationId, status)
                .map(UserMapper::toResponse);
    }
}
