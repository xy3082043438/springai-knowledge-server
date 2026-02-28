package com.lamb.springaiknowledgeserver.controller;

import com.lamb.springaiknowledgeserver.config.SystemBoundaryText;
import com.lamb.springaiknowledgeserver.dto.SystemBoundaryResponse;
import com.lamb.springaiknowledgeserver.dto.SystemStatusResponse;
import com.lamb.springaiknowledgeserver.service.SystemConfigService;
import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemConfigService systemConfigService;
    private final DataSource dataSource;

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
        boolean healthy = true;
        String status = "UP";
        String message = "运行正常";
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                healthy = false;
                status = "DOWN";
                message = "数据库连接不可用";
            }
        } catch (Exception ex) {
            healthy = false;
            status = "DOWN";
            message = "数据库连接不可用";
        }
        return new SystemStatusResponse(status, healthy, message);
    }
}
