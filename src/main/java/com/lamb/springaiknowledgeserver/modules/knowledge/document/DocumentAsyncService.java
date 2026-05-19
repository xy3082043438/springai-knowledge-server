package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamb.springaiknowledgeserver.core.config.RabbitConfig;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAsyncService.class);
    private final DocumentRepository documentRepository;
    private final DocumentProcessorHelper processorHelper;
    private final QaService qaService;
    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private DocumentAsyncService self;

    @RabbitListener(queues = RabbitConfig.DOCUMENT_PROCESS_QUEUE)
    public void receiveDocumentTask(
        String messageJson,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        DocumentTaskMessage message;
        try {
            message = objectMapper.readValue(messageJson, DocumentTaskMessage.class);
        } catch (Exception e) {
            log.error("无法解析MQ消息,丢弃: {}", messageJson, e);
            // 无法解析的脏消息直接 ACK 丢弃,避免无限重投
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long documentId = message.getDocumentId();
        String action = message.getAction();

        try {
            if ("PARSE".equals(action)) {
                self.processDocument(documentId, message.getContentType(), message.getFileName());
            } else if ("REINDEX".equals(action)) {
                self.reindexDocument(documentId);
            } else {
                log.warn("未知的文档任务操作类型: {}", action);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理MQ消息失败 docId={} action={}", documentId, action, e);
            // 不重新入队,依赖业务侧的失败状态记录,避免重试风暴
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @Transactional
    public void processDocument(Long documentId, String contentType, String fileName) {
        Document document = findDocumentWithRetry(documentId);
        if (document == null) {
            log.error("[解析任务] 经过重试仍未能从数据库找到文档 ID: {} (文件名: {})", documentId, fileName);
            return;
        }

        // 幂等性: 已经成功的文档不再处理
        if (document.getStatus() == DocumentStatus.READY) {
            log.info("[解析任务] 文档已为 READY 状态,跳过重复消费 (ID: {})", documentId);
            return;
        }

        log.info("[解析任务] 开始解析文档: {} (ID: {})", fileName, documentId);
        try {
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            Path storagePath = Paths.get(document.getStoragePath());
            byte[] fileBytes = Files.readAllBytes(storagePath);

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

    @Transactional
    public void reindexDocument(Long documentId) {
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
