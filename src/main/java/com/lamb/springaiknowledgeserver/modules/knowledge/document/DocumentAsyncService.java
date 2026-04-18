package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAsyncService.class);
    private final DocumentRepository documentRepository;
    private final DocumentProcessorHelper processorHelper;
    private final QaService qaService;

    @Async
    @Transactional
    public void processDocumentAsync(Long documentId, byte[] fileBytes, String contentType, String fileName) {
        Document document = findDocumentWithRetry(documentId);
        if (document == null) {
            log.error("[解析任务] 经过重试仍未能从数据库找到文档 ID: {} (文件名: {})", documentId, fileName);
            return;
        }

        log.info("[解析任务] 开始解析文档: {} (ID: {})", fileName, documentId);
        try {
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            processorHelper.processAndIndex(document, fileBytes, contentType, fileName);
            
            String suggestions = qaService.generateSuggestedQuestions(document.getTitle(), document.getContent());
            document.setSuggestedQuestions(suggestions);

            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
            log.info("[解析任务] 解析成功: {} (ID: {})", fileName, documentId);
        } catch (Exception e) {
            log.error("[解析任务] 解析失败: {} (ID: {}), 原因: {}", fileName, documentId, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
        } finally {
            documentRepository.save(document);
        }
    }

    @Async
    @Transactional
    public void reindexAsync(Long documentId) {
        Document document = findDocumentWithRetry(documentId);
        if (document == null) {
            log.error("[重索引] 经过重试仍未能从数据库找到文档 ID: {}", documentId);
            return;
        }

        log.info("[重索引] 开始处理文档: {} (ID: {})", document.getFileName(), documentId);
        try {
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            processorHelper.rebuildChunks(document);

            String suggestions = qaService.generateSuggestedQuestions(document.getTitle(), document.getContent());
            document.setSuggestedQuestions(suggestions);

            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
            log.info("[重索引] 处理成功: {} (ID: {})", document.getFileName(), documentId);
        } catch (Exception e) {
            log.error("[重索引] 处理失败: {} (ID: {}), 原因: {}", document.getFileName(), documentId, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
        } finally {
            documentRepository.save(document);
        }
    }

    private Document findDocumentWithRetry(Long documentId) {
        int attempts = 0;
        int maxAttempts = 5;
        long delayMs = 500;
        
        while (attempts < maxAttempts) {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                return document;
            }
            
            attempts++;
            if (attempts < maxAttempts) {
                log.debug("[解析任务] 文档记录尚不可见，正在进行第 {} 次重试... (ID: {})", attempts, documentId);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
}
