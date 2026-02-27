package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.dto.RoleCreateRequest;
import com.lamb.springaiknowledgeserver.dto.RoleResponse;
import com.lamb.springaiknowledgeserver.dto.RoleUpdateRequest;
import com.lamb.springaiknowledgeserver.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping
    public List<RoleResponse> list() {
        return roleService.listRoles().stream()
            .map(RoleResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping("/{id}")
    public RoleResponse get(@PathVariable Long id) {
        return RoleResponse.from(roleService.getById(id));
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @PostMapping
    public RoleResponse create(@Valid @RequestBody RoleCreateRequest request) {
        return RoleResponse.from(roleService.create(request));
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @PatchMapping("/{id}")
    public RoleResponse update(
        @PathVariable Long id,
        @Valid @RequestBody RoleUpdateRequest request
    ) {
        return RoleResponse.from(roleService.update(id, request));
    }
}
