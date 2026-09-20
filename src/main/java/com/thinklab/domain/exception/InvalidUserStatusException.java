package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal or unpermitted lifecycle state transition attempt on a
 * {@link com.thinklab.domain.model.User}.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 *
 * @author ThinkLab
 * @since 1.0
 */
public class InvalidUserStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-USR-00409";

    public InvalidUserStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
