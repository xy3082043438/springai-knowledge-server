package com.lamb.springaiknowledgeserver.exception;

import com.lamb.springaiknowledgeserver.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
        ResponseStatusException ex,
        HttpServletRequest request
    ) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = defaultMessage(status);
        }
        return build(status, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "请求参数错误";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        String message = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "请求参数错误";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        log.debug("Request body not readable", ex);
        return build(HttpStatus.BAD_REQUEST, "请求体格式错误", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        String name = ex.getName();
        String message = (name.isBlank() ? "请求参数" : name) + ": 参数格式错误";
        log.debug("Request parameter type mismatch", ex);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(
        MaxUploadSizeExceededException ex,
        HttpServletRequest request
    ) {
        log.debug("Upload size exceeded", ex);
        return build(HttpStatus.BAD_REQUEST, "上传文件过大", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        log.debug("Access denied", ex);
        return build(HttpStatus.FORBIDDEN, "无权限访问", request);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<?> handleNotWritable(
        HttpMessageNotWritableException ex,
        HttpServletRequest request
    ) {
        if (isClientAbort(ex)) {
            log.debug("Client disconnected before response completed [{}]", requestUri(request));
            return ResponseEntity.noContent().build();
        }
        log.error("Failed to write response", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常，请稍后重试", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.debug("Client disconnected before response completed [{}]", requestUri(request));
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常，请稍后重试", request);
    }

    private ResponseEntity<ApiErrorResponse> build(
        HttpStatusCode status,
        String message,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.now(),
            status.value(),
            message,
            request == null ? null : request.getRequestURI()
        );
        return ResponseEntity.status(status.value()).body(response);
    }

    private String formatFieldError(FieldError error) {
        if (error == null) {
            return "";
        }
        String field = error.getField();
        String message = error.getDefaultMessage();
        if (message == null || message.isBlank()) {
            return field;
        }
        return field + ": " + message;
    }

    private String defaultMessage(HttpStatusCode status) {
        if (status == null) {
            return "请求处理失败";
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "资源不存在";
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return "无权限访问";
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return "请求参数错误";
        }
        if (status.value() >= 500) {
            return "服务异常，请稍后重试";
        }
        return "请求处理失败";
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String requestUri(HttpServletRequest request) {
        return request == null ? "unknown" : request.getRequestURI();
    }
}
