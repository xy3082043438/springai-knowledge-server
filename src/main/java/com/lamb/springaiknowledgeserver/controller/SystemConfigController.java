package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.dto.SystemConfigRequest;
import com.lamb.springaiknowledgeserver.dto.SystemConfigResponse;
import com.lamb.springaiknowledgeserver.entity.SystemConfig;
import com.lamb.springaiknowledgeserver.service.OperationLogService;
import com.lamb.springaiknowledgeserver.service.SystemConfigService;
import com.lamb.springaiknowledgeserver.util.RequestUtils;
import com.lamb.springaiknowledgeserver.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final OperationLogService operationLogService;

    @PreAuthorize("hasAuthority('CONFIG_READ')")
    @GetMapping
    public List<SystemConfigResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        List<SystemConfigResponse> response = systemConfigService.listAll().stream()
            .map(SystemConfigResponse::from)
            .toList();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "CONFIG_LIST",
            "CONFIG",
            null,
            "list",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('CONFIG_READ')")
    @GetMapping("/{key}")
    public SystemConfigResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String key,
        HttpServletRequest httpRequest
    ) {
        SystemConfig config = systemConfigService.findByKey(key)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在"));
        SystemConfigResponse response = SystemConfigResponse.from(config);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "CONFIG_VIEW",
            "CONFIG",
            key,
            "config=" + key,
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    @PutMapping("/{key}")
    public SystemConfigResponse upsert(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String key,
        @Valid @RequestBody SystemConfigRequest request,
        HttpServletRequest httpRequest
    ) {
        SystemConfig saved = systemConfigService.upsert(key, request.getValue(), request.getDescription());
        SystemConfigResponse response = SystemConfigResponse.from(saved);
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "CONFIG_UPSERT",
            "CONFIG",
            key,
            "config=" + key,
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return response;
    }

    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
        @AuthenticationPrincipal UserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        systemConfigService.refresh();
        operationLogService.log(
            principal.getId(),
            principal.getUsername(),
            "CONFIG_REFRESH",
            "CONFIG",
            null,
            "refresh",
            RequestUtils.resolveClientIp(httpRequest),
            true
        );
        return ResponseEntity.noContent().build();
    }
}
