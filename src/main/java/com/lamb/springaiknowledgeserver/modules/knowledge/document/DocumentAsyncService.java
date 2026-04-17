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
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) return;

        try {
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            processorHelper.processAndIndex(document, fileBytes, contentType, fileName);

            // Option B: Generate suggested questions
            String suggestions = qaService.generateSuggestedQuestions(document.getTitle(), document.getContent());
            document.setSuggestedQuestions(suggestions);

            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
        } catch (Exception e) {
            log.error("异步解析文档失败 [ID: {}]", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
        } finally {
            documentRepository.save(document);
        }
    }

    @Async
    @Transactional
    public void reindexAsync(Long documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) return;

        try {
            document.setStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            processorHelper.rebuildChunks(document);

            // Option B: Generate suggested questions
            String suggestions = qaService.generateSuggestedQuestions(document.getTitle(), document.getContent());
            document.setSuggestedQuestions(suggestions);

            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
        } catch (Exception e) {
            log.error("异步重索引失败 [ID: {}]", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
        } finally {
            documentRepository.save(document);
        }
    }
}
