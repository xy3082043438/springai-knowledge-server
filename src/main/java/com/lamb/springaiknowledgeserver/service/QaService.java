package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import com.lamb.springaiknowledgeserver.dto.QaSourceResponse;
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
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业知识库问答助手。请严格基于提供的“上下文”回答问题。\n");
        builder.append("如果上下文没有相关信息，请直接回答“未在知识库中找到相关信息。”不要编造答案。\n");
        builder.append("回答要面向企业内部场景。\n");
        if (answerStyle != null && !answerStyle.isBlank()) {
            builder.append("回答风格：").append(answerStyle).append("。\n");
        }
        if (maxAnswerChars > 0) {
            builder.append("回答字数不超过 ").append(maxAnswerChars).append(" 字。\n");
        }
        return builder.toString().trim();
    }

    private String userPrompt(String question, String context) {
        return """
            问题:
            %s

            上下文:
            %s
            """.formatted(question, context);
    }

    private OpenAiChatOptions buildChatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (maxOutputTokens > 0) {
            builder.maxTokens(maxOutputTokens);
        }
        if (temperature >= 0) {
            builder.temperature(temperature);
        }
        if (topP > 0 && topP <= 1) {
            builder.topP(topP);
        }
        return builder.build();
    }
}
