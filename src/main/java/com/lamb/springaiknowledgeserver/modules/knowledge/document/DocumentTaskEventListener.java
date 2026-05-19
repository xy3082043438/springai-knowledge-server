package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamb.springaiknowledgeserver.core.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentTaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentTaskEventListener.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentTask(DocumentTaskEvent event) {
        try {
            DocumentTaskMessage msg = new DocumentTaskMessage(
                event.documentId(), event.action(), event.contentType(), event.fileName());
            rabbitTemplate.convertAndSend(
                RabbitConfig.DOCUMENT_EXCHANGE,
                RabbitConfig.DOCUMENT_ROUTING_KEY,
                objectMapper.writeValueAsString(msg)
            );
        } catch (JsonProcessingException e) {
            log.error("序列化文档任务消息失败 docId={} action={}", event.documentId(), event.action(), e);
        } catch (Exception e) {
            log.error("发送文档任务消息失败 docId={} action={}", event.documentId(), event.action(), e);
        }
    }
}
