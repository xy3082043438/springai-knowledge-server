package com.lamb.springaiknowledgeserver.modules.system.log;

import com.lamb.springaiknowledgeserver.modules.system.log.OperationLog;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OperationLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resource;
    private String resourceId;
    private String detail;
    private String ip;
    private boolean success;
    private Instant createdAt;

    public static OperationLogResponse from(OperationLog log) {
        return new OperationLogResponse(
            log.getId(),
            log.getUserId(),
            log.getUsername(),
            log.getAction(),
            log.getResource(),
            log.getResourceId(),
            log.getDetail(),
            log.getIp(),
            log.isSuccess(),
            log.getCreatedAt()
        );
    }
}


