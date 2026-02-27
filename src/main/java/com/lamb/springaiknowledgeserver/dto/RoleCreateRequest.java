package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    private Set<Permission> permissions;
}
