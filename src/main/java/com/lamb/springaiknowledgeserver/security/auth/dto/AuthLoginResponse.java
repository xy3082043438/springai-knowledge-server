package com.lamb.springaiknowledgeserver.security.auth.dto;

import com.lamb.springaiknowledgeserver.modules.system.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthLoginResponse {

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;
}


