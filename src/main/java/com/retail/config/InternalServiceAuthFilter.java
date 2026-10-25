package com.retail.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private final String sharedSecret;
    private final long maxClockSkewSeconds;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    public InternalServiceAuthFilter(@Value("${app.service-auth.shared-secret:}") String sharedSecret,
                                    @Value("${app.service-auth.max-clock-skew-seconds:60}") long maxClockSkewSeconds,
                                    ObjectMapper objectMapper) {
        this.sharedSecret = sharedSecret;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod()) || sharedSecret.isBlank()) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "SERVICE_AUTHENTICATION_FAILED",
                    "Invalid service authentication");
            return;
        }
        String timestamp = request.getHeader("X-Internal-Timestamp");
        String nonce = request.getHeader("X-Internal-Nonce");
        String signature = request.getHeader("X-Internal-Signature");
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_TOO_LARGE",
                    "Request body exceeds the maximum allowed size");
            return;
        }
        if (!validTimestamp(timestamp) || nonce == null || nonce.isBlank() || nonce.length() > 128
                || signature == null || !validSignature(request, timestamp, nonce, body, signature)
                || isReplay(nonce)) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "SERVICE_AUTHENTICATION_FAILED",
                    "Invalid service authentication");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                            String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.create(status, code, detail, request));
    }

    private boolean validTimestamp(String value) {
        try {
            long requestTime = Long.parseLong(value);
            long now = Instant.now().getEpochSecond();
            return maxClockSkewSeconds >= 0 && requestTime >= now - maxClockSkewSeconds
                    && requestTime <= now + maxClockSkewSeconds;
        } catch (RuntimeException exception) { return false; }
    }

    private boolean validSignature(HttpServletRequest request, String timestamp, String nonce,
                                   byte[] body, String supplied) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String canonical = request.getMethod() + "\n" + request.getRequestURI() + "\n"
                    + timestamp + "\n" + nonce + "\n" + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)),
                    HexFormat.of().parseHex(supplied));
        } catch (Exception exception) { return false; }
    }

    private boolean isReplay(String nonce) {
        long now = Instant.now().getEpochSecond();
        usedNonces.entrySet().removeIf(entry -> entry.getValue() < now);
        return usedNonces.putIfAbsent(nonce, now + maxClockSkewSeconds) != null;
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
