package com.thinklab.domain.exception;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain Exception: Indicates that a requested {@link com.thinklab.domain.model.User} could not
 * be resolved from the repository.
 *
 * <p>RFC 7807 mapping: HTTP 404 Not Found.
 *
 * @author ThinkLab
 * @since 1.0
 */
public class UserNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-USR-00404";

    public UserNotFoundException(UUID id) {
        super(
                ERROR_CODE,
                String.format("User with sovereign ID [%s] could not be found in the system of record.",
                        Objects.requireNonNull(id, "Domain Exception constraint violated: UUID cannot be null."))
        );
    }

    public UserNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
