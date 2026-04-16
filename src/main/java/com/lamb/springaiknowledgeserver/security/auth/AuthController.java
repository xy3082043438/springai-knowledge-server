package com.lamb.springaiknowledgeserver.security.auth;

import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogService;
import com.lamb.springaiknowledgeserver.modules.system.user.User;
import com.lamb.springaiknowledgeserver.modules.system.user.UserRepository;
import com.lamb.springaiknowledgeserver.modules.system.user.UserResponse;
import com.lamb.springaiknowledgeserver.core.exception.ServiceException;
import com.lamb.springaiknowledgeserver.security.auth.dto.AuthLoginRequest;
import com.lamb.springaiknowledgeserver.security.auth.dto.AuthLoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OperationLogService operationLogService;
    private final CaptchaController captchaController;

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
        @Valid @RequestBody AuthLoginRequest request,
        HttpServletRequest httpRequest
    ) {
        // Verify captcha
        String captchaKey = request.getCaptchaKey();
        String captchaCode = request.getCaptchaCode();
        String cachedValue = captchaController.getCaptchaValue(captchaKey);
        
        if (cachedValue == null || captchaCode == null) {
            throw new ServiceException("验证码已过期，请刷新");
        }

        try {
            if (cachedValue == null || captchaCode == null || !cachedValue.equals(captchaCode.trim())) {
                throw new ServiceException("验证码不正确或已过期");
            }
            // Clear verification cache after success
            captchaController.removeCaptcha(captchaKey);
        } catch (Exception e) {
            throw new ServiceException("验证失败，请重试");
        }

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();

        String token = jwtService.generateToken(user);
        long expiresIn = jwtService.getExpirationSeconds();

        operationLogService.log(
            user.getId(),
            user.getUsername(),
            "USER_LOGIN",
            "AUTH",
            String.valueOf(user.getId()),
            "login success",
            com.lamb.springaiknowledgeserver.core.util.RequestUtils.resolveClientIp(httpRequest),
            true
        );

        return ResponseEntity.ok(new AuthLoginResponse(
            token,
            "Bearer",
            expiresIn,
            UserResponse.from(user)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        // Token is stateless, logout is handled by frontend deleting token.
        // We can just log it if we have context, or ignore.
        return ResponseEntity.noContent().build();
    }
}
