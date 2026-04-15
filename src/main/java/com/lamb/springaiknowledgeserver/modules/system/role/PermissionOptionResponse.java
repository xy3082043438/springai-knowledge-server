package com.lamb.springaiknowledgeserver.modules.system.role;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PermissionOptionResponse {

    private String code;
    private String category;
    private String categoryLabel;
    private String label;
    private String description;
}


