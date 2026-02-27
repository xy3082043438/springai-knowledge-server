package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.auth.JwtService;
import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.dto.AuthLoginRequest;
import com.lamb.springaiknowledgeserver.dto.AuthLoginResponse;
import com.lamb.springaiknowledgeserver.dto.UserResponse;
import com.lamb.springaiknowledgeserver.entity.User;
import com.lamb.springaiknowledgeserver.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthLoginResponse login(AuthLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Objects.requireNonNull(principal, "Authenticated principal must not be null");
        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        String token = jwtService.generateToken(user);
        return new AuthLoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    public void logout(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }
}
