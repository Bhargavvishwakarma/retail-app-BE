package com.retail.exception;

import org.springframework.http.HttpStatus;

public class UpstreamServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String upstreamRequestId;

    public UpstreamServiceException(HttpStatus status, String code, String detail, String upstreamRequestId) {
        super(detail);
        this.status = status;
        this.code = code;
        this.upstreamRequestId = upstreamRequestId;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getUpstreamRequestId() { return upstreamRequestId; }
}
