package com.lamb.springaiknowledgeserver.modules.system.user;

import com.lamb.springaiknowledgeserver.modules.system.user.User;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String role;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getRole().getName(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}


