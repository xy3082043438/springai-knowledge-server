package com.lamb.springaiknowledgeserver.core.bootstrap;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.Document;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentRepository;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时检查是否有卡在“解析中”状态的文档。
 * 这些文档通常是因为服务器非正常关闭导致后台异步任务丢失。
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class DocumentStatusRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentStatusRecovery.class);
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[启动自检] 正在检查异常停滞的解析任务...");
        List<Document> parsingDocs = documentRepository.findByStatus(DocumentStatus.PARSING);
        
        if (parsingDocs.isEmpty()) {
            log.info("[启动自检] 未发现停滞的任务。");
            return;
        }

        log.warn("[启动自检] 发现 {} 个可能由于服务重启而中断的解析任务。正在重置状态...", parsingDocs.size());
        
        for (Document doc : parsingDocs) {
            log.info("[启动自检] 重置文档状态: {} (ID: {})", doc.getTitle(), doc.getId());
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage("服务器在解析过程中重启，请尝试手动“重索引”。");
        }
        
        documentRepository.saveAll(parsingDocs);
        log.info("[启动自检] 任务状态重置完毕。");
    }
}
