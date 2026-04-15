package com.lamb.springaiknowledgeserver.modules.system.config;

import com.lamb.springaiknowledgeserver.modules.system.config.SystemConfig;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SystemConfigResponse {

    private String key;
    private String value;
    private String description;
    private Instant updatedAt;

    public static SystemConfigResponse from(SystemConfig config) {
        return new SystemConfigResponse(
            config.getConfigKey(),
            config.getConfigValue(),
            config.getDescription(),
            config.getUpdatedAt()
        );
    }
}


