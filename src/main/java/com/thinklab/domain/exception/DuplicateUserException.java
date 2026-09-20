package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an attempt is made to create a User with an email that already
 * exists within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 *
 * @author ThinkLab
 * @since 1.0
 */
public class DuplicateUserException extends BusinessException {

    private static final String DEFAULT_ERROR_CODE = "ERR-USR-00409";

    public DuplicateUserException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DuplicateUserException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }
}
