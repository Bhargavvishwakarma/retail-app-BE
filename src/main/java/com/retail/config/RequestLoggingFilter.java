package com.retail.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validRequestId(request.getHeader(REQUEST_ID_HEADER))
                ? request.getHeader(REQUEST_ID_HEADER) : UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        request.setAttribute(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();
            if (status >= 500) {
                log.error("request_completed method={} path={} status={} durationMs={}", method, path, status, durationMs);
            } else if (status >= 400) {
                log.warn("request_completed method={} path={} status={} durationMs={}", method, path, status, durationMs);
            } else {
                log.info("request_completed method={} path={} status={} durationMs={}", method, path, status, durationMs);
            }
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private boolean validRequestId(String value) {
        if (value == null || value.length() > 36) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
}
