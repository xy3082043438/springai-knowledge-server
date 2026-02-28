package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.AuthLoginRequest;
import com.lamb.springaiknowledgeserver.dto.AuthLoginResponse;
import com.lamb.springaiknowledgeserver.service.AuthService;
import com.lamb.springaiknowledgeserver.service.OperationLogService;
import com.lamb.springaiknowledgeserver.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OperationLogService operationLogService;

    @PostMapping("/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request, HttpServletRequest httpRequest) {
        AuthLoginResponse response = authService.login(request);
        if (response.getUser() != null) {
            operationLogService.log(
                response.getUser().getId(),
                response.getUser().getUsername(),
                "LOGIN",
                "AUTH",
                response.getUser().getId() == null ? null : String.valueOf(response.getUser().getId()),
                "login",
                RequestUtils.resolveClientIp(httpRequest),
                true
            );
        }
        return response;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        authService.logout(principal.getId());
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "LOGOUT",
            "AUTH",
            String.valueOf(principal.getId()),
            "logout",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return ResponseEntity.noContent().build();
    }
}
