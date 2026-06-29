package com.upi.offline.exception;

import com.upi.offline.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.error("Access denied exception at path '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Access Denied: " + ex.getMessage()
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTransactionNotFound(TransactionNotFoundException ex, HttpServletRequest request) {
        log.error("Transaction not found exception at path '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Transaction Not Found",
                ex.getMessage()
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransaction(InvalidTransactionException ex, HttpServletRequest request) {
        log.error("Invalid transaction exception at path '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Transaction",
                ex.getMessage()
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.error("Validation failed for request at path '{}'", request.getRequestURI());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        log.error("Authentication failed at path '{}': {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage()
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred at path '{}'", request.getRequestURI(), ex);
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage()
        );
        response.setPath(request.getRequestURI());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
