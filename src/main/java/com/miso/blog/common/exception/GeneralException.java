package com.miso.blog.common.exception;

import com.miso.blog.common.code.ErrorCode;

public class GeneralException extends RuntimeException {
    private final ErrorCode errorCode;

    public GeneralException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public GeneralException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
