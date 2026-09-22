package com.thinklab.domain.exception;

/** The authenticated caller is not allowed to perform the operation on this resource. */
public class OperationForbiddenException extends BusinessException {

    private static final String ERROR_CODE = "ERR-USR-00403";

    public OperationForbiddenException(String message) {
        super(ERROR_CODE, message);
    }
}
