package com.thinklab.application.usecase;

import com.thinklab.domain.exception.UserNotFoundException;
import com.thinklab.domain.model.User.UserStatus;
import com.thinklab.domain.repository.UserRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing the User lifecycle (BIAN Behavior Qualifier: {@code control}).
 *
 * <p><b>State Machine Enforcement:</b> Loads the aggregate first, delegates the transition to the
 * domain model (which throws {@link com.thinklab.domain.exception.InvalidUserStatusException} on
 * an illegal move via {@link UserStatus#validateTransitionTo}), and only then issues the granular
 * persistence update — never a blind partial write. This mirrors the fix applied to the Party
 * Reference Data Directory's {@code ControlOrganisationUseCase} after its own E2E validation
 * surfaced the exact class of bug this pattern prevents.
 */
@Singleton
public class ControlUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlUserUseCase.class);

    private final UserRepository userRepository;

    public ControlUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<Void> execute(UUID id, Action action) {
        log.info("[USE CASE] Controlling user lifecycle: {} for ID: {}", action, id);

        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(user -> {
                    action.apply(user);
                    return userRepository.updateStatus(id, action.targetStatus());
                });
    }

    public enum Action {
        ACTIVATE(UserStatus.ACTIVE) {
            @Override void apply(com.thinklab.domain.model.User user) { user.activate(); }
        },
        SUSPEND(UserStatus.SUSPENDED) {
            @Override void apply(com.thinklab.domain.model.User user) { user.suspend(); }
        },
        DEACTIVATE(UserStatus.DEACTIVATED) {
            @Override void apply(com.thinklab.domain.model.User user) { user.deactivate(); }
        };

        private final UserStatus targetStatus;

        Action(UserStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public UserStatus targetStatus() {
            return targetStatus;
        }

        abstract void apply(com.thinklab.domain.model.User user);
    }
}
