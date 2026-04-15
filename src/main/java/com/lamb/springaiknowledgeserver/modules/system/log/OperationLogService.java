package com.lamb.springaiknowledgeserver.modules.system.log;

import com.lamb.springaiknowledgeserver.modules.system.log.OperationLog;
import com.lamb.springaiknowledgeserver.modules.system.log.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogRepository operationLogRepository;

    public void log(
        Long userId,
        String username,
        String action,
        String resource,
        String resourceId,
        String detail,
        String ip,
        boolean success
    ) {
        try {
            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setAction(action);
            log.setResource(resource);
            log.setResourceId(resourceId);
            log.setDetail(detail);
            log.setIp(ip);
            log.setSuccess(success);
            operationLogRepository.save(log);
        } catch (Exception ex) {
            // Do not break business flow on log failure.
            log.debug("Failed to write operation log", ex);
        }
    }
}


