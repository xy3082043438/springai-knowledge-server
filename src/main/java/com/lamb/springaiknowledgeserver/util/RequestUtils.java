package com.lamb.springaiknowledgeserver.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtils {

    private RequestUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = extractFirst(forwarded);
        if (ip != null) {
            return ip;
        }
        String realIp = extractFirst(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private static String extractFirst(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length == 0) {
            return null;
        }
        String candidate = parts[0].trim();
        if (candidate.isBlank() || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }
        return candidate;
    }
}
