package com.thinklab.domain.exception;

/** The supplied credentials could not be verified. Deliberately generic: it never says which part was wrong. */
public class InvalidCredentialsException extends BusinessException {

    private static final String ERROR_CODE = "ERR-USR-00401";

    public InvalidCredentialsException() {
        super(ERROR_CODE, "Invalid credentials.");
    }
}
