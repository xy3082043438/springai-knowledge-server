package com.lamb.springaiknowledgeserver.security.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final Cache<String, String> captchaCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    private final Cache<String, Integer> failedAttemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_MINUTES, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> getCaptcha() {
        // Arithmetic captcha (e.g. 3+5=?)
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(111, 36);
        captcha.setLen(2);

        String key = UUID.randomUUID().toString();
        String result = captcha.text();

        captchaCache.put(key, result);

        Map<String, Object> response = new HashMap<>();
        response.put("captchaKey", key);
        response.put("captchaImg", captcha.toBase64());
        return ResponseEntity.ok(response);
    }

    public String getCaptchaValue(String key) {
        return captchaCache.getIfPresent(key);
    }

    public void removeCaptcha(String key) {
        captchaCache.invalidate(key);
    }

    public boolean verify(String captchaKey, String providedCode) {
        String cached = getCaptchaValue(captchaKey);
        if (cached == null || providedCode == null) return false;
        return cached.equals(providedCode.trim());
    }

    public boolean isLocked(String clientId) {
        Integer attempts = failedAttemptsCache.getIfPresent(clientId);
        return attempts != null && attempts >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailedAttempt(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        failedAttemptsCache.asMap().merge(clientId, 1, Integer::sum);
    }

    public void resetFailedAttempts(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        failedAttemptsCache.invalidate(clientId);
    }
}
