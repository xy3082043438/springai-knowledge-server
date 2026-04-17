package com.lamb.springaiknowledgeserver.core.exception;

import com.lamb.springaiknowledgeserver.core.dto.ApiErrorResponse;
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
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleService(
        ServiceException ex,
        HttpServletRequest request
    ) {
        log.debug("Service error: {}", ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), request);
    }

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
            .map(violation -> {
                String path = violation.getPropertyPath().toString();
                // 取最后一段作为字段名
                int dot = path.lastIndexOf('.');
                String field = dot >= 0 ? path.substring(dot + 1) : path;
                String friendlyField = FIELD_NAME_MAP.getOrDefault(field, field);
                return friendlyField + ": " + violation.getMessage();
            })
            .collect(Collectors.joining("；"));
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
        String friendlyName = FIELD_NAME_MAP.getOrDefault(name, name);
        String message = (friendlyName.isBlank() ? "请求参数" : friendlyName) + ": 格式不正确";
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

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
        org.springframework.security.authentication.BadCredentialsException ex,
        HttpServletRequest request
    ) {
        log.debug("Bad credentials", ex);
        return build(HttpStatus.UNAUTHORIZED, "用户名或密码错误", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
        AuthenticationException ex,
        HttpServletRequest request
    ) {
        log.debug("Authentication failed", ex);
        return build(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录", request);
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
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        // Temporarily exposing error details to help debugging
        String detailMessage = "服务异常: " + ex.getClass().getSimpleName() + " (" + ex.getMessage() + ")";
        return build(HttpStatus.INTERNAL_SERVER_ERROR, detailMessage, request);
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

    private static final java.util.Map<String, String> FIELD_NAME_MAP = java.util.Map.ofEntries(
        java.util.Map.entry("username", "用户名"),
        java.util.Map.entry("password", "密码"),
        java.util.Map.entry("role", "角色"),
        java.util.Map.entry("name", "名称"),
        java.util.Map.entry("title", "标题"),
        java.util.Map.entry("content", "内容"),
        java.util.Map.entry("question", "问题"),
        java.util.Map.entry("file", "文件"),
        java.util.Map.entry("value", "值"),
        java.util.Map.entry("description", "描述"),
        java.util.Map.entry("permissions", "权限"),
        java.util.Map.entry("allowedRoles", "允许角色"),
        java.util.Map.entry("qaLogId", "问答记录"),
        java.util.Map.entry("helpful", "反馈评价"),
        java.util.Map.entry("comment", "反馈内容")
    );

    private String formatFieldError(FieldError error) {
        if (error == null) {
            return "";
        }
        String field = error.getField();
        String friendlyField = FIELD_NAME_MAP.getOrDefault(field, field);
        String message = error.getDefaultMessage();
        if (message == null || message.isBlank()) {
            return friendlyField + "格式不正确";
        }
        // 如果 message 已经是完整的中文提示，直接使用
        if (message.length() > 4 && message.chars().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
            return message;
        }
        return friendlyField + ": " + message;
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


