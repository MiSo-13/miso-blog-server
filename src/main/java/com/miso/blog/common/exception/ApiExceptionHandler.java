package com.miso.blog.common.exception;

import com.miso.blog.common.api.ApiErrorResponse;
import com.miso.blog.common.code.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(GeneralException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getDefaultMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ErrorCode.BAD_REQUEST.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        log.error("처리하지 못한 서버 예외가 발생했습니다.", exception);
        return ResponseEntity
                .internalServerError()
                .body(ApiErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()
                ));
    }
}
