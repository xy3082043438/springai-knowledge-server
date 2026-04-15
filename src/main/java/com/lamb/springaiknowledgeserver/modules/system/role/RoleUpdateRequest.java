package com.lamb.springaiknowledgeserver.modules.system.role;

import com.lamb.springaiknowledgeserver.modules.system.role.Permission;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {

    @Size(max = 64)
    private String name;
    private Set<Permission> permissions;
}


