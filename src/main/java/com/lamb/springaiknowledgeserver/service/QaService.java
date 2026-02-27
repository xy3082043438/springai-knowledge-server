package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import com.lamb.springaiknowledgeserver.dto.QaSourceResponse;
import com.lamb.springaiknowledgeserver.config.PromptTemplates;
import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QaService {

    private static final String DEFAULT_NO_ANSWER = "未在知识库中找到相关信息。";

    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final SystemConfigService systemConfigService;

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

    public QaResponse answer(String roleName, String question) {
        List<HybridSearchService.HybridChunk> chunks = hybridSearchService.search(roleName, question);
        chunks = rerankService.rerank(question, chunks);
        if (chunks.isEmpty()) {
            return new QaResponse(DEFAULT_NO_ANSWER, List.of(), List.of());
        }

        String context = buildContext(chunks);
        OpenAiChatOptions options = buildChatOptions();
        String answer = chatClient.prompt()
            .options(options)
            .system(systemPrompt())
            .user(userPrompt(question, context))
            .call()
            .content();
        if (answer == null || answer.isBlank()) {
            answer = DEFAULT_NO_ANSWER;
        }

        List<DocumentResponse> documents = resolveDocuments(chunks, roleName);
        List<QaSourceResponse> sources = buildSources(chunks);
        return new QaResponse(answer, documents, sources);
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
}
