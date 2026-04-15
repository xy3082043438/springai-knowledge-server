package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import com.lamb.springaiknowledgeserver.modules.system.dashboard.SystemStatusResponse;
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

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/status")
    public SystemStatusResponse status() {
        var health = healthEndpoint.health();
        String code = health.getStatus().getCode();
        boolean healthy = Status.UP.equals(health.getStatus());
        String message = healthy ? "运行正常" : "运行异常";
        return new SystemStatusResponse(code, healthy, message);
    }
}



