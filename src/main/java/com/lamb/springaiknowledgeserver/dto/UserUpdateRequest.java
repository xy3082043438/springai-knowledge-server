package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.UserRole;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {

    @Size(min = 3, max = 64)
    private String username;

    private UserRole role;
}
