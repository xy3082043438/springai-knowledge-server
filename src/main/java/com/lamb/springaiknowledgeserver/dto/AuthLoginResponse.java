package com.lamb.springaiknowledgeserver.dto;

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
