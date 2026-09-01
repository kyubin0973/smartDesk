package com.smartdesk.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/** 모든 오류 응답을 { timestamp, status, code, message, path } 형태로 통일. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String timestamp, int status, String code, String message, String path) {}

    private static ApiError body(int status, String code, String message, HttpServletRequest req) {
        return new ApiError(Instant.now().toString(), status, code, message, req.getRequestURI());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e, HttpServletRequest req) {
        return ResponseEntity.status(e.getStatus())
                .body(body(e.getStatus().value(), e.getCode(), e.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(body(400, "VALIDATION", msg, req));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiError> handleBadInput(Exception e, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(body(400, "BAD_REQUEST", "요청 형식이 올바르지 않습니다.", req));
    }

    /** 매핑되지 않은 경로 — generic 핸들러로 떨어져 500 + ERROR 스택트레이스가 남던 것을 404 로 정정. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e, HttpServletRequest req) {
        log.debug("매핑되지 않은 경로: {} {}", req.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(404, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", req));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(body(413, "FILE_TOO_LARGE", "파일이 허용 용량을 초과했습니다.", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e, HttpServletRequest req) {
        String ref = org.slf4j.MDC.get(RequestIdFilter.MDC_KEY);
        if (ref == null) ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("처리되지 않은 예외 at {} {}", req.getMethod(), req.getRequestURI(), e);
        // 내부 세부정보(SQL·스택 등)를 클라이언트에 노출하지 않음. ref 로 서버 로그와 대조
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "INTERNAL", "서버 오류가 발생했습니다. (ref: " + ref + ")", req));
    }
}
