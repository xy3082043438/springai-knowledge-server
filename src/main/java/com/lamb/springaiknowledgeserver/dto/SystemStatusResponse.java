package com.lamb.springaiknowledgeserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SystemStatusResponse {

    private String status;
    private boolean healthy;
    private String message;
}
