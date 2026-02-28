package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.config.SystemBoundaryText;
import com.lamb.springaiknowledgeserver.dto.SystemBoundaryResponse;
import com.lamb.springaiknowledgeserver.dto.SystemStatusResponse;
import com.lamb.springaiknowledgeserver.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemConfigService systemConfigService;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/boundary")
    public SystemBoundaryResponse boundary() {
        String boundary = systemConfigService.getString(
            "system.boundary",
            SystemBoundaryText.DEFAULT_BOUNDARY
        );
        return new SystemBoundaryResponse(boundary);
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        var health = healthEndpoint.health();
        String code = health.getStatus().getCode();
        boolean healthy = Status.UP.equals(health.getStatus());
        String message = healthy ? "运行正常" : "运行异常";
        return new SystemStatusResponse(code, healthy, message);
    }
}
