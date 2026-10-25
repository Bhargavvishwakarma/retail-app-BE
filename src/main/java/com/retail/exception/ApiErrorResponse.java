package com.retail.exception;

import com.retail.config.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.slf4j.MDC;

import java.time.Instant;

/** Creates the shared RFC 9457 error representation used by MVC and servlet filters. */
public final class ApiErrorResponse {
    private ApiErrorResponse() { }

    public static ProblemDetail create(HttpStatusCode status, String code, String detail,
                                       HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status.value()));
        if (request != null) problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        String requestId = request == null ? MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)
                : (String) request.getAttribute(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
        if (requestId == null) requestId = MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null) problem.setProperty("requestId", requestId);
        return problem;
    }

    public static String title(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Request Failed";
        };
    }
}
