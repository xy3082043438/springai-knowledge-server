package com.lamb.springaiknowledgeserver.modules.system.role;

import com.lamb.springaiknowledgeserver.security.auth.UserPrincipal;
import com.lamb.springaiknowledgeserver.modules.system.role.PermissionOptionResponse;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleCreateRequest;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleResponse;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleUpdateRequest;
import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogService;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleService;
import com.lamb.springaiknowledgeserver.core.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final OperationLogService operationLogService;

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping
    public List<RoleResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        List<RoleResponse> response = roleService.listRoleResponses();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_LIST",
            "ROLE",
            null,
            "list",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping("/{id}")
    public RoleResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        HttpServletRequest httpRequest
    ) {
        RoleResponse response = roleService.getResponseById(id);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_VIEW",
            "ROLE",
            String.valueOf(id),
            "role=" + response.getName(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping("/permissions")
    public List<PermissionOptionResponse> permissions(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        List<PermissionOptionResponse> response = roleService.listPermissionOptions();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_PERMISSION_LIST",
            "ROLE",
            null,
            "permissions",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @PostMapping
    public RoleResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody RoleCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        RoleResponse response = roleService.toResponse(roleService.create(request));
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_CREATE",
            "ROLE",
            String.valueOf(response.getId()),
            "role=" + response.getName(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @PatchMapping("/{id}")
    public RoleResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        @Valid @RequestBody RoleUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        RoleResponse response = roleService.toResponse(roleService.update(id, request));
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_UPDATE",
            "ROLE",
            String.valueOf(response.getId()),
            "role=" + response.getName(),
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        HttpServletRequest httpRequest
    ) {
        roleService.delete(id);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "ROLE_DELETE",
            "ROLE",
            String.valueOf(id),
            "delete",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return ResponseEntity.noContent().build();
    }
}



