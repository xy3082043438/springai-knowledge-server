package com.lamb.springaiknowledgeserver.modules.system.role;

import com.lamb.springaiknowledgeserver.modules.system.role.Permission;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private Set<Permission> permissions;
    private Instant createdAt;
    private Instant updatedAt;
    private long userCount;
    private long documentCount;
    private boolean systemRole;

    public static RoleResponse from(Role role, long userCount, long documentCount, boolean systemRole) {
        Set<Permission> permissions = role.getPermissions() == null || role.getPermissions().isEmpty()
            ? EnumSet.noneOf(Permission.class)
            : EnumSet.copyOf(role.getPermissions());
        return new RoleResponse(
            role.getId(),
            role.getName(),
            permissions,
            role.getCreatedAt(),
            role.getUpdatedAt(),
            userCount,
            documentCount,
            systemRole
        );
    }
}


