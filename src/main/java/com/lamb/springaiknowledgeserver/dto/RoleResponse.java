package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.Permission;
import com.lamb.springaiknowledgeserver.entity.Role;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private Set<Permission> permissions;

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getPermissions());
    }
}
