package com.smartdesk.common;

import org.springframework.http.HttpStatus;

/** 도메인/검증 오류 표준 예외. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String what)      { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " 를 찾을 수 없습니다."); }
    public static ApiException badRequest(String msg)     { return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg); }
    public static ApiException conflict(String code, String msg) { return new ApiException(HttpStatus.CONFLICT, code, msg); }
    public static ApiException forbidden(String msg)      { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
    public static ApiException unauthorized(String msg)   { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg); }
    public static ApiException unavailable(String code, String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, msg); }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
