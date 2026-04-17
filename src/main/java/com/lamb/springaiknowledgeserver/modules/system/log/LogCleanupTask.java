package com.lamb.springaiknowledgeserver.modules.system.log;

import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLogRepository;
import com.lamb.springaiknowledgeserver.modules.aiqa.feedback.QaFeedbackRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanupTask {

    private final OperationLogRepository operationLogRepository;
    private final QaLogRepository qaLogRepository;
    private final QaFeedbackRepository qaFeedbackRepository;

    /**
     * 每天凌晨 3 点清理 180 天前的日志数据
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        log.info("Starting log cleanup task...");
        Instant threshold = Instant.now().minus(180, ChronoUnit.DAYS);

        try {
            operationLogRepository.deleteAllByCreatedAtBefore(threshold);
            log.info("Cleaned up old operation logs.");

            qaLogRepository.deleteAllByCreatedAtBefore(threshold);
            log.info("Cleaned up old QA logs.");

            qaFeedbackRepository.deleteAllByCreatedAtBefore(threshold);
            log.info("Cleaned up old QA feedbacks.");

            log.info("Log cleanup task completed successfully.");
        } catch (Exception e) {
            log.error("Failed to cleanup old logs", e);
        }
    }
}
