package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.dto.SystemConfigRequest;
import com.lamb.springaiknowledgeserver.dto.SystemConfigResponse;
import com.lamb.springaiknowledgeserver.entity.SystemConfig;
import com.lamb.springaiknowledgeserver.service.SystemConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('CONFIG_READ')")
    @GetMapping
    public List<SystemConfigResponse> list() {
        return systemConfigService.listAll().stream()
            .map(SystemConfigResponse::from)
            .toList();
    }

    @PreAuthorize("hasAuthority('CONFIG_READ')")
    @GetMapping("/{key}")
    public SystemConfigResponse get(@PathVariable String key) {
        SystemConfig config = systemConfigService.findByKey(key)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found"));
        return SystemConfigResponse.from(config);
    }

    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    @PutMapping("/{key}")
    public SystemConfigResponse upsert(@PathVariable String key, @Valid @RequestBody SystemConfigRequest request) {
        SystemConfig saved = systemConfigService.upsert(key, request.getValue(), request.getDescription());
        return SystemConfigResponse.from(saved);
    }

    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        systemConfigService.refresh();
        return ResponseEntity.noContent().build();
    }
}
