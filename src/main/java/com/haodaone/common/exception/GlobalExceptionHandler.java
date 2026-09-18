package com.haodaone.common.exception;

import com.haodaone.common.dto.ApiError;
import com.haodaone.attendance.exception.AttendanceLocationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(AttendanceLocationException.class)
    public ResponseEntity<ApiError> handleAttendanceLocation(AttendanceLocationException ex, HttpServletRequest request) {
        log.warn("Attendance location rejected: code={}, accuracyMeters={}, distanceMeters={}, allowedRadiusMeters={}, validationStage={}, source={}",
            ex.getCode(), ex.getAccuracyMeters(), ex.getDistanceMeters(), ex.getAllowedRadiusMeters(), ex.getValidationStage(), ex.getSource());
        ApiError body = buildBody(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request);
        body.setCode(ex.getCode());
        body.setAccuracyMeters(ex.getAccuracyMeters());
        body.setRequiredAccuracyMeters(ex.getRequiredAccuracyMeters());
        body.setDistanceMeters(ex.getDistanceMeters());
        body.setAllowedRadiusMeters(ex.getAllowedRadiusMeters());
        body.setValidationStage(ex.getValidationStage());
        body.setSource(ex.getSource());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiError> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest request) {
        log.warn("Email delivery failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler({AuthenticationFailedException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> handleAuthFailed(RuntimeException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials or session expired", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "You don't have permission to perform this action", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Validation Failed", "One or more fields are invalid", request.getRequestURI());
        body.setDetails(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = buildBody(status, status.getReasonPhrase(), message, request);
        return ResponseEntity.status(status).body(body);
    }

    private ApiError buildBody(HttpStatus status, String error, String message, HttpServletRequest request) {
        return new ApiError(status.value(), error, message, request.getRequestURI());
    }
}
