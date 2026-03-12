package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import com.lamb.springaiknowledgeserver.dto.QaSourceResponse;
import com.lamb.springaiknowledgeserver.config.PromptTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.QaLog;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.DocumentRepository;
import com.lamb.springaiknowledgeserver.repository.QaLogRepository;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class QaService {

    private static final String DEFAULT_NO_ANSWER = "未在知识库中找到相关信息。";
    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final SystemConfigService systemConfigService;
    private final QaLogRepository qaLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.answer-style:简洁、准确、专业}")
    private String answerStyle;

    @Value("${app.rag.max-answer-chars:0}")
    private int maxAnswerChars;

    @Value("${app.rag.max-output-tokens:512}")
    private int maxOutputTokens;

    @Value("${app.rag.temperature:0.2}")
    private double temperature;

    @Value("${app.rag.top-p:0.9}")
    private double topP;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String defaultChatModel;

    public QaResponse answer(Long userId, String username, String roleName, String question) {
        List<HybridSearchService.HybridChunk> chunks;
        try {
            chunks = hybridSearchService.search(roleName, question);
            chunks = rerankService.rerank(question, chunks);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "检索失败", ex);
        }
        List<DocumentResponse> documents = List.of();
        List<QaSourceResponse> sources = List.of();
        if (chunks.isEmpty()) {
            Long qaLogId = logQa(userId, username, DEFAULT_NO_ANSWER, documents, roleName, question, sources);
            return new QaResponse(DEFAULT_NO_ANSWER, List.of(), List.of(), qaLogId);
        }

        String context = buildContext(chunks);
        OpenAiChatOptions options = buildChatOptions();
        String answer;
        try {
            answer = chatClient.prompt()
                .options(options)
                .system(systemPrompt())
                .user(userPrompt(question, context))
                .call()
                .content();
        } catch (Exception ex) {
            if (isTimeoutException(ex)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "模型调用超时，请稍后重试", ex);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "模型调用失败", ex);
        }
        if (answer == null || answer.isBlank()) {
            answer = DEFAULT_NO_ANSWER;
        }
        answer = enforceMaxAnswerChars(answer);

        documents = resolveDocuments(chunks, roleName);
        sources = buildSources(chunks);
        Long qaLogId = logQa(userId, username, answer, documents, roleName, question, sources);
        return new QaResponse(answer, documents, sources, qaLogId);
    }

    private String buildContext(List<HybridSearchService.HybridChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (HybridSearchService.HybridChunk chunk : chunks) {
            String title = Objects.toString(chunk.title(), "未知文档");
            String pageText = chunk.pageNumber() > 0 ? "第" + chunk.pageNumber() + "页" : "";
            builder.append("片段 ").append(index++).append("（")
                .append(title);
            if (!pageText.isBlank()) {
                builder.append("，").append(pageText);
            }
            builder.append("）:\n");
            builder.append(chunk.content() == null ? "" : chunk.content());
            builder.append("\n\n");
        }
        return builder.toString().trim();
    }

    private List<DocumentResponse> resolveDocuments(List<HybridSearchService.HybridChunk> chunks, String roleName) {
        Set<Long> docIds = new LinkedHashSet<>();
        for (HybridSearchService.HybridChunk chunk : chunks) {
            Long id = chunk.documentId();
            if (id != null) {
                docIds.add(id);
            }
        }
        if (docIds.isEmpty()) {
            return List.of();
        }
        List<Document> documents = documentRepository.findAllById(docIds);
        List<DocumentResponse> responses = new ArrayList<>();
        for (Long id : docIds) {
            Document document = findDocument(documents, id);
            if (document == null || !hasRoleAccess(document, roleName)) {
                continue;
            }
            responses.add(DocumentResponse.from(document));
        }
        return responses;
    }

    private List<QaSourceResponse> buildSources(List<HybridSearchService.HybridChunk> chunks) {
        List<QaSourceResponse> sources = new ArrayList<>();
        for (HybridSearchService.HybridChunk chunk : chunks) {
            sources.add(new QaSourceResponse(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.title(),
                chunk.fileName(),
                chunk.pageNumber(),
                chunk.chunkIndex(),
                chunk.startOffset(),
                chunk.endOffset(),
                chunk.combinedScore(),
                chunk.content()
            ));
        }
        return sources;
    }

    private Document findDocument(List<Document> documents, Long id) {
        for (Document document : documents) {
            if (Objects.equals(document.getId(), id)) {
                return document;
            }
        }
        return null;
    }

    private boolean hasRoleAccess(Document document, String roleName) {
        for (Role role : document.getAllowedRoles()) {
            if (roleName.equals(role.getName())) {
                return true;
            }
        }
        return false;
    }

    private String systemPrompt() {
        String template = systemConfigService.getString("rag.prompt.system", PromptTemplates.SYSTEM_TEMPLATE);
        String style = resolveAnswerStyle();
        int maxChars = resolveMaxAnswerChars();
        String prompt = template
            .replace("{answerStyle}", style == null ? "" : style)
            .replace("{maxAnswerChars}", maxChars > 0 ? String.valueOf(maxChars) : "");
        return cleanupPrompt(prompt);
    }

    private String userPrompt(String question, String context) {
        String template = systemConfigService.getString("rag.prompt.user", PromptTemplates.USER_TEMPLATE);
        return cleanupPrompt(template
            .replace("{question}", question == null ? "" : question)
            .replace("{context}", context == null ? "" : context));
    }

    private OpenAiChatOptions buildChatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        int maxTokens = resolveMaxOutputTokens();
        if (maxTokens > 0) {
            builder.maxTokens(maxTokens);
        }
        double resolvedTemperature = resolveTemperature();
        if (resolvedTemperature >= 0) {
            builder.temperature(resolvedTemperature);
        }
        double resolvedTopP = resolveTopP();
        if (resolvedTopP > 0 && resolvedTopP <= 1) {
            builder.topP(resolvedTopP);
        }
        if (shouldDisableThinking()) {
            builder.extraBody(Map.of("enable_thinking", false));
        }
        return builder.build();
    }

    private String resolveAnswerStyle() {
        return systemConfigService.getString("rag.answerStyle", answerStyle);
    }

    private int resolveMaxAnswerChars() {
        return systemConfigService.getInt("rag.maxAnswerChars", maxAnswerChars);
    }

    private int resolveMaxOutputTokens() {
        return systemConfigService.getInt("rag.maxOutputTokens", maxOutputTokens);
    }

    private double resolveTemperature() {
        return systemConfigService.getDouble("rag.temperature", temperature);
    }

    private double resolveTopP() {
        return systemConfigService.getDouble("rag.topP", topP);
    }

    private boolean shouldDisableThinking() {
        return defaultChatModel != null && defaultChatModel.startsWith("Qwen/Qwen3");
    }

    private String cleanupPrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        String cleaned = prompt.replaceAll("[ \\t]+\\n", "\n").trim();
        if (cleaned.contains("{maxAnswerChars}") || cleaned.contains("{answerStyle}")) {
            cleaned = cleaned.replace("{maxAnswerChars}", "").replace("{answerStyle}", "").trim();
        }
        return cleaned;
    }

    private String enforceMaxAnswerChars(String answer) {
        if (answer == null) {
            return null;
        }
        int limit = resolveMaxAnswerChars();
        if (limit <= 0 || answer.length() <= limit) {
            return answer;
        }
        if (limit <= 3) {
            return answer.substring(0, limit);
        }
        return answer.substring(0, limit - 3) + "...";
    }

    private Long logQa(
        Long userId,
        String username,
        String answer,
        List<DocumentResponse> documents,
        String roleName,
        String question,
        List<QaSourceResponse> sources
    ) {
        try {
            QaLog log = new QaLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setQuestion(question);
            log.setAnswer(answer);
            log.setRoleName(roleName);
            int topKValue = sources.size();
            log.setTopK(topKValue == 0 ? null : topKValue);
            log.setRetrievalJson(buildRetrievalJson(documents, sources));
            QaLog saved = qaLogRepository.save(log);
            return saved.getId();
        } catch (Exception ex) {
            // Avoid impacting QA flow.
            log.debug("Failed to save QA log", ex);
        }
        return null;
    }

    private String buildRetrievalJson(List<DocumentResponse> documents, List<QaSourceResponse> sources) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("documents", documents);
            payload.put("sources", sources);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.debug("Failed to serialize retrieval payload", ex);
            return null;
        }
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
