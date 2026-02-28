package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.config.SystemBoundaryText;
import com.lamb.springaiknowledgeserver.dto.SystemBoundaryResponse;
import com.lamb.springaiknowledgeserver.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/boundary")
    public SystemBoundaryResponse boundary() {
        String boundary = systemConfigService.getString(
            "system.boundary",
            SystemBoundaryText.DEFAULT_BOUNDARY
        );
        return new SystemBoundaryResponse(boundary);
    }
}
