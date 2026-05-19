package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class QaLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(QaLogCleanupJob.class);
    private static final long RETENTION_DAYS = 90;

    private final QaLogRepository qaLogRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldLogs() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        try {
            qaLogRepository.deleteAllByCreatedAtBefore(cutoff);
            log.info("[QaLog 清理] 删除 {} 之前的问答日志完成", cutoff);
        } catch (Exception e) {
            log.error("[QaLog 清理] 删除旧日志失败", e);
        }
    }
}
