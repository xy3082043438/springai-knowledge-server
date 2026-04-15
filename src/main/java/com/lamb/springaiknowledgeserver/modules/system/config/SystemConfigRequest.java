package com.lamb.springaiknowledgeserver.modules.system.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemConfigRequest {

    @NotBlank
    private String value;

    private String description;
}


