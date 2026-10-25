package com.retail.exception;

import com.retail.config.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(com.retail.service.AuthService.InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            com.retail.service.AuthService.InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("authentication_failed reason=invalid_credentials requestId={}", requestId());
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Email or password is incorrect", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("request_rejected reason={} requestId={}", ex.getMessage(), requestId());
        return response(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        ProblemDetail body = ApiErrorResponse.create(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more request fields are invalid", request);
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        body.setProperty("errors", errors);
        log.warn("request_validation_failed errorCount={} requestId={}", errors.size(), requestId());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleParameterTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                       HttpServletRequest request) {
        String detail = "Parameter '%s' has an invalid value".formatted(ex.getName());
        log.warn("request_parameter_invalid parameter={} requestId={}", ex.getName(), requestId());
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", detail, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '%s' is missing".formatted(ex.getParameterName()), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this resource", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String detail = ex.getReason() == null ? ApiErrorResponse.title(status.value()) : ex.getReason();
        if (status.is5xxServerError()) {
            log.warn("upstream_request_failed status={} requestId={}", status.value(), requestId());
        }
        return response(status, status.name().toUpperCase(Locale.ROOT), detail, request);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamService(UpstreamServiceException ex,
                                                                HttpServletRequest request) {
        log.warn("upstream_request_failed status={} upstreamCode={} upstreamRequestId={} requestId={}",
                ex.getStatus().value(), ex.getCode(), ex.getUpstreamRequestId(), requestId());
        ProblemDetail problem = ApiErrorResponse.create(ex.getStatus(), "UPSTREAM_FAILURE",
                ex.getMessage(), request);
        if (ex.getCode() != null) problem.setProperty("upstreamCode", ex.getCode());
        if (ex.getUpstreamRequestId() != null) {
            problem.setProperty("upstreamRequestId", ex.getUpstreamRequestId());
        }
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleOther(Exception ex, HttpServletRequest request) {
        log.error("unhandled_request_error requestId={}", requestId(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String detail,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.create(status, code, detail, request));
    }

    private String requestId() {
        return MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
    }

    public record ValidationError(String field, String message) { }
}
